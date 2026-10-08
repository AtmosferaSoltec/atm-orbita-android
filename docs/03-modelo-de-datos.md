# 03 · Modelo de datos

> **Cambio del 7 oct 2026.** El backend ya no es Supabase: es el API propio (`atm-orbita-api`: NestJS + PostgreSQL + Prisma). Este documento contenía el SQL de las migraciones de Supabase; ese SQL **ya no aplica**.
>
> **La fuente de verdad del esquema** es `atm-orbita-api/prisma/` y está explicada en `atm-orbita-api/docs/plans/02-modelo-de-datos.md`. Aquí queda un resumen para entender la app, más los **cálculos** (sección 5) y los **datos de ejemplo** (sección 7), que siguen vigentes y a los que remiten los demás documentos.

## 1. Resumen

| Tabla | Para qué | Versión |
|---|---|---|
| `users`, `sessions`, `refresh_tokens`, `email_tokens` | Cuentas de usuario, sesiones y tokens de recuperación de contraseña | MVP |
| `profiles` | Preferencias: moneda principal, **moneda secundaria**, moneda de visualización, modo de cambio y **zona horaria** | MVP |
| `exchange_rates` | Tipos de cambio: manuales del usuario y globales (tarea diaria) | MVP |
| `accounts` | Las "tarjetas": efectivo, débito, ahorro… cada una con su moneda | MVP |
| `categories` | Categorías de ingreso y de egreso | MVP |
| `transactions` | Ingresos y egresos | MVP |
| `transfers` | Transferencias entre cuentas (con conversión) | MVP |
| `credit_cards` | Tarjetas de crédito: nombre, moneda, archivada | v1.1 |
| `credit_purchases` | Compras con tarjeta, pendientes o pagadas, cada una de una tarjeta | v1.1 |

Lo que antes eran vistas, triggers y funciones SQL de Supabase (`account_balances`, `report_*`, `pay_credit_purchase`, `handle_new_user`, `validate_*`) ahora son consultas y transacciones del API.

Con este cambio quedan resueltos los tres pendientes de esquema que tenía la app: la tabla de tarjetas de crédito y su relación con las compras, la moneda secundaria del perfil y la moneda de cada tarjeta.

## 2. Convenciones
- **Montos**: `NUMERIC(14,2)`. **Tipos de cambio**: `NUMERIC(18,6)`. En el API viajan como **texto**.
- **Monedas**: texto de 3 letras mayúsculas (`PEN`, `USD`), del catálogo de 9 monedas admitidas.
- **IDs**: `uuid`; el cliente puede enviar el suyo al crear, y repetir la petición no duplica.
- **Fechas**: columnas `DATE` (`occurred_on`, `purchase_date`, `due_date`, `paid_on`), **sin valor por defecto en la base**. El día lo calcula el API con la zona horaria del perfil. Antes se usaba `default current_date`, que en un servidor en UTC ya es "mañana" desde las 19:00 de Lima.
- Toda tabla de usuario tiene `user_id`, `created_at`, `updated_at` y `deleted_at` (borrado lógico, preparado para sincronizar offline).
- **Borrado lógico** para movimientos, transferencias y compras; **archivado** (`is_archived`) para cuentas, categorías y tarjetas. La única eliminación real es la de la cuenta de usuario completa.
- **Integridad entre usuarios**: las claves foráneas son **compuestas** `(id, user_id)`, de modo que un movimiento nunca puede apuntar a una cuenta o categoría de otra persona. Ya no hay RLS: el aislamiento lo impone el API, y estas claves son la segunda barrera.
- El saldo **no se guarda**: sale de `initial_balance` + movimientos + transferencias.
- Un movimiento **hereda la moneda de su cuenta**; la moneda de una cuenta no cambia después de crearla.
- **Nombre de categoría único por usuario y tipo**, sin distinguir mayúsculas ni tildes. `categories` tiene dos columnas: `name`, el nombre original que siempre se muestra, y `name_normalized` (minúsculas, sin tildes), que calcula el backend y sirve solo para la restricción única. La app nunca envía ni recibe la segunda.
- **El tipo de cambio puede no existir**: un usuario nuevo no tiene ninguna fila en `exchange_rates` hasta que lo configura.
- **Alta de usuario**: perfil con la moneda principal elegida al registrarse (por defecto `PEN`), secundaria `USD` (o `PEN` si la principal es `USD`), 9 categorías y la cuenta "Efectivo" en la moneda principal.
- **Crédito**: una tarjeta no se archiva con compras pendientes; eliminar el egreso de un pago devuelve la compra a pendiente.

## 3. Esquema
**Las migraciones de Prisma son la única fuente del esquema.** Viven en `atm-orbita-api/prisma/` (`schema.prisma` y `migrations/`). Tablas, restricciones, índices y consultas: `atm-orbita-api/docs/plans/02-modelo-de-datos.md`.

Este repositorio ya no contiene ningún SQL: `supabase/migrations/0001_init.sql` y su carpeta se eliminaron el 7 oct 2026.

## 4. Crédito (v1.1)
Ya no hay migración `0002` de Supabase. Las tablas `credit_cards` y `credit_purchases`, el pago atómico y la edición y eliminación de compras pendientes están en `atm-orbita-api/docs/plans/08-fase-5-tarjetas-de-credito.md`.

