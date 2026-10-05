# 05 · Pantallas y flujos (maqueta)

Referencia visual interactiva (privada del usuario): https://claude.ai/artifact/2bTbBpoECpVaJXDBy1B5uq — **no** está disponible para ti; este documento es la fuente de verdad. Los datos de las maquetas son de ejemplo (`docs/03`, sección 7).

Los wireframes son orientativos. Las medidas de la maqueta están en px de un lienzo de 390×844; en Android usa dp y Material 3.

## 1. Tokens de diseño
**Colores**
| Token | Hex | Uso |
|---|---|---|
| `background` | `#F3F5F7` | Fondo de pantallas |
| `surface` | `#FFFFFF` | Tarjetas |
| `onSurface` (tinta) | `#0E1A2B` | Texto principal |
| `muted` | `#5B6778` | Texto secundario |
| `outline` | `#E3E8EE` | Bordes y divisores (`#EEF1F5` divisor suave) |
| `primary` | `#1D4ED8` | Acciones, selección, botón principal |
| `primarySoft` | `#E8EEFD` | Fondos suaves de iconos/botones secundarios |
| `hero` | `#0E1A2B` | Tarjetas destacadas (ahorro total, por pagar); texto `#FFFFFF`, secundario `#C7D2E3` |
| `income` / `incomeSoft` | `#0B7A5A` / `#E3F4EE` | Ingresos |
| `expense` / `expenseSoft` | `#B93815` / `#FBEAE4` | Egresos y vencimientos urgentes |
| `neutral` / `neutralSoft` | `#3B4A60` / `#EEF1F5` | Transferencias, chips inactivos |
| `chipBorder` | `#CBD3DE` | Borde de chips sin seleccionar |
| `switchOff` | `#8793A6` | Interruptor apagado |

Colores de categoría: Vivienda `#1D4ED8`, Alimentación `#0E7490`, Transporte `#B45309`, Ocio `#BE185D`, Otros `#64748B`, Salud `#7C3AED`, Sueldo `#0B7A5A`, Freelance `#0E7490`.

**Tipografía**: Manrope (400, 500, 600, 700, 800) — vía Google Fonts descargables o incluida en `res/font`. Títulos de pantalla 26 sp/800; monto destacado 38 sp/800; montos de tarjeta 16–20 sp/800; cuerpo 14–16 sp; etiquetas 12–13 sp/600.

**Formas**: tarjetas 18–20 dp; tarjeta destacada 24 dp; chips y botones tipo píldora 22 dp; botón principal 18 dp de radio y 56 dp de alto; campos 14 dp de radio y 52 dp de alto.

**Reglas**: objetivos táctiles ≥ 48 dp; ingresos con `+`, egresos con `−` (U+2212) además del color; iconos de línea (stroke ~1.9) sin emojis; sin tema oscuro en el MVP.

## 2. Navegación
```
Barra inferior:  Inicio · Cuentas · [ + ] · Reportes · Crédito(v1.1)
[+]  → Nuevo movimiento (pantalla completa)  ──(pestaña Transferencia)──► Transferencia
Inicio ─ engranaje ─► Ajustes
Inicio ─ tarjeta de crédito ─► Crédito ─ "Marcar como pagada" ─► Pagar
Cuentas ─ "Transferir" ─► Transferencia
```
Nuevo movimiento, Transferencia, Pagar y Ajustes **no** muestran la barra inferior y tienen botón de cierre/volver arriba a la izquierda.

