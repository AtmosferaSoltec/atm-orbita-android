# 04 · Guía: conectar la app al API de Orbita

> **Cambio del 7 oct 2026.** Este documento era la guía paso a paso de Supabase (`04-guia-supabase.md`). El backend ahora es el API propio, `atm-orbita-api` (NestJS + PostgreSQL + Prisma), con autenticación propia. No se usa Supabase ni ningún otro backend gestionado.
>
> **El API todavía no está construido**: existen sus planes en `atm-orbita-api/docs/plans/`. Los pasos de abajo describen cómo se trabajará cuando su Fase 1 (autenticación) esté lista. Hasta entonces la app funciona entera en **modo demo**.

## Conceptos en 2 minutos
- **API**: el único backend. Android, web e iOS le hablan por HTTPS con JSON, bajo `/api/v1`.
- **Token de acceso**: lo entrega el API al iniciar sesión, dura 15 minutos y acompaña cada petición.
- **Token de renovación**: dura semanas, se guarda cifrado en el teléfono y sirve para pedir un token de acceso nuevo. Cambia en cada uso.
- **La app no lleva ninguna clave.** Solo conoce la URL del API. Quién puede ver qué lo decide el API a partir del token.
- **Migración**: un cambio versionado del esquema. Viven en `atm-orbita-api/prisma/migrations/`; desde esta app nunca se toca la base.

## Paso 1 · Levantar el API en local
Requisitos: Docker Desktop, Node.js 24 y pnpm. En el repositorio `atm-orbita-api`:
```bash
docker compose up -d db        # PostgreSQL en un contenedor
pnpm install
pnpm db:migrate                # aplica las migraciones
pnpm db:seed                   # opcional: carga los datos de ejemplo de docs/03, sección 7
pnpm start:dev                 # API en http://localhost:3000/api/v1
```
Comprobación: `http://localhost:3000/api/v1/health/ready` responde `200`.

Los nombres exactos de los comandos se confirman en el `README.md` del API cuando exista su Fase 0.

## Paso 2 · Apuntar la app al API
En `local.properties` de este proyecto (archivo **ignorado por git**):
```properties
API_BASE_URL=http://10.0.2.2:3000/api/v1
```
- `10.0.2.2` es, desde el emulador de Android, la máquina donde corre el API.
- En un teléfono físico, usa la IP de tu computadora en la red local.
- Sin `API_BASE_URL`, la app arranca en **modo demo** (datos de ejemplo en memoria, cualquier correo y contraseña entran).

`app/build.gradle.kts` ya expone ese valor como `BuildConfig.API_BASE_URL`, y `AppConfig.usesApi` (en `di/AppModule.kt`) indica si está configurado. Lo que falta es el cliente HTTP y las implementaciones de `data/remote`: hoy, aun con la URL puesta, todos los repositorios siguen en modo demo. El código y las dependencias de Supabase ya se retiraron (7 oct 2026).

En compilaciones `debug` se permite HTTP sin cifrar **solo** hacia `10.0.2.2` y `localhost`. En `release`, únicamente HTTPS.

## Paso 3 · Prueba mínima
1. Registrar un usuario.
2. Cerrar la app y reabrirla: debe seguir con sesión.
3. Comprobar que el usuario nuevo tiene sus 9 categorías y la cuenta "Efectivo", y que Inicio muestra "Configura tu tipo de cambio".
4. Cerrar sesión: vuelve a Iniciar sesión.

## Paso 4 · Migrar módulo por módulo
Cada repositorio pasa de `data/demo` a `data/remote` con una línea en `di/AppModule.kt`, en el orden de las fases del API:

| Paso | Repositorio | Requiere del API |
|---|---|---|
| 1 | `AuthRepository`, cliente HTTP, almacén de tokens | Fase 1 |
| 2 | `SettingsRepository`, `AccountsRepository`, `CategoriesRepository` | Fase 2 |
| 3 | `EntriesRepository` | Fase 3 |
| 4 | `ReportsRepository` e Inicio | Fase 4 |
| 5 | `CreditRepository` | Fase 5 |

El detalle (sesión, caché, errores, cambios del dominio) está en `atm-orbita-api/docs/plans/14-integracion-android.md`.

Mientras unos módulos sean reales y otros de demostración, los datos no cuadran entre pantallas. Sirve para desarrollar; **no se publica** una versión en ese estado.

## Entornos
| Entorno | `API_BASE_URL` | Datos |
|---|---|---|
| Demo | vacío | En memoria, de ejemplo |
| Desarrollo | API local | Base local, se puede recrear |
| Producción | URL pública con HTTPS | Reales, con respaldos |

## Qué garantiza el API (y ya no la app ni Supabase)
- **Cada persona ve solo sus datos.** La app nunca envía un identificador de usuario.
- **Operaciones atómicas**: pagar una compra de crédito crea el egreso y marca la compra, o no hace nada.
- **Reportes calculados en la base**, listas paginadas.
- **"Hoy"** según la zona horaria del perfil.
- **Respaldos** de la base y su restauración.

## Requisitos antes de publicar la app
- Recuperar contraseña ("¿Olvidaste tu contraseña?") funcionando de principio a fin.
- Eliminar la cuenta desde Ajustes, y una página web para hacerlo fuera de la app (requisito de Google Play).
- Todos los módulos conectados al API; ninguno en modo demo.
- `release` solo con HTTPS.
