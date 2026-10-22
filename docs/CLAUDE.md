# Orbita · App Android

App **Android nativa** (Kotlin + Jetpack Compose) para registrar ingresos y egresos personales por cuenta, en varias monedas, con transferencias, compras con tarjeta de crédito pendientes de pago y reportes. Multiusuario: cada persona ve solo sus datos.

`applicationId`: `com.atmosferast.orbita`.

Backend: **API propio** en `atm-orbita-api` (Go + Gin + PostgreSQL), con autenticación propia. La app habla solo con ese API.

> Documentos regenerados el 21 oct 2026 con la forma de trabajo del API (los anteriores, que hablaban de NestJS y Supabase, siguen en el historial de git).

## Ramas

- Todo el trabajo va en **`develop`**. `main` guarda lo que ya funciona; pasar a `main` lo decide el usuario.
- Commits en Conventional Commits, en inglés, uno por cambio lógico.

## Lee en este orden

1. Este archivo.
2. [`docs/ORBITA_SPEC.md`](ORBITA_SPEC.md): la especificación funcional, pantalla por pantalla, con reglas, mensajes, diseño, cálculos y datos de ejemplo. Es la base para iOS.
3. `atm-orbita-api/docs/plans/README.md`: los planes del backend, que son el **contrato** (endpoints, errores, reglas). El plan `13-android-integration.md` dice qué cambia en esta app con cada plan.

## Estado (21 oct 2026)

- Toda la app funciona sobre datos de ejemplo en memoria (`data/demo`), con las mismas reglas que el API.
- **Conectado al API** (se activa con `API_BASE_URL`; sin ella, todo es modo demo):
  - Plan 01: la sesión (registro, inicio y cierre de sesión, sesión persistente).
  - Plan 02: Ajustes (monedas, tipo de cambio, moneda de visualización), `RemoteSettingsRepository`.
  - Plan 03: Categorías, `RemoteCategoriesRepository`. Mientras las compras con tarjeta sigan en demo, `DemoCategoryMirror` copia las categorías del API al `DemoStore` (se quita con los planes 09 y 10).
  - Plan 04: Cuentas, `RemoteAccountsRepository`. Igual que con las categorías, `DemoAccountMirror` copia las cuentas del API al `DemoStore` (con el saldo del API como inicial) mientras los pagos de tarjeta sigan en demo (plan 10).
  - Planes 05 y 06: Movimientos, transferencias y su lista, `RemoteEntriesRepository`. Mientras los pagos de tarjeta sigan en demo (plan 10), `DemoEntriesMix` mezcla en las listas el gasto de un pago hecho desde una cuenta del API. Con el API, "hoy" es la fecha del teléfono (`AppModule.provideDateProvider`).
  - Plan 07: Reportes e "Ingresos / Gastos · mes" de Inicio, `RemoteReportsRepository` (vuelve a leer cuando cambia un movimiento). Los porcentajes los sigue calculando la app con `percentOf`; dan lo mismo que los del API.
- Pendiente, plan por plan: Inicio (08), tarjetas y pagos (09, 10), recuperar contraseña con código de 6 dígitos (11) y eliminar cuenta (12).
- Navegación: pila propia en `ui/navigation/OrbitaApp.kt` (migrar a Navigation Compose está pendiente).

## Arquitectura

```
ui (Compose + ViewModel, StateFlow)  →  domain (modelos, borradores, reglas, repositorios: Kotlin puro)  ←  data (demo | remote)
```