## 5. Cálculos (se implementan en `domain`, con pruebas unitarias)

El API hace estos mismos cálculos y debe dar exactamente los mismos resultados; la app los repite para responder al instante.

**Saldo de una cuenta** = `initial_balance` + ingresos − egresos + transferencias entrantes − transferencias salientes (sin borrados lógicos). Lo entrega `GET /accounts`.

**Ahorro total** (en la moneda de visualización `D`):
```
total = Σ convertir(saldo_i, moneda_i → D)   para cuentas con include_in_savings = true,
                                              is_archived = false
convertir(x, A → A) = x
convertir(x, A → B) = x × rate(A→B)          si existe rate(A→B)
                    = x ÷ rate(B→A)          si solo existe rate(B→A)
redondeo final: HALF_UP a 2 decimales (se redondea una sola vez, al sumar)
```
El tipo de cambio vigente es el más reciente del usuario para el par. **Si no hay ninguno**, no hay conversión: el total se muestra solo en la moneda principal y la app pide configurarlo ("Configura tu tipo de cambio"). En ese estado todas las cuentas están en la moneda principal, porque no se permite crear una en otra moneda.

**Transferencia entre monedas** (tres campos enlazados: `out`, `rate`, `in`):
- Cambia `out` o `rate` → `in = round(out × rate, 2)`.
- Cambia `in` → `rate = in ÷ out` (guardar con 6 decimales; mostrar 2–4).
- Se persisten los tres: `from_amount`, `to_amount`, `exchange_rate`.
- Misma moneda: `rate` no aplica (`null`), `to_amount = from_amount`.
- Los que mueven los saldos son los dos montos; el cambio es informativo.

**Pagar compra de crédito**: `POST /credit-purchases/{id}/pay` con cuenta, monto real y fecha.
- Si la moneda de la compra ≠ moneda de la cuenta, el monto real lo escribe el usuario (el banco aplica su propio cambio).
- Si coinciden, se sugiere el monto de la compra.

**Editar o eliminar una compra pendiente**: no cambia ningún saldo ni reporte (una compra pendiente no es un movimiento). Solo cambia la deuda de su tarjeta y la deuda total, que se calculan al leerlas.

## 6. Qué pide la app al API
| Necesidad | Endpoint |
|---|---|
| Saldos y ahorro total | `GET /accounts` |
| Todo lo de Inicio en una petición | `GET /home` |
| Movimientos y transferencias mezclados, por fecha | `GET /entries?from&to&accountId&q` |
| Reporte de un periodo | `GET /reports/summary?from&to` |
| Tarjetas y deuda | `GET /credit-cards` |
| Compras pendientes | `GET /credit-purchases?status=pending` |
| Pagar una compra | `POST /credit-purchases/{id}/pay` |
| Preferencias y tipo de cambio | `GET /settings`, `PUT /settings/fx` |
| Borrado lógico | `DELETE /transactions/{id}`, `DELETE /transfers/{id}` |

La lista completa, con cuerpos y errores, está en `atm-orbita-api/docs/plans/00-contexto-y-hallazgos.md`, sección 3.

## 7. Datos de ejemplo (usar en pruebas y previews)
Cambio manual: **1 US$ = S/ 3.20**.

| Cuenta | Moneda | Saldo | ¿Cuenta en ahorros? |
|---|---|---|---|
| Efectivo | PEN | 320.50 | sí |
| Débito principal | PEN | 1,245.80 | sí |
| Ahorros | PEN | 4,800.00 | sí |
| Cuenta Dólares | USD | 250.00 | sí (= S/ 800.00) |
| Billetera digital | PEN | 85.00 | **no** |

- **Ahorro total** = 320.50 + 1,245.80 + 4,800.00 + 800.00 = **S/ 7,166.30** = **US$ 2,239.47** (7,166.30 ÷ 3.20 = 2,239.46875 → redondeo HALF_UP).
- **Septiembre 2026**: ingresos S/ 4,200.00 (Sueldo 3,500.00 · Freelance 600.00 · Otros ingresos 100.00); gastos S/ 2,148.60 (Vivienda 800.00 · Alimentación 612.40 · Transporte 215.20 · Ocio 190.00 · Otros 183.00 · Salud 148.00); balance **+S/ 2,051.40**.
- **Transferencia**: Cuenta Dólares → Débito principal, US$ 20.00 a 3.20 = **S/ 64.00**. Si el banco da S/ 63.40, el cambio queda 3.17.
- **Crédito pendiente**: Pasajes S/ 240.00 (vence 5 oct), Cena S/ 96.00 y Audífonos S/ 189.90 (vencen 15 oct) → S/ 525.90; Suscripción US$ 12.00 (15 oct).
- **Pagar Pasajes** desde Débito principal por S/ 240.00: saldo 1,245.80 → **1,005.80**.

El API usa estos mismos datos como semilla de sus pruebas.

## 8. Pruebas de seguridad
Las pruebas de aislamiento entre usuarios ya no se hacen con SQL y RLS: son pruebas automáticas del API, que recorren todas sus rutas con un usuario intentando acceder a los datos de otro. Están en `atm-orbita-api/docs/plans/12-pruebas.md`, sección 5.

Lo que se espera sigue siendo lo mismo: A no ve filas de B, no puede crear nada a nombre de B ni apuntar movimientos a cuentas de B, y sin sesión no se puede leer nada.
