# 02 · Arquitectura Android

## Decisión clave: ¿hace falta backend propio?
**No.** La app habla directo con Supabase (Auth + API REST de Postgres, "PostgREST"). La seguridad la imponen las políticas **RLS** en la base de datos, no un servidor intermedio.

| Qué | Dónde vive |
|---|---|
| Autenticación | Supabase Auth |
| Datos | Postgres (Supabase) |
| Seguridad por usuario | RLS (políticas en SQL) |
| Reglas que deben ser **atómicas** (pagar una compra de crédito) | Funciones SQL (RPC) en Postgres |
| Reportes con filtros y agregaciones | Funciones SQL (RPC) / vistas — la app no descarga miles de filas para sumar |
| Tarea programada (tipo de cambio automático, más adelante) | Edge Function + cron de Supabase |

Se agrega un servidor propio **solo** si algún día hay lógica que no se pueda expresar bien en SQL o en una Edge Function.

## Vista general
```
┌────────────────────────── Android (Kotlin) ──────────────────────────┐
│  ui  (Compose screens + ViewModels, StateFlow<UiState>)               │
│   ▲                                                                   │
│  domain (modelos, casos de uso, Money/FX)  ← Kotlin puro              │
│   ▲                                                                   │
│  data  (repositorios, DTOs, mappers, supabase-kt)                     │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTPS (JWT del usuario)
                ┌───────────────▼────────────────┐
                │ Supabase: Auth · PostgREST · DB │
                │ RLS + vistas + funciones SQL    │
                └─────────────────────────────────┘
```

## Stack
| Área | Elección |
|---|---|
| Lenguaje / UI | Kotlin, Jetpack Compose, Material 3 |
| Navegación | Navigation Compose (barra inferior + pantallas modales) |
| DI | Hilt |
| Asincronía | Coroutines + Flow |
| Red / backend | `supabase-kt`: módulos `auth-kt` y `postgrest-kt` (+ motor Ktor, p. ej. OkHttp) |
| Serialización | kotlinx.serialization |
| Gráficos | Barras horizontales dibujadas con Compose (no hace falta librería) |
| Pruebas | JUnit, MockK, Turbine, Compose UI Test |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`) |
| `minSdk` | 26 (Android 8.0). `targetSdk`: el estable más reciente |
| Más adelante | Room + WorkManager (offline), `PdfDocument` (PDF), Credential Manager (Google) |

> Verifica coordenadas y API de `supabase-kt` en su documentación oficial al crear el proyecto; cambian entre versiones.

## Estructura de paquetes (un módulo Gradle `app`, organizado por capas y funciones)
```
app/src/main/java/<applicationId>/
├── core/            # Money, Fx, formateadores, Result/errores, utilidades
├── domain/
│   ├── model/       # Account, Category, Transaction, Transfer, CreditPurchase, ExchangeRate
│   ├── repository/  # interfaces
│   └── usecase/     # ConvertAmount, ComputeSavingsTotal, RecalculateTransfer, ...
├── data/
│   ├── remote/      # SupabaseClient, DTOs (@Serializable), datasources Postgrest
│   ├── mapper/
│   └── repository/  # implementaciones
├── di/              # módulos Hilt
└── ui/
    ├── theme/       # colores, tipografía, formas (ver docs/05)
    ├── navigation/
    ├── components/  # AmountText, ChipGroup, SwitchRow, SegmentedControl, ...
    └── feature/     # auth, home, accounts, movement, transfer, reports, credit, settings
