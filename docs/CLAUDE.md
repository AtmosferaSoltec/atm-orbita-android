# Orbita — App de gastos personales (Android nativo)

App **Android nativa** (Kotlin + Jetpack Compose) para registrar ingresos y egresos personales con varias cuentas ("tarjetas"), varias monedas (PEN por defecto, USD) y reportes.

Backend: **API propio** en el repositorio `atm-orbita-api` (NestJS + PostgreSQL + Prisma), con autenticación propia. **No se usa Supabase ni ningún otro backend gestionado**; la app habla solo con ese API.

> Decisión del 7 oct 2026. Antes estos documentos describían Supabase sin servidor propio. Los planes del backend están en `atm-orbita-api/docs/plans/` y son la fuente de verdad del contrato y del esquema.

`applicationId`: `com.atmosferast.orbita`.

## Estado
- Fase actual: **0** (ver `docs/06-plan-y-pruebas.md`). Actualiza esta línea al cerrar cada fase.
- **Adelantado el 7 oct 2026, a pedido del usuario:** la app ya tiene la arquitectura de la Fase 1 (Hilt, capas `domain` / `data` / `ui`, ViewModels) y **toda la lógica de las Fases 2–6 funcionando sobre datos en memoria** (`data/demo`). Lo que falta de cada fase es su implementación en `data/remote` contra el API y la línea correspondiente en `di/AppModule.kt`.
- El API todavía no está construido: solo existen sus planes. La Fase 0 de este proyecto pasa a ser "el API tiene lista su Fase 1".
- **Supabase retirado del repositorio (7 oct 2026):** se eliminaron la migración SQL y su carpeta, el repositorio de sesión contra Supabase, el cliente, las claves de `BuildConfig` y las dependencias. La app queda **entera en modo demo**, también la sesión, hasta que se escriban las implementaciones contra el API (`atm-orbita-api/docs/plans/14-integracion-android.md`). `AppConfig` solo tiene `apiBaseUrl` (`API_BASE_URL` en `local.properties`).
- **Reglas del 7 oct 2026 ya aplicadas en el código**, sobre el modo demo: tipo de cambio opcional (`FxPair.rate` nulo), nombres de categoría únicos sin mayúsculas ni tildes (`normalizeName`), editar y eliminar compras pendientes, eliminar el egreso de un pago devuelve la compra a pendiente, y no se archiva una tarjeta con pagos pendientes. Falta, porque necesita el API: recuperar contraseña, eliminar cuenta y moneda al registrarse.
- **El esquema de la base no vive aquí.** La única fuente son las migraciones de Prisma en `atm-orbita-api/prisma/`.
- Navegación: sigue siendo una pila propia en `ui/navigation/OrbitaApp.kt`; migrarla a Navigation Compose está pendiente.

## Lee en este orden
1. `docs/01-vision-y-alcance.md` — qué se construye, qué entra en el MVP y qué no.
2. `docs/02-arquitectura-android.md` — stack, capas, paquetes, reglas de dinero y monedas, sesión.
3. `docs/03-modelo-de-datos.md` — resumen del modelo, cálculos y datos de ejemplo.
4. `docs/04-guia-api.md` — cómo levantar el API en local y conectar la app.
5. `docs/05-pantallas-y-flujos.md` — las 8 pantallas maquetadas, flujos y tokens de diseño.
6. `docs/06-plan-y-pruebas.md` — fases, criterios de aceptación y pruebas.
7. `docs/ORBITA_SPEC.md` — especificación funcional completa e independiente de la plataforma (base para iOS).
8. `atm-orbita-api/docs/plans/README.md` — planes del backend: contrato, esquema, seguridad y pruebas.

## Stack (resumen)
- Android nativo: Kotlin, Jetpack Compose + Material 3, Hilt, Coroutines/Flow, kotlinx.serialization.
- Red: cliente HTTP **Ktor** contra el API (`/api/v1`), con token de acceso y renovación.
- Dinero: `BigDecimal` en la app; en el API viaja como **texto** (`"1245.80"`) y se guarda como `NUMERIC(14,2)`.
- Solo Android por ahora. iOS queda fuera de alcance (el API y las reglas son reutilizables).

