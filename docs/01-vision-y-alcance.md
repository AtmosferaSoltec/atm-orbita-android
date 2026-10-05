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
5. **Tipo de cambio**: en el MVP es **manual** (un valor que el usuario fija en Ajustes). Más adelante, automático diario.
6. **Transferencia entre monedas**: el usuario indica monto que sale, y la app propone el cambio y calcula el monto que entra (moneda destino). Si el banco aplicó otro cambio, el usuario edita el cambio **o** el monto recibido y el otro se recalcula. Se guarda monto que sale, monto que entra y cambio usado, así los saldos cuadran con la realidad.
7. **Tarjeta de crédito** (versión 1.1):
   - Se registra la compra con descripción, categoría, monto, moneda, fecha de compra y **fecha límite de pago**. Estado `pending`.
   - **No afecta** saldos, ahorros ni reportes mientras esté pendiente.
   - Al marcarla pagada, el usuario elige la **cuenta de pago**, la **fecha de pago** (hoy por defecto, editable) y el **monto real descontado** (puede diferir si la compra fue en otra moneda). Recién entonces se crea un **egreso** en esa cuenta con la categoría original.
   - Sin fecha de corte ni cuotas por ahora.
8. **Reportes**: por mes y por rango de fechas; gasto por categoría (ranking), fuentes de ingreso principales y balance del periodo. Los totales se muestran **por moneda** (sin mezclar) en el MVP.
9. **Seguridad**: login con correo y contraseña; cada usuario accede únicamente a sus propios datos (RLS).

## Alcance por versión
**MVP (v1.0)**
- Registro e inicio de sesión (correo y contraseña), cerrar sesión.
- Cuentas (crear, renombrar, moneda, interruptor de ahorros, archivar).
- Categorías de ingreso y egreso (con unas iniciales creadas al registrarse).
- Registrar ingresos y egresos con descripción y fecha.
- Transferencias entre cuentas, con conversión editable.
- Saldo por cuenta y ahorro total con selector S/ ↔ US$ y cambio manual.
- Reportes por mes y por rango de fechas.
- Funciona **con internet** (sin modo offline).

**v1.1** — Compras con tarjeta de crédito pendientes de pago.

**Después** — Exportar reportes a PDF · modo sin internet (Room + sincronización) · tipo de cambio automático diario e histórico · acceso con Google · presupuestos y gastos recurrentes · versión iOS.

## Fuera de alcance (por ahora)
Fecha de corte y cuotas de tarjeta, subcategorías, presupuestos, gastos recurrentes, conversión histórica por fecha, otras monedas en la interfaz, iOS, tema oscuro.

## Registro de decisiones
| Decisión | Motivo |
|---|---|
| App **Android nativa (Kotlin + Compose)** en vez de Flutter | Cambio de rumbo del usuario; mejor integración con Android y base sólida para offline (Room). Reemplaza la idea inicial de Flutter. |
| **iOS fuera de alcance** por ahora | Se prioriza Android. El backend es el mismo, así que iOS se puede sumar después (Swift nativo o código compartido con Kotlin Multiplatform). Por eso `domain` es Kotlin puro. |
| **Supabase** sin backend propio | Da Postgres, Auth y seguridad por usuario (RLS); menos mantenimiento y escala bien. |
| `NUMERIC(14,2)` y `BigDecimal` | Evitar errores de redondeo de `Double` (0.1 + 0.2). |
| Saldo calculado, no guardado | Nunca se descuadra. |
| Crédito como tabla aparte | Evita doble conteo y que los reportes cuenten gastos no pagados. |
| Cambio manual en el MVP | Más simple; el automático se agrega luego sin cambiar el esquema. |
| Cambio **actual** para convertir totales (no histórico) | Simplicidad del MVP; el esquema ya admite historial. |
