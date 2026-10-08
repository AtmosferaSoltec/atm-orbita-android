# ORBITA_SPEC · Especificación funcional de Orbita

> **Para qué sirve este archivo.** Describe **todo lo que hace la aplicación**, pantalla por pantalla y flujo por flujo, sin depender de Android. Es la referencia para construir **la misma app en iOS**: quien la porte debe poder hacerlo leyendo solo este archivo (más el contrato del backend en `atm-orbita-api/docs/plans/`).
>
> **Regla de mantenimiento.** Este archivo se actualiza **en el mismo cambio** en que se agrega o modifica un módulo, pantalla, regla o flujo en Android. Al terminar, se añade una fila al [registro de cambios](#13-registro-de-cambios) y se ajusta la columna *Estado* de la [tabla de módulos](#2-estado-de-los-módulos). Si el código y este archivo no coinciden, es un error que hay que corregir.

- App: **Orbita** · `applicationId` Android: `com.atmosferast.orbita`
- Última actualización: **7 oct 2026**
- Documentos relacionados: `01` visión y alcance · `02` arquitectura Android · `03` modelo de datos · `04` guía del API · `05` maquetas y tokens · `06` plan y pruebas · planes del backend en `atm-orbita-api/docs/plans/`

---

## 1. Qué es Orbita

App personal para registrar **ingresos y egresos** separados por **cuenta** (efectivo, débito, ahorro, billetera), en **soles (PEN)** y **dólares (USD)**, con **transferencias** entre cuentas propias, **compras con tarjeta de crédito pendientes de pago** y **reportes**. Es multiusuario: cada persona inicia sesión y solo ve sus datos.

- Backend: **API propio** (`atm-orbita-api`: NestJS + PostgreSQL + Prisma), con autenticación propia. **No se usa Supabase ni ningún otro backend gestionado** (decisión del 7 oct 2026). Todas las plataformas hablan solo con ese API, por HTTPS y JSON, bajo `/api/v1`.
- Solo funciona **con internet** en el MVP.
- Idioma de la interfaz: **español (es-PE)**. Solo tema claro.

## 2. Estado de los módulos

Estados: **Maqueta** = pantalla navegable con datos de ejemplo fijos, sin lógica · **Funcional (datos de ejemplo)** = toda la lógica de la pantalla funciona de punta a punta (leer, crear, editar, eliminar, validar), pero sobre datos en memoria que se pierden al cerrar la app · **Implementado** = conectado al API y probado · **Pendiente** = aún no existe.

| Módulo | Pantallas | Versión | Estado Android |
|---|---|---|---|
| Autenticación | Iniciar sesión, Registro | MVP | Funcional (datos de ejemplo). Falta conectarla al API, y las pantallas de recuperar contraseña y eliminar cuenta |
| Inicio | Inicio | MVP | Funcional (datos de ejemplo) |
| Cuentas | Cuentas, Nueva/Editar cuenta | MVP | Funcional (datos de ejemplo) |
| Movimientos | Nuevo/Editar movimiento, Lista de movimientos | MVP | Funcional (datos de ejemplo) |
| Transferencias | Transferir, Editar transferencia | MVP | Funcional (datos de ejemplo) |
| Reportes | Reportes | MVP | Funcional (datos de ejemplo) |
| Categorías | Categorías, Nueva/Editar categoría | MVP | Funcional (datos de ejemplo) |
| Ajustes | Ajustes | MVP | Funcional (datos de ejemplo) |
| Tarjeta de crédito | Crédito, Nueva/Editar tarjeta, Marcar como pagada | v1.1 | Funcional (datos de ejemplo) |
| Backend (API propio) | — | MVP | Pendiente: planificado en `atm-orbita-api/docs/plans/`, aún sin construir. El esquema vive solo en las migraciones de Prisma de ese repositorio |

**Qué funciona hoy (7 oct 2026).** La app ya no es una maqueta: **todo se guarda, se edita y se elimina de verdad**, y cada total se **calcula** a partir de los movimientos (saldos, ahorro total, totales del mes, reportes), con las mismas reglas que tendrá el backend. Lo único que falta es **dónde** viven los datos: hoy están en memoria, sembrados con los [datos de ejemplo](#11-datos-de-ejemplo), y se pierden al cerrar la app. Funciona: iniciar y cerrar sesión, crear/editar/archivar cuentas, categorías y tarjetas, registrar/editar/eliminar ingresos, egresos y transferencias, registrar compras con tarjeta y pagarlas, cambiar monedas y tipo de cambio, los reportes por mes, año y rango, todos los selectores de fecha y las validaciones con su mensaje. Desde el 7 oct 2026 el modo demo cumple además las reglas decididas ese día: tipo de cambio opcional, nombres de categoría sin repetir, editar y eliminar compras pendientes, deshacer un pago al eliminar su egreso y no archivar tarjetas con deuda. **No** funciona todavía: la persistencia real (el API), la paginación de listas, la exportación a PDF, recuperar la contraseña, eliminar la cuenta y elegir la moneda al registrarse; las tres últimas necesitan el API.

**Cómo está armada para conectar los datos reales (Android).** Las pantallas no saben de dónde vienen los datos: hablan con **ViewModels**, y estos solo con los **repositorios** del dominio (`domain/repository`). Hoy cada repositorio tiene una implementación en memoria (`data/demo`). Conectar un módulo al API es escribir su implementación en `data/remote` y cambiar **una línea** en `di/AppModule.kt`; ni las pantallas ni las reglas se tocan. Con `API_BASE_URL` en `local.properties` la app usará el API; sin ella arranca en **modo demo** (cualquier correo y contraseña entran). Los datos siguen siendo los de ejemplo hasta que se migre cada módulo, fase por fase (`docs/06`). El código y las dependencias de Supabase se retiraron el 7 oct 2026: hasta que exista la implementación contra el API, **la sesión también está en modo demo**.

**Decisiones de esquema ya tomadas** (7 oct 2026; detalle en `atm-orbita-api/docs/plans/02-modelo-de-datos.md`): hay tabla de tarjetas de crédito y cada compra pertenece a una; el perfil guarda la moneda secundaria y la **zona horaria**; el movimiento guarda **solo la fecha**, sin hora editable; el nombre de categoría es único por usuario y tipo; el tipo de cambio puede no existir.

Las pantallas de Autenticación, Lista de movimientos, Editar movimiento/transferencia, Nueva/Editar cuenta y Categorías **no estaban en la maqueta original** (`docs/05`, sección 9): su diseño es una propuesta pendiente de confirmación.

## 3. Conceptos y reglas de negocio

| Concepto | Definición |
|---|---|
| **Cuenta** | Lugar donde hay dinero. Tiene nombre editable, tipo (`cash` Efectivo, `debit` Débito, `savings` Ahorro, `other` Otra), **una sola moneda** y el interruptor "Contar en el total de ahorros" (activado por defecto). Se puede archivar. |
| **Movimiento** | Un **ingreso** o un **egreso** en una cuenta, con categoría, monto, fecha y descripción opcional. Hereda la moneda de su cuenta. |
| **Categoría** | Clasifica movimientos. Es de ingreso **o** de egreso, tiene nombre y color. **El nombre no se repite dentro del mismo tipo**, sin distinguir mayúsculas ni tildes (sección 7.11). Al registrarse el usuario recibe: egreso → Alimentación, Transporte, Vivienda, Salud, Ocio, Otros; ingreso → Sueldo, Freelance, Otros ingresos. |
| **Transferencia** | Mueve dinero entre dos cuentas propias distintas. **No es ingreso ni gasto.** Puede cambiar de moneda. |
| **Moneda principal y secundaria** | Las dos monedas del usuario. La principal **se elige al registrarse** (por defecto soles) y ambas se pueden cambiar en Ajustes; la secundaria nace en dólares, o en soles si la principal ya es dólares. La principal es la de las cuentas y tarjetas nuevas; la secundaria sirve para ver equivalentes. El tipo de cambio manual es entre ambas. |
| **Tarjeta de crédito** | Cuenta de deuda. Tiene nombre editable, **una moneda** (por defecto la principal) y se puede archivar. Las compras nuevas se registran en la moneda de su tarjeta. Agrupa compras pendientes; su deuda es la suma de esas compras, por moneda. El usuario puede tener varias. |
| **Compra con tarjeta de crédito** | Pertenece a **una** tarjeta de crédito. Gasto pendiente de pago. Es un **recordatorio**: no afecta saldos, ahorros ni reportes hasta que se marca como pagada; recién entonces se crea un egreso. Mientras está pendiente **se puede editar y eliminar**; eso solo cambia la deuda de su tarjeta. |
| **Tipo de cambio** | Unidades de la moneda destino por 1 unidad de la moneda origen (1 US$ = S/ 3.20). En el MVP lo fija el usuario a mano. **Puede no existir**: un usuario nuevo no lo tiene hasta que lo escribe en Ajustes (regla 10 y sección 7.12). |
| **Ahorro total** | Suma de los saldos de las cuentas no archivadas con el interruptor activado, convertida a la moneda de visualización. |

Reglas que no cambian entre plataformas:

1. **Dinero con decimales exactos**: tipo decimal (Android `BigDecimal`, iOS `Decimal`), escala 2, redondeo *half up*. Nunca coma flotante. Se redondea **una sola vez, al final** del cálculo.
2. El **saldo no se guarda**: se calcula (saldo inicial + ingresos − egresos + transferencias que entran − transferencias que salen), ignorando lo borrado.
3. Los reportes y las tarjetas del mes cuentan **solo movimientos**: excluyen transferencias y compras de crédito pendientes.
4. Los totales de reportes se muestran **por moneda, sin mezclar**.
5. **Borrado lógico**: eliminar marca `deleted_at`; nada se borra físicamente desde la app.
6. Los identificadores son **UUID generados en el cliente**.
7. **Cada persona ve solo sus datos, y lo garantiza el API.** La app no lleva ninguna clave ni envía nunca un identificador de usuario: solo su token de sesión. Un recurso de otra persona se comporta como si no existiera.
8. **No hay datos del futuro.** Un movimiento o una transferencia no puede llevar fecha posterior a hoy, y los reportes no pasan del mes ni del día actual. Hacia atrás **no hay tope**. La única excepción son las fechas de **crédito** (fecha límite de pago, vencimientos), que son futuras por naturaleza.
9. **"Hoy" depende de la zona horaria del usuario.** El perfil guarda su zona horaria (identificador IANA, por ejemplo `America/Lima`), que la app envía al registrarse. El backend calcula "hoy" con ella, tanto para fechar un movimiento nuevo como para validar que una fecha no sea futura. La fecha de un movimiento se guarda **como fecha**, ya calculada, no como un instante: no cambia aunque la persona viaje.
10. **Sin tipo de cambio no hay otra moneda.** Mientras el usuario no haya configurado su tipo de cambio, todos sus datos están en la moneda principal: no puede crear cuentas ni tarjetas en otra moneda, ni ver el ahorro total en la secundaria. La app muestra el estado "Configura tu tipo de cambio" (sección 7.12).

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

### 4.1 Mensajes de validación y de error

Al tocar el botón de guardar, el formulario se revisa **en este orden** y se muestra **solo el primer problema**, en un aviso de validación (ver Componentes); no se guarda nada. En Iniciar sesión y Registro el mensaje va **dentro del formulario**, en rojo, encima del botón.

| Cuándo | Mensaje |
|---|---|
| Nombre vacío (cuenta, categoría, tarjeta) | "Escribe un nombre." |
| Nombre de más de 60 caracteres | "El nombre puede tener hasta 60 caracteres." |
| Monto en cero o vacío | "Escribe un monto mayor que cero." |
| Sin cuenta | "Elige una cuenta." |
| Sin categoría | "Elige una categoría." |
| Compra con tarjeta sin tarjeta | "Elige una tarjeta de crédito." |
| Descripción o nota de más de 500 caracteres | "El texto puede tener hasta 500 caracteres." |
| Transferencia con la misma cuenta en ambos lados | "Elige dos cuentas distintas." |
| Transferencia entre monedas sin tipo de cambio > 0 | "Escribe un tipo de cambio mayor que cero." |
| Fecha de movimiento, transferencia o pago posterior a hoy | "La fecha no puede ser posterior a hoy." |
| Fecha límite de pago anterior a hoy | "La fecha límite de pago no puede ser anterior a hoy." |
| Correo sin formato válido | "Escribe un correo válido." |
| Contraseña de menos de 8 caracteres | "La contraseña debe tener al menos 8 caracteres." |
| Correo o contraseña incorrectos | "Correo o contraseña incorrectos." |
| Registro con un correo que ya existe | "Ese correo ya tiene una cuenta. Inicia sesión." |
| Registro cuando se exige confirmar el correo | "Revisa tu correo y confirma tu cuenta para poder ingresar." |
| Nombre de categoría que ya existe en ese tipo | "Ya tienes una categoría de este tipo con ese nombre." — **bajo el campo Nombre**, no en el aviso |
| Operación en otra moneda sin tipo de cambio | "Configura tu tipo de cambio en Ajustes para usar otra moneda." |
| Editar, eliminar o pagar una compra que ya está pagada | "Esta compra ya está pagada." |
| Archivar una tarjeta de crédito con pagos pendientes | "Esta tarjeta tiene pagos pendientes. Págalos o elimínalos antes de archivarla." |
| Cambiar la categoría o la descripción del egreso de un pago con tarjeta | "Este egreso es el pago de una compra con tarjeta: solo puedes cambiar la cuenta, el monto y la fecha." |
| Confirmación al eliminar el egreso de un pago con tarjeta | "Este egreso es el pago de una compra con tarjeta. Al eliminarlo, la compra volverá a estar pendiente." |
| Enlace de recuperación de contraseña usado o vencido | "El enlace ya no es válido. Pide uno nuevo." |
| Demasiados intentos seguidos | "Demasiados intentos. Espera un momento." |
| La sesión terminó en el servidor | "Tu sesión terminó. Inicia sesión de nuevo." |
| Sin conexión | "Sin conexión. Revisa tu internet e inténtalo de nuevo." |
| El registro ya no existe | "Este registro ya no existe." |
| Cualquier otro fallo | "Algo salió mal. Inténtalo de nuevo." |

Orden de revisión por formulario: **movimiento** → monto, cuenta, categoría, largo de la descripción, fecha · **compra con tarjeta** → monto, tarjeta, categoría, largo de la descripción, fecha límite · **transferencia** → cuentas distintas, montos, tipo de cambio, largo de la nota, fecha · **pago de compra** → cuenta, monto, fecha · **sesión** → correo, contraseña. En modo demo la sesión no se valida: cualquier correo y contraseña entran.

Las validaciones de la app dan respuesta inmediata; **el API vuelve a validar todo y es quien decide**. Sus errores llegan con un código estable (por ejemplo `CATEGORY_NAME_TAKEN`, `FX_RATE_NOT_CONFIGURED`), y la app elige el texto. El API no envía textos de interfaz.

Formularios que no se pueden llenar todavía muestran un aviso a pantalla completa en lugar del formulario: Nuevo movimiento sin ninguna cuenta → "Aún no tienes cuentas — Crea una cuenta para poder registrar movimientos."; Transferir con menos de dos cuentas → "Necesitas dos cuentas — Una transferencia mueve dinero entre dos cuentas tuyas.". Dentro de Nuevo movimiento, si no hay categorías del tipo elegido, en lugar del desplegable va "No tienes categorías de este tipo. Créalas en Ajustes."; y con el interruptor de crédito activado y sin tarjetas, "Aún no tienes tarjetas de crédito".

## 5. Diseño

Los valores exactos (colores, tipografía Manrope, radios) están en `docs/05`, sección 1. Resumen de lo que debe sentirse igual en iOS:

- Fondo gris muy claro `#F3F5F7` y tarjetas blancas con esquinas de 20.
- **Tarjeta destacada** (Ahorro total en Inicio, Total en ahorros en Cuentas): esquinas de 28 y fondo en **degradado diagonal de tres colores**, de arriba-izquierda a abajo-derecha: violeta `#3B1D8F` → azul `#1D4ED8` → verde azulado `#0E7490`. Encima lleva un brillo celeste (`#67E8F9` al 30 %) que nace en la esquina superior derecha y una sombra violeta en la inferior izquierda. Texto blanco; etiquetas al 85–90 % de opacidad. Las superficies interiores (píldoras, franjas) son blanco al 14 %.
- **El color distingue ahorro de crédito.** Ese degradado violeta-azul es el de las tarjetas de **ahorro** (Inicio y Cuentas). La tarjeta "Deuda total" de **Crédito** usa la misma forma y brillo pero en **amarillos**: amarillo `#FDE047` → ámbar `#FBBF24` → naranja ámbar `#F59E0B`, con brillo blanco. Como el blanco no se lee sobre amarillo, hay dos variantes:
  - **Con velo y texto blanco** (tarjeta "Deuda total" de Crédito): texto en blanco (etiqueta al 85 %, nota al 90 %) sobre el velo descrito abajo.
  - **Sin velo y con texto oscuro** `#0E1A2B` (tarjeta flotante del inicio de sesión).
- **Velo oscuro de las tarjetas destacadas.** Las tres tarjetas destacadas de las pantallas — "Ahorro total" (Inicio), "Total en ahorros" (Cuentas) y "Deuda total" (Crédito) — llevan sobre su degradado un **velo vertical negro del 0 % arriba al 80 % abajo**, para que se vean uniformes: el color pleno arriba y la tarjeta oscureciéndose hacia el pie. Las tarjetas flotantes del inicio de sesión no lo llevan.
- Azul `#1D4ED8` para acciones y selección; verde intenso `#2EAD5B` (fondo suave `#E6F6EC`) ingresos; rojo intenso `#E53935` (fondo suave `#FDECEA`) egresos, errores, acciones destructivas y urgencias; gris azulado `#3B4A60` para el monto de las transferencias; celeste `#0EA5E9` (fondo suave `#E0F2FE`) para el icono de transferencias y cambios de moneda; naranja `#F97316` (fondo suave `#FFEDD5`) para el icono del aviso de tarjeta de crédito en Inicio.
- Botón principal: ancho completo, 56 de alto, radio 18, fijo al pie de los formularios.
- Campos: 52 de alto, radio 14, borde fino. Controles tipo píldora con radio 22.
- Iconos de línea (trazo ~1.9), sin emojis. Áreas táctiles de al menos 48.

Componentes reutilizados en toda la app:

| Componente | Comportamiento |
|---|---|
| **Control segmentado** | Píldora con 2–3 opciones, una seleccionada (fondo blanco). Variante de ancho completo (pestañas) y compacta (S/ \| US$). |
| **Desplegable** | Campo que muestra la opción elegida con un icono o punto de color a la izquierda, un detalle opcional a la derecha (p. ej. el saldo) y una flecha hacia abajo. Al tocarlo abre un menú del ancho del campo; la opción actual lleva un check. Elegir cierra el menú. |
| **Caja de texto** | Campo de varias líneas (alto mínimo ~4 líneas) con contador `n/máx` abajo a la derecha; no deja escribir más del máximo. |
| **Teclado de monto** | Teclado numérico propio (sin tecla de punto): los dígitos entran por la derecha y el decimal se coloca solo. Detalle en 7.5. Lo usan **Nuevo/Editar movimiento** y **Transferir** (sus dos montos). Los demás montos (pago de crédito, saldo inicial) y el tipo de cambio aún usan el teclado decimal del sistema. |
| **Chip** | Píldora seleccionable; seleccionada = fondo azul y texto blanco. |
| **Fila con interruptor** | Texto + interruptor; toda la fila es táctil. |
| **Campo selector** | Campo de solo lectura con icono (calendario) que abre el **calendario** de la app. |
| **Calendario** | Diálogo propio (no el selector nativo del sistema), con el estilo de la app: tarjeta blanca de esquinas 28, centrada, con margen lateral de 20. De arriba abajo: **título** pequeño en gris ("Elige la fecha" / "Elige el rango de fechas"); **la selección en grande** (`2 oct 2026`, o `24 sep – 2 oct 2026` en rango; mientras falta un extremo dice "Desde" o "Hasta"); **fila de navegación** con botón circular **‹**, el **mes-año con una flecha ⌄** centrado y botón circular **›**; iniciales de los días **D L M M J V S** (la semana empieza en domingo); rejilla de días de **6 filas fijas** (el diálogo no cambia de alto entre meses); y dos botones píldora de igual ancho: **"Cancelar"** (gris suave) y **"Aceptar"** (azul; gris y desactivado si la selección está incompleta). **Días:** el elegido es un círculo azul relleno con número blanco; **hoy** lleva un anillo azul y número azul; los no elegibles van en gris muy claro y no responden. **Rango:** el primer toque fija el inicio, el segundo el fin (si el segundo es anterior al inicio, pasa a ser el nuevo inicio); con un rango completo, un toque nuevo empieza otro; inicio y fin pueden ser el mismo día. Los días intermedios llevan una **banda azul suave** `#E8EEFD` con número azul, que une ambos círculos y termina redondeada en el borde de cada semana y del mes. **Moverse:** ‹ › cambian de mes, y también **deslizar** la rejilla hacia los lados. Tocar el **mes-año** cambia a la vista de **meses**: el centro muestra solo el año (flecha ⌃), ‹ › cambian de **año** y la rejilla son los 12 meses en píldoras (3 × 4: Ene … Dic), el mes visible en azul; tocar un mes vuelve a los días de ese mes. **Tope:** por defecto no se puede pasar de **hoy** (regla 8 de la sección 3): los días y meses posteriores salen desactivados y › se atenúa al llegar al mes (o año) actual; hacia atrás no hay límite. Tiene una variante que permite fechas futuras, para las fechas de crédito. Lo usan Reportes (rango) y Transferir (una fecha). |
| **Fila de movimiento** | Icono en recuadro de color suave (**↗ ingreso verde, flecha hacia arriba; ↙ egreso rojo, flecha hacia abajo**; ⇄ transferencia o cambio de moneda **celeste** `#0EA5E9` sobre `#E0F2FE`), título, subtítulo y monto a la derecha. El monto de la transferencia sigue en gris azulado. |
| **Selector de mes** | Tarjeta con **‹** a la izquierda, **›** a la derecha y, centrados, el mes-año con el rango de días debajo. ‹ = mes anterior, › = mes siguiente. Cada flecha puede desactivarse (atenuada) al llegar a un límite. Lo usan Reportes (sin límite hacia atrás; hacia adelante llega hasta el mes actual) y la Lista de movimientos (5 meses). |
| **Selector de año** | Igual que el selector de mes, pero con el **año** (`2026`) y debajo su rango: `1 ene – 31 dic`, o **`1 ene – hoy`** en el año en curso. ‹ = año anterior, › = año siguiente, atenuada en el año actual. Lo usa Reportes. |
| **Aviso de validación** | Barra oscura con texto blanco que aparece unos segundos sobre el botón principal cuando un formulario no se puede guardar o una operación falla. Un aviso nuevo reemplaza al anterior. Textos en la sección 4.1. |
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
| Inicio | Tarjeta "Tarjetas de Crédito" | pestaña Crédito |
| Inicio | Tarjeta "Ingresos · mes" o "Gastos · mes" | pestaña Reportes (mes actual, con ese detalle primero) |
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
| Crédito | Tocar la fila superior de una tarjeta | repliega o despliega ese bloque (no navega) |
| Crédito | "Editar tarjeta" (dentro del bloque desplegado) | Editar tarjeta |
| Crédito | "Nueva tarjeta de crédito" | Nueva tarjeta |
| Ajustes | "Gestionar categorías" | Categorías → Nueva/Editar categoría |
| Ajustes | "Cerrar sesión" | Iniciar sesión (se borra el historial) |

- Las pantallas que no son pestañas **no muestran la barra inferior** y tienen arriba a la izquierda un botón circular: **X** en las que crean algo (Nuevo movimiento, Transferir, Nueva cuenta, Nueva categoría) y **‹** en las demás.
- Botón atrás del sistema: cierra la pantalla actual; en una pestaña distinta de Inicio vuelve a Inicio; en Inicio sale de la app.
- La pestaña **Crédito** pertenece a la v1.1.

## 7. Pantallas

Cada pantalla indica: qué muestra, cómo se comporta, validaciones, estados y qué lee o escribe en el backend (endpoints del API; lista completa en la sección 10).

### 7.1 Iniciar sesión y Registro

**Muestra:** todo el contenido va **centrado verticalmente** en la pantalla (si no cabe, se desplaza). Arriba, las **tarjetas flotantes** (ver abajo); luego título ("Inicia sesión" / "Crea tu cuenta"), subtítulo, campo **Correo**, campo **Contraseña** con botón de ojo para mostrar/ocultar, botón principal ("Ingresar" / "Crear cuenta") y enlace a la otra pantalla ("¿No tienes cuenta? Regístrate" / "¿Ya tienes cuenta? Inicia sesión"). En Registro, bajo la contraseña: "Mínimo 8 caracteres."

**Tarjetas flotantes:** dos tarjetas decorativas de 264 × 166 con esquinas de 28, centradas sobre el formulario en una franja de 220 de alto: una con el degradado de **ahorro** (violeta-azul, texto blanco) y otra con el de **crédito** (amarillo, texto oscuro), ambas de la sección 5. **No llevan datos**: solo el nombre "Orbita" en la esquina superior izquierda y, en la superior derecha, un **chip metálico** dibujado (40 × 30, esquinas de 6, degradado blanco → gris `#E2E8F0` → `#B6C0CE`, con líneas finas oscuras que simulan los contactos) para que se lea como tarjeta bancaria. Están apiladas y desfasadas: la de delante inclinada −4° y la de atrás +7°, al 92 % de tamaño, desplazada arriba a la derecha. Flotan suavemente (±6 de vaivén vertical, ciclo de 2.6 s, en sentidos opuestos) y **cada 3.2 s se alternan**: la de delante pasa atrás y la otra al frente, separándose hacia los lados a mitad del cambio (transición de 0.9 s). Llevan sombra del color de su degradado. No son táctiles.

**Comportamiento:** el ojo alterna la visibilidad de la contraseña. Los errores aparecen sobre el botón, con icono de alerta y texto rojo.

**Validaciones:** correo con formato válido; contraseña de 8 caracteres o más. Mensajes: "Correo o contraseña incorrectos.", correo ya registrado, sin conexión.

**Backend:** autenticación propia del API con correo y contraseña (`POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`). La app recibe un token de acceso de 15 minutos y un token de renovación que guarda cifrado; con él la sesión se renueva sola, y al abrir la app con sesión vigente se entra directo a Inicio. Al registrarse, el API crea en una sola operación el perfil (con la zona horaria que envía la app), las 9 categorías iniciales y la cuenta "Efectivo" **en la moneda principal elegida**. El usuario nace **sin tipo de cambio**.

**Moneda al registrarse** (decidido el 7 oct 2026; campo aún sin diseño). La pantalla de Registro incluye un desplegable **Moneda** con las 9 monedas admitidas (7.12). Arranca en la moneda de la región del teléfono si es una de ellas; si no, en soles. La app la envía al registrarse (`mainCurrency`). Con ella nacen la moneda principal del perfil y la cuenta "Efectivo". La moneda secundaria nace en dólares, o en soles si la principal es dólares. Antes todo usuario nacía con soles y dólares y "Efectivo" en soles.

**Recuperar contraseña** (requisito para publicar; pantallas aún sin diseño). En Iniciar sesión, el enlace "¿Olvidaste tu contraseña?" pide el correo y muestra siempre "Si ese correo tiene una cuenta, te enviamos un enlace", exista o no. El correo trae un enlace **de un solo uso que vence a los 30 minutos**; abre la app en una pantalla para escribir la contraseña nueva. Al guardarla se cierran todas las sesiones abiertas y se vuelve a Iniciar sesión. Un enlace usado o vencido muestra "El enlace ya no es válido. Pide uno nuevo." Endpoints: `POST /auth/forgot-password` y `POST /auth/reset-password`.

**Fuera de alcance:** acceso con Google.

### 7.2 Inicio

**Muestra, de arriba abajo:**

1. Encabezado, arriba a la izquierda: **"Bienvenido"** (título, sin emoji) y debajo, en gris y tamaño de cuerpo (14), "Revisa cómo van tus finanzas"; a la derecha el botón de Ajustes.
2. **Tarjeta "Ahorro total"**, la pieza principal de la pantalla:
   - Fondo: la **tarjeta destacada con degradado** de tres colores (violeta → azul → verde azulado) descrita en la sección 5. Todo el texto es blanco.
   - Arriba: "Ahorro total" y, a la derecha, el selector `[principal] | [secundaria]` (por defecto `S/ | US$`; las dos monedas de Ajustes) sobre una pista translúcida (opción elegida en blanco).
   - El total en grande (42, peso 800): el **símbolo de moneda va al mismo tamaño y color que la cifra** (`S/ 7,166.30`).
   - Justo debajo, como **texto de apoyo sin píldora ni fondo** (14, blanco al 85 %): el mismo total en la otra moneda y el cambio usado, introducido por "o" en lugar de `≈`: `o US$ 2,239.47 · cambio 3.20 (manual)`.
   - Al pie, una **franja translúcida** táctil con icono de billetera: "4 de 5 cuentas incluidas" y "Elegir ›".
3. Dos tarjetas de ancho completo, **una sobre otra** (separación de 10): **"Ingresos · Octubre"** (`+S/ 3,500.00`, verde) y debajo **"Gastos · Octubre"** (`−S/ 30.50`, rojo). Cada una lleva a la izquierda un icono en recuadro de color suave — **línea de tendencia hacia arriba** en ingresos (verde), **hacia abajo** en gastos (rojo) — y a su derecha la etiqueta (14, peso 700; antes 12) con el monto debajo (20, peso 800, en su color; antes 16). En la etiqueta, "Ingresos"/"Gastos" va en gris `#5B6778` y el **mes, con mayúscula inicial, en un gris más claro** `#94A0B2` (igual que el "·" que lo precede). A la derecha, una flecha **›** en ese gris claro indica que la tarjeta es táctil.
4. Nota: "Las transferencias y las compras de tarjeta pendientes no entran aquí."
5. **Tarjeta "Tarjetas de Crédito"** (v1.1, solo si hay pendientes), con el icono de tarjeta en **naranja** `#F97316` sobre fondo naranja suave `#FFEDD5`: "4 pagos pendientes · vence 5 oct" (pagos pendientes de todas las tarjetas y la fecha límite más próxima).
6. **"Últimos movimientos"** con "Ver todos": los 4–5 más recientes, movimientos y transferencias mezclados, por fecha descendente.

**Comportamiento:**

- El selector cambia la moneda del total; la línea secundaria muestra siempre la otra. La elección es la misma que en Ajustes y en Cuentas.
- "N de M cuentas": N = cuentas con el interruptor activado, M = cuentas no archivadas.
- Tocar una tarjeta del mes abre la pestaña **Reportes** en la pestaña "Mes", con el **mes actual** seleccionado y el detalle de lo tocado primero: desde "Ingresos" va arriba "Tus fuentes de ingreso"; desde "Gastos", "Dónde gastas más" (ver 7.8).
- Tarjetas del mes: totales del **mes calendario actual**. Con una sola moneda se muestra esa; con varias, el total de la moneda predeterminada sin convertir (o un renglón por moneda).
- Fila de movimiento: título = descripción, o el nombre de la categoría si está vacía. Subtítulo = `categoría · cuenta · fecha corta`; si el título ya es la categoría, en su lugar va "Ingreso" o "Egreso". Monto con signo y color.
- Fila de transferencia: título = nota (o "Transferencia"); subtítulo = `origen → destino · fecha`; monto `US$ 20.00 → S/ 64.00` entre monedas distintas, o un solo monto si es la misma.

**Estados:** cargando (bloques grises con la forma del contenido) · sin movimientos ("Registra tu primer movimiento", totales en cero, sin "Ver todos") · sin cuentas · error ("No se pudo cargar" + "Reintentar").

**Sin tipo de cambio configurado** (usuario nuevo): la tarjeta "Ahorro total" muestra el total en la moneda principal, el selector de moneda aparece desactivado y, en lugar de la línea "o US$ … · cambio …", va el enlace **"Configura tu tipo de cambio ›"**, que abre Ajustes.

**Backend:** `GET /home` devuelve en una sola petición el ahorro total en ambas monedas, el tipo de cambio vigente, los totales del mes actual, los últimos movimientos y transferencias, y el resumen de compras pendientes (v1.1).

### 7.3 Cuentas

**Muestra:** título "Cuentas" y botón "Transferir"; tarjeta destacada con degradado "Total en ahorros · 4 de 5 cuentas" con el total y, justo debajo, como texto de apoyo sin píldora ni fondo (14, blanco al 85 %), el equivalente introducido por "o" (`o US$ 2,239.47 · cambio 3.20`), igual que en Inicio; una tarjeta por cuenta; botón de borde punteado "+ Nueva cuenta"; texto de ayuda.

**Tarjeta de cuenta:** icono según tipo (billete = efectivo, banco = débito/ahorro, signo de dólar = cualquier cuenta en USD, billetera = otra), nombre, subtítulo `tipo · moneda` ("Ahorro · dólares"), saldo a la derecha y, si la cuenta no está en soles, `≈ S/ 800.00` debajo. Bajo un divisor, la fila "Contar en el total de ahorros" con su interruptor.

**Comportamiento:** el interruptor guarda `include_in_savings` y actualiza el total **al instante** (aquí y en Inicio). Tocar la parte superior de la tarjeta abre Editar cuenta.

**Sin tipo de cambio configurado:** igual que en Inicio: total en la moneda principal y el enlace "Configura tu tipo de cambio ›" en lugar del equivalente. Ninguna cuenta muestra `≈`.

**Backend:** `GET /accounts` (cuentas con su saldo calculado y el ahorro total); `PATCH /accounts/{id}` para el interruptor.

### 7.4 Nueva cuenta / Editar cuenta

| Campo | Nueva | Editar |
|---|---|---|
| Nombre | obligatorio, 1–60 caracteres | editable |
| Tipo | chips Efectivo / Débito / Ahorro / Otra (por defecto Débito) | editable |
| Moneda | **desplegable** con las monedas admitidas (ver 7.12); por defecto, la **moneda predeterminada** del usuario | **bloqueada**, con el aviso "La moneda no se puede cambiar porque la cuenta ya tiene movimientos." |
| Saldo inicial | opcional, ≥ 0, con el símbolo de la moneda elegida | no se muestra |
| Contar en el total de ahorros | interruptor, activado | editable |

Botón al pie: "Guardar cuenta" / "Guardar cambios". En Editar hay además **"Archivar cuenta"** (rojo suave) con el texto "La cuenta deja de aparecer en las listas, pero conserva sus movimientos." y diálogo de confirmación "¿Archivar esta cuenta?".

**Sin tipo de cambio configurado:** el desplegable de Moneda queda fijo en la moneda principal, con la ayuda "Configura tu tipo de cambio en Ajustes para usar otras monedas."

**Backend:** `POST /accounts`, `PATCH /accounts/{id}` y `POST /accounts/{id}/archive`. La moneda y el saldo inicial no se pueden cambiar después de crear la cuenta.

### 7.5 Nuevo movimiento / Editar movimiento

**Muestra, de arriba abajo:**

1. Barra superior: **X** y el título ("Nuevo movimiento" / "Editar movimiento"). En Editar, a la derecha, un botón de papelera rojo.
2. Control segmentado **Egreso | Ingreso | Transferencia**. En Editar solo Egreso | Ingreso.
   - **Encabezado del tipo (solo en Nuevo, no en Editar):** debajo del control, una fila con un **icono sobre fondo suave** (44, esquinas redondeadas) a la izquierda y, a su derecha, **título** y **subtítulo**. Egreso: icono ↙ rojo sobre rojo suave, "Registrar egreso" / "¿En qué gastaste?". Ingreso: icono ↗ verde sobre verde suave, "Registrar ingreso" / "¿De dónde recibiste dinero?". Son los mismos iconos y colores de las filas de movimiento. Cambia al cambiar de pestaña.
3. Tarjeta **Monto**: símbolo de moneda y el monto en grande. Se escribe con el **teclado propio de la app** (ver "Teclado de monto" más abajo), nunca con el teclado del sistema.
4. **"Sale de la cuenta"** (egreso) o **"Ingresa a la cuenta"** (ingreso): **desplegable** con las cuentas no archivadas. Cada opción muestra el icono de la cuenta, su nombre y su saldo a la derecha. **Cuenta inicial en un movimiento nuevo:** la del **último movimiento registrado**; si no hay ninguno, la primera de la lista.
5. **Categoría**: **desplegable** con las categorías del tipo elegido. Cada opción muestra el punto de color de la categoría y su nombre.
6. **Descripción**: **caja de texto de varias líneas**, opcional, máximo 500 caracteres, con contador `n/500`.
7. **Fecha**:
   - **Nuevo movimiento: no hay campo de fecha.** El movimiento toma la **fecha y hora del momento en que se guarda**. En su lugar se muestra, con un icono de reloj, el texto "Se guarda con la fecha y hora de este momento. Podrás cambiarla después, al editar el movimiento."
   - **Editar movimiento:** campo selector **Fecha** con la fecha del movimiento; tocarlo abre el **calendario** de la app ("Elige la fecha"). **No admite fechas posteriores a hoy.**
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
- Con el interruptor de crédito **activado**: se oculta el desplegable de cuenta, aparecen dentro de la tarjeta el desplegable **"Tarjeta"** (las tarjetas de crédito no archivadas; por defecto la primera) y el campo "Fecha límite de pago", y el botón dice "Guardar compra pendiente". Se guarda una compra pendiente, **no** un movimiento.
- Texto del botón: "Guardar egreso" / "Guardar ingreso" / "Guardar compra pendiente" / "Guardar cambios" (Editar).
- La papelera pide confirmación: "¿Eliminar este movimiento?" — "Dejará de contar en tus saldos y reportes."
- **Si el egreso es el pago de una compra con tarjeta** (decidido el 7 oct 2026), el texto de la confirmación es: "Este egreso es el pago de una compra con tarjeta. Al eliminarlo, la compra volverá a estar pendiente." Al confirmar, el egreso se elimina y **la compra reaparece en la deuda de su tarjeta**, como si no se hubiera pagado. Desde aquí **nunca se elimina la compra**: eso solo se hace en Crédito, y solo mientras está pendiente, para no alterar la información.
- **Editar el egreso de un pago** (decidido el 7 oct 2026): solo se pueden cambiar la **cuenta**, el **monto** y la **fecha**, que son los datos del pago. No aparecen las pestañas Egreso | Ingreso; la **categoría** se muestra fija (como un chip) y la **descripción**, como texto, porque son las de la compra. Arriba del formulario va la nota "Este egreso es el pago de una compra con tarjeta: solo puedes cambiar la cuenta, el monto y la fecha." Para corregir la categoría o la descripción hay que eliminar el egreso (la compra vuelve a pendiente), editar la compra en Crédito y pagarla de nuevo.
- Al guardar o eliminar se vuelve a la pantalla anterior y se actualizan saldos y listas.

**Validaciones:** monto > 0 (el teclado ya garantiza 2 decimales); cuenta obligatoria (salvo crédito); categoría obligatoria; descripción ≤ 500; fecha límite ≥ fecha de compra.

**Backend:** `POST /transactions`, `PATCH /transactions/{id}` y `DELETE /transactions/{id}` (borrado lógico); con crédito, `POST /credit-purchases` (compra pendiente). El API rechaza una categoría de tipo distinto al del movimiento. Al crear, la app no envía fecha: **el API pone el día de hoy según la zona horaria del perfil** y lo guarda como fecha; aparte guarda el instante exacto de creación, que desempata el orden dentro del día. **Decidido (7 oct 2026):** el movimiento guarda solo la fecha; la hora no se edita.

### 7.6 Transferir / Editar transferencia

**Muestra:**

1. Barra superior con **X** y "Transferir" (papelera en Editar).
2. Control segmentado con "Transferencia" seleccionada (no aparece en Editar). Debajo, el mismo **encabezado del tipo** que en Nuevo movimiento (tampoco aparece en Editar): icono ⇄ celeste sobre celeste suave, "Registrar transferencia" / "¿Entre qué cuentas moviste dinero?".
3. **Desde** y **Hacia**: dos **desplegables** de cuenta (el mismo componente que en Nuevo movimiento), cada uno con su etiqueta encima y mostrando icono, nombre y saldo de la cuenta elegida. Entre ambos, centrado, un **botón circular "Invertir cuentas"** (48, borde fino, icono azul de dos flechas verticales opuestas).
4. Tarjeta de montos: **"Monto que sale"** (grande, moneda de origen) · **"Tipo de cambio · editable"** con `1 US$ = S/ [3.20]` y a la derecha "Referencia · manual 3.20" · **"Monto que entra · editable"** (moneda de destino). **En una transferencia nueva los dos montos empiezan vacíos** (se ve `0.00` en gris) y el cambio en el manual vigente; en Editar muestran lo guardado, incluido el cambio con que se guardó.
   - **Los dos montos se escriben con el teclado de monto de la app** (el mismo de Nuevo movimiento, sección 7.5: solo dígitos, entran por la derecha y el punto se coloca solo; ⌫ quita un dígito y, mantenida, deja `0.00`). Nunca usan el teclado del sistema.
   - El teclado **empieza cerrado**. Tocar "Monto que sale" o "Monto que entra" lo abre **escribiendo en ese monto**: ese monto muestra el cursor que parpadea, la tarjeta de montos lleva borde azul y la pantalla se desplaza para que la tarjeta quede visible sobre el teclado. Tocar el otro monto cambia a cuál se escribe sin cerrar el teclado.
   - Se cierra con "Listo", con el botón atrás del sistema (el primer atrás cierra el teclado) o al tocar el tipo de cambio o la nota. Mientras está abierto ocupa el lugar del botón de guardar.
   - El **tipo de cambio** sigue con el teclado decimal del sistema, porque admite hasta 4 decimales.
5. Ayuda: "Si el banco aplicó otro cambio, edita el tipo de cambio o el monto que entra y el otro valor se recalcula."
6. **"Saldos después de transferir"**: los dos saldos resultantes. En Editar se parte de los saldos **sin** la transferencia que se está editando, para no contarla dos veces.
7. **Fecha**: por defecto **la del día**; tocar el campo abre el **calendario** de la app (ver Componentes) con el título "Elige la fecha" para cambiarla. **No admite fechas posteriores a hoy.** En Editar muestra la fecha de la transferencia. Debajo, **Nota (opcional)**.
8. Botón "Guardar transferencia" / "Guardar cambios".

**Comportamiento:**

- Tocar Desde o Hacia abre la lista de **todas las cuentas no archivadas**; se puede elegir cualquiera como origen y cualquiera como destino.
- **Nunca queda la misma cuenta en ambos lados:** si en un lado se elige la cuenta que está en el otro, las dos se **intercambian**.
- El botón "Invertir cuentas" intercambia origen y destino.
- Cada vez que cambia una cuenta (o se invierten), el tipo de cambio vuelve al **manual vigente** para el nuevo par de monedas y "Monto que entra" se recalcula a partir de "Monto que sale"; un cambio escrito a mano antes se pierde.
- **Misma moneda:** se ocultan el tipo de cambio y "Monto que entra"; entra lo mismo que sale.
- **Monedas distintas:** el cambio sugerido es el vigente y los tres campos están enlazados:
  - cambia "sale" o el cambio → `entra = redondear(sale × cambio, 2)`;
  - cambia "entra" → `cambio = entra ÷ sale` (se guarda con 6 decimales, se muestra con 2–4).
- El cambio siempre se expresa como unidades de destino por 1 de origen. De soles a dólares: `1 S/ = US$ 0.3125`.
- "Saldos después" se recalcula mientras se escribe. Si el saldo de origen quedara negativo, se pinta en rojo y aparece "El saldo de X quedaría en negativo." — **avisa pero no bloquea**.
- **Cuentas iniciales en una transferencia nueva:** las dos primeras de la lista. Con menos de dos cuentas no se puede transferir: en lugar del formulario se muestra "Necesitas dos cuentas — Una transferencia mueve dinero entre dos cuentas tuyas."

**Validaciones:** montos > 0; cuentas distintas; cambio > 0 cuando las monedas difieren.

**Backend:** `POST /transfers`, `PATCH /transfers/{id}` y `DELETE /transfers/{id}`, con monto que sale, monto que entra, tipo de cambio (nulo si es la misma moneda), fecha y nota. El API exige montos iguales en la misma moneda y tipo de cambio en monedas distintas. Los que mueven los saldos son los dos montos; el tipo de cambio guardado es informativo. Sin tipo de cambio configurado no pueden existir dos cuentas de monedas distintas, así que toda transferencia es en la misma moneda.

### 7.7 Lista de movimientos

Es un **vistazo a lo reciente**, un mes a la vez; el historial más antiguo se consulta en Reportes.

**Muestra:** barra con **‹** y "Movimientos"; campo de búsqueda "Buscar por descripción"; fila deslizable de chips de cuenta ("Todas las cuentas" + una por cuenta); el **selector de mes** (el mismo componente de Reportes: ‹, "Octubre 2026" con "1 – 31 oct" debajo, ›); y los movimientos **de ese mes** en una sola tarjeta, con las mismas filas que Inicio. No hay filtros de categoría ni de fechas.

**Comportamiento:**

- Se abre en el **mes actual**. **‹** va al mes anterior y **›** al siguiente, de uno en uno.
- Solo se pueden ver **5 meses: el actual y los 4 anteriores**. En el mes actual **›** está desactivada; en el más antiguo, **‹**. Una flecha desactivada se ve atenuada (icono al 30 %) y no responde.
- La búsqueda filtra por descripción (o nota en transferencias) sin distinguir mayúsculas, **solo dentro del mes visible**. El chip de cuenta filtra por esa cuenta; una transferencia aparece si la cuenta es origen o destino. Búsqueda, cuenta y mes se combinan; al cambiar de mes se conservan la búsqueda y la cuenta.
- Tocar una fila abre su edición.

**Estados:** mes sin movimientos → "No hay movimientos en este mes" · sin resultados con búsqueda o cuenta activas → "No hay movimientos con estos filtros".

**Backend:** `GET /entries?from&to` con el primer y el último día del mes visible. El API devuelve movimientos y transferencias ya mezclados y ordenados por fecha y creación descendentes, por páginas. Admite además filtrar por cuenta (`accountId`) y buscar por texto (`q`) en el servidor.

### 7.8 Reportes

**Muestra:**

1. Título "Reportes" y la etiqueta inactiva "PDF · próximamente".
2. Control segmentado de tres pestañas **Mes | Año | Rango**.
3. Debajo, el selector del periodo según la pestaña:
   - **Mes:** el **selector de mes** (‹, "Octubre 2026" con "1 – 31 oct" debajo, ›). Por defecto, el mes actual.
   - **Año:** el **selector de año** (‹, "2026" con "1 ene – hoy" debajo, ›). Por defecto, el año actual. Un año pasado muestra "1 ene – 31 dic". El reporte suma todo el año (el año en curso, hasta hoy). No hay desglose mes a mes: es el mismo reporte con un periodo más largo.
   - **Rango:** campos **Desde** y **Hasta** (por defecto, del primer día del mes actual a hoy). Tocar **cualquiera de los dos** abre **un único calendario de rango** (el **Calendario** de la app, ver Componentes): se toca el día de inicio y luego el de fin, y los días intermedios quedan resaltados. Lleva el título "Elige el rango de fechas" y el rango elegido en grande (`1 sep – 30 sep 2026`; muestra "Desde" o "Hasta" mientras falte ese extremo). **"Aceptar" solo se activa con las dos fechas elegidas.** Al aceptar, se actualizan Desde y Hasta y el reporte. **El tope es hoy:** no se pueden elegir días futuros.
4. Tarjeta resumen: **Ingresos** (verde) y **Gastos** (rojo) lado a lado; debajo "Balance del periodo" con signo (verde si ≥ 0, rojo si es negativo).
5. **"Dónde gastas más"**: por categoría, punto de color, nombre, `S/ 800.00 · 37.2%` y una barra horizontal.
6. **"Tus fuentes de ingreso"**: igual, con categorías de ingreso.
7. Nota: "Las compras con tarjeta aparecen aquí solo cuando las marcas como pagadas. Las transferencias entre tus cuentas no cuentan como ingreso ni gasto."

**Comportamiento:** las flechas cambian de mes o de año. **El mes y el año actuales son los últimos:** al llegar a ellos la flecha › se atenúa; hacia atrás no hay tope. Lo elegido en cada pestaña **se conserva** al pasar a otra y al salir y volver a Reportes. Si se llega desde una tarjeta del mes de Inicio, se abre en "Mes" con el mes actual y, si la tarjeta era la de **Ingresos**, el bloque "Tus fuentes de ingreso" se muestra **antes** que "Dónde gastas más" (desde Gastos se mantiene el orden normal). Al entrar por la barra inferior el orden es siempre el normal. Barras ordenadas de mayor a menor, con ancho = porcentaje sobre el total **de esa moneda** y el color de la categoría. Si el periodo solo tiene ingresos o solo gastos, el bloque vacío no se muestra. Con varias monedas en el periodo se repiten los bloques (resumen y rankings) por moneda, cada uno bajo el nombre de su moneda (`Soles (S/)`), la principal primero.

**Estados:** sin datos → "No hay movimientos en este periodo" · cargando → dos bloques grises en lugar del resumen y el ranking · error → "No se pudo cargar — Revisa tu conexión e inténtalo de nuevo." con "Reintentar".

**Backend:** `GET /reports/summary?from&to`. Devuelve, por moneda, los totales, el balance y el ranking de egresos e ingresos por categoría, **con el porcentaje ya calculado**, para que todas las plataformas muestren lo mismo. El PDF saldrá de `GET /reports/summary.pdf` con las mismas cifras.

### 7.9 Tarjeta de crédito (v1.1)

Funciona **igual que Cuentas, pero para deudas**: un total arriba y, debajo, la deuda subdividida por **tarjeta de crédito** (una persona puede tener varias, de distintas marcas o bancos).

**Muestra:** "Deudas pendientes de pago" (pequeño) y **"Tarjetas de Crédito"** (título, en plural porque pueden ser varias); tarjeta destacada en **degradado amarillo con velo oscuro y texto blanco** (sección 5) **"Deuda total · 2 tarjetas"** con el total en la **moneda principal** en grande y, debajo, una línea `+ US$ 12.00` por cada otra moneda en la que haya deuda, y la nota "No cuenta en tus saldos ni en tus reportes hasta que la marques como pagada."; botón "+ Registrar compra con tarjeta"; **un bloque por tarjeta de crédito**; botón de borde punteado "+ Nueva tarjeta de crédito"; texto de ayuda "Cada compra pertenece a una tarjeta. La deuda total suma todas tus tarjetas, sin mezclar monedas."

**Bloque de tarjeta de crédito:** arriba, una fila táctil con icono de tarjeta — **rojo** sobre fondo rojo suave si la tarjeta tiene algún pago **urgente** (vence en 3 días o menos, o ya venció), **azul** sobre azul suave si no —, el **nombre** de la tarjeta y, debajo, en **dos líneas**: la cantidad de pagos pendientes (`2 pagos pendientes`) y la fecha límite más próxima (`Vence 5 oct`, en rojo si es urgente). Una tarjeta sin deuda muestra solo "Sin pagos pendientes", con el icono azul y, a la derecha, **su deuda** en la moneda de la tarjeta, con una línea `+ US$ 12.00` debajo por cada otra moneda que deba (si solo debe en otra moneda, esa es el monto principal). Debajo, separadas por divisores, **sus compras pendientes**, ordenadas por fecha límite.

**Compra pendiente (dentro del bloque):** descripción y monto; `categoría · compra 10 sep`; chip de vencimiento; botón "✓ Marcar como pagada".

**Orden de los bloques:** primero la tarjeta con la fecha límite más próxima; las tarjetas sin deuda van al final.

**Nueva tarjeta / Editar tarjeta:** formulario con **Nombre** (obligatorio, 1–60 caracteres, placeholder "Ej. Visa Clásica"), **Moneda** (desplegable con las monedas admitidas de 7.12; en una tarjeta nueva arranca en la **moneda principal** del usuario; ayuda "Las compras que registres con esta tarjeta usarán esta moneda.") y botón "Guardar tarjeta" / "Guardar cambios". En Editar hay además "Archivar tarjeta" (rojo suave) con el texto "La tarjeta deja de aparecer en las listas, pero conserva sus compras." y diálogo de confirmación "¿Archivar esta tarjeta?". **Una tarjeta con pagos pendientes no se puede archivar** (decidido el 7 oct 2026): hay que pagarlos o eliminarlos primero. En ese caso, en lugar del diálogo se muestra "Esta tarjeta tiene pagos pendientes. Págalos o elimínalos antes de archivarla." Así no queda deuda en una tarjeta que ya no se ve. Se abre con "+ Nueva tarjeta de crédito" o con "Editar tarjeta" dentro de un bloque desplegado.

**Replegar y desplegar:** cada bloque es plegable. Tocar su fila superior alterna entre **replegado** (solo icono, nombre, subtítulo y deuda) y **desplegado** (además, sus compras pendientes y, al final, tras un divisor, el enlace centrado "✎ Editar tarjeta"). A la derecha de la deuda hay una flecha **⌄** que gira 180° al desplegar; el contenido aparece y desaparece con animación. Por defecto, las tarjetas **con** pagos pendientes empiezan desplegadas y las que no deben nada, replegadas. El estado de cada bloque se conserva al rotar la pantalla.

**Botón "Marcar como pagada":** ocupa **todo el ancho** del bloque (respetando su padding de 16), con el icono y el texto centrados.

**"Pagos pendientes":** es la cantidad de compras aún sin pagar (cada compra se paga por separado con "Marcar como pagada"). El texto usa singular y plural: "1 pago pendiente", "2 pagos pendientes"; "1 tarjeta", "2 tarjetas".

**Chip de vencimiento:**

| Días hasta la fecha límite | Texto | Color |
|---|---|---|
| más de 3 | "Vence 15 oct · en 13 días" | gris |
| 2 o 3 | "Vence 5 oct · en 3 días" | rojo |
| 1 | "Vence 3 oct · mañana" | rojo |
| 0 | "Vence hoy" | rojo |
| −1 | "Vencida hace 1 día" | rojo |
| menos de −1 | "Vencida hace 2 días" | rojo |

**Comportamiento:** totales por moneda, sin mezclar, tanto en la deuda total como en cada tarjeta. Al pagar una compra, baja la deuda de su tarjeta y la deuda total.

**Estados:** sin tarjetas → "Aún no tienes tarjetas de crédito" · tarjeta sin deuda → "Sin pagos pendientes" y sin lista de compras.

**Editar y eliminar una compra pendiente** (aprobado e implementado el 7 oct 2026, reutilizando el formulario de movimiento; su diseño es una propuesta pendiente de confirmación). Tocar la descripción de una compra pendiente abre su edición, con el título "Editar compra" y sin las pestañas Egreso | Ingreso | Transferencia: el mismo formulario de "Compra con tarjeta" con los datos cargados (monto, tarjeta, categoría, descripción, fecha límite) y una papelera con confirmación — "¿Eliminar esta compra?" — "Dejará de contar en la deuda de la tarjeta." Reglas:

- Solo se edita o elimina una compra **pendiente**. Una ya pagada muestra "Esta compra ya está pagada."
- Al editar, la fecha límite **puede ser anterior a hoy** (una compra vencida debe poder corregirse); solo no puede ser anterior a la fecha de compra.
- Si se cambia la tarjeta, la compra toma la moneda de la tarjeta nueva.
- **No cambia ningún saldo ni reporte**, porque una compra pendiente no es un movimiento. Cambian la deuda de su tarjeta, la deuda total, el conteo de pagos pendientes y la fecha de vencimiento más próxima. Ninguna de esas cifras está guardada: se suman al leerlas.

**Sin tipo de cambio configurado:** en Nueva/Editar tarjeta, el desplegable de Moneda queda fijo en la moneda principal.

**Backend:** `GET /credit-cards` (tarjetas con su deuda por moneda), `POST` y `PATCH /credit-cards`, `POST /credit-cards/{id}/archive`; `GET /credit-purchases?status=pending`, `POST`, `PATCH` y `DELETE /credit-purchases/{id}`. El esquema ya tiene la tabla de tarjetas y cada compra apunta a la suya.

### 7.10 Marcar como pagada (v1.1)

**Muestra:** barra con **‹** y "Marcar como pagada"; tarjeta de la compra (descripción, monto, `categoría · compra 10 sep · vence 5 oct`); **"Pagar desde"** con las cuentas no archivadas; **"Monto realmente descontado"** con el símbolo de la cuenta elegida y la ayuda "Si la compra fue en dólares, escribe aquí los soles que te cobró el banco."; **"Fecha de pago · hoy, editable"**; tarjeta **"Se creará este egreso"** con `categoría · cuenta`, el monto en rojo y `Saldo de X: S/ 1,245.80 → S/ 1,005.80`; botón "Confirmar pago".

**Comportamiento:** la cuenta inicial es la primera **en la moneda de la compra** (ahí no hay conversión); si no hay ninguna, la primera de la lista. Si la moneda de la cuenta coincide con la de la compra se **sugiere** el monto de la compra; si no, el campo queda vacío y es obligatorio. Al cambiar de cuenta se recalcula la sugerencia. La **fecha de pago** empieza en hoy y se cambia con el calendario de la app; **no admite fechas futuras** (el pago se convierte en un egreso). La vista previa se actualiza mientras se escribe. Al confirmar se vuelve a Crédito, la compra desaparece de pendientes y el egreso aparece en movimientos y reportes con la fecha de pago y la categoría original.

**Backend:** `POST /credit-purchases/{id}/pay` con cuenta, monto real y fecha. Es atómica: crea el egreso y marca la compra como pagada, o no hace nada. No se puede pagar dos veces: el segundo intento recibe "Esta compra ya está pagada."

### 7.11 Categorías y Nueva/Editar categoría

**Categorías:** barra con **‹**; control segmentado **Egresos | Ingresos**; lista en una tarjeta (punto de color, nombre, icono de lápiz); botón al pie "+ Nueva categoría", que abre el formulario con el tipo de la pestaña activa.

**Formulario:** Nombre (1–60 caracteres, placeholder "Ej. Mascotas"); Tipo Egreso/Ingreso (**solo al crear**; después no cambia); Color (7 muestras circulares, la elegida con anillo); botón "Guardar categoría" / "Guardar cambios". En Editar: "Archivar categoría" con confirmación — "Deja de aparecer al registrar movimientos, pero los anteriores se conservan."

**Validaciones:** el nombre no se repite **dentro del mismo tipo**. Criterio de comparación:

| Aspecto | Criterio |
|---|---|
| Mayúsculas | Se ignoran: "alimentación" choca con "Alimentación" |
| Espacios al inicio, al final y repetidos | Se ignoran |
| Tildes y diéresis | **Se ignoran**: "Alimentación", "alimentacion" y "ALIMENTACIÓN" son la misma categoría |
| Letra `ñ` | Se conserva: "Año" y "Ano" son distintas |
| Lo que se muestra | Siempre el nombre **tal como lo escribió el usuario**. La versión sin tildes y en minúsculas la calcula y la guarda el backend solo para comparar; la app nunca la ve |
| Tipo | Se puede tener "Otros" en egresos y "Otros" en ingresos |
| Archivadas | No cuentan: se puede volver a usar el nombre de una categoría archivada |
| Renombrar | Cambiar solo mayúsculas o tildes de una categoría ("ocio" → "Ocio") está permitido: no choca consigo misma |

Si el nombre ya existe, el mensaje va **bajo el campo Nombre**, en rojo y con el campo marcado en rojo: "Ya tienes una categoría de este tipo con ese nombre." Es el único error de la app que se muestra bajo su campo en lugar de en el aviso de validación. Desaparece cuando el usuario cambia el texto. La app puede avisar antes de enviar comparando con la lista que ya tiene; quien decide es el API.

**Backend:** `GET` y `POST /categories`, `PATCH /categories/{id}`, `POST /categories/{id}/archive`. Un nombre repetido responde `409` con el código `CATEGORY_NAME_TAKEN`.

### 7.12 Ajustes

**Muestra, por secciones:**

- **Monedas:** el usuario trabaja con **dos monedas**, ambas elegibles con un desplegable (la app se usa en distintos países):
  - **"Moneda predeterminada"** (la *principal*; por defecto soles), con la ayuda "Es la moneda con la que se crean tus cuentas nuevas. Puedes cambiarla cuando quieras; tus cuentas actuales conservan la suya."
  - **"Moneda secundaria"** (por defecto **dólares**), con la ayuda "Sirve para ver el equivalente de tus ahorros en otra moneda. El tipo de cambio de abajo es entre estas dos monedas."
  - **"Ver ahorro total en"** con un selector de dos opciones: `[principal] | [secundaria]` (p. ej. `S/ | US$`; el mismo valor que en Inicio).
  - **Las dos monedas nunca coinciden:** si en un desplegable se elige la moneda que está en el otro, se **intercambian** (y el tipo de cambio pasa a su inverso). Si se elige cualquier otra moneda, **el campo del tipo de cambio queda vacío**, porque no hay valor conocido para el nuevo par: la app no inventa uno (antes ponía `1.00`). **El par nuevo no se guarda hasta que el usuario escribe un valor mayor que cero**; si sale de la pantalla sin escribirlo, se conserva el par anterior. Si la moneda en la que se veía el ahorro deja de estar en el par, la vista pasa a la principal.
  - **Monedas admitidas** (código ISO · símbolo · nombre): `PEN` S/ Soles · `USD` US$ Dólares · `EUR` € Euros · `MXN` MX$ Pesos mexicanos · `COP` COL$ Pesos colombianos · `CLP` CLP$ Pesos chilenos · `ARS` AR$ Pesos argentinos · `BOB` Bs Bolivianos · `BRL` R$ Reales. Cada opción se muestra como `Soles (S/)` con el código a la derecha.
  - **Dónde se usan las dos monedas:** la principal es la moneda inicial de las **cuentas nuevas** y de las **tarjetas de crédito nuevas**, la que encabeza la "Deuda total" de Crédito y aquella en la que se muestra el `≈` de las cuentas en otra moneda. El par principal/secundaria alimenta el selector y el equivalente ("o US$ …") del ahorro total en Inicio y Cuentas, y el tipo de cambio sugerido en Transferir.
  - **Límite actual: solo hay tipo de cambio entre la principal y la secundaria.** Una cuenta en una tercera moneda **no entra en el ahorro total** ni muestra `≈`, y una transferencia que la involucre arranca con el tipo de cambio **vacío**, para escribirlo a mano (no se guarda sin un valor mayor que cero). Los datos de ejemplo siguen en soles y dólares, y las tarjetas del mes de Inicio y los Reportes siguen mostrando soles. Para admitir más de dos monedas a la vez hará falta un tipo de cambio por cada par (la tabla `exchange_rates` ya lo permite).
- **Tipo de cambio:** control **Manual | Automático · pronto** (Automático aún no se puede elegir); `1 [secundaria] = [principal] [valor]` editable, p. ej. `1 US$ = S/ [3.20]`; "Equivale a 1 S/ = US$ 0.3125"; ayuda "Al cambiar de monedas, escribe el tipo de cambio entre las dos para guardar."; ayuda: "Se usa para el total de ahorros y como valor sugerido en las transferencias. Puedes cambiarlo cuando quieras."
- **Categorías:** fila "Gestionar categorías".
- **Seguridad:** "Inicias sesión con tu correo y contraseña. Cada persona solo ve sus propios datos.", el correo de la sesión, el botón rojo suave "Cerrar sesión" y, debajo, **"Eliminar cuenta"** (ver más abajo).
- **Próximamente:** Exportar a PDF · Modo sin internet · Cambio automático diario · Acceso con Google.

**Comportamiento:** el inverso se recalcula al escribir, con 4 decimales; si el valor no es válido muestra "—". Cerrar sesión limpia el estado en memoria y vuelve a Iniciar sesión.

**Validaciones:** tipo de cambio > 0.

**Sin tipo de cambio configurado.** Un usuario nuevo **no tiene tipo de cambio**: el backend no conoce el valor y no inventa uno. Mientras no exista:

- En esta pantalla, el campo del tipo de cambio aparece **vacío y resaltado**, con el aviso **"Configura tu tipo de cambio"**; el inverso muestra "—".
- En Inicio y Cuentas, la tarjeta de ahorro muestra el total en la moneda principal, desactiva el selector de moneda y reemplaza el equivalente por el enlace "Configura tu tipo de cambio ›", que trae aquí.
- **No se puede operar en otra moneda.** En Nueva cuenta y en Nueva/Editar tarjeta, la moneda queda fija en la principal, con la ayuda "Configura tu tipo de cambio en Ajustes para usar otras monedas." Tampoco se puede elegir la moneda secundaria en "Ver ahorro total en".
- Todo lo que es en la moneda principal funciona con normalidad: cuentas, movimientos, transferencias, compras con tarjeta y reportes.

El estado termina al escribir un tipo de cambio mayor que cero y guardarlo. **No se puede volver a él**: el tipo de cambio se puede modificar, pero no quitar.

Regla que lo resume: *sin tipo de cambio, todos los datos del usuario están en su moneda principal.* Así ninguna pantalla tiene que mostrar un total a medias.

**Cambio de par (decidido el 7 oct 2026).** Al elegir un par de monedas nuevo, el campo del tipo de cambio queda vacío y resaltado, igual que en el estado sin configurar, y el botón o la acción de guardar no se completa hasta que haya un valor mayor que cero. Con el campo vacío o en cero se muestra "Escribe un tipo de cambio mayor que cero." El backend valida lo mismo y rechaza un par sin tipo de cambio. Intercambiar las dos monedas del par no vacía el campo: el valor pasa a su inverso, que sí se conoce.

**Eliminar cuenta** (requisito para publicar y de Google Play; aún sin diseño). Acción destructiva en rojo al final de la sección Seguridad. Abre un diálogo — "¿Eliminar tu cuenta?" — "Se borrarán todos tus datos: cuentas, movimientos, categorías y tarjetas. No se puede deshacer." — que pide la **contraseña** para confirmar. Al confirmar se borran de verdad todos los datos de la persona en el backend (no es un archivado ni un borrado lógico) y se vuelve a Iniciar sesión. Con el mismo correo se puede crear después una cuenta nueva, que empieza vacía. La misma eliminación debe poder hacerse desde una página web, sin la app.

**Backend:** `GET /settings` (las dos monedas, la de visualización, la zona horaria, `fxConfigured` y el tipo de cambio, que puede ser nulo); `PUT /settings/fx` para el par de monedas y su tipo de cambio — **cada valor se guarda como una fila nueva**, sin modificar la anterior —; `PATCH /settings` para la moneda de visualización y la zona horaria; `DELETE /me` para eliminar la cuenta. El perfil ya guarda la moneda secundaria y cada tarjeta de crédito, su moneda.

## 8. Flujos completos

1. **Primer uso.** Registro → Inicio vacío ("Registra tu primer movimiento") con la cuenta "Efectivo" ya creada → Cuentas → Nueva cuenta → **+** → Nuevo movimiento (ingreso) → Inicio muestra el saldo, el ingreso del mes y el movimiento.
2. **Registrar un egreso.** **+** → Egreso → teclear el monto en el teclado de la app (solo dígitos) → Listo → cuenta en el desplegable → categoría en el desplegable → descripción → Guardar egreso → vuelve a la pantalla anterior; baja el saldo de la cuenta, sube "Gastos" del mes y aparece en Últimos movimientos y en Reportes.
3. **Ver el ahorro en dólares.** Inicio → tocar `US$` → el total pasa a dólares y la línea secundaria muestra los soles. Se refleja en Cuentas y en Ajustes.
4. **Excluir una cuenta del ahorro.** Cuentas → apagar el interruptor → el total baja al instante y el conteo pasa a "3 de 5".
5. **Transferir entre monedas.** Cuentas → Transferir → Desde Cuenta Dólares, Hacia Débito principal → sale US$ 20.00 → se propone 3.20 y entra S/ 64.00 → el banco dio S/ 63.40: se escribe ese monto y el cambio pasa a 3.17 → Guardar. Bajan los dólares, suben los soles; no cambia ningún reporte.
6. **Compra con tarjeta y su pago (v1.1).** Crédito → Registrar compra con tarjeta → monto, categoría, descripción, fecha límite → Guardar compra pendiente. No cambia saldos ni reportes. Más tarde: Crédito → Marcar como pagada → cuenta, monto real, fecha → Confirmar pago. La compra sale de pendientes y nace un egreso.
7. **Revisar un periodo.** Reportes → Mes → flechas hasta el mes → resumen, ranking de gastos y fuentes de ingreso. O Año → flechas hasta el año. O Rango → Desde / Hasta.
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

El tipo de cambio vigente es el más reciente del usuario para el par de monedas. **Si no hay ninguno**, `convertir` entre monedas distintas no da resultado, el ahorro total se muestra solo en la moneda principal y la app pide configurarlo ("Configura tu tipo de cambio", sección 7.12).

"Hoy", en `días_para_vencer` y en toda validación de fechas, es el día según la zona horaria del perfil.

Editar o eliminar una compra con tarjeta pendiente no entra en ninguna de estas fórmulas salvo en la deuda, que es la suma de las compras pendientes por moneda.

Casos de prueba que ambas plataformas deben cumplir: `docs/06`, tabla "Pruebas unitarias obligatorias".

## 10. Backend compartido

Android, iOS y la web usan **el mismo API** (`atm-orbita-api`: NestJS + PostgreSQL + Prisma), sin Supabase ni ningún otro backend gestionado. El contrato completo, el esquema, la seguridad y las pruebas están en `atm-orbita-api/docs/plans/`. Cómo conectar la app: `docs/04`.

Todas las rutas van bajo `/api/v1` y, salvo las de autenticación, exigen el token de acceso.

| Necesidad | Recurso |
|---|---|
| Sesión | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Recuperar contraseña | `POST /auth/forgot-password`, `/auth/reset-password` |
| Eliminar cuenta | `DELETE /me` |
| Inicio | `GET /home` |
| Saldos y ahorro total | `GET /accounts` |
| Movimientos | `POST`, `PATCH`, `DELETE /transactions` |
| Transferencias | `POST`, `PATCH`, `DELETE /transfers` |
| Lista mezclada | `GET /entries` |
| Reportes | `GET /reports/summary` (y `.pdf`) |
| Tipo de cambio y preferencias | `GET /settings`, `PUT /settings/fx`, `PATCH /settings` |
| Categorías | `GET`, `POST`, `PATCH /categories`, `POST …/archive` |
| Crédito (v1.1) | `/credit-cards`, `/credit-purchases`, `POST /credit-purchases/{id}/pay` |
| Monedas admitidas | `GET /currencies` |

Reglas del contrato que toda plataforma debe respetar:

- **Los montos viajan como texto** (`"1245.80"`), nunca como número JSON: hay que leerlos como decimal, nunca como `Double`.
- Las fechas van como `YYYY-MM-DD`, sin hora ni zona.
- Los identificadores son UUID generados en el cliente; repetir una creación con el mismo identificador no duplica.
- Los errores traen un `code` estable; el texto lo pone cada app (sección 4.1).
- Un recurso de otra persona responde igual que uno que no existe.
- Hay que ignorar los campos desconocidos de las respuestas, para que una versión antigua de la app siga funcionando cuando el API agregue datos.

## 11. Datos de ejemplo

Son la semilla del **modo demo**, de las vistas previas y de las pruebas. "Hoy" = **2 oct 2026** (el modo demo fija esa fecha; con datos reales "hoy" es la fecha del dispositivo); cambio manual **1 US$ = S/ 3.20**. Los saldos **no están escritos**: salen del saldo inicial de cada cuenta más sus movimientos y transferencias.

| Cuenta | Tipo | Moneda | Saldo | En ahorros |
|---|---|---|---|---|
| Cuenta Dólares | Ahorro | USD | 250.00 | sí |
| Efectivo | Efectivo | PEN | 320.50 | sí |
| Débito principal | Débito | PEN | 1,245.80 | sí |
| Ahorros | Ahorro | PEN | 4,800.00 | sí |
| Billetera digital | Otra | PEN | 85.00 | no |

- Ahorro total: **S/ 7,166.30** = **US$ 2,239.47**.
- Octubre: ingresos S/ 3,500.00 (Sueldo), gastos S/ 30.50 (Alimentación 18.50 · Transporte 12.00), más la transferencia Cuenta Dólares → Débito principal de US$ 20.00 = S/ 64.00 (1 oct).
- Septiembre: ingresos S/ 4,200.00, gastos S/ 2,148.60, balance +S/ 2,051.40; porcentajes de gasto 37.2 / 28.5 / 10.0 / 8.8 / 8.5 / 6.9.
- Crédito pendiente: S/ 525.90 + US$ 12.00 en 4 compras de 2 tarjetas; la más próxima vence el 5 oct.
  - **Visa Clásica**: Pasajes S/ 240.00 (vence 5 oct) y Cena en restaurante S/ 96.00 (vence 15 oct) → S/ 336.00.
  - **Mastercard Oro**: Audífonos S/ 189.90 y Suscripción de software US$ 12.00 (ambas vencen 15 oct) → S/ 189.90 + US$ 12.00.
- **Movimientos de septiembre** (suman exactamente el reporte de arriba): ingresos — Sueldo 3,500.00 (1 sep, Débito), Venta de libros 100.00 (12 sep, Efectivo, *Otros ingresos*), Diseño de logo 600.00 (27 sep, Débito, *Freelance*); gastos — Mercado 210.00 (6), Pasajes de bus 95.20 (8, Efectivo), Consulta médica 110.00 (9), Concierto 145.00 (12), Supermercado 180.00 (14), Regalo de cumpleaños 123.00 (15), Gasolina 120.00 (17), Restaurante 70.00 (19, Efectivo), Útiles 60.00 (20, Efectivo), Farmacia 38.00 (24, Efectivo), Cine 45.00 (26, Efectivo), Supermercado 152.40 (28), Alquiler 800.00 (30); los que no indican cuenta salen de Débito principal. Además, la transferencia "Ahorro del mes" Débito → Ahorros por S/ 500.00 (22 sep). El resto de meses no tiene datos.
- Año 2026 completo (reporte por año): ingresos S/ 7,700.00, gastos S/ 2,179.10.
- Detalle completo: `docs/03`, sección 7.

## 12. Notas para la versión iOS

| Android | Equivalente sugerido en iOS |
|---|---|
| Kotlin + Jetpack Compose | Swift + SwiftUI |
| `BigDecimal`, HALF_UP | `Decimal` con redondeo `.plain` |
| Cliente HTTP Ktor contra el API, con renovación de sesión de una en una | `URLSession` (o equivalente) con el mismo esquema de tokens |
| Token de renovación cifrado con el Android Keystore; respaldos del dispositivo desactivados | Token de renovación en el llavero del sistema, excluido de los respaldos |
| `FxPair.rate: BigDecimal?` | `rate: Decimal?` |
| ViewModel + `StateFlow<UiState>` | `@Observable` / `ObservableObject` por pantalla |
| Repositorios del dominio (interfaces) con dos implementaciones: memoria (demo) y API | Protocolos con las mismas dos implementaciones; así la app se puede probar entera sin backend |
| Navigation Compose, barra inferior | `TabView` + `NavigationStack`; formularios como `fullScreenCover` |
| `DropdownMenu` | `Menu` o `Picker` con estilo de menú |
| Teclado de monto (composable propio) | Vista propia en SwiftUI anclada al pie (no `keyboardType`); vibración con `UIImpactFeedbackGenerator` |
| Calendario propio (composable) | Vista propia en SwiftUI, no `DatePicker`: debe verse y comportarse igual (ver Componentes) |
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
| 6 oct 2026 | Color: el verde de ingresos pasa de `#0B7A5A` a **verde intenso `#2EAD5B`**, y su fondo suave de `#E3F4EE` a `#E6F6EC`. El color de la categoría Sueldo (`#0B7A5A`) no cambia. |
| 6 oct 2026 | Inicio: las tarjetas de ingresos y gastos del mes pasan de estar lado a lado a **una sobre otra**, con texto y monto más grandes y un icono de tendencia (arriba en ingresos, abajo en gastos). |
| 6 oct 2026 | Inicio: en las tarjetas del mes, el mes va con **mayúscula inicial** y en gris más claro (`#94A0B2`) que "Ingresos"/"Gastos"; las tarjetas son **táctiles** y abren Reportes en el mes actual con ese detalle primero. La maqueta de Reportes suma datos de octubre. |
| 6 oct 2026 | Crédito: la pantalla pasa de una lista única de compras a **"Deuda total" subdividida por tarjeta de crédito** (como Cuentas con los ahorros): bloque por tarjeta con su deuda y sus compras, pantallas Nueva/Editar tarjeta y desplegable "Tarjeta" al registrar una compra. En Inicio, el aviso dice "pagos pendientes" en vez de "compras pendientes". **Falta reflejar las tarjetas en el esquema de `docs/03`.** |
| 6 oct 2026 | Inicio: el encabezado pasa a "Bienvenido" con el subtítulo "Revisa cómo van tus ahorros, ingresos, gastos y movimientos."; en "Ahorro total" el símbolo de moneda va al tamaño de la cifra y el equivalente deja la píldora y el `≈` por un texto de apoyo "o US$ …"; el icono de "Tarjeta de crédito" pasa a naranja. Filas de movimiento: el icono de transferencia o cambio pasa de gris a celeste (en Inicio y en la Lista de movimientos). |
| 6 oct 2026 | Inicio: el título pasa a "👋 Bienvenido" (con emoji de saludo) y el subtítulo a "Revisa cómo van tus finanzas". |
| 6 oct 2026 | Cuentas: en "Total en ahorros" el equivalente deja la píldora y el `≈` por el texto de apoyo "o US$ … · cambio 3.20", como en Inicio. |
| 6 oct 2026 | Reportes: el selector de mes pierde el icono de calendario; el mes-año y el rango de días van centrados entre las flechas. |
| 6 oct 2026 | Crédito: el título pasa a "Tarjetas de Crédito" (también en el aviso de Inicio); el degradado de crédito pasa de amarillos con texto oscuro a **naranjas con brillo amarillo y texto blanco** (`#C2410C` → `#EA580C` → `#D97706`), también en la tarjeta flotante del inicio de sesión; los bloques de tarjeta son **replegables**; "Editar tarjeta" pasa a un enlace dentro del bloque; "Marcar como pagada" ocupa todo el ancho. |
| 6 oct 2026 | Crédito: se **revierte** el degradado naranja; vuelve el amarillo (`#FDE047` → `#FBBF24` → `#F59E0B`). En "Deuda total" el texto blanco se resuelve con un **velo oscuro vertical** (`#1C1206`, 30 % arriba → 78 % abajo) sobre el amarillo. La tarjeta flotante del inicio de sesión vuelve a amarillo con texto oscuro, sin velo. |
| 6 oct 2026 | Tarjetas destacadas: el velo pasa a **negro del 0 % arriba al 80 % abajo** y se aplica también a las tarjetas azules de ahorro de Inicio y Cuentas, para que las tres se vean uniformes. |
| 6 oct 2026 | Transferir: **Desde** y **Hacia** pasan de tarjetas fijas a **desplegables** con todas las cuentas, con botón "Invertir cuentas" entre ambos; elegir la cuenta del otro lado las intercambia; al cambiar de cuentas el tipo de cambio vuelve al manual. La **fecha** (hoy por defecto) ya se puede editar con el selector nativo. |
| 6 oct 2026 | Filas de movimiento: se invierten las flechas; **ingreso ↗ (arriba)** y **egreso ↙ (abajo)**. Ajustes: la **moneda predeterminada** pasa de fija (soles) a elegible entre 9 monedas; Nueva cuenta usa un desplegable de monedas que arranca en la predeterminada. El resto de la app sigue en soles y dólares (pendiente de definir). |
| 6 oct 2026 | Monedas: el dólar deja de ser fijo y pasa a ser la **moneda secundaria**, elegible en Ajustes (por defecto dólares). El selector del ahorro total, el equivalente "o …", el tipo de cambio de Ajustes y el sugerido en Transferir usan el par principal/secundaria. Las **tarjetas de crédito tienen moneda** (por defecto la principal) y sus compras nuevas la heredan; la "Deuda total" muestra primero la principal y una línea por cada otra moneda. Límite: solo hay tipo de cambio entre esas dos monedas. |
| 6 oct 2026 | Reportes: en "Rango de fechas", Desde y Hasta abren un **calendario de rango** para elegir inicio y fin a la vez. Crédito: el icono de cada tarjeta es **rojo si tiene un pago urgente** y azul si no; bajo el nombre van en dos líneas los pagos pendientes y "Vence …". |
| 6 oct 2026 | Lista de movimientos: se quitan los botones "Categoría: todas" y "Fechas: todas" y la lista completa agrupada por mes. Ahora muestra **un mes a la vez** con el selector de mes de Reportes, limitado al **mes actual y los 4 anteriores**; la búsqueda actúa sobre el mes visible y ya no hay paginación. Nuevo estado "No hay movimientos en este mes". |
| 7 oct 2026 | Nuevo movimiento y Transferir: bajo las pestañas se agrega un **encabezado del tipo** (icono con fondo suave + título + subtítulo) para distinguir egreso, ingreso y transferencia: "Registrar egreso / ¿En qué gastaste?", "Registrar ingreso / ¿De dónde recibiste dinero?", "Registrar transferencia / ¿Entre qué cuentas moviste dinero?". No aparece al editar. |
| 7 oct 2026 | Regla nueva: **no hay datos del futuro**. Reportes: "Por mes" no pasa del mes actual y "Rango de fechas" no pasa de hoy; hacia atrás sin tope. Transferir: la fecha no puede ser posterior a hoy. Las fechas de crédito siguen admitiendo futuro. |
| 7 oct 2026 | **Calendario propio** en lugar del selector nativo del sistema (Reportes y Transferir): tarjeta blanca con la selección en grande, días en círculo azul, banda azul suave para el rango, anillo en hoy, botones píldora, deslizar para cambiar de mes y vista de meses/años al tocar el mes-año. |
| 7 oct 2026 | Reportes: tercera pestaña **Año** (selector de año, "1 ene – hoy" en el año en curso, sin desglose mes a mes); las pestañas pasan a llamarse **Mes \| Año \| Rango**. El mes por defecto es el actual; lo elegido en cada pestaña se conserva; se muestran los estados de carga y error; con varias monedas, un bloque por moneda. |
| 7 oct 2026 | **La app deja de ser maqueta.** Todo se guarda, edita y elimina de verdad sobre datos en memoria (modo demo), con saldos, totales y reportes **calculados** a partir de los movimientos. Arquitectura lista para conectar Supabase módulo por módulo (repositorios con dos implementaciones); la sesión ya es real si están las claves. Nueva sección 4.1 con los mensajes de validación y error. |
| 7 oct 2026 | Nuevo movimiento: la cuenta inicial es la del último movimiento registrado; la fecha (al editar) y la fecha límite de pago ya se cambian con el calendario. Transferir: los montos empiezan vacíos; al editar, "Saldos después" no cuenta dos veces la transferencia. Marcar como pagada: cuenta inicial en la moneda de la compra y fecha de pago editable (no futura). |
| 7 oct 2026 | Datos de ejemplo: septiembre gana movimientos para que la lista sume exactamente el reporte (ingresos 4,200.00, gastos 2,148.60); los saldos pasan a calcularse desde el saldo inicial. |
| 7 oct 2026 | Transferir: "Monto que sale" y "Monto que entra" pasan a escribirse con el **teclado de monto de la app** (el punto decimal se coloca solo), como en Nuevo movimiento; el teclado empieza cerrado y escribe en el monto que se toque. El tipo de cambio sigue con el teclado del sistema. |
| 7 oct 2026 | Inicio: el título vuelve a "Bienvenido", **sin emoji**. |
| 7 oct 2026 | **Cambio de backend.** Ya no se usa Supabase: el backend es un API propio (`atm-orbita-api`: NestJS + PostgreSQL + Prisma) con autenticación propia. Se reescriben las secciones 1, 2 y 10, la regla 7 y el apartado "Backend" de cada pantalla, que pasa de nombrar tablas y funciones SQL a nombrar endpoints. `docs/04` pasa a ser la guía del API. |
| 7 oct 2026 | Regla nueva 9: **"hoy" según la zona horaria del perfil**; la fecha del movimiento se guarda como fecha ya calculada. Queda decidido que el movimiento guarda solo la fecha. |
| 7 oct 2026 | Regla nueva 10: **el tipo de cambio puede no existir.** Estado "Configura tu tipo de cambio" en Inicio, Cuentas y Ajustes; sin él no se opera en otra moneda (secciones 7.2, 7.3, 7.4, 7.9 y 7.12). |
| 7 oct 2026 | Categorías: nombre único **por usuario y tipo**, sin distinguir mayúsculas **ni tildes** ("Alimentación" = "alimentacion" = "ALIMENTACIÓN"); se muestra siempre el nombre original; el mensaje de nombre repetido va **bajo el campo** (7.11). |
| 7 oct 2026 | Ajustes: al cambiar de par de monedas, el tipo de cambio **queda vacío** en lugar de volver a `1.00`, y el par solo se guarda con un valor mayor que cero (7.12). Lo mismo en una transferencia con una moneda fuera del par. |
| 7 oct 2026 | Registro: la **moneda principal se elige al registrarse** (propuesta según la región del teléfono) y la cuenta "Efectivo" nace en ella (7.1). |
| 7 oct 2026 | Crédito: **eliminar el egreso de un pago devuelve la compra a pendiente**; una compra solo se elimina desde Crédito y mientras está pendiente (7.5). **No se puede archivar una tarjeta con pagos pendientes** (7.9). Se confirman: la `ñ` se conserva y las categorías archivadas dejan libre su nombre (7.11). |
| 7 oct 2026 | **Reglas del 7 oct aplicadas en el código (modo demo):** `FxPair.rate` opcional, con "Configura tu tipo de cambio ›" en Inicio y Cuentas, moneda fija en Nueva cuenta y Nueva/Editar tarjeta, y campo vacío en Ajustes y Transferir al cambiar de par; nombre de categoría único sin mayúsculas ni tildes, con el mensaje bajo el campo; editar y eliminar una compra pendiente tocándola en Crédito; eliminar el egreso de un pago devuelve la compra a pendiente, con su aviso; una tarjeta con pagos pendientes no se archiva. Pendiente de implementar, porque necesita el API: recuperar contraseña, eliminar cuenta y moneda al registrarse. |
| 7 oct 2026 | Editar movimiento: el **egreso de un pago con tarjeta solo deja cambiar cuenta, monto y fecha**; su categoría y su descripción quedan fijas y no puede pasar a ingreso (7.5). |
| 7 oct 2026 | **Supabase retirado del repositorio:** se eliminaron la migración SQL, las dependencias, las claves de configuración y el repositorio de sesión. La app queda entera en modo demo, también la sesión, hasta conectar el API. |
| 7 oct 2026 | Crédito: una compra **pendiente se puede editar y eliminar**, sin efecto en saldos ni reportes (7.9). |
| 7 oct 2026 | **Requisitos para publicar:** recuperar contraseña con enlace de un solo uso de 30 minutos (7.1) y eliminar cuenta con borrado real desde Ajustes (7.12). Mensajes nuevos en 4.1. |
| 7 oct 2026 | Seguridad Android: `allowBackup="false"` y reglas de extracción que excluyen todo en Android 12+, para que la sesión no salga del dispositivo. |
