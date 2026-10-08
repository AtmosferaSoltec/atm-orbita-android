# 06 · Plan de construcción y pruebas

Reglas: una fase a la vez · al cerrar cada fase, correr las pruebas, resumir y **pedir revisión al usuario** · no adelantar funciones de fases posteriores. Estado actual en `CLAUDE.md`.

## Fases

> **Cambio del 7 oct 2026.** El backend es el API propio (`atm-orbita-api`), no Supabase. Cada fase de esta app necesita que el API tenga lista la fase equivalente; el plan del API está en `atm-orbita-api/docs/plans/README.md`.

| Fase de esta app | Necesita del API |
|---|---|
| 0 | Fases 0 y 1 (fundaciones y autenticación) |
| 1 | Fase 1 |
| 2 | Fase 2 |
| 3 | Fases 3 y 4 |
| 4 | Fase 3 |
| 5 | Fases 2 y 4 |
| 6 | Fase 5 |

### Fase 0 — Backend disponible
- El API corre en local con su base de datos (ver `docs/04`).
- **Listo cuando**: `health/ready` responde; registrar un usuario por el API le crea perfil, 9 categorías y la cuenta "Efectivo"; las pruebas de aislamiento entre usuarios del API pasan.

### Fase 1 — Proyecto Android, tema y login
- Proyecto Compose + Hilt + version catalog; tema (`docs/05`, sección 1); componentes base. *(Hecho.)*
- Cliente HTTP con `API_BASE_URL` por `BuildConfig`; almacén cifrado del token de renovación; registro, inicio y cierre de sesión; sesión persistente con renovación.
- El código y las dependencias de Supabase ya se retiraron (7 oct 2026); la sesión está en modo demo hasta escribir esta implementación.
- **Listo cuando**: un usuario nuevo se registra, entra, cierra la app, la reabre y sigue con sesión; cerrar sesión vuelve a login; errores en español; varias peticiones con el token vencido producen una sola renovación.

### Fase 2 — Cuentas y categorías
- Listar, crear, editar (nombre, tipo, interruptor de ahorros), archivar cuentas; moneda PEN/USD; saldo inicial.
- Gestionar categorías (crear, renombrar, color, archivar). Nombre repetido dentro del mismo tipo: mensaje **bajo el campo** ("Ya tienes una categoría de este tipo con ese nombre.").
- `FxPair.rate` opcional y estado "Configura tu tipo de cambio": sin tipo de cambio, la moneda de una cuenta nueva queda fija en la principal.
- **Listo cuando**: se ven los saldos que entrega `GET /accounts`; el interruptor persiste; la moneda no se edita; un usuario nuevo ve "Configura tu tipo de cambio" y, tras escribirlo, puede crear una cuenta en otra moneda.

### Fase 3 — Movimientos e Inicio
- Nuevo movimiento (ingreso/egreso), editar, eliminar (lógico), lista de movimientos paginada con filtros.
- Inicio: ahorro total (en la moneda predeterminada), tarjetas del mes, últimos movimientos.
- **Listo cuando**: crear un egreso de 45.52 cambia el saldo de la cuenta en 45.52 exactos; los totales del mes excluyen transferencias.

### Fase 4 — Transferencias
- Pantalla de transferencia con las tres reglas enlazadas (`out`, `rate`, `in`), saldos "después", guardado.
- **Listo cuando**: US$ 20 a 3.20 → S/ 64.00; editar el monto recibido recalcula el cambio; los saldos de ambas cuentas cuadran; misma moneda oculta el cambio.

### Fase 5 — Reportes, ahorro total con conversión y ajustes
- Ajustes: cambio manual (`PUT /settings/fx`; el API guarda cada valor como una fila nueva), inverso, cerrar sesión.
- Inicio/Cuentas: selector S/ ↔ US$ y totales convertidos.
- Reportes: por mes y por rango, totales por moneda, ranking de gasto, fuentes de ingreso.
- **Listo cuando**: con los datos de ejemplo (`docs/03`, sección 7) el ahorro total es S/ 7,166.30 y US$ 2,239.47, y septiembre da ingresos 4,200.00, gastos 2,148.60 y balance 2,051.40.

