# 06 · Plan de construcción y pruebas

Reglas: una fase a la vez · al cerrar cada fase, correr las pruebas, resumir y **pedir revisión al usuario** · no adelantar funciones de fases posteriores. Estado actual en `CLAUDE.md`.

## Fases

### Fase 0 — Base de datos (Supabase)
- Crear proyecto `gastos-dev`, enlazar CLI, aplicar `0001_init.sql` (ver `docs/04`).
- Crear dos usuarios de prueba y ejecutar las pruebas RLS (`docs/03`, sección 8).
- **Listo cuando**: tablas con RLS activo, trigger de alta crea perfil + categorías + "Efectivo", pruebas RLS pasan, Advisors sin alertas críticas.

### Fase 1 — Proyecto Android, tema y login
- Proyecto Compose + Hilt + Navigation + version catalog; tema (`docs/05`, sección 1); componentes base.
- Cliente Supabase por `BuildConfig`; registro, inicio y cierre de sesión; sesión persistente.
- **Listo cuando**: un usuario nuevo se registra, entra, cierra la app, la reabre y sigue con sesión; cerrar sesión vuelve a login; errores en español.

### Fase 2 — Cuentas y categorías
- Listar, crear, editar (nombre, tipo, interruptor de ahorros), archivar cuentas; moneda PEN/USD; saldo inicial.
- Gestionar categorías (crear, renombrar, color, archivar).
- **Listo cuando**: se ven los saldos de `account_balances`; el interruptor persiste; la moneda no se edita con movimientos.

### Fase 3 — Movimientos e Inicio
- Nuevo movimiento (ingreso/egreso), editar, eliminar (lógico), lista de movimientos paginada con filtros.
- Inicio: ahorro total (en la moneda predeterminada), tarjetas del mes, últimos movimientos.
- **Listo cuando**: crear un egreso de 45.52 cambia el saldo de la cuenta en 45.52 exactos; los totales del mes excluyen transferencias.

### Fase 4 — Transferencias
- Pantalla de transferencia con las tres reglas enlazadas (`out`, `rate`, `in`), saldos "después", guardado.
- **Listo cuando**: US$ 20 a 3.20 → S/ 64.00; editar el monto recibido recalcula el cambio; los saldos de ambas cuentas cuadran; misma moneda oculta el cambio.

### Fase 5 — Reportes, ahorro total con conversión y ajustes
- Ajustes: cambio manual (inserta en `exchange_rates`), inverso, cerrar sesión.
- Inicio/Cuentas: selector S/ ↔ US$ y totales convertidos.
- Reportes: por mes y por rango, totales por moneda, ranking de gasto, fuentes de ingreso.
- **Listo cuando**: con los datos de ejemplo (`docs/03`, sección 7) el ahorro total es S/ 7,166.30 y US$ 2,239.47, y septiembre da ingresos 4,200.00, gastos 2,148.60 y balance 2,051.40.

### Fase 6 — Tarjeta de crédito (v1.1)
- Migración `0002_credit_purchases.sql`. Registrar compra pendiente, lista por vencimiento, pagar (`pay_credit_purchase`), aviso en Inicio.
- **Listo cuando**: una compra pendiente **no** cambia saldos ni reportes; al pagarla se crea el egreso en la cuenta elegida con la fecha de pago y la categoría original, y recién ahí cuenta en reportes.

### Fase 7 — Pulido y lanzamiento
- Estados vacíos/errores/cargando en todas las pantallas, accesibilidad (TalkBack, contraste, tamaño de fuente), pruebas manuales completas, R8, firma de release, ícono y nombre.
- Proyecto `gastos-prod` con confirmación de correo y SMTP propio.

### Después (no iniciar sin pedido)
Exportar PDF · modo offline (Room + WorkManager) · tipo de cambio automático diario (Edge Function + cron) · acceso con Google · presupuestos y recurrentes · iOS.

## Estrategia de pruebas
| Nivel | Qué cubre | Herramientas |
|---|---|---|
| Unitarias (`domain`/`core`) | Dinero, conversiones, ahorro total, recálculo de transferencia, formato de montos y fechas | JUnit |
| ViewModel | Estados de pantalla, validaciones, errores | JUnit + MockK + Turbine, repositorios falsos |
| Base de datos | RLS, integridad entre usuarios, triggers, funciones de reporte y de pago | SQL en `gastos-dev` / Supabase local |
| UI | Flujos clave de punta a punta (ver abajo) | Compose UI Test |
| Manual | Lista de verificación de la Fase 7 | Dispositivo/emulador |

**Pruebas unitarias obligatorias (casos con resultado esperado)**
| Caso | Esperado |
|---|---|
| `0.1 + 0.2` con `BigDecimal` | `0.3` |
| Convertir US$ 20.00 a PEN con 3.20 | `64.00` |
| Ahorro total en S/ con los datos de ejemplo | `7,166.30` |
| Ahorro total en US$ con los datos de ejemplo | `2,239.47` (HALF_UP) |
| Cuenta excluida (Billetera digital) | No suma al total |
| Inversa: S/ 64.00 a US$ con 1 US$ = 3.20 | `20.00` |
| Transferencia: out=20.00, rate=3.20 | in=`64.00` |
| Transferencia: out=20.00, in=63.40 | rate=`3.17` |
| Transferencia misma moneda | `rate` nulo; `in = out` |
| Parseo de monto `"45,52"` y `"45.52"` | `45.52` |
| Monto `"45.523"` / `"0"` / `"-5"` | Inválido |
| Formato de `1245.8` en PEN | `S/ 1,245.80` |
| Porcentajes de gasto de septiembre | 37.2 / 28.5 / 10.0 / 8.8 / 8.5 / 6.9 |

**Pruebas de base de datos obligatorias**
- A no ve ni modifica filas de B; `anon` no lee nada.
- Un movimiento no puede apuntar a una cuenta o categoría de otro usuario (clave compuesta).
- Categoría de ingreso en un egreso → error del trigger.
- Transferencia entre monedas distintas sin `exchange_rate` → error; misma moneda con montos distintos → error.
- `account_balances` = suma esperada tras crear movimientos y transferencias; los borrados lógicos no cuentan.
- `pay_credit_purchase`: crea el egreso, marca `paid`, no se puede pagar dos veces; `unpay_credit_purchase` lo revierte.
- Intentar `DELETE` sobre tablas de datos → denegado.

**Pruebas de UI (flujos)**
1. Registro → Inicio vacío → crear cuenta → registrar ingreso → saldo correcto.
2. Egreso con descripción y categoría → aparece en Inicio y en Reportes.
3. Alternar S/ ↔ US$ en Inicio.
4. Apagar una cuenta en Cuentas → baja el ahorro total.
5. Transferencia USD → PEN editando cambio y monto recibido.
6. Compra con tarjeta → pendiente (no cambia saldos) → pagar → aparece como egreso.
7. Reporte por mes y por rango de fechas.

## Lista de verificación manual (Fase 7)
- [ ] Todas las pantallas coinciden con `docs/05` (colores, textos, estados).
- [ ] TalkBack lee interruptores, chips y botones con icono; contraste y objetivos ≥ 48 dp.
- [ ] Rotación y tamaños de fuente grandes no cortan textos.
- [ ] Sin conexión: mensaje claro y reintento; sin cierres inesperados.
- [ ] Ningún secreto en el repositorio; solo clave pública en la app.
- [ ] Cambios de esquema solo en `supabase/migrations/` y aplicados en `gastos-prod`.
