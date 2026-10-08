# 02 · Arquitectura Android

## Decisión clave: backend propio
**La app habla solo con el API de Orbita** (`atm-orbita-api`: NestJS + PostgreSQL + Prisma). No se usa Supabase ni ningún otro backend gestionado, y la autenticación es propia. Decisión del 7 oct 2026; reemplaza a la idea inicial de hablar directo con Supabase.

| Qué | Dónde vive |
|---|---|
| Autenticación | API: correo y contraseña, token de acceso y token de renovación |
| Datos | PostgreSQL del API |
| Seguridad por usuario | API: el usuario sale del token; toda consulta filtra por él; la base lo refuerza con claves foráneas compuestas |
| Reglas que deben ser **atómicas** (pagar una compra de crédito) | Transacciones en el API |
| Reportes con filtros y agregaciones | El API suma en la base; la app no descarga miles de filas |
| Tarea programada (tipo de cambio automático) | Tarea diaria del API |

El contrato (endpoints, formatos, errores) está en `atm-orbita-api/docs/plans/`. Lo que cambia en esta app para consumirlo está en su plan `14-integracion-android.md`.

## Vista general
```
┌────────────────────────── Android (Kotlin) ──────────────────────────┐
│  ui  (Compose screens + ViewModels, StateFlow<UiState>)               │
│   ▲                                                                   │
│  domain (modelos, borradores, Money/FX, repositorios)  ← Kotlin puro  │
│   ▲                                                                   │
│  data  (demo en memoria · remote con cliente HTTP y caché)            │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTPS · JSON · token de acceso
                ┌───────────────▼────────────────┐
                │ API de Orbita (NestJS)          │
                │ PostgreSQL + Prisma             │
                └─────────────────────────────────┘
```

## Stack
| Área | Elección |
|---|---|
| Lenguaje / UI | Kotlin, Jetpack Compose, Material 3 |
| Navegación | Pila propia en `ui/navigation`; migrar a Navigation Compose está pendiente |
| DI | Hilt |
| Asincronía | Coroutines + Flow |
| Red / backend | Cliente HTTP **Ktor** (motor OkHttp) con JSON, autenticación *bearer* y tiempos de espera |
| Serialización | kotlinx.serialization |
| Gráficos | Barras horizontales dibujadas con Compose (no hace falta librería) |
| Pruebas | JUnit, coroutines-test, Compose UI Test |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`) |
| `minSdk` | 29 (Android 10). `targetSdk`: el estable más reciente |
| Más adelante | Room + WorkManager (offline), Credential Manager (Google) |

> Las dependencias de Supabase (`supabase-bom`, `auth-kt`, `postgrest-kt`) y su código se retiraron el 7 oct 2026. `ktor-client-okhttp` se conserva para el cliente HTTP del API, que aún no está escrito.

## Estructura de paquetes (un módulo Gradle `app`, organizado por capas y funciones)
```
app/src/main/java/com/atmosferast/orbita/
├── core/            # formateadores, entrada de montos, monedas admitidas
├── domain/
│   ├── model/       # Account, Category, Movement, Transfer, CreditCard, CreditPurchase, FxPair, borradores
│   └── repository/  # interfaces, DataError, DateProvider
├── data/
│   ├── demo/        # implementación en memoria con los datos de ejemplo
│   └── remote/      # cliente HTTP, DTOs (@Serializable), TokenStore, repositorios contra el API
├── di/              # módulos Hilt: decide demo o API por repositorio
└── ui/
    ├── theme/       # colores, tipografía, formas (ver docs/05)
    ├── navigation/
    ├── components/
    └── feature/     # auth, home, accounts, movement, transfer, reports, credit, categories, settings
```
Si luego se quiere iOS con código compartido, `core` y `domain` se mueven a un módulo Kotlin Multiplatform; por eso no deben importar nada de Android.

## Dinero y monedas (reglas de implementación)
- Modelo: `BigDecimal` + `currency: String` (ISO 4217, mayúsculas).
- Escala 2, `RoundingMode.HALF_UP`. Se redondea **al final** de un cálculo, nunca en pasos intermedios.
- Tipo de cambio: `BigDecimal` con hasta 6 decimales (se muestra con 2–4). Convención: `rate` = unidades de la **moneda destino** por 1 unidad de la **moneda origen**.
  - Convertir `X` de A a B: `X × rate(A→B)`. Si solo se conoce B→A: `X ÷ rate(B→A)`.
- **El tipo de cambio puede no existir.** `FxPair.rate` es `BigDecimal?`. Un usuario nuevo no lo tiene hasta que lo escribe en Ajustes. Mientras sea nulo:
  - las conversiones entre monedas distintas no dan resultado;
  - la app muestra "Configura tu tipo de cambio" en Inicio, Cuentas y Ajustes;
  - no se puede crear una cuenta ni una tarjeta en una moneda distinta de la principal, ni ver el ahorro en la secundaria.
  - **Nunca se inventa un valor.** Al elegir un par de monedas nuevo, el campo del tipo de cambio queda **vacío** (antes volvía a `1.00`) y el par no se guarda hasta que el usuario escribe un valor mayor que cero. El API valida lo mismo.
  - El detalle del flujo está en `ORBITA_SPEC.md`, sección 7.12.
- Entrada de montos: acepta `.` o `,` como decimal, máximo 2 decimales, siempre `> 0`.
- Formato: símbolo + espacio + `#,##0.00` con locale que use `,` miles y `.` decimal. Símbolos: PEN → `S/`, USD → `US$`.
- **El API envía y recibe los montos como texto** (`"1245.80"`), nunca como número JSON, para que ninguna plataforma los pase por coma flotante. `BigDecimalSerializer` (en `data/remote`) lee tanto texto como número y siempre escribe texto con `toPlainString()`:
```kotlin
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): BigDecimal {
        val json = decoder as? JsonDecoder ?: error("Only JSON is supported")
        return BigDecimal(json.decodeJsonElement().jsonPrimitive.content)
    }
    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeString(value.toPlainString())
}
```