## 3. Inicio
```
Resumen                                   (icono ajustes)
Inicio
┌────────────────────────────────────────┐
│ Ahorro total                  [S/|US$] │
│ S/ 7,166.30                            │
│ ≈ US$ 2,239.47 · cambio 3.20 (manual)  │
│ ─────────────────────────────────────  │
│ 4 de 5 cuentas incluidas   Elegir ›    │
└────────────────────────────────────────┘
┌──────────────────┐ ┌──────────────────┐
│ Ingresos · octubre│ │ Gastos · octubre │
│ +S/ 3,500.00      │ │ −S/ 30.50        │
└──────────────────┘ └──────────────────┘
Las transferencias y las compras de tarjeta pendientes no entran aquí.
┌────────────────────────────────────────┐
│ [icono] Tarjeta de crédito            ›  │   (solo v1.1 y si hay pendientes)
│ 4 compras pendientes · vence 5 oct      │
└────────────────────────────────────────┘
Últimos movimientos
 ↗ Almuerzo            Alimentación · Efectivo · 2 oct      −S/ 18.50
 ↗ Taxi al trabajo     Transporte · Débito principal · 2 oct −S/ 12.00
 ↙ Sueldo              Ingreso · Débito principal · 1 oct   +S/ 3,500.00
 ⇄ Cambio de dólares   Cuenta Dólares → Débito principal · 1 oct   US$ 20.00 → S/ 64.00
[ Inicio ][ Cuentas ][ + ][ Reportes ][ Crédito ]
```
- **Selector S/ | US$** (control segmentado, 2 opciones): cambia la moneda del total y la línea secundaria muestra el equivalente en la otra moneda y el cambio usado ("manual" o "automático").
- "N de M cuentas incluidas" cuenta cuentas con `include_in_savings` sobre las no archivadas; toca → Cuentas.
- Tarjetas de mes: ingresos y egresos del **mes actual** (calendario), por moneda de visualización si hay una sola moneda; si hay varias, mostrar el total en la moneda predeterminada (sin convertir) o un renglón por moneda. Excluyen transferencias y crédito pendiente.
- Lista: últimos 4–5 movimientos y transferencias mezclados, por fecha desc. Icono: ↙ ingreso (`income`), ↗ egreso (`expense`), ⇄ transferencia (`neutral`). Título = descripción (si vacía, nombre de la categoría). Monto de transferencia entre monedas: `US$ 20.00 → S/ 64.00`.
- Estados: cargando (esqueleto), sin cuentas, sin movimientos ("Registra tu primer movimiento"), error con "Reintentar".

## 4. Cuentas
```
Cuentas                                [⇄ Transferir]
┌────────────────────────────────────────┐
│ Total en ahorros · 4 de 5 cuentas      │
│ S/ 7,166.30                            │
│ ≈ US$ 2,239.47 · cambio 3.20           │
└────────────────────────────────────────┘
┌────────────────────────────────────────┐
│ [icono] Cuenta Dólares      US$ 250.00 │
│         Ahorro · dólares     ≈ S/ 800.00│
│ ──────────────────────────────────────  │
│ Contar en el total de ahorros   (●━━)  │
└────────────────────────────────────────┘
 … Efectivo · Débito principal · Ahorros · Billetera digital (apagada) …
[ + Nueva cuenta ]  (borde punteado)
Cada cuenta tiene su propia moneda y un nombre que puedes cambiar…
```
- El interruptor actualiza `include_in_savings` y el total al instante.
- Iconos por tipo: efectivo (billete), banco (débito/ahorro), dólar (cuentas USD), billetera.
- Tocar la tarjeta abre **Editar cuenta**: nombre, tipo, interruptor, archivar. La **moneda no se puede cambiar** si ya tiene movimientos.
- **Nueva cuenta**: nombre, tipo (`cash`/`debit`/`savings`/`other`), moneda (PEN por defecto / USD), saldo inicial (opcional, ≥ 0), interruptor de ahorros (activado).
- Las cuentas en USD muestran `≈ S/ x` con el cambio vigente.

## 5. Nuevo movimiento
```
[X]  Nuevo movimiento
[ Egreso | Ingreso | Transferencia ]     ← "Transferencia" navega a la pantalla 6
┌────────────────────────────────────────┐
│ Monto                                  │
│ S/  45.52                              │
└────────────────────────────────────────┘
Sale de la cuenta        (Ingresa a la cuenta si es ingreso)
[ (icono) Débito principal        S/ 1,245.80  ▾ ]   ← desplegable: cuenta + saldo
Categoría
[ ● Alimentación                               ▾ ]   ← desplegable: punto de color + nombre
Descripción   ┌ Almuerzo con equipo             ┐   ← caja de texto de varias líneas
              └                          19/500 ┘
Fecha         [ 2 oct 2026                    ]
┌ Compra con tarjeta de crédito          (━●) ┐   (solo egreso, v1.1)
│ No descuenta de tus cuentas. Queda pendiente│
│ hasta que la pagues.                        │
│ Fecha límite de pago [ 15 oct 2026 ]        │   (solo si está activado)
└─────────────────────────────────────────────┘
[            Guardar egreso            ]
```
- **Egreso** y **Ingreso** cambian la lista de categorías (egreso: Alimentación, Transporte, Vivienda, Salud, Ocio, Otros; ingreso: Sueldo, Freelance, Otros ingresos) y el color del monto (ingreso en `income`). Al cambiar de tipo se selecciona la primera categoría.
- El símbolo del monto sigue la moneda de la cuenta elegida (`S/` o `US$`).
- Con "Compra con tarjeta de crédito" **activado**: se ocultan las cuentas, aparece "Fecha límite de pago", y el botón dice **"Guardar compra pendiente"** (crea una fila en `credit_purchases`, no un movimiento). Solo aplica a egresos.
- Botón: "Guardar egreso" / "Guardar ingreso" / "Guardar compra pendiente".
- Fecha: por defecto hoy; selector de fecha de Material 3.
- Validaciones: monto > 0 y ≤ 2 decimales; cuenta (salvo crédito) y categoría obligatorias; descripción opcional ≤ 500; fecha límite ≥ fecha de compra.
- Al guardar: vuelve a Inicio y se actualizan saldos y listas.