## Reglas que NO se negocian
1. **Dinero = `BigDecimal`** (escala 2, `RoundingMode.HALF_UP`). Nunca `Float` ni `Double` para montos.
2. El saldo de una cuenta **no se guarda**: lo calcula el API.
3. **Cada persona ve solo sus datos.** Lo garantiza el API; la app nunca envía un identificador de usuario, solo su token.
4. **Ningún secreto en la app ni en el repositorio.** La app solo conoce la URL del API. El token de renovación se guarda cifrado con el Keystore y la app no hace respaldos (`allowBackup="false"`).
5. Cada cuenta tiene **una sola moneda**; un movimiento hereda la de su cuenta.
6. Las **compras de tarjeta de crédito no son movimientos**: están pendientes hasta que se pagan. Los reportes solo cuentan movimientos.
7. Las **transferencias** no cuentan como ingreso ni gasto.
8. Los cambios de esquema se hacen **solo con migraciones versionadas** en `atm-orbita-api/prisma/`, nunca a mano.
9. **"Hoy" lo decide la zona horaria del perfil.** La app envía la fecha local del dispositivo; el API la valida con la zona guardada.
10. **Sin tipo de cambio no hay otra moneda.** Mientras el usuario no lo configure, la app muestra "Configura tu tipo de cambio" y no deja operar en una moneda distinta de la principal.
11. No agregues funciones fuera del alcance de la fase actual.

## Convenciones
- Código, identificadores y commits en **inglés**; textos de la interfaz en **español (es-PE)** en `strings.xml`.
- Capas `ui` → `domain` ← `data`. El paquete `domain` es Kotlin puro (sin imports de Android).
- Estado de pantalla: `StateFlow<UiState>` en un `ViewModel` por pantalla.
- Montos en pantalla: `S/ 1,245.80` y `US$ 250.00` (separador de miles `,` y decimal `.`).
- IDs: UUID generados en el cliente (`UUID.randomUUID()`); el API los acepta al crear, de modo que reintentar no duplica.
- Los mensajes de error los elige la app a partir del `code` que devuelve el API; el API no envía textos de interfaz.

## Comandos
```bash
./gradlew assembleDebug                 # compilar
./gradlew test                          # pruebas unitarias
./gradlew connectedDebugAndroidTest     # pruebas instrumentadas (emulador)
./gradlew :app:lintDebug                # lint
```
Los comandos del backend (base de datos, migraciones, pruebas) están en `atm-orbita-api`.

## Cómo trabajar
- **`docs/ORBITA_SPEC.md` es la especificación funcional viva** (pantalla por pantalla y flujos) con la que se construirá la versión iOS. Cada vez que agregues o cambies un módulo, pantalla, regla o flujo, actualízalo **en el mismo cambio**: sección de la pantalla, tabla de estado de módulos y registro de cambios.
- Si un cambio afecta al contrato (un campo, un endpoint, un código de error), se actualiza también el plan correspondiente en `atm-orbita-api/docs/plans/`.
- Una fase a la vez. Al terminar: corre las pruebas de la fase, resume qué quedó hecho y **pide revisión al usuario** antes de pasar a la siguiente.
- Si algo del plano es ambiguo o falta una pantalla, **pregunta**; no inventes reglas de negocio.
- Versiones de librerías: usa las estables más recientes al crear el proyecto (Gradle version catalog) y verifica la API en la documentación oficial; no te fíes de versiones copiadas de estos documentos.

## Preguntas abiertas (resolver con el usuario)
- Pantallas **no maquetadas**: login/registro, lista de movimientos, editar/eliminar un movimiento, editar una cuenta/categoría. Propuesta en `docs/05`, sección 9.
- Pantallas **nuevas por las decisiones del 7 oct 2026**, aún sin diseño: "¿Olvidaste tu contraseña?" y nueva contraseña, "Eliminar cuenta" en Ajustes, editar y eliminar una compra con tarjeta pendiente.
- Las decisiones abiertas del backend están en `atm-orbita-api/docs/plans/00-contexto-y-hallazgos.md`, sección 6.
