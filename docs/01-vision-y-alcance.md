# 01 · Visión y alcance

## Problema
El usuario quiere registrar **todos** sus gastos e ingresos, separados por fuente de dinero, con categorías y descripciones, y ver cuánto gasta, en qué y cuánto tiene ahorrado, en soles y en dólares.

## Qué es la app
Una app Android nativa para uso personal, **multiusuario y segura**: cada persona crea su cuenta y solo ve sus propios datos.

## Glosario
| Término | Significado |
|---|---|
| **Cuenta** (en la conversación, "tarjeta" o "fuente") | Un lugar donde hay dinero: efectivo, débito, ahorro, billetera. Tiene nombre editable y **una moneda**. |
| **Movimiento** | Un ingreso o un egreso en una cuenta. |
| **Transferencia** | Mover dinero entre dos cuentas propias. No es ingreso ni gasto. Puede cambiar de moneda. |
| **Compra con tarjeta de crédito** | Gasto hecho con crédito, **pendiente de pago**. Solo es un recordatorio hasta que se paga. |
| **Ahorro total** | Suma de los saldos de las cuentas marcadas "contar en ahorros", convertida a S/ o US$. |
| **Tipo de cambio** | Cuántas unidades de la moneda destino equivalen a 1 unidad de la moneda origen (ej. 1 US$ = S/ 3.20). |

## Reglas de negocio
1. Cada cuenta tiene nombre editable, tipo (`cash`, `debit`, `savings`, `other`), moneda y un interruptor **"Contar en el total de ahorros"** (activado por defecto).
2. Monedas: **PEN** por defecto y **USD**. El diseño admite otras códigos ISO de 3 letras, pero el MVP solo ofrece PEN y USD.
3. Los montos tienen decimales exactos (ej. `45.52`).
4. **Ahorro total**: suma de saldos de cuentas incluidas, convertidas a la moneda de visualización con el tipo de cambio vigente. El usuario alterna S/ ↔ US$. Con 1 US$ = 3.20: US$ 20 → S/ 64.00; al ver en dólares se usa 1 ÷ 3.20.
5. **Tipo de cambio**: en el MVP es **manual** (un valor que el usuario fija en Ajustes). Más adelante, automático diario. **Un usuario nuevo no tiene tipo de cambio**: hasta que lo escribe, la app muestra "Configura tu tipo de cambio" y no permite operar en una moneda distinta de la principal.
6. **Transferencia entre monedas**: el usuario indica monto que sale, y la app propone el cambio y calcula el monto que entra (moneda destino). Si el banco aplicó otro cambio, el usuario edita el cambio **o** el monto recibido y el otro se recalcula. Se guarda monto que sale, monto que entra y cambio usado, así los saldos cuadran con la realidad.
7. **Tarjeta de crédito** (versión 1.1):
   - Se registra la compra con descripción, categoría, monto, moneda, fecha de compra y **fecha límite de pago**. Estado `pending`.
   - **No afecta** saldos, ahorros ni reportes mientras esté pendiente.
   - Al marcarla pagada, el usuario elige la **cuenta de pago**, la **fecha de pago** (hoy por defecto, editable) y el **monto real descontado** (puede diferir si la compra fue en otra moneda). Recién entonces se crea un **egreso** en esa cuenta con la categoría original.
   - Una compra **pendiente** se puede editar y eliminar. No afecta saldos ni reportes: solo cambia la deuda de su tarjeta.
   - Una compra **pagada** no se edita ni se elimina. Si se elimina el egreso de su pago, la compra **vuelve a pendiente**; desde Movimientos nunca se elimina una compra.
   - Una tarjeta **no se puede archivar mientras tenga compras pendientes**.
   - Sin fecha de corte ni cuotas por ahora.
8. **Reportes**: por mes y por rango de fechas; gasto por categoría (ranking), fuentes de ingreso principales y balance del periodo. Los totales se muestran **por moneda** (sin mezclar) en el MVP.
9. **Seguridad**: login con correo y contraseña; cada usuario accede únicamente a sus propios datos. Lo garantiza el API propio, que identifica al usuario por su token.
10. **"Hoy"** se calcula con la zona horaria del usuario (por ejemplo `America/Lima`), guardada en su perfil. No hay datos del futuro.
11. **Categorías**: el nombre no se repite dentro del mismo tipo (ingreso o egreso), **sin distinguir mayúsculas ni tildes**: "Alimentación", "alimentacion" y "ALIMENTACIÓN" son la misma. Se muestra siempre como el usuario lo escribió.
12. **Recuperar la contraseña** y **eliminar la cuenta** (con borrado real de todos los datos) deben existir antes de publicar la app.
13. **La moneda principal se elige al registrarse** (por defecto soles; la app propone la de la región del teléfono). La cuenta "Efectivo" inicial nace en esa moneda. La secundaria nace en dólares, o en soles si la principal es dólares.
14. La verificación de correo **no es obligatoria** al lanzar. El PDF de reportes es **solo el resumen**, sin lista de movimientos.

