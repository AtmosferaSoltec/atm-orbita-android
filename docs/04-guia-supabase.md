# 04 · Guía paso a paso: base de datos con Supabase

Objetivo: crear la base de datos, aplicar las migraciones de `docs/03`, comprobar que la seguridad funciona y conectarla a la app Android. Es para una persona que está **aprendiendo Supabase**: cada paso explica qué hace.

> La interfaz de Supabase cambia con frecuencia. Si un menú no coincide con lo descrito, busca el nombre en el buscador del panel o en https://supabase.com/docs.

## Conceptos en 2 minutos
- **Proyecto**: una base de datos Postgres + autenticación + API, todo en uno.
- **Auth**: guarda los usuarios (tabla interna `auth.users`) y entrega un **JWT** a la app al iniciar sesión.
- **API automática (PostgREST)**: cada tabla de `public` se puede consultar por HTTPS. Lo que cada usuario puede ver o modificar lo deciden las **políticas RLS**.
- **Claves**: la clave **pública** (`anon` / "publishable") va en la app y es segura **solo porque RLS está activo**. La clave **secreta** (`service_role` / "secret") se salta RLS: **nunca** va en la app ni en el repositorio.
- **Migración**: un archivo `.sql` versionado que cambia el esquema. Todo cambio de la base se hace por migración.

## Paso 1 · Crear dos proyectos: desarrollo y producción
1. Crea una cuenta en https://supabase.com y una organización.
2. Crea el proyecto `gastos-dev` (para probar). Más adelante `gastos-prod` (datos reales).
3. **Región**: elige la más cercana a Perú, *South America (São Paulo)*, para menor latencia.
4. Define una **contraseña de base de datos** fuerte y guárdala en un gestor de contraseñas (no la pongas en el repositorio).

## Paso 2 · Instalar la CLI y enlazar el proyecto
Requisitos: Node.js (para `npx`) o instalar la CLI de Supabase por tu gestor de paquetes. Docker solo si quieres base de datos local.
```bash
mkdir gastos-app && cd gastos-app          # o la carpeta del repositorio
npx supabase@latest init                   # crea la carpeta supabase/
npx supabase@latest login                  # abre el navegador para autorizar
npx supabase@latest link --project-ref <REF_DEL_PROYECTO>   # el ref está en la URL del proyecto
```

## Paso 3 · Crear y aplicar la migración del MVP
```bash
# La migración del MVP ya está en el repositorio: supabase/migrations/0001_init.sql
# (es el SQL de la sección 3 de docs/03). No hace falta crearla.
```
1. Revisa que `supabase/migrations/0001_init.sql` exista. Si cambias el esquema, **no lo edites**: crea una migración nueva (`npx supabase@latest migration new <nombre>`) y actualiza `docs/03`.
2. Aplícalo:
```bash
npx supabase@latest db push
```
3. Comprueba en el panel → **Table Editor**: deben existir `profiles`, `exchange_rates`, `accounts`, `categories`, `transactions`, `transfers`, todas con el candado de **RLS activado**.

*Alternativa para aprender:* pegar el SQL en el panel → **SQL Editor** → Run. Sirve para probar, pero para trabajo real usa siempre migraciones, así el esquema queda en git y se puede repetir en producción.

La migración `0002_credit_purchases` (v1.1) se crea igual (`migration new credit_purchases`) cuando llegue esa fase.

## Paso 4 · Configurar la autenticación
Panel → **Authentication**:
1. **Providers → Email**: activado.
2. **Confirmar correo**: en `gastos-dev` puedes desactivarlo para probar rápido; en producción déjalo **activado**.
3. Longitud mínima de contraseña: 8.
4. **URL Configuration**: más adelante, al implementar enlaces de recuperación de contraseña, se agrega el *deep link* de la app.
5. Los correos de prueba/confirmación del plan gratuito tienen límite de envío; para producción configura un proveedor SMTP propio.

Al registrarse un usuario, el trigger `handle_new_user` le crea perfil, categorías iniciales y una cuenta "Efectivo".