### Fase 6 — Tarjeta de crédito (v1.1)
- Registrar compra pendiente, lista por vencimiento, pagar (`POST /credit-purchases/{id}/pay`), aviso en Inicio.
- **Editar y eliminar una compra pendiente** (pantalla nueva, aún sin diseño).
- **Listo cuando**: una compra pendiente **no** cambia saldos ni reportes, tampoco al editarla o eliminarla; al pagarla se crea el egreso en la cuenta elegida con la fecha de pago y la categoría original, y recién ahí cuenta en reportes.

### Fase 7 — Pulido y lanzamiento
- Estados vacíos/errores/cargando en todas las pantallas, accesibilidad (TalkBack, contraste, tamaño de fuente), pruebas manuales completas, R8, firma de release, ícono y nombre.
- **Requisitos para publicar** (decisión del 7 oct 2026):
  - "¿Olvidaste tu contraseña?" de principio a fin, con el enlace del correo abriendo la app.
  - "Eliminar cuenta" en Ajustes, con confirmación y contraseña, y una página web equivalente para la ficha de Google Play.
- API en producción con HTTPS, respaldos y correo configurado.

### Después (no iniciar sin pedido)
Modo offline (Room + WorkManager) · acceso con Google · presupuestos y recurrentes · iOS. El tipo de cambio automático diario y el PDF están planificados en el API (fases 6 y 7); en la app son activar "Automático" en Ajustes y el botón de PDF en Reportes.

## Estrategia de pruebas
| Nivel | Qué cubre | Herramientas |
|---|---|---|
| Unitarias (`domain`/`core`) | Dinero, conversiones, ahorro total, recálculo de transferencia, formato de montos y fechas | JUnit |
| ViewModel | Estados de pantalla, validaciones, errores | JUnit + MockK + Turbine, repositorios falsos |
| Backend | Aislamiento entre usuarios, integridad, reportes, pago atómico. **Viven en el API**, no aquí | `atm-orbita-api/docs/plans/12-pruebas.md` |
| Repositorios remotos | Mapeo de DTO, traducción de errores, renovación de sesión | Servidor HTTP simulado con respuestas del API |
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
| Tipo de cambio sin configurar: convertir entre monedas distintas | Sin resultado |
| Tipo de cambio sin configurar: ahorro total | Suma solo las cuentas en la moneda pedida |

**Pruebas del backend obligatorias** (se ejecutan en `atm-orbita-api`; se listan aquí porque definen lo que la app puede dar por garantizado)
- A no ve ni modifica nada de B; sin sesión no se lee nada.
- Un movimiento no puede apuntar a una cuenta o categoría de otro usuario.
- Categoría de ingreso en un egreso → error.
- Transferencia entre monedas distintas sin tipo de cambio → error; misma moneda con montos distintos → error.
- El saldo es la suma esperada tras crear movimientos y transferencias; los borrados lógicos no cuentan.
- Pagar una compra crea el egreso, la marca pagada y no se puede pagar dos veces; deshacer el pago lo revierte.
- Editar o eliminar una compra pendiente no cambia saldos ni reportes.
- Eliminar el egreso de un pago devuelve la compra a pendiente; una compra pagada no se puede eliminar.
- Archivar una tarjeta con compras pendientes → error.
- Registrarse con una moneda principal distinta de soles crea "Efectivo" en esa moneda.
- Sin tipo de cambio configurado, crear una cuenta en otra moneda → error.
- Dos categorías del mismo tipo con el mismo nombre, con cualquier combinación de mayúsculas y tildes ("Alimentación", "alimentacion", "ALIMENTACIÓN") → error. El nombre se muestra siempre como se escribió.
- Guardar un par de monedas sin tipo de cambio, o con cero → error.
- Un movimiento sin fecha creado a las 20:00 de Lima queda con la fecha de ese día, no la del siguiente.
- Eliminar la cuenta borra todas las filas del usuario.

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
- [ ] Ningún secreto en el repositorio ni en la app; la app solo conoce la URL del API.
- [ ] `allowBackup="false"` y reglas de extracción que lo excluyen todo.
- [ ] Recuperar contraseña y eliminar cuenta probados de principio a fin.
- [ ] Ningún módulo en modo demo; `release` solo con HTTPS.
- [ ] Cambios de esquema solo como migraciones en `atm-orbita-api`.
