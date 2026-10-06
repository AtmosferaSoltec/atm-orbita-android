# ORBITA_SPEC · Especificación funcional de Orbita

> **Para qué sirve este archivo.** Describe **todo lo que hace la aplicación**, pantalla por pantalla y flujo por flujo, sin depender de Android. Es la referencia para construir **la misma app en iOS**: quien la porte debe poder hacerlo leyendo solo este archivo (más `docs/03` para el SQL).
>
> **Regla de mantenimiento.** Este archivo se actualiza **en el mismo cambio** en que se agrega o modifica un módulo, pantalla, regla o flujo en Android. Al terminar, se añade una fila al [registro de cambios](#13-registro-de-cambios) y se ajusta la columna *Estado* de la [tabla de módulos](#2-estado-de-los-módulos). Si el código y este archivo no coinciden, es un error que hay que corregir.

- App: **Orbita** · `applicationId` Android: `com.atmosferast.orbita`
- Última actualización: **6 oct 2026**
- Documentos relacionados: `01` visión y alcance · `02` arquitectura Android · `03` modelo de datos y SQL · `04` guía Supabase · `05` maquetas y tokens · `06` plan y pruebas

---

## 1. Qué es Orbita

App personal para registrar **ingresos y egresos** separados por **cuenta** (efectivo, débito, ahorro, billetera), en **soles (PEN)** y **dólares (USD)**, con **transferencias** entre cuentas propias, **compras con tarjeta de crédito pendientes de pago** y **reportes**. Es multiusuario: cada persona inicia sesión y solo ve sus datos.

- Backend: **Supabase** (Postgres + Auth + Row Level Security). No hay servidor propio; la app habla directo con Supabase usando la clave pública.
- Solo funciona **con internet** en el MVP.
- Idioma de la interfaz: **español (es-PE)**. Solo tema claro.

## 2. Estado de los módulos

Estados: **Maqueta** = pantalla navegable con datos de ejemplo, sin backend · **Implementado** = conectado a Supabase y probado · **Pendiente** = aún no existe.

| Módulo | Pantallas | Versión | Estado Android |
|---|---|---|---|
| Autenticación | Iniciar sesión, Registro | MVP | Maqueta |
| Inicio | Inicio | MVP | Maqueta |
| Cuentas | Cuentas, Nueva/Editar cuenta | MVP | Maqueta |
| Movimientos | Nuevo/Editar movimiento, Lista de movimientos | MVP | Maqueta |
| Transferencias | Transferir, Editar transferencia | MVP | Maqueta |
| Reportes | Reportes | MVP | Maqueta |
| Categorías | Categorías, Nueva/Editar categoría | MVP | Maqueta |
| Ajustes | Ajustes | MVP | Maqueta |
| Tarjeta de crédito | Crédito, Marcar como pagada | v1.1 | Maqueta |
| Base de datos (Supabase) | — | MVP | Pendiente (Fase 0) |

**Qué significa "Maqueta" hoy.** Todo vive en memoria con los [datos de ejemplo](#11-datos-de-ejemplo). Funcionan de verdad: la navegación, el selector S/ ↔ US$, los interruptores de ahorro (recalculan el total), los tres campos enlazados de la transferencia, el teclado de monto de Nuevo movimiento, el filtro por cuenta y la búsqueda en la lista, y quitar de pendientes una compra al pagarla. **No** funcionan todavía: guardar/eliminar (solo cierran la pantalla), los selectores de fecha, los filtros de categoría y fechas, el selector de cuenta de la transferencia, las validaciones y el inicio de sesión real.

Las pantallas de Autenticación, Lista de movimientos, Editar movimiento/transferencia, Nueva/Editar cuenta y Categorías **no estaban en la maqueta original** (`docs/05`, sección 9): su diseño es una propuesta pendiente de confirmación.

## 3. Conceptos y reglas de negocio

| Concepto | Definición |
|---|---|
| **Cuenta** | Lugar donde hay dinero. Tiene nombre editable, tipo (`cash` Efectivo, `debit` Débito, `savings` Ahorro, `other` Otra), **una sola moneda** y el interruptor "Contar en el total de ahorros" (activado por defecto). Se puede archivar. |
| **Movimiento** | Un **ingreso** o un **egreso** en una cuenta, con categoría, monto, fecha y descripción opcional. Hereda la moneda de su cuenta. |
| **Categoría** | Clasifica movimientos. Es de ingreso **o** de egreso, tiene nombre y color. Al registrarse el usuario recibe: egreso → Alimentación, Transporte, Vivienda, Salud, Ocio, Otros; ingreso → Sueldo, Freelance, Otros ingresos. |
| **Transferencia** | Mueve dinero entre dos cuentas propias distintas. **No es ingreso ni gasto.** Puede cambiar de moneda. |
| **Compra con tarjeta de crédito** | Gasto pendiente de pago. Es un **recordatorio**: no afecta saldos, ahorros ni reportes hasta que se marca como pagada; recién entonces se crea un egreso. |
| **Tipo de cambio** | Unidades de la moneda destino por 1 unidad de la moneda origen (1 US$ = S/ 3.20). En el MVP lo fija el usuario a mano. |
| **Ahorro total** | Suma de los saldos de las cuentas no archivadas con el interruptor activado, convertida a la moneda de visualización. |

Reglas que no cambian entre plataformas:

1. **Dinero con decimales exactos**: tipo decimal (Android `BigDecimal`, iOS `Decimal`), escala 2, redondeo *half up*. Nunca coma flotante. Se redondea **una sola vez, al final** del cálculo.
2. El **saldo no se guarda**: se calcula (saldo inicial + ingresos − egresos + transferencias que entran − transferencias que salen), ignorando lo borrado.
3. Los reportes y las tarjetas del mes cuentan **solo movimientos**: excluyen transferencias y compras de crédito pendientes.
4. Los totales de reportes se muestran **por moneda, sin mezclar**.
5. **Borrado lógico**: eliminar marca `deleted_at`; nada se borra físicamente desde la app.
6. Los identificadores son **UUID generados en el cliente**.
7. La app solo lleva la clave pública de Supabase; la seguridad por usuario la impone RLS.

## 4. Formatos y textos

| Dato | Formato | Ejemplo |
|---|---|---|
| Monto | símbolo + espacio + `#,##0.00` (miles `,`, decimal `.`) | `S/ 1,245.80` · `US$ 250.00` |
| Ingreso / egreso | signo + monto; el egreso usa el signo menos tipográfico U+2212 | `+S/ 3,500.00` · `−S/ 18.50` |
| Equivalencia | `≈` + monto | `≈ S/ 800.00` |
| Tipo de cambio | 2 a 4 decimales | `1 US$ = S/ 3.20` · inverso `0.3125` |
| Fecha | `d MMM yyyy`, mes en minúscula y sin punto | `2 oct 2026` |
| Fecha corta | `d MMM` | `2 oct` |
| Mes | nombre con mayúscula inicial + año | `Septiembre 2026` |
| Porcentaje | un decimal | `37.2%` |

- Entrada de montos: en Nuevo/Editar movimiento, con el teclado de monto propio (sección 7.5). En el resto de campos, teclado decimal del sistema: acepta `.` o `,` como separador, máximo 2 decimales. Siempre mayor que 0.
- El color nunca es la única señal: ingresos y egresos llevan siempre su signo.
- Errores breves y accionables: "No se pudo guardar. Revisa tu conexión e inténtalo de nuevo."

## 5. Diseño

Los valores exactos (colores, tipografía Manrope, radios) están en `docs/05`, sección 1. Resumen de lo que debe sentirse igual en iOS:

- Fondo gris muy claro `#F3F5F7` y tarjetas blancas con esquinas de 20.
- **Tarjeta destacada** (Ahorro total en Inicio, Total en ahorros en Cuentas): esquinas de 28 y fondo en **degradado diagonal de tres colores**, de arriba-izquierda a abajo-derecha: violeta `#3B1D8F` → azul `#1D4ED8` → verde azulado `#0E7490`. Encima lleva un brillo celeste (`#67E8F9` al 30 %) que nace en la esquina superior derecha y una sombra violeta en la inferior izquierda. Texto blanco; etiquetas al 85–90 % de opacidad. Las superficies interiores (píldoras, franjas) son blanco al 14 %.
- **El color distingue ahorro de crédito.** Ese degradado violeta-azul es el de las tarjetas de **ahorro** (Inicio y Cuentas). La tarjeta "Por pagar" de **Crédito** usa la misma forma y brillo pero en **amarillos**: amarillo `#FDE047` → ámbar `#FBBF24` → naranja ámbar `#F59E0B`, con brillo blanco y **texto oscuro** `#0E1A2B` (secundario al 80 %), porque el blanco no se lee sobre amarillo.
- Azul `#1D4ED8` para acciones y selección; verde `#0B7A5A` ingresos; rojo intenso `#E53935` (fondo suave `#FDECEA`) egresos, errores, acciones destructivas y urgencias; gris azulado `#3B4A60` transferencias.
- Botón principal: ancho completo, 56 de alto, radio 18, fijo al pie de los formularios.
- Campos: 52 de alto, radio 14, borde fino. Controles tipo píldora con radio 22.
- Iconos de línea (trazo ~1.9), sin emojis. Áreas táctiles de al menos 48.

Componentes reutilizados en toda la app:

| Componente | Comportamiento |
|---|---|
| **Control segmentado** | Píldora con 2–3 opciones, una seleccionada (fondo blanco). Variante de ancho completo (pestañas) y compacta (S/ \| US$). |
| **Desplegable** | Campo que muestra la opción elegida con un icono o punto de color a la izquierda, un detalle opcional a la derecha (p. ej. el saldo) y una flecha hacia abajo. Al tocarlo abre un menú del ancho del campo; la opción actual lleva un check. Elegir cierra el menú. |
| **Caja de texto** | Campo de varias líneas (alto mínimo ~4 líneas) con contador `n/máx` abajo a la derecha; no deja escribir más del máximo. |
| **Teclado de monto** | Teclado numérico propio (sin tecla de punto): los dígitos entran por la derecha y el decimal se coloca solo. Detalle en 7.5. Hoy solo lo usa Nuevo/Editar movimiento; los demás montos (transferencia, pago de crédito, saldo inicial, tipo de cambio) aún usan el teclado decimal del sistema. |
| **Chip** | Píldora seleccionable; seleccionada = fondo azul y texto blanco. |
| **Fila con interruptor** | Texto + interruptor; toda la fila es táctil. |
| **Campo selector** | Campo de solo lectura con icono (calendario) que abre un selector nativo. |
| **Fila de movimiento** | Icono en recuadro de color suave (↙ ingreso verde, ↗ egreso rojo, ⇄ transferencia gris), título, subtítulo y monto a la derecha. |
| **Diálogo de confirmación** | Título, texto, "Cancelar" y la acción destructiva en rojo. |
| **Mensaje de estado** | Icono + título + texto + acción opcional, para vacío y error. |

## 6. Navegación

```
Sin sesión:   Iniciar sesión ⇄ Registro
Con sesión:   barra inferior  Inicio · Cuentas · [ + ] · Reportes · Crédito
```

| Desde | Acción | Va a |
|---|---|---|
| Iniciar sesión / Registro | Enviar con éxito | Inicio (se borra el historial de navegación) |
| Barra inferior | Botón central **+** | Nuevo movimiento |
| Inicio | Engranaje | Ajustes |
| Inicio | "Elegir ›" en la tarjeta de ahorro | pestaña Cuentas |
| Inicio | Tarjeta "Tarjeta de crédito" | pestaña Crédito |
| Inicio | "Ver todos" | Lista de movimientos |
| Inicio / Lista | Tocar un movimiento | Editar movimiento |
| Inicio / Lista | Tocar una transferencia | Editar transferencia |
| Cuentas | "Transferir" | Transferir |
| Cuentas | Tocar una cuenta | Editar cuenta |
| Cuentas | "Nueva cuenta" | Nueva cuenta |
| Nuevo movimiento | Pestaña "Transferencia" | Transferir (reemplaza la pantalla) |
| Transferir | Pestaña "Egreso" o "Ingreso" | Nuevo movimiento (reemplaza la pantalla) |
| Crédito | "Registrar compra con tarjeta" | Nuevo movimiento con el interruptor de crédito activado |
| Crédito | "Marcar como pagada" | Marcar como pagada |
| Ajustes | "Gestionar categorías" | Categorías → Nueva/Editar categoría |
| Ajustes | "Cerrar sesión" | Iniciar sesión (se borra el historial) |

- Las pantallas que no son pestañas **no muestran la barra inferior** y tienen arriba a la izquierda un botón circular: **X** en las que crean algo (Nuevo movimiento, Transferir, Nueva cuenta, Nueva categoría) y **‹** en las demás.
- Botón atrás del sistema: cierra la pantalla actual; en una pestaña distinta de Inicio vuelve a Inicio; en Inicio sale de la app.
- La pestaña **Crédito** pertenece a la v1.1.

## 7. Pantallas

Cada pantalla indica: qué muestra, cómo se comporta, validaciones, estados y qué lee o escribe en el backend (nombres de `docs/03`).

### 7.1 Iniciar sesión y Registro

**Muestra:** todo el contenido va **centrado verticalmente** en la pantalla (si no cabe, se desplaza). Arriba, las **tarjetas flotantes** (ver abajo); luego título ("Inicia sesión" / "Crea tu cuenta"), subtítulo, campo **Correo**, campo **Contraseña** con botón de ojo para mostrar/ocultar, botón principal ("Ingresar" / "Crear cuenta") y enlace a la otra pantalla ("¿No tienes cuenta? Regístrate" / "¿Ya tienes cuenta? Inicia sesión"). En Registro, bajo la contraseña: "Mínimo 8 caracteres."

**Tarjetas flotantes:** dos tarjetas decorativas de 264 × 166 con esquinas de 28, centradas sobre el formulario en una franja de 220 de alto: una con el degradado de **ahorro** (violeta-azul, texto blanco) y otra con el de **crédito** (amarillo, texto oscuro), ambas de la sección 5. **No llevan datos**: solo el nombre "Orbita" en la esquina superior izquierda y, en la superior derecha, un **chip metálico** dibujado (40 × 30, esquinas de 6, degradado blanco → gris `#E2E8F0` → `#B6C0CE`, con líneas finas oscuras que simulan los contactos) para que se lea como tarjeta bancaria. Están apiladas y desfasadas: la de delante inclinada −4° y la de atrás +7°, al 92 % de tamaño, desplazada arriba a la derecha. Flotan suavemente (±6 de vaivén vertical, ciclo de 2.6 s, en sentidos opuestos) y **cada 3.2 s se alternan**: la de delante pasa atrás y la otra al frente, separándose hacia los lados a mitad del cambio (transición de 0.9 s). Llevan sombra del color de su degradado. No son táctiles.

**Comportamiento:** el ojo alterna la visibilidad de la contraseña. Los errores aparecen sobre el botón, con icono de alerta y texto rojo.

**Validaciones:** correo con formato válido; contraseña de 8 caracteres o más. Mensajes: "Correo o contraseña incorrectos.", correo ya registrado, sin conexión.

**Backend:** Supabase Auth con correo y contraseña. La sesión se guarda y se renueva sola: al abrir la app con sesión vigente se entra directo a Inicio. Al registrarse, un trigger crea el perfil, las 9 categorías iniciales y la cuenta "Efectivo" (PEN).

**Fuera de alcance:** "Olvidé mi contraseña", acceso con Google.

### 7.2 Inicio

**Muestra, de arriba abajo:**

1. Encabezado: "Resumen" (pequeño) y "Inicio" (título); a la derecha el botón de Ajustes.
2. **Tarjeta "Ahorro total"**, la pieza principal de la pantalla:
   - Fondo: la **tarjeta destacada con degradado** de tres colores (violeta → azul → verde azulado) descrita en la sección 5. Todo el texto es blanco.
   - Arriba: "Ahorro total" y, a la derecha, el selector `S/ | US$` sobre una pista translúcida (opción elegida en blanco).
   - El total en grande (42, peso 800): el símbolo de moneda va más pequeño (24) y con 72 % de opacidad para que destaque la cifra.
   - Debajo, en una **píldora translúcida** (blanco al 14 %): el equivalente en la otra moneda y el cambio usado (`≈ US$ 2,239.47 · cambio 3.20 (manual)`).
   - Al pie, una **franja translúcida** táctil con icono de billetera: "4 de 5 cuentas incluidas" y "Elegir ›".
3. Dos tarjetas: **"Ingresos · octubre"** (`+S/ 3,500.00`, verde) y **"Gastos · octubre"** (`−S/ 30.50`, rojo).
4. Nota: "Las transferencias y las compras de tarjeta pendientes no entran aquí."
5. **Tarjeta "Tarjeta de crédito"** (v1.1, solo si hay pendientes): "4 compras pendientes · vence 5 oct" (la fecha límite más próxima).
6. **"Últimos movimientos"** con "Ver todos": los 4–5 más recientes, movimientos y transferencias mezclados, por fecha descendente.

**Comportamiento:**

- El selector cambia la moneda del total; la línea secundaria muestra siempre la otra. La elección es la misma que en Ajustes y en Cuentas.
- "N de M cuentas": N = cuentas con el interruptor activado, M = cuentas no archivadas.
- Tarjetas del mes: totales del **mes calendario actual**. Con una sola moneda se muestra esa; con varias, el total de la moneda predeterminada sin convertir (o un renglón por moneda).
- Fila de movimiento: título = descripción, o el nombre de la categoría si está vacía. Subtítulo = `categoría · cuenta · fecha corta`; si el título ya es la categoría, en su lugar va "Ingreso" o "Egreso". Monto con signo y color.
- Fila de transferencia: título = nota (o "Transferencia"); subtítulo = `origen → destino · fecha`; monto `US$ 20.00 → S/ 64.00` entre monedas distintas, o un solo monto si es la misma.

**Estados:** cargando (bloques grises con la forma del contenido) · sin movimientos ("Registra tu primer movimiento", totales en cero, sin "Ver todos") · sin cuentas · error ("No se pudo cargar" + "Reintentar").

**Backend:** vista `account_balances`; tipo de cambio vigente de `exchange_rates`; `report_period_summary` del mes actual; últimos `transactions` y `transfers`; conteo de `credit_purchases` pendientes (v1.1).

### 7.3 Cuentas

**Muestra:** título "Cuentas" y botón "Transferir"; tarjeta destacada con degradado "Total en ahorros · 4 de 5 cuentas" con el total y, en una píldora translúcida, el equivalente (`≈ US$ 2,239.47 · cambio 3.20`); una tarjeta por cuenta; botón de borde punteado "+ Nueva cuenta"; texto de ayuda.

**Tarjeta de cuenta:** icono según tipo (billete = efectivo, banco = débito/ahorro, signo de dólar = cualquier cuenta en USD, billetera = otra), nombre, subtítulo `tipo · moneda` ("Ahorro · dólares"), saldo a la derecha y, si la cuenta no está en soles, `≈ S/ 800.00` debajo. Bajo un divisor, la fila "Contar en el total de ahorros" con su interruptor.

**Comportamiento:** el interruptor guarda `include_in_savings` y actualiza el total **al instante** (aquí y en Inicio). Tocar la parte superior de la tarjeta abre Editar cuenta.

**Backend:** `account_balances`; `update` de `accounts.include_in_savings`.

### 7.4 Nueva cuenta / Editar cuenta

| Campo | Nueva | Editar |
|---|---|---|
| Nombre | obligatorio, 1–60 caracteres | editable |
| Tipo | chips Efectivo / Débito / Ahorro / Otra (por defecto Débito) | editable |
| Moneda | chips Soles (S/) / Dólares (US$) (por defecto Soles) | **bloqueada**, con el aviso "La moneda no se puede cambiar porque la cuenta ya tiene movimientos." |
| Saldo inicial | opcional, ≥ 0, con el símbolo de la moneda elegida | no se muestra |
| Contar en el total de ahorros | interruptor, activado | editable |

Botón al pie: "Guardar cuenta" / "Guardar cambios". En Editar hay además **"Archivar cuenta"** (rojo suave) con el texto "La cuenta deja de aparecer en las listas, pero conserva sus movimientos." y diálogo de confirmación "¿Archivar esta cuenta?".

**Backend:** `insert` / `update` en `accounts` (`is_archived = true` al archivar).

### 7.5 Nuevo movimiento / Editar movimiento

**Muestra, de arriba abajo:**

1. Barra superior: **X** y el título ("Nuevo movimiento" / "Editar movimiento"). En Editar, a la derecha, un botón de papelera rojo.
2. Control segmentado **Egreso | Ingreso | Transferencia**. En Editar solo Egreso | Ingreso.
3. Tarjeta **Monto**: símbolo de moneda y el monto en grande. Se escribe con el **teclado propio de la app** (ver "Teclado de monto" más abajo), nunca con el teclado del sistema.
4. **"Sale de la cuenta"** (egreso) o **"Ingresa a la cuenta"** (ingreso): **desplegable** con las cuentas no archivadas. Cada opción muestra el icono de la cuenta, su nombre y su saldo a la derecha.
5. **Categoría**: **desplegable** con las categorías del tipo elegido. Cada opción muestra el punto de color de la categoría y su nombre.
6. **Descripción**: **caja de texto de varias líneas**, opcional, máximo 500 caracteres, con contador `n/500`.
7. **Fecha**:
   - **Nuevo movimiento: no hay campo de fecha.** El movimiento toma la **fecha y hora del momento en que se guarda**. En su lugar se muestra, con un icono de reloj, el texto "Se guarda con la fecha y hora de este momento. Podrás cambiarla después, al editar el movimiento."
   - **Editar movimiento:** campo selector **Fecha** con la fecha del movimiento, editable.
8. Tarjeta **"Compra con tarjeta de crédito"** con interruptor (solo egreso nuevo, v1.1) y el texto "No descuenta de tus cuentas. Queda pendiente hasta que la pagues."
9. Al pie: el teclado de monto mientras está abierto; si no, el botón de guardar.

**Teclado de monto:**

- Es un panel blanco de esquinas redondeadas fijo al pie de la pantalla, con teclas grandes gris claro en 3 columnas: `1 2 3` / `4 5 6` / `7 8 9` / `(vacío) 0 ⌫` (el `0` va centrado bajo el `8`; el hueco inferior izquierdo queda libre), y debajo un botón azul de ancho completo **"✓ Listo"**. **No tiene tecla de punto decimal.**
- **Los dígitos entran por la derecha y el punto se coloca solo** (como en Yape): el monto parte en `0.00` y al pulsar 4, 5, 5, 2 se ve `0.04` → `0.45` → `4.55` → `45.52`. El monto se muestra siempre con 2 decimales y separador de miles.
- **⌫** quita el último dígito (`45.52` → `4.55`); **mantenerla pulsada** deja el monto en `0.00`.
- Máximo `9,999,999.99`; los dígitos que lo superarían se ignoran.
- Cada tecla da una vibración corta.
- Mientras el monto vale `0.00` se ve en gris claro; al escribir toma su color (tinta en egreso, verde en ingreso).
- **Cuándo se abre:** al entrar a Nuevo movimiento ya está abierto; en Editar empieza cerrado. Tocar la tarjeta Monto lo abre (y cierra el teclado del sistema si estaba visible). Con el teclado abierto, la tarjeta Monto lleva borde azul y un cursor que parpadea tras el monto.
- **Cuándo se cierra:** con "Listo", con el botón atrás del sistema (el primer atrás cierra el teclado, el segundo la pantalla) o al tocar la caja de Descripción (que sí usa el teclado del sistema). Al cerrarse reaparece el botón de guardar.
- Internamente el monto se guarda como entero en céntimos mientras se escribe y se convierte a decimal al guardar.

**Comportamiento:**

- Cambiar entre Egreso e Ingreso cambia la lista de categorías, **selecciona la primera** y cambia el color del monto (verde en ingreso).
- Tocar "Transferencia" abre la pantalla Transferir en lugar de esta.
- El símbolo del monto sigue la moneda de la cuenta elegida.
- Con el interruptor de crédito **activado**: se oculta el desplegable de cuenta, aparece el campo "Fecha límite de pago" dentro de la tarjeta y el botón dice "Guardar compra pendiente". Se guarda una compra pendiente, **no** un movimiento.
- Texto del botón: "Guardar egreso" / "Guardar ingreso" / "Guardar compra pendiente" / "Guardar cambios" (Editar).
- La papelera pide confirmación: "¿Eliminar este movimiento?" — "Dejará de contar en tus saldos y reportes."
- Al guardar o eliminar se vuelve a la pantalla anterior y se actualizan saldos y listas.

**Validaciones:** monto > 0 (el teclado ya garantiza 2 decimales); cuenta obligatoria (salvo crédito); categoría obligatoria; descripción ≤ 500; fecha límite ≥ fecha de compra.

**Backend:** `insert` / `update` en `transactions`; borrado lógico con `deleted_at`; con crédito, `insert` en `credit_purchases` (estado `pending`). Un trigger rechaza una categoría de tipo distinto al del movimiento. Al crear, la app no envía fecha: `occurred_on` toma el día actual y `created_at` guarda la fecha y hora exactas. **Pendiente de decidir:** `occurred_on` es solo fecha, así que hoy la **hora** queda registrada (`created_at`) pero no se puede editar; si se quiere editar también la hora hace falta una migración que agregue ese dato.

### 7.6 Transferir / Editar transferencia

**Muestra:**

1. Barra superior con **X** y "Transferir" (papelera en Editar).
2. Control segmentado con "Transferencia" seleccionada (no aparece en Editar).
3. Tarjeta **Desde** y tarjeta **Hacia**, cada una con icono, nombre de la cuenta y "Saldo S/ …"; entre ambas, un círculo con flecha hacia abajo.
4. Tarjeta de montos: **"Monto que sale"** (grande, moneda de origen) · **"Tipo de cambio · editable"** con `1 US$ = S/ [3.20]` y a la derecha "Referencia · manual 3.20" · **"Monto que entra · editable"** (moneda de destino).
5. Ayuda: "Si el banco aplicó otro cambio, edita el tipo de cambio o el monto que entra y el otro valor se recalcula."
6. **"Saldos después de transferir"**: los dos saldos resultantes.
7. **Fecha** (hoy, editable) y **Nota (opcional)**.
8. Botón "Guardar transferencia" / "Guardar cambios".

**Comportamiento:**

- Tocar Desde o Hacia abre un selector de cuenta; no se puede elegir la misma en ambas.
- **Misma moneda:** se ocultan el tipo de cambio y "Monto que entra"; entra lo mismo que sale.
- **Monedas distintas:** el cambio sugerido es el vigente y los tres campos están enlazados:
  - cambia "sale" o el cambio → `entra = redondear(sale × cambio, 2)`;
  - cambia "entra" → `cambio = entra ÷ sale` (se guarda con 6 decimales, se muestra con 2–4).
- El cambio siempre se expresa como unidades de destino por 1 de origen. De soles a dólares: `1 S/ = US$ 0.3125`.
- "Saldos después" se recalcula mientras se escribe. Si el saldo de origen quedara negativo, se pinta en rojo y aparece "El saldo de X quedaría en negativo." — **avisa pero no bloquea**.

**Validaciones:** montos > 0; cuentas distintas; cambio > 0 cuando las monedas difieren.

**Backend:** `insert` / `update` en `transfers` con `from_amount`, `to_amount`, `exchange_rate` (nulo si es la misma moneda), fecha y nota. Un trigger exige montos iguales en la misma moneda y cambio en monedas distintas.

### 7.7 Lista de movimientos

**Muestra:** barra con **‹** y "Movimientos"; campo de búsqueda "Buscar por descripción"; fila deslizable de chips de cuenta ("Todas las cuentas" + una por cuenta); botones "Categoría: todas" y "Fechas: todas"; la lista agrupada por mes ("Octubre 2026", "Septiembre 2026"), cada grupo en una tarjeta con las mismas filas que Inicio.

**Comportamiento:** la búsqueda filtra por descripción (o nota en transferencias) sin distinguir mayúsculas. El chip de cuenta filtra por esa cuenta; una transferencia aparece si la cuenta es origen o destino. Los filtros se combinan. Tocar una fila abre su edición. Carga por páginas al llegar al final.

**Estados:** sin resultados → "No hay movimientos con estos filtros".

**Backend:** `transactions` y `transfers` paginados (`range`), ordenados por fecha y creación descendentes, combinados en el cliente.

### 7.8 Reportes

**Muestra:**

1. Título "Reportes" y la etiqueta inactiva "PDF · próximamente".
2. Control segmentado **Por mes | Rango de fechas**.
3. Por mes: tarjeta con flecha ‹, icono de calendario, "Septiembre 2026" y "1 – 30 sep", flecha ›. Por rango: campos **Desde** y **Hasta**.
4. Tarjeta resumen: **Ingresos** (verde) y **Gastos** (rojo) lado a lado; debajo "Balance del periodo" con signo (verde si ≥ 0, rojo si es negativo).
5. **"Dónde gastas más"**: por categoría, punto de color, nombre, `S/ 800.00 · 37.2%` y una barra horizontal.
6. **"Tus fuentes de ingreso"**: igual, con categorías de ingreso.
7. Nota: "Las compras con tarjeta aparecen aquí solo cuando las marcas como pagadas. Las transferencias entre tus cuentas no cuentan como ingreso ni gasto."

**Comportamiento:** las flechas cambian de mes (por defecto, el actual). Barras ordenadas de mayor a menor, con ancho = porcentaje sobre el total **de esa moneda** y el color de la categoría. Con varias monedas en el periodo se repiten los bloques por moneda, soles primero.

**Estados:** sin datos → "No hay movimientos en este periodo" · cargando · error.

**Backend:** `report_period_summary(desde, hasta)` y `report_by_category(tipo, desde, hasta)` para `expense` e `income`. El porcentaje se calcula en la app.

### 7.9 Tarjeta de crédito (v1.1)

**Muestra:** "Compras pendientes de pago" (pequeño) y "Tarjeta de crédito"; tarjeta destacada en **degradado amarillo con texto oscuro** (sección 5) **"Por pagar"** con el total en soles en grande, `+ US$ 12.00` debajo si hay compras en dólares, y la nota "No cuenta en tus saldos ni en tus reportes hasta que la marques como pagada."; botón "+ Registrar compra con tarjeta"; "Ordenadas por fecha límite"; una tarjeta por compra.

**Tarjeta de compra:** descripción y monto; `categoría · compra 10 sep`; chip de vencimiento; botón "✓ Marcar como pagada".

**Chip de vencimiento:**

| Días hasta la fecha límite | Texto | Color |
|---|---|---|
| más de 3 | "Vence 15 oct · en 13 días" | gris |
| 2 o 3 | "Vence 5 oct · en 3 días" | rojo |
| 1 | "Vence 3 oct · mañana" | rojo |
| 0 | "Vence hoy" | rojo |
| −1 | "Vencida hace 1 día" | rojo |
| menos de −1 | "Vencida hace 2 días" | rojo |

**Comportamiento:** totales por moneda, sin mezclar. Orden por fecha límite ascendente.

**Estados:** vacío → "No tienes compras pendientes".

**Backend:** `credit_purchases` con `status = 'pending'`.

### 7.10 Marcar como pagada (v1.1)

**Muestra:** barra con **‹** y "Marcar como pagada"; tarjeta de la compra (descripción, monto, `categoría · compra 10 sep · vence 5 oct`); **"Pagar desde"** con las cuentas no archivadas; **"Monto realmente descontado"** con el símbolo de la cuenta elegida y la ayuda "Si la compra fue en dólares, escribe aquí los soles que te cobró el banco."; **"Fecha de pago · hoy, editable"**; tarjeta **"Se creará este egreso"** con `categoría · cuenta`, el monto en rojo y `Saldo de X: S/ 1,245.80 → S/ 1,005.80`; botón "Confirmar pago".

**Comportamiento:** si la moneda de la cuenta coincide con la de la compra se **sugiere** el monto de la compra; si no, el campo queda vacío y es obligatorio. Al cambiar de cuenta se recalcula la sugerencia. La vista previa se actualiza mientras se escribe. Al confirmar se vuelve a Crédito, la compra desaparece de pendientes y el egreso aparece en movimientos y reportes con la fecha de pago y la categoría original.

**Backend:** `pay_credit_purchase(compra, cuenta, monto_real, fecha)`, atómica: crea el egreso y marca la compra como `paid`. No se puede pagar dos veces.

### 7.11 Categorías y Nueva/Editar categoría

**Categorías:** barra con **‹**; control segmentado **Egresos | Ingresos**; lista en una tarjeta (punto de color, nombre, icono de lápiz); botón al pie "+ Nueva categoría", que abre el formulario con el tipo de la pestaña activa.

**Formulario:** Nombre (1–60 caracteres, placeholder "Ej. Mascotas"); Tipo Egreso/Ingreso (**solo al crear**; después no cambia); Color (7 muestras circulares, la elegida con anillo); botón "Guardar categoría" / "Guardar cambios". En Editar: "Archivar categoría" con confirmación — "Deja de aparecer al registrar movimientos, pero los anteriores se conservan."

**Validaciones:** el nombre no se repite dentro del mismo tipo (sin distinguir mayúsculas).

**Backend:** `insert` / `update` en `categories`.

### 7.12 Ajustes

**Muestra, por secciones:**

- **Monedas:** "Moneda predeterminada · Soles (S/)" y "Ver ahorro total en" con el selector `S/ | US$` (el mismo valor que en Inicio).
- **Tipo de cambio:** control **Manual | Automático · pronto** (Automático aún no se puede elegir); `1 US$ = S/ [3.20]` editable; "Equivale a 1 S/ = US$ 0.3125"; ayuda: "Se usa para el total de ahorros y como valor sugerido en las transferencias. Puedes cambiarlo cuando quieras."
- **Categorías:** fila "Gestionar categorías".
- **Seguridad:** "Inicias sesión con tu correo y contraseña. Cada persona solo ve sus propios datos.", el correo de la sesión y el botón rojo suave "Cerrar sesión".
- **Próximamente:** Exportar a PDF · Modo sin internet · Cambio automático diario · Acceso con Google.

**Comportamiento:** el inverso se recalcula al escribir, con 4 decimales; si el valor no es válido muestra "—". Cerrar sesión limpia el estado en memoria y vuelve a Iniciar sesión.

**Validaciones:** tipo de cambio > 0.

**Backend:** editar el cambio **inserta una fila nueva** en `exchange_rates` (`source = 'manual'`, fecha de hoy); no modifica la anterior. La moneda de visualización se guarda en `profiles.display_currency`.

## 8. Flujos completos

1. **Primer uso.** Registro → Inicio vacío ("Registra tu primer movimiento") con la cuenta "Efectivo" ya creada → Cuentas → Nueva cuenta → **+** → Nuevo movimiento (ingreso) → Inicio muestra el saldo, el ingreso del mes y el movimiento.
2. **Registrar un egreso.** **+** → Egreso → teclear el monto en el teclado de la app (solo dígitos) → Listo → cuenta en el desplegable → categoría en el desplegable → descripción → Guardar egreso → vuelve a la pantalla anterior; baja el saldo de la cuenta, sube "Gastos" del mes y aparece en Últimos movimientos y en Reportes.
3. **Ver el ahorro en dólares.** Inicio → tocar `US$` → el total pasa a dólares y la línea secundaria muestra los soles. Se refleja en Cuentas y en Ajustes.
4. **Excluir una cuenta del ahorro.** Cuentas → apagar el interruptor → el total baja al instante y el conteo pasa a "3 de 5".
5. **Transferir entre monedas.** Cuentas → Transferir → Desde Cuenta Dólares, Hacia Débito principal → sale US$ 20.00 → se propone 3.20 y entra S/ 64.00 → el banco dio S/ 63.40: se escribe ese monto y el cambio pasa a 3.17 → Guardar. Bajan los dólares, suben los soles; no cambia ningún reporte.
6. **Compra con tarjeta y su pago (v1.1).** Crédito → Registrar compra con tarjeta → monto, categoría, descripción, fecha límite → Guardar compra pendiente. No cambia saldos ni reportes. Más tarde: Crédito → Marcar como pagada → cuenta, monto real, fecha → Confirmar pago. La compra sale de pendientes y nace un egreso.
7. **Revisar un mes.** Reportes → Por mes → flechas hasta el mes → resumen, ranking de gastos y fuentes de ingreso. O Rango de fechas → Desde / Hasta.
8. **Corregir o eliminar.** Inicio o Lista de movimientos → tocar la fila → cambiar datos → Guardar cambios; o papelera → confirmar. Saldos y reportes se recalculan.
9. **Cambiar el tipo de cambio.** Ajustes → escribir el nuevo valor → cambian el ahorro total convertido, los `≈` de las cuentas en dólares y el cambio sugerido en transferencias nuevas. Las transferencias ya guardadas conservan su cambio.
10. **Cerrar sesión.** Ajustes → Cerrar sesión → Iniciar sesión.

## 9. Cálculos

```
saldo(cuenta)   = saldo_inicial + Σ ingresos − Σ egresos
                  + Σ transferencias que entran − Σ transferencias que salen

convertir(x, A→A) = x
convertir(x, A→B) = x × cambio(A→B)      si existe cambio(A→B)
                  = x ÷ cambio(B→A)      si solo existe el inverso

ahorro_total(D) = redondear( Σ convertir(saldo_i, moneda_i → D) , 2 )
                  sobre cuentas no archivadas con el interruptor activado

porcentaje(categoría) = redondear( total_categoría × 100 ÷ total_de_la_moneda , 1 )
días_para_vencer      = fecha_límite − hoy       (urgente si ≤ 3)
```

El tipo de cambio vigente es la fila más reciente del usuario para el par de monedas. Si no hay ninguna, la app pide configurarlo en Ajustes antes de mostrar conversiones.

Casos de prueba que ambas plataformas deben cumplir: `docs/06`, tabla "Pruebas unitarias obligatorias".

## 10. Backend compartido

Android e iOS usan **el mismo proyecto de Supabase** sin cambios. Esquema, RLS, triggers y funciones: `docs/03`. Operación: `docs/04`.

| Necesidad | Recurso |
|---|---|
| Sesión | Supabase Auth (correo y contraseña) |
| Saldos | vista `account_balances` |
| Movimientos | tabla `transactions` (con `categories` y `accounts` embebidos) |
| Transferencias | tabla `transfers` |
| Reportes | funciones `report_period_summary`, `report_by_category` |
| Tipo de cambio | tabla `exchange_rates` |
| Preferencias | tabla `profiles` |
| Crédito (v1.1) | tabla `credit_purchases`, funciones `pay_credit_purchase`, `unpay_credit_purchase` |

Postgres devuelve los montos como número JSON: hay que leerlos como texto/decimal, nunca como `Double`.

## 11. Datos de ejemplo

Los usan las maquetas y las pruebas. "Hoy" = **2 oct 2026**; cambio manual **1 US$ = S/ 3.20**.

| Cuenta | Tipo | Moneda | Saldo | En ahorros |
|---|---|---|---|---|
| Cuenta Dólares | Ahorro | USD | 250.00 | sí |
| Efectivo | Efectivo | PEN | 320.50 | sí |
| Débito principal | Débito | PEN | 1,245.80 | sí |
| Ahorros | Ahorro | PEN | 4,800.00 | sí |
| Billetera digital | Otra | PEN | 85.00 | no |

- Ahorro total: **S/ 7,166.30** = **US$ 2,239.47**.
- Octubre: ingresos S/ 3,500.00, gastos S/ 30.50.
- Septiembre: ingresos S/ 4,200.00, gastos S/ 2,148.60, balance +S/ 2,051.40; porcentajes de gasto 37.2 / 28.5 / 10.0 / 8.8 / 8.5 / 6.9.
- Crédito pendiente: S/ 525.90 + US$ 12.00 en 4 compras; la más próxima vence el 5 oct.
- Detalle completo: `docs/03`, sección 7. Los movimientos de septiembre de la lista (Alquiler, Supermercado, Diseño de logo, Cine, Farmacia, Ahorro del mes) son relleno de la maqueta.

## 12. Notas para la versión iOS

| Android | Equivalente sugerido en iOS |
|---|---|
| Kotlin + Jetpack Compose | Swift + SwiftUI |
| `BigDecimal`, HALF_UP | `Decimal` con redondeo `.plain` |
| `supabase-kt` (Auth, Postgrest) | `supabase-swift` |
| ViewModel + `StateFlow<UiState>` | `@Observable` / `ObservableObject` por pantalla |
| Navigation Compose, barra inferior | `TabView` + `NavigationStack`; formularios como `fullScreenCover` |
| `DropdownMenu` | `Menu` o `Picker` con estilo de menú |
| Teclado de monto (composable propio) | Vista propia en SwiftUI anclada al pie (no `keyboardType`); vibración con `UIImpactFeedbackGenerator` |
| Selector de fecha de Material 3 | `DatePicker` |
| `strings.xml` | String Catalog (`Localizable.xcstrings`) |
| Fuente en `res/font/manrope.ttf` | Manrope incluida en el bundle |
| Botón atrás del sistema | gesto de deslizar y botón ‹ |

- El botón **+** central de la barra inferior no es una pestaña: abre el formulario a pantalla completa.
- Los textos, formatos de la sección 4 y cálculos de la sección 9 deben dar **exactamente** los mismos resultados.

## 13. Registro de cambios

| Fecha | Cambio |
|---|---|
| 5 oct 2026 | Creación del documento. Maquetas navegables de todos los módulos en Android (datos de ejemplo, sin backend). |
| 5 oct 2026 | Nuevo movimiento: cuenta y categoría pasan de chips a **desplegables** (la cuenta muestra su saldo); la descripción pasa a **caja de texto de varias líneas** con contador `n/500`. |
| 5 oct 2026 | Nuevo movimiento: se quita el campo de fecha (toma la fecha y hora del momento de guardar; la fecha solo se cambia al editar). El monto se escribe con un **teclado propio** sin tecla de punto: los dígitos entran por la derecha y el decimal se coloca solo. |
| 5 oct 2026 | Teclado de monto: se elimina la tecla `00`; la fila inferior queda `(vacío) 0 ⌫`. |
| 5 oct 2026 | Inicio: la tarjeta "Ahorro total" pasa de fondo oscuro plano a **degradado azul** con brillos, píldora translúcida para el equivalente y franja translúcida para "cuentas incluidas". |
| 5 oct 2026 | Tarjeta destacada: el degradado pasa a tres colores (violeta `#3B1D8F` → azul `#1D4ED8` → verde azulado `#0E7490`) y se aplica también a Cuentas y Crédito; el fondo oscuro plano `#0E1A2B` deja de usarse. |
| 5 oct 2026 | Crédito: la tarjeta "Por pagar" pasa a degradado **amarillo** (`#FDE047` → `#FBBF24` → `#F59E0B`) con texto oscuro, para distinguir crédito (amarillo) de ahorro (violeta-azul). |
| 6 oct 2026 | Iniciar sesión / Registro: el icono y el nombre de la app se reemplazan por dos **tarjetas flotantes** (ahorro y crédito) sin datos, solo con "Orbita" arriba a la izquierda, que se alternan al frente cada 3.2 s. |
| 6 oct 2026 | Iniciar sesión / Registro: las tarjetas flotantes llevan un **chip metálico** arriba a la derecha y el contenido de la pantalla pasa a estar **centrado verticalmente**. |
| 6 oct 2026 | Color: el rojo de la app pasa de ladrillo `#B93815` a **rojo intenso `#E53935`**, y su fondo suave de `#FBEAE4` a `#FDECEA`. Afecta a egresos, errores, acciones destructivas y vencimientos. |
