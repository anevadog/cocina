# Requisitos

Catálogo de referencia del proyecto. Cada requisito tiene identificador estable, prioridad y criterio
de aceptación verificable.

- **M (Must):** entra en el MVP. Sin él no hay entrega.
- **S (Should):** siguiente iteración (v1).
- **C (Could):** futuro, no comprometido.

Los criterios de aceptación están redactados para convertirse en tests. Si un requisito cambia, se
actualiza aquí y en `docs/plan.md`.

## 1. Alcance por fase

| Fase | Requisitos incluidos |
|---|---|
| MVP | INV-01..09, REC-01..06, PRD-01..05, PER-01..05, CAL-01..06, ECO-01/03/06, PLA-01..06 |
| v1 | INV-10/11, REC-07/08, PRD-06/07, CAL-07, ECO-02/04/05/08 |
| Futuro | REC-09, ECO-07, multi-establecimiento activo, TPV, pedidos automáticos, predicción |

## 2. Supuestos

1. Un establecimiento, moneda EUR, sistema métrico, API en español, código en inglés.
2. 5–30 empleados, 100–300 ingredientes, 50–150 recetas, menos de 1.000 operaciones diarias.
3. Los clientes (web/móvil) se construirán después; ahora el producto es la API.
4. Conexión a internet estable en cocina; no se requiere modo offline.
5. Las cantidades se pesan y registran con disciplina razonable.
6. Zona horaria de negocio `Europe/Madrid`; almacenamiento y API en UTC.
7. Los precios de compra se introducen manualmente; no hay integración con facturas.

## 3. Requisitos funcionales

### 3.1 Inventario y unidades (`INV`)

| ID | Requisito | P | Criterio de aceptación |
|---|---|---|---|
| INV-01 | CRUD de productos con categoría, unidad base, stock mínimo y proveedor habitual | M | Crear, editar, listar y archivar; no se permite borrar con stock o uso en recetas |
| INV-02 | Conversión entre unidades compatibles (g/kg, ml/l, ud) con factor por producto | M | 1 kg de entrada y 500 g de salida dejan 500 g; g→ml se rechaza |
| INV-03 | Persistencia en unidad base con 3 decimales | M | 0,25 kg se almacena como 250 g; nunca coma flotante |
| INV-04 | Entrada de compra con lote, proveedor, precio, recepción y caducidad | M | Incrementa stock, crea lote y recalcula coste; aparece en el kardex |
| INV-05 | Salidas y ajustes manuales con motivo obligatorio y usuario | M | Ajuste sin motivo o sin usuario autenticado → 400 |
| INV-06 | Kardex inalterable con saldo resultante | M | No existe edición ni borrado; el saldo cuadra con la suma de movimientos |
| INV-07 | Alertas de stock bajo mínimo y caducidad próxima | M | El producto bajo mínimo aparece en alertas tras el movimiento |
| INV-08 | Política configurable de stock negativo | M | Con bloqueo, un descuento que deje saldo negativo → 409 sin cambios |
| INV-09 | Coste medio ponderado (PMP) recalculado en cada entrada | M | Dos compras a distinto precio dan la media ponderada con redondeo definido |
| INV-10 | Ubicaciones de almacenamiento (cámara, congelador, seco) | S | Stock por ubicación; la suma coincide con el global |
| INV-11 | Inventario físico con recuento y ajuste por diferencias | S | Genera ajuste documentado con diferencia y usuario |

### 3.2 Recetario (`REC`)

| ID | Requisito | P | Criterio de aceptación |
|---|---|---|---|
| REC-01 | Receta con raciones base, rendimiento, instrucciones y tiempo | M | Crear receta válida; cantidades y coste siempre por ración base |
| REC-02 | Ingredientes con cantidad por ración, unidad y merma (%) | M | Cambiar la merma altera consumo y coste de forma verificable |
| REC-03 | Escalado a N raciones con redondeo determinista | M | 250 g × 3 = 750 g; el redondeo está probado |
| REC-04 | Coste por ración y por receta con el coste vigente | M | Coincide con la suma de cantidad × coste unitario |
| REC-05 | Versionado inmutable de recetas | M | Editar una versión publicada → error; se crea versión nueva y se conserva historial |
| REC-06 | Descuento automático de stock al registrar producción | M | Una sola transacción con la versión exacta usada; si falla un ingrediente no se descuenta ninguno |
| REC-07 | Subrecetas con explosión de necesidades | S | Se descuentan los ingredientes de la subreceta, no la subreceta |
| REC-08 | Alérgenos por ingrediente y receta | S | La ficha lista los alérgenos derivados |
| REC-09 | Ficha técnica exportable en PDF | C | PENDIENTE: formato y consumidor |