## Paso 5 · Verificar la seguridad (no te lo saltes)
1. Crea dos usuarios de prueba (panel → Authentication → Users → *Add user*) y anota sus UUID.
2. Ejecuta las pruebas de la sección 8 de `docs/03` en el **SQL Editor**.
3. Panel → **Advisors** (Security y Performance): corrige cualquier alerta de tablas sin RLS.
4. Regla de oro: una tabla con datos de usuarios y **sin RLS = datos expuestos**.

## Paso 6 · Conectar la app Android
1. Panel → **Project Settings → API** (o *API Keys*): copia la **Project URL** y la clave **anon / publishable**.
2. En `local.properties` del proyecto Android (archivo **ignorado por git**):
```properties
SUPABASE_URL=https://xxxxxxxx.supabase.co
SUPABASE_ANON_KEY=<clave pública>
```
3. **Ya está hecho en el código:** `app/build.gradle.kts` las expone por `BuildConfig` y `di/AppModule.kt` crea el cliente así:
```kotlin
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    install(Auth)
    install(Postgrest)
}
```
4. Vuelve a compilar. Con las dos claves puestas, la app deja el **modo demo** para la sesión: registro, inicio y cierre de sesión son reales, y la sesión se conserva al reabrir la app. **Los datos siguen siendo los de ejemplo** hasta que cada módulo se conecte (Fase 2 en adelante, `docs/06`). Sin las claves, la app sigue funcionando entera en modo demo.
5. Prueba mínima: registrar un usuario, iniciar sesión, cerrar la app, reabrirla (debe seguir con sesión) y cerrar sesión. En el panel → Authentication → Users debe aparecer el usuario, y en Table Editor su perfil, sus categorías y su cuenta "Efectivo".

## Paso 7 · Desarrollo local (opcional)
Con Docker instalado: `npx supabase@latest start` levanta una copia local (base de datos, Auth y panel) y `npx supabase@latest db reset` la recrea desde las migraciones. Útil para probar migraciones sin tocar `gastos-dev`.

## Paso 8 · Pasar a producción
1. Crea `gastos-prod`, enlázalo (`supabase link --project-ref <REF_PROD>`) y corre `db push`.
2. La app `release` usa la URL y clave de producción (otro `local.properties` / variables de CI).
3. Activa confirmación de correo y SMTP propio.

## Escalabilidad y "sin caídas"
**Qué se hace bien desde el diseño**
- Índices por `user_id` + fecha en las tablas grandes (ya están en la migración).
- Listas **paginadas** (`range`) y reportes **calculados en SQL** (funciones `report_*`): la app nunca descarga miles de filas para sumarlas.
- Políticas RLS escritas con `(select auth.uid())` (se evalúa una vez por consulta, no por fila).
- Un cambio de esquema = una migración, probada primero en `gastos-dev`.
- Operaciones que deben ser atómicas (pagar crédito) van en funciones SQL: o se hace todo o nada.

**Disponibilidad y respaldos (verifica los límites vigentes en la página de precios y la documentación)**
- En el plan gratuito, los proyectos **se pausan tras un periodo de inactividad** y los respaldos son limitados o inexistentes. Sirve para aprender y probar, **no para datos reales** que no quieras perder.
- Para uso real, el plan de pago incluye respaldos diarios y opciones de recuperación a un punto en el tiempo, además de poder ampliar los recursos de cómputo.
- Además: respaldo manual periódico con `npx supabase db dump -f backup.sql` (esquema) y `--data-only` (datos), guardado fuera de Supabase.
- Monitorea el uso en el panel (**Reports**, **Logs**) y fija alertas si hay opción.
- La app debe manejar errores de red con mensajes claros y reintento (ver `docs/02`).

**Cuándo agregar más (no ahora)**
- Edge Function programada para el tipo de cambio diario.
- Sincronización offline (Room + WorkManager) — ver `docs/02`.
- Un backend propio solo si aparece lógica que no encaje en SQL/Edge Functions.