```
Si luego se quiere iOS con código compartido, `core` y `domain` se mueven a un módulo Kotlin Multiplatform; por eso no deben importar nada de Android.

## Dinero y monedas (reglas de implementación)
- Modelo: `Money(amount: BigDecimal, currency: Currency)` o `BigDecimal` + `currency: String` (ISO 4217, mayúsculas).
- Escala 2, `RoundingMode.HALF_UP`. Se redondea **al final** de un cálculo, nunca en pasos intermedios.
- Tipo de cambio: `BigDecimal` con hasta 6 decimales (se muestra con 2–4). Convención: `rate` = unidades de la **moneda destino** por 1 unidad de la **moneda origen**.
  - Convertir `X` de A a B: `X × rate(A→B)`. Si solo se conoce B→A: `X ÷ rate(B→A)`.
- Entrada de montos: acepta `.` o `,` como decimal, máximo 2 decimales, siempre `> 0`.
- Formato: símbolo + espacio + `#,##0.00` con locale que use `,` miles y `.` decimal. Símbolos: PEN → `S/`, USD → `US$`.
- **PostgREST devuelve `numeric` como número JSON.** Si se deserializa con `Double` se pierde exactitud. Usar un serializador propio para `BigDecimal`:
```kotlin
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): BigDecimal {
        val json = decoder as? JsonDecoder ?: error("Only JSON is supported")
        return BigDecimal(json.decodeJsonElement().jsonPrimitive.content)
    }
    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeString(value.toPlainString()) // PostgREST convierte el texto a numeric
}
```

## Flujo de datos
1. `ViewModel` expone `StateFlow<UiState>` (`Loading`, `Content`, `Error`, `Empty` según la pantalla).
2. La UI envía eventos al `ViewModel`; este llama a casos de uso / repositorios (`suspend` o `Flow`).
3. Los repositorios llaman a Supabase y mapean DTO → modelo de dominio.
4. Errores de red/validación se convierten en un tipo de error de dominio y se muestran con mensajes en español (Snackbar o texto en el campo).
5. **Consultas de lista paginadas** (`range`) y ordenadas por fecha descendente. Los reportes usan las funciones SQL de `docs/03`, no cálculo en el cliente.

## Autenticación y sesión
- Correo + contraseña con `supabase-kt` Auth. La librería persiste la sesión y renueva el token; la app observa el estado de sesión y navega a login o a Inicio.
- Al cerrar sesión: limpiar estado en memoria y volver a login.
- Contraseña: mínimo 8 caracteres. Mensajes de error claros (credenciales inválidas, correo ya registrado, sin conexión).
- Más adelante: Google con Credential Manager.

## Navegación
- Rutas con barra inferior: `home`, `accounts`, `reports`, `credit` (esta última aparece en v1.1) y un botón central **+** que abre `movement/new` como pantalla completa.
- Pantallas secundarias: `transfer`, `settings`, `credit/pay/{id}`, `account/edit/{id}`, `movement/edit/{id}`.
- Si no hay sesión, solo `auth/login` y `auth/register`.

## Estrategia offline (planificada, no en el MVP)
- MVP: **solo online**.
- Más adelante: Room como fuente local de verdad + cola de operaciones pendientes con WorkManager + sincronización con Supabase usando `updated_at` y `deleted_at` (ya existen en el esquema) e IDs UUID generados en el cliente.
- En Room, los montos se guardan como **`Long` en unidades mínimas (céntimos)** para poder sumar en SQL sin errores; se convierten a `BigDecimal` en el borde.
- Diseñar los repositorios como interfaces para poder cambiar la fuente de datos sin tocar UI ni dominio.

## Seguridad en la app
- URL y clave pública de Supabase por `BuildConfig` desde `local.properties` (no se sube al repositorio). Perfiles `debug` (proyecto de desarrollo) y `release` (producción).
- Nunca usar la clave `service_role`/secreta.
- `android:allowBackup="false"` (o reglas de exclusión) para no respaldar la sesión.
- Release con R8/ProGuard activado; solo tráfico HTTPS.
- Opcional más adelante: bloqueo con biometría.

## Tema y diseño
Los tokens (colores, tipografía, formas) están en `docs/05-pantallas-y-flujos.md`, sección 1. Implementarlos en `ui/theme` como esquema de color de Material 3 + colores semánticos extra (ingreso, egreso, neutro). Objetivos táctiles ≥ 48 dp; contraste de texto ≥ 4.5:1; los colores de ingreso/egreso siempre acompañados de signo (+/−).