### 3.3 Producción (`PRD`)

| ID | Requisito | P | Criterio de aceptación |
|---|---|---|---|
| PRD-01 | Plan de producción por fecha y servicio (comida/cena) | M | No admite duplicados para la misma fecha y servicio |
| PRD-02 | Necesidades agregadas de ingredientes del plan | M | Suma de todas las recetas agrupada por producto |
| PRD-03 | Registro de producción real con descuento de stock | M | Descuenta stock y queda atribuido al usuario autenticado |
| PRD-04 | Estados: pendiente, en preparación, listo, servido, cancelado | M | Solo transiciones válidas; cancelada no descuenta stock |
| PRD-05 | Desviación previsto vs real con motivo | M | Diferencia distinta de cero exige motivo y aparece en el informe |
| PRD-06 | Tiempos estimados frente a reales | S | Se registra inicio/fin y se calcula duración |
| PRD-07 | Checklists de apertura, preparación y cierre | S | Firmados con usuario y fecha; no se firman dos veces el mismo día |

### 3.4 Calidad y trazabilidad (`CAL`)

| ID | Requisito | P | Criterio de aceptación |
|---|---|---|---|
| CAL-01 | Lotes de proveedor con trazabilidad entrada → consumo | M | Dado un lote se obtiene qué preparaciones lo consumieron y cuándo |
| CAL-02 | Registro de temperaturas de equipo y preparación | M | Fuera de rango genera alerta con usuario y hora |
| CAL-03 | Mermas y desperdicios con causa y coste | M | Descuenta stock y calcula coste con el PMP vigente |
| CAL-04 | Bloqueo de consumo de lotes caducados | M | Usar un lote caducado → 409 |
| CAL-05 | Incidencias con severidad, estado y resolución | M | Cerrar exige resolución y usuario |
| CAL-06 | Auditoría de acciones sensibles | M | Ajustes, costes y roles generan registro no editable con valor anterior y nuevo |
| CAL-07 | Consulta del histórico de calidad con filtros | S | Temperaturas, mermas e incidencias filtrables por fecha |

### 3.5 Personal y permisos (`PER`)

| ID | Requisito | P | Criterio de aceptación |
|---|---|---|---|
| PER-01 | Usuarios con rol, estado y credenciales | M | Alta, edición y desactivación; nunca borrado físico |
| PER-02 | Roles: Administrador, Responsable de cocina, Cocinero, Almacén | M | Matriz de permisos documentada y probada |
| PER-03 | Autorización por recurso y acción | M | Un Cocinero no ajusta stock ni gestiona usuarios (403 con log) |
| PER-04 | Atribución de toda operación al usuario autenticado | M | Ninguna operación de negocio sin `actor_id` |
| PER-05 | Mínimo privilegio por defecto | M | Un endpoint sin permiso declarado es inaccesible |

### 3.6 Control económico (`ECO`)

| ID | Requisito | P | Criterio de aceptación |
|---|---|---|---|
| ECO-01 | Coste actual e histórico de receta y ración por versión | M | Se consulta una versión antigua con los precios de su momento |
| ECO-02 | Comparativa de costes entre periodos | S | Dos rangos devuelven variación absoluta y porcentual |
| ECO-03 | Consumo teórico frente a real por ingrediente | M | Diferencia = real − teórico, explicada por mermas y ajustes |
| ECO-04 | Ranking de desperdicio por ingrediente y causa | S | Ordenado por coste de merma del periodo |
| ECO-05 | Evolución de stock y valoración | S | Serie temporal del valor del inventario |
| ECO-06 | Productos próximos a caducar con valor en riesgo | M | Listado con días restantes y valor estimado |
| ECO-07 | Margen por plato | C | Requiere precio de venta; fuera del MVP |
| ECO-08 | Exportación CSV de informes | S | UTF-8, cabeceras y filtros aplicados |