## 6. Transferencia entre cuentas
```
[X]  Transferir
[ Egreso | Ingreso | Transferencia● ]
┌ Desde  Cuenta Dólares           Saldo US$ 250.00 ┐
                    (↓)
┌ Hacia  Débito principal         Saldo S/ 1,245.80┐
┌────────────────────────────────────────┐
│ Monto que sale                         │
│ US$  20.00                             │
│ ────────────────────────────────────── │
│ Tipo de cambio · editable   Referencia │
│ 1 US$ = S/ [ 3.20 ]         manual 3.20│
│ ────────────────────────────────────── │
│ Monto que entra · editable             │
│ S/  64.00                              │
└────────────────────────────────────────┘
Si el banco aplicó otro cambio, edita el tipo de cambio o el monto que entra…
Saldos después de transferir
  Cuenta Dólares     US$ 230.00
  Débito principal   S/ 1,309.80
[        Guardar transferencia        ]
```
- Tocar "Desde"/"Hacia" abre un selector de cuenta (no se puede elegir la misma).
- Si las monedas son **iguales**, se oculta el tipo de cambio y "entra" = "sale".
- Si son **distintas**, el cambio sugerido es el vigente (manual); los tres campos están enlazados (ver reglas en `docs/03`, sección 5). Para el par inverso (S/ → US$), mostrar "1 S/ = US$ x" o convertir con la inversa.
- "Saldos después" se recalcula en vivo. Si un saldo quedara negativo, **avisar pero no bloquear**.
- Guarda en `transfers` con `from_amount`, `to_amount`, `exchange_rate`, fecha (hoy, editable) y nota opcional.

## 7. Reportes
```
Reportes                               [PDF · próximamente]
[ Por mes | Rango de fechas ]
[icono calendario] Septiembre 2026   1 – 30 sep        ← en "Rango": campos Desde / Hasta
┌────────────────────────────────────────┐
│ Ingresos S/ 4,200.00   Gastos S/ 2,148.60│
│ ────────────────────────────────────── │
│ Balance del periodo         +S/ 2,051.40│
└────────────────────────────────────────┘
Dónde gastas más
 Vivienda        S/ 800.00 · 37.2%   ████████▌
 Alimentación    S/ 612.40 · 28.5%   ██████▌
 Transporte      S/ 215.20 · 10.0%   ██▎
 Ocio · Otros · Salud …
Tus fuentes de ingreso
 Sueldo S/ 3,500.00 · 83.3% · Freelance S/ 600.00 · 14.3% · Otros ingresos S/ 100.00 · 2.4%
Las compras con tarjeta aparecen aquí solo cuando las marcas como pagadas. …
```
- **Por mes**: selector de mes/año (por defecto el mes actual) con flechas anterior/siguiente. **Rango**: selector de rango de Material 3. Ambos llaman a `report_period_summary` y `report_by_category` (`expense` e `income`).
- Barras: ancho = % del total de esa moneda; ordenadas de mayor a menor; color de la categoría; el porcentaje se calcula en la app sobre el total de la moneda.
- Con varias monedas en el periodo, repetir los bloques **por moneda** (S/ primero). Conversión a una sola moneda: fuera del MVP.
- El botón/etiqueta "PDF" es solo un marcador visible pero inactivo (o se omite); el PDF viene después.
- Estados: sin datos en el periodo ("No hay movimientos en este periodo"), cargando, error.