## Alcance por versión
**MVP (v1.0)**
- Registro e inicio de sesión (correo y contraseña), cerrar sesión, **recuperar contraseña** y **eliminar cuenta** (los dos últimos, requisito para publicar).
- Cuentas (crear, renombrar, moneda, interruptor de ahorros, archivar).
- Categorías de ingreso y egreso (con unas iniciales creadas al registrarse).
- Registrar ingresos y egresos con descripción y fecha.
- Transferencias entre cuentas, con conversión editable.
- Saldo por cuenta y ahorro total con selector S/ ↔ US$ y cambio manual.
- Reportes por mes y por rango de fechas.
- Funciona **con internet** (sin modo offline).

**v1.1** — Compras con tarjeta de crédito pendientes de pago.

**Planificado en el backend** (`atm-orbita-api/docs/plans/`, fases 6 y 7) — Tipo de cambio automático diario · exportar reportes a PDF.

**Después** — Modo sin internet (Room + sincronización) · tipo de cambio histórico · acceso con Google · presupuestos y gastos recurrentes · versión iOS.

## Fuera de alcance (por ahora)
Fecha de corte y cuotas de tarjeta, subcategorías, presupuestos, gastos recurrentes, conversión histórica por fecha, otras monedas en la interfaz, iOS, tema oscuro.

## Registro de decisiones
| Decisión | Motivo |
|---|---|
| App **Android nativa (Kotlin + Compose)** en vez de Flutter | Cambio de rumbo del usuario; mejor integración con Android y base sólida para offline (Room). Reemplaza la idea inicial de Flutter. |
| **iOS fuera de alcance** por ahora | Se prioriza Android. El backend es el mismo, así que iOS se puede sumar después (Swift nativo o código compartido con Kotlin Multiplatform). Por eso `domain` es Kotlin puro. |
| **Backend propio** (NestJS + PostgreSQL + Prisma), autenticación propia. **Sin Supabase** ni ningún backend gestionado (7 oct 2026) | Decisión del usuario. Reemplaza a la idea inicial de usar Supabase sin servidor propio. Control total de los datos y de las reglas; el contrato sirve igual a Android, web e iOS. |
| Zona horaria IANA en el perfil; "hoy" lo calcula el backend | Un servidor en UTC ya está en "mañana" desde las 19:00 de Lima. La fecha se guarda como fecha, ya calculada. |
| El tipo de cambio puede no existir | El backend no inventa un valor. Sin él no se opera en otra moneda, y así ningún total queda a medias. |
| Nombre de categoría único por usuario y tipo, sin distinguir mayúsculas ni tildes | "Comida" y "comida", o "Alimentación" y "Alimentacion", partirían un reporte en dos. El backend guarda aparte una versión normalizada para comparar y siempre devuelve el nombre original. |
| Al cambiar de par de monedas, el tipo de cambio queda vacío hasta escribirlo | Un `1.00` puesto por la app sería un valor inventado. Solo se guarda con un valor mayor que cero. |
| Resend como servicio de correo inicial | Para los correos de recuperación de contraseña. Va detrás de una interfaz en el backend, para poder cambiarlo. |
| Supabase retirado del repositorio | Se eliminaron la migración SQL, las dependencias y el código. Las migraciones de Prisma del backend son la única fuente del esquema. |
| Compras pendientes editables y eliminables | Un monto mal escrito no tenía arreglo. |
| Respaldos del dispositivo desactivados | El token de sesión no debe salir del teléfono; los datos se recuperan del backend. |
| `NUMERIC(14,2)` y `BigDecimal` | Evitar errores de redondeo de `Double` (0.1 + 0.2). |
| Saldo calculado, no guardado | Nunca se descuadra. |
| Crédito como tabla aparte | Evita doble conteo y que los reportes cuenten gastos no pagados. |
| Cambio manual en el MVP | Más simple; el automático se agrega luego sin cambiar el esquema. |
| Cambio **actual** para convertir totales (no histórico) | Simplicidad del MVP; el esquema ya admite historial. |