### 3.7 Plataforma (`PLA`)

| ID | Requisito | P | Criterio de aceptación |
|---|---|---|---|
| PLA-01 | API REST `/api/v1` con OpenAPI | M | Contrato consultable y sincronizado con los endpoints |
| PLA-02 | Validación y error uniforme | M | Todo error devuelve `{ code, message, details, requestId }` |
| PLA-03 | Paginación, filtros y orden en listados | M | Sin parámetros devuelve página 1 con límite por defecto |
| PLA-04 | `/health` y `/ready` | M | Health sin autenticación; ready verifica la base de datos |
| PLA-05 | Migraciones y seed de datos demo | M | Base vacía levantada con un comando |
| PLA-06 | Fechas ISO 8601 en UTC y zona de negocio explícita | M | Operación a las 23:30 de Madrid conserva la fecha correcta |

## 4. Requisitos no funcionales

| ID | Requisito | Métrica / criterio |
|---|---|---|
| NFR-SEG-01 | Contraseñas Argon2id, política mínima y bloqueo por intentos | 5 intentos → bloqueo temporal; nunca se registra la contraseña |
| NFR-SEG-02 | Access token corto + refresh rotatorio revocable | Access ≤ 15 min; refresh con hash en base de datos |
| NFR-SEG-03 | RBAC de mínimo privilegio | 100 % de endpoints protegidos salvo `auth` y `health`, verificado por test |
| NFR-SEG-04 | Sin secretos en el repositorio | Solo `.env.example`; comprobación en CI |
| NFR-REN-01 | Latencia de CRUD | p95 < 300 ms con 10 usuarios y 100k movimientos |
| NFR-REN-02 | Cálculo de necesidades de producción | < 2 s para 100 recetas y 500 raciones |
| NFR-INT-01 | Integridad transaccional del stock | Todo o nada; sin carreras que dejen stock negativo |
| NFR-DIS-01 | Disponibilidad en horario de servicio | 99 %; RTO 4 h, RPO 24 h |
| NFR-DIS-02 | Copias de seguridad | Backup diario + restauración probada y documentada |
| NFR-MAN-01 | Calidad de código | `./mvnw verify` verde; cobertura ≥ 70 % en stock, costes y versionado |
| NFR-MAN-02 | Historial legible | Conventional Commits + CI en cada push |
| NFR-OBS-01 | Observabilidad | Logs estructurados con `requestId`, sin datos personales |
| NFR-POR-01 | Portabilidad | Imagen Docker sobre JRE 25; configuración solo por entorno |
| NFR-USO-01 | Operaciones cortas | Registrar producción ≤ 3 pasos desde el futuro cliente |
| NFR-ESC-01 | Preparado para multi-establecimiento | `establishment_id` desde la primera migración; segundo establecimiento sin migración destructiva |
| NFR-DAT-01 | Protección de datos | Minimización aplicada e inventario documentado en `docs/seguridad.md`; retención propuesta. PENDIENTE P2: base legal RGPD/LOPDGDD y plazos definitivos |
| NFR-COST-01 | Coste | 0 € en desarrollo; ≤ 15 €/mes en producción |
| NFR-I18N-01 | Idioma | API y documentación en español; código, tablas y commits en inglés; UTF-8 |

## 5. Fuera de alcance

Frontend, app móvil, TPV y ventas reales, multi-restaurante activo, pedidos automáticos, predicción de
compra, facturación, nóminas, fichaje laboral, hardware, modo offline, SSO externo, margen por plato.

## 6. Riesgos abiertos

| Riesgo | Estado |
|---|---|
| Datos personales de empleados (RGPD/LOPDGDD) | Abierto — decidir antes de datos reales |
| Adopción real del registro diario por el personal | Abierto — se mitiga con operaciones cortas |
| Despliegue en producción y coste | Abierto — se decide en F7 |