## 8. Tarjeta de crédito (v1.1) y Pagar
**Crédito**
```
Compras pendientes de pago
Tarjeta de crédito
┌────────────────────────────────────────┐
│ Por pagar                              │
│ S/ 525.90                              │
│ + US$ 12.00                            │
│ No cuenta en tus saldos ni en tus reportes hasta que la marques como pagada. │
└────────────────────────────────────────┘
[ + Registrar compra con tarjeta ]   → Nuevo movimiento con "crédito" activado
Ordenadas por fecha límite
┌ Pasajes                       S/ 240.00 ┐
│ Transporte · compra 10 sep               │
│ (Vence 5 oct · en 3 días)  [icono check] Marcar como pagada │
└──────────────────────────────────────────┘
… Cena en restaurante · Audífonos · Suscripción de software (US$ 12.00) …
```
- Totales por moneda de las compras `pending`. Orden: `due_date` ascendente.
- Chip de vencimiento: urgente (≤ 3 días o vencida) con `expenseSoft/expense`; normal con `neutralSoft/neutral`. Texto: "Vence 5 oct · en 3 días" / "Vencida hace 2 días".
- Estado vacío: "No tienes compras pendientes".

**Pagar (Marcar como pagada)**
```
[‹]  Marcar como pagada
┌ Pasajes — Transporte · compra 10 sep · vence 5 oct     S/ 240.00 ┐
Pagar desde   ( ■ Débito principal ■ ) ( Efectivo ) ( Ahorros )
Monto realmente descontado    S/ [ 240.00 ]
  Si la compra fue en dólares, escribe aquí los soles que te cobró el banco.
Fecha de pago · hoy, editable   [ 2 oct 2026 ]
┌ Se creará este egreso ─────────────────────────────────┐
│ Transporte · Débito principal          −S/ 240.00        │
│ Saldo de Débito principal   S/ 1,245.80 → S/ 1,005.80    │
└──────────────────────────────────────────────────────────┘
[ Confirmar pago ]
```
- Confirmar llama a `pay_credit_purchase`. Después vuelve a Crédito (la compra desaparece de pendientes) y el egreso aparece en movimientos y reportes con la fecha de pago.
- Moneda de la cuenta ≠ moneda de la compra: el monto real es obligatorio y lo escribe el usuario; si coinciden, se sugiere el monto de la compra.
- Más adelante: marcar varias compras a la vez; deshacer un pago (`unpay_credit_purchase`).
- Solo se muestran como destino las cuentas no archivadas.

## 9. Ajustes y pantallas no maquetadas
**Ajustes**
```
[‹] Ajustes
Monedas:        Moneda predeterminada  Soles (S/) · Ver ahorro total en  S/ o US$ (se alterna en Inicio)
Tipo de cambio: [ Manual● | Automático · pronto ]
                1 US$ = S/ [ 3.20 ]      Equivale a 1 S/ = US$ 0.3125
                Se usa para el total de ahorros y como valor sugerido en las transferencias…
Seguridad:      Inicias sesión con tu correo y contraseña. Cada persona solo ve sus propios datos.
Próximamente:   Exportar a PDF · Modo sin internet · Cambio automático diario · Acceso con Google o Apple
```
- Editar el cambio inserta una fila nueva en `exchange_rates` (`source = 'manual'`, fecha de hoy); el inverso se muestra con 4 decimales. Validar `> 0`.
- **Agregar (no maquetado)**: botón "Cerrar sesión" y el correo de la cuenta.
- "Acceso con Google o Apple": en Android solo Google y más adelante.

**Pantallas necesarias que no están maquetadas** — implementarlas en el mismo estilo y **confirmar con el usuario** antes de darlas por cerradas:
1. **Login / Registro**: correo, contraseña (mostrar/ocultar), botón principal, enlace a la otra pantalla, errores en español. "Olvidé mi contraseña": después.
2. **Lista completa de movimientos** (desde "Últimos movimientos"): scroll infinito con paginación y filtros por cuenta, categoría y fechas; búsqueda por descripción.
3. **Editar/Eliminar movimiento y transferencia**: mismo formulario que "Nuevo" con datos cargados y acción "Eliminar" (borrado lógico) con confirmación.
4. **Editar cuenta** y **gestionar categorías** (crear, renombrar, color, archivar).

## 10. Textos y formatos
- Idioma: español (es-PE). Fechas: `2 oct 2026` (`d MMM yyyy`, mes en minúscula, sin punto). Mes: `Septiembre 2026`.
- Montos: `S/ 1,245.80`, `US$ 250.00`. Equivalencias: `≈ S/ 800.00`. Cambio: `1 US$ = S/ 3.20`.
- Errores: breves y accionables ("No se pudo guardar. Revisa tu conexión e inténtalo de nuevo.").
- Accesibilidad: `contentDescription` en iconos sin texto, estado de interruptores y chips (seleccionado/no) anunciado a TalkBack.