## Fechas y "hoy"
- La app trabaja con `LocalDate`. Las fechas viajan como `YYYY-MM-DD`, sin hora ni zona.
- `DateProvider` da "hoy": el reloj del dispositivo con datos reales; fijo en 2 oct 2026 en modo demo.
- **Quien decide qué día es hoy es el API**, con la zona horaria IANA guardada en el perfil (por ejemplo `America/Lima`). La app la envía al registrarse (`TimeZone.getDefault().id`).
- Un movimiento nuevo no envía fecha: el API le pone el día de hoy en la zona del usuario y lo guarda como fecha, no como instante.

## Flujo de datos
1. `ViewModel` expone `StateFlow<UiState>` (cargando, contenido, error, vacío según la pantalla).
2. La UI envía eventos al `ViewModel`; este llama a los repositorios (`suspend` o `Flow`).
3. Los repositorios remotos llaman al API y mapean DTO → modelo de dominio. Como REST no avisa de los cambios, cada uno mantiene una caché observable y la recarga tras cada escritura y al volver a primer plano.
4. Los errores llegan con un `code` estable; se convierten en un error de dominio (`DataError` o `ValidationError`) y se muestran en español.
5. Las listas largas se piden por páginas con cursor. Los reportes los calcula el API.

## Autenticación y sesión
- Correo + contraseña contra el API. La app recibe un **token de acceso** (15 minutos, solo en memoria) y un **token de renovación** (guardado cifrado con el Android Keystore).
- Al abrir la app con un token de renovación guardado, se renueva la sesión y se entra directo a Inicio.
- Ante un `401`, la app renueva **una sola vez a la vez** (con un `Mutex`) y repite la petición. Si la renovación falla, cierra la sesión.
- Al cerrar sesión: limpiar tokens y estado en memoria y volver a login. Debe funcionar sin red.
- Contraseña: mínimo 8 caracteres. Mensajes de error claros (credenciales inválidas, correo ya registrado, sin conexión).
- **Recuperar contraseña** ("¿Olvidaste tu contraseña?") y **eliminar la cuenta** (en Ajustes) son requisito de salida a producción.
- Más adelante: Google con Credential Manager.

## Navegación
- Rutas con barra inferior: `home`, `accounts`, `reports`, `credit` (esta última aparece en v1.1) y un botón central **+** que abre `movement/new` como pantalla completa.
- Pantallas secundarias: `transfer`, `settings`, `credit/pay/{id}`, `account/edit/{id}`, `movement/edit/{id}`.
- Si no hay sesión, solo `auth/login` y `auth/register`.

## Estrategia offline (planificada, no en el MVP)
- MVP: **solo online**.
- Más adelante: Room como fuente local de verdad + cola de operaciones pendientes con WorkManager + sincronización con el API usando `updated_at` y `deleted_at` (ya existen en el esquema) e IDs UUID generados en el cliente.
- En Room, los montos se guardan como **`Long` en unidades mínimas (céntimos)** para poder sumar en SQL sin errores; se convierten a `BigDecimal` en el borde.
- Los repositorios ya son interfaces, para poder cambiar la fuente de datos sin tocar UI ni dominio.

## Seguridad en la app
- La app solo conoce la **URL del API** (`API_BASE_URL` por `BuildConfig`, desde `local.properties`). No lleva ninguna clave. Perfiles `debug` (API local o de pruebas) y `release` (producción, solo HTTPS).
- **Respaldos desactivados**, tal como está en el manifiesto:
  - `android:allowBackup="false"` en `AndroidManifest.xml`.
  - `res/xml/data_extraction_rules.xml` (Android 12 o superior) excluye todos los dominios en `<cloud-backup>` y en `<device-transfer>`. Hace falta además del atributo porque, desde Android 12, `allowBackup="false"` no impide la transferencia directa entre dispositivos.
  - `res/xml/backup_rules.xml` (Android 11 o inferior) excluye todos los dominios.
  - Motivo: el token de sesión no debe salir del teléfono. Los datos se recuperan del API al iniciar sesión en el dispositivo nuevo.
- El token de renovación se guarda cifrado; ni los tokens ni los cuerpos de autenticación se escriben en los registros.
- Release con R8 activado; solo tráfico HTTPS.
- Opcional más adelante: bloqueo con biometría.

## Tema y diseño
Los tokens (colores, tipografía, formas) están en `docs/05-pantallas-y-flujos.md`, sección 1. Implementarlos en `ui/theme` como esquema de color de Material 3 + colores semánticos extra (ingreso, egreso, neutro). Objetivos táctiles ≥ 48 dp; contraste de texto ≥ 4.5:1; los colores de ingreso/egreso siempre acompañados de signo (+/−).
