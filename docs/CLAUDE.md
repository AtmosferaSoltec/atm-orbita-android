# [NOMBRE_APP] — App de gastos personales (Android nativo)

App **Android nativa** (Kotlin + Jetpack Compose) para registrar ingresos y egresos personales con varias cuentas ("tarjetas"), varias monedas (PEN por defecto, USD) y reportes. Backend: **Supabase** (Postgres + Auth + Row Level Security). **No hay servidor propio.**

> Estos documentos son **planos (maquetas y especificaciones)**, no código. Tu trabajo es implementarlos fase por fase.
> `[NOMBRE_APP]` y el `applicationId` (`com.example.gastos` como marcador) los define el usuario antes de crear el proyecto Android.

## Estado
- Fase actual: **0** (ver `docs/06-plan-y-pruebas.md`). Actualiza esta línea al cerrar cada fase.

## Lee en este orden
1. `docs/01-vision-y-alcance.md` — qué se construye, qué entra en el MVP y qué no.
2. `docs/02-arquitectura-android.md` — stack, capas, paquetes, reglas de dinero y monedas.
3. `docs/03-modelo-de-datos.md` — tablas, SQL de migraciones, RLS, cálculos y datos de ejemplo.
4. `docs/04-guia-supabase.md` — paso a paso para crear y operar la base de datos.
5. `docs/05-pantallas-y-flujos.md` — las 8 pantallas maquetadas, flujos y tokens de diseño.
6. `docs/06-plan-y-pruebas.md` — fases, criterios de aceptación y pruebas.
7. `docs/ORBITA_SPEC.md` — especificación funcional completa e independiente de la plataforma (base para iOS).

## Stack (resumen)
- Android nativo: Kotlin, Jetpack Compose + Material 3, Navigation Compose, Hilt, Coroutines/Flow, kotlinx.serialization.
- Supabase vía `supabase-kt` (módulos Auth y Postgrest). Sin backend propio.
- Dinero: `BigDecimal` en la app, `NUMERIC(14,2)` en Postgres.
- Solo Android por ahora. iOS queda fuera de alcance (la base de datos y las reglas son reutilizables).

## Reglas que NO se negocian
1. **Dinero = `BigDecimal`** (escala 2, `RoundingMode.HALF_UP`). Nunca `Float` ni `Double` para montos.
2. El saldo de una cuenta **no se guarda**: se calcula (vista `account_balances`).
3. **RLS activo en todas las tablas** con datos de usuario. Nunca desactivarlo "para probar".
4. En la app solo va la clave pública (`anon`/publishable) de Supabase. **Jamás** `service_role` ni la clave secreta, ni en código ni en el repo.
5. Cada cuenta tiene **una sola moneda**; un movimiento hereda la de su cuenta.
6. Las **compras de tarjeta de crédito no son movimientos**: viven en `credit_purchases` hasta que se pagan (ver `docs/03`). Los reportes solo leen `transactions`.
7. Las **transferencias** no cuentan como ingreso ni gasto.
8. Los cambios de esquema se hacen **solo con migraciones versionadas** en `supabase/migrations/`, nunca editando tablas a mano en el panel.
9. No agregues funciones fuera del alcance de la fase actual.

## Convenciones
- Código, identificadores y commits en **inglés**; textos de la interfaz en **español (es-PE)** en `strings.xml`.
- Capas `ui` → `domain` ← `data`. El paquete `domain` es Kotlin puro (sin imports de Android).
- Estado de pantalla: `StateFlow<UiState>` en un `ViewModel` por pantalla.
- Montos en pantalla: `S/ 1,245.80` y `US$ 250.00` (separador de miles `,` y decimal `.`).
- IDs: UUID generados en el cliente (`UUID.randomUUID()`), para dejar lista la sincronización offline.

## Comandos
```bash
./gradlew assembleDebug                 # compilar
./gradlew test                          # pruebas unitarias
./gradlew connectedDebugAndroidTest     # pruebas instrumentadas (emulador)
npx supabase db push                    # aplicar migraciones al proyecto enlazado
npx supabase db dump -f backup.sql      # respaldo manual
```

## Cómo trabajar
- **`docs/ORBITA_SPEC.md` es la especificación funcional viva** (pantalla por pantalla y flujos) con la que se construirá la versión iOS. Cada vez que agregues o cambies un módulo, pantalla, regla o flujo, actualízalo **en el mismo cambio**: sección de la pantalla, tabla de estado de módulos y registro de cambios.
- Una fase a la vez. Al terminar: corre las pruebas de la fase, resume qué quedó hecho y **pide revisión al usuario** antes de pasar a la siguiente.
- Si algo del plano es ambiguo o falta una pantalla, **pregunta**; no inventes reglas de negocio.
- Versiones de librerías: usa las estables más recientes al crear el proyecto (Gradle version catalog) y verifica la API en la documentación oficial; no te fíes de versiones copiadas de estos documentos.

## Preguntas abiertas (resolver con el usuario)
- Pantallas **no maquetadas** que el MVP necesita: login/registro, lista completa de movimientos (con filtros), editar/eliminar un movimiento y editar una cuenta/categoría. Propuesta en `docs/05`, sección 9.
- Nombre final de la app y `applicationId`.