- Las pantallas solo hablan con su `ViewModel`; este, con los **repositorios** de `domain/repository`.
- Cada repositorio tiene dos implementaciones: `data/demo` (memoria) y `data/remote` (API). **`di/AppModule.kt` decide cuál** según `AppConfig.usesApi`. Conectar un módulo al API es escribir su implementación remota y cambiar su línea ahí.
- `data/remote`:
  - `ApiClient.kt`: Ktor (OkHttp), JSON que ignora campos desconocidos, tiempos de espera, `X-Request-Id`.
  - `ApiSession.kt`: tokens y peticiones autorizadas; renueva **una sola vez a la vez** (`Mutex`) y repite la petición; si la renovación se rechaza, cierra la sesión. Avisa cada cambio de sesión (`onSessionChange`) para que los repositorios borren lo que guardan en memoria.
  - Repositorios remotos: guardan la última lectura en un `MutableStateFlow` (REST no avisa de cambios), la reemplazan con la respuesta de cada escritura y la borran al cambiar de sesión.
  - `TokenStore.kt`: refresh token y correo cifrados con una clave del **Android Keystore**; el access token solo en memoria.
  - `ApiErrors.kt`: `{ "error": { "code", ... } }` → `DataException(DataError)`, o el primer `ValidationError` de `details`.
  - DTOs con `@SerialName` en `snake_case`; montos y tipos de cambio con `BigDecimalSerializer` (el API los manda como texto).

## Reglas que no se negocian

1. **Dinero = `BigDecimal`**, escala 2, `HALF_UP`, redondeo al final. Nunca `Float` ni `Double`. En el API viaja como texto.
2. El saldo **no se guarda**: lo calcula el API (y `data/demo` igual).
3. **Cada persona ve solo sus datos.** La app nunca envía un id de usuario: solo su token.
4. **Ningún secreto en la app ni en el repo.** La app solo conoce la URL del API. Respaldos desactivados (`allowBackup="false"` y reglas de extracción).
5. Cada cuenta tiene una sola moneda; un movimiento hereda la de su cuenta.
6. Las compras con tarjeta **no son movimientos** hasta pagarse; las transferencias no son ingreso ni gasto.
7. **"Hoy" lo decide el API** con la zona horaria del perfil (la app la envía al registrarse).
8. **Sin tipo de cambio no hay otra moneda.**
9. El esquema solo cambia con migraciones del API; desde aquí nunca se toca la base.
10. Release solo con **HTTPS**; el HTTP sin cifrar solo se permite en debug (`src/debug/res/xml/network_security_config.xml`).

## Convenciones

- Código, identificadores y commits en **inglés**; textos de la interfaz en **español (es-PE)**, en `strings.xml`.
- `domain` es Kotlin puro (sin imports de Android).
- Un `ViewModel` por pantalla con `StateFlow`.
- Montos en pantalla: `S/ 1,245.80`, `US$ 250.00`.
- IDs: UUID generados en el cliente; el API los acepta al crear.
- Los mensajes de error los elige la app por el `code` del API; el `message` del API no se muestra.

## Conectar la app al API

1. Levanta el API en `atm-orbita-api` (ver su `docs/plans/00-foundations.md`, "Cómo correrlo en local"): Postgres local con `docker compose -f docker-compose.dev.yml up -d`, luego `go run ./cmd/api migrate` y `go run ./cmd/api`. Escucha en el puerto 3001.
2. En `local.properties` de este proyecto (no se sube a git):
   ```properties
   # Emulador:
   API_BASE_URL=http://10.0.2.2:3001/api/v1
   # Teléfono en la misma red Wi-Fi (IP de la PC):
   # API_BASE_URL=http://192.168.x.x:3001/api/v1
   ```
3. Compila la variante debug. Sin `API_BASE_URL`, la app arranca en modo demo.

Producción: `https://orbita-api.atmosferast.com/api/v1`.

## Comandos

En Windows, con el JDK de Android Studio: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`.

```bash
./gradlew assembleDebug                 # compilar
./gradlew testDebugUnitTest             # pruebas unitarias
./gradlew lintDebug                     # lint
./gradlew connectedDebugAndroidTest     # pruebas instrumentadas (emulador o teléfono)
```

## Cómo trabajar

- Un plan del API a la vez; con cada uno se conecta su parte aquí. Al terminar: pruebas, resumen y revisión del usuario.
- `docs/ORBITA_SPEC.md` se actualiza **en el mismo cambio** que una pantalla, regla o flujo: su sección, la tabla de estado de módulos y el registro de cambios.
- Si algo es ambiguo, se pregunta; no se inventan reglas de negocio.
