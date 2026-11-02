# Arquitectura

Documento de diseño. No contiene código de implementación. Requisitos: `docs/requisitos.md`.
Decisiones: `docs/decisiones.md`.

## 1. Estilo

Monolito modular desplegable como una sola aplicación Spring Boot. Cada dominio es un módulo con
fronteras verificadas por ArchUnit.

```text
┌──────────────────────────────────────────────┐
│                API REST /api/v1              │
│  auth · users · ingredients · inventory      │
│  suppliers · recipes · production · quality  │
│  reporting · audit                           │
└──────────────────────┬───────────────────────┘
                       │
                ┌──────▼──────┐
                │ PostgreSQL  │
                └─────────────┘
```

Reglas de dependencia:

- `api` conoce `domain`; nunca al revés.
- `infrastructure` implementa puertos de `domain`.
- Un módulo no importa entidades JPA de otro; usa su servicio de aplicación o un puerto publicado.
- `domain` no importa Spring, JPA ni Jackson: se testea con JUnit puro.

## 2. Módulos

| Módulo | Responsabilidad | Depende de |
|---|---|---|
| `auth` | Login, tokens, contraseñas | `users`, `audit` |
| `users` | Usuarios, roles, estado | `audit` |
| `ingredients` | Productos, categorías, unidades y conversiones | — |
| `suppliers` | Proveedores | — |
| `inventory` | Lotes, movimientos, stock, PMP, alertas | `ingredients`, `suppliers`, `audit` |
| `recipes` | Recetas, versiones, costes, escalado | `ingredients` |
| `production` | Planes, preparaciones, descuento de stock | `recipes`, `inventory` |
| `quality` | Temperaturas, mermas, caducidades, incidencias | `inventory` |
| `reporting` | Consultas agregadas y exportaciones | solo lectura del resto |
| `audit` | Registro de acciones sensibles | — |

`reporting` es de solo lectura: puede consultar vistas o tablas, nunca escribir en otros dominios.

### 2.1 Capas y dónde vive cada cosa

| Capa | Contenido | Puede importar |
|---|---|---|
| `domain` | Entidades inmutables (`AppUser`), reglas (`PasswordPolicy`, `Role`), puertos (`UserRepository`, `UserService`) | Java y `com.killa.cocina.shared` |
| `infrastructure` | Entidades JPA, repositorios Spring Data, adaptadores, **casos de uso transaccionales** (`@Service`), JWT | `domain` propio y de otros módulos, Spring |
| `api` | Controladores y DTOs `record` | `domain` propio, `shared` |

Regla que se verifica con `ModuleBoundariesTest`: `domain` no importa Spring ni Jakarta Persistence y
`api` no importa `infrastructure`.

Consecuencia deliberada: los casos de uso (`UserApplicationService`, `AuthApplicationService`) viven
en `infrastructure` y se publican al API a través de una interfaz del dominio (`UserService`,
`AuthService`). Así la capa web no toca entidades JPA ni `@Transactional`, y las reglas puras siguen
probándose sin Spring. El detalle está en ADR-020.

Fuera de `modules` existe el paquete transversal `com.killa.cocina.shared`, que agrupa lo que no
pertenece a ningún dominio: cuerpo de error uniforme (`shared.error`), correlación de peticiones
(`shared.web`), actor autenticado (`shared.security`) y sondas `/health` y `/ready`
(`shared.health`). No contiene reglas de negocio y sirve igualmente a todos los módulos.

## 3. Modelo de datos

Convenciones: `snake_case` plural, `bigint` con secuencia, `establishment_id` NOT NULL, `created_at`
y `updated_at` en UTC, borrado lógico mediante `active` cuando proceda.

### 3.1 Núcleo

| Tabla | Campos principales |
|---|---|
| `establishments` | `id`, `name`, `timezone`, `currency` |
| `app_users` | `id`, `establishment_id`, `username`, `full_name`, `password_hash`, `role`, `active`, `failed_attempts`, `locked_until`; único `(establishment_id, username)` |
| `refresh_tokens` | `id`, `establishment_id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `replaced_by`; único `token_hash` |
| `audit_logs` | `id`, `establishment_id`, `actor_id`, `action`, `entity_type`, `entity_id`, `before_json`, `after_json`, `occurred_at` |

Roles: `ADMIN`, `KITCHEN_MANAGER`, `COOK`, `STOREKEEPER`.

### 3.2 Inventario

| Tabla | Campos principales |
|---|---|
| `product_categories` | `id`, `establishment_id`, `name`, `created_at`, `updated_at`; único `(establishment_id, name)` |
| `products` | `id`, `establishment_id`, `category_id`, `name`, `base_unit`, `min_stock`, `current_avg_cost`, `current_stock`, `allow_negative`, `active`; único `(establishment_id, name)` |
| `product_unit_conversions` | `id`, `establishment_id`, `product_id`, `unit`, `factor_to_base`; único `(product_id, unit)` |
| `suppliers` | `id`, `establishment_id`, `name`, `contact`, `active`; único `(establishment_id, name)` |
| `stock_lots` | `id`, `establishment_id`, `product_id`, `supplier_id`, `received_at`, `expires_at`, `unit_cost`, `initial_qty`, `remaining_qty` |
| `stock_movements` | `id`, `establishment_id`, `product_id`, `lot_id`, `type`, `quantity_base`, `unit_cost`, `balance_after`, `reason`, `reference_type`, `reference_id`, `actor_id`, `occurred_at` |

`base_unit`: `G`, `ML`, `UNIT`. Tipos de movimiento: `PURCHASE_IN`, `PRODUCTION_OUT`, `WASTE_OUT`,
`ADJUSTMENT_IN`, `ADJUSTMENT_OUT`.

La unidad base es inmutable y el factor de conversión es **por producto**: `kg` vale 1000 en un
producto de peso y `l` vale 1000 en uno de volumen. Declarar una unidad conocida de otra dimensión
(`l` en un producto en gramos) devuelve `UNIT_INCOMPATIBLE`; las etiquetas propias (`caja`, `saco`)
se aceptan con la dimensión del producto (ADR-023). Ni categorías, ni productos, ni proveedores se
borran: se archivan o desactivan con `active=false`. Un producto con stock distinto de cero no se
archiva (409 `RESOURCE_IN_USE`).

`products.current_stock` es una desnormalización de rendimiento; la fuente de verdad es
`stock_movements` y el desglose físico son los `stock_lots`. La suma de `remaining_qty` de los lotes
coincide con `current_stock` salvo cuando el saldo es negativo por excepción (ADR-024), caso en el que
no hay lote que consumir y el servicio deja un aviso en el log.

`stock_movements` es un libro append-only: no existe endpoint de edición ni borrado y un **trigger**
de la migración V4 rechaza `UPDATE` y `DELETE` (SQLSTATE 23000), también para el propietario de la
tabla. Las correcciones se hacen con un ajuste y quedan auditadas. `unit_cost` se guarda siempre por
unidad base: 12 €/kg en un producto en gramos son 0,0120 €/g.

### 3.3 Recetas

| Tabla | Campos principales |
|---|---|
| `recipes` | `id`, `establishment_id`, `name`, `category`, `active`, `current_version_id` |
| `recipe_versions` | `id`, `establishment_id`, `recipe_id`, `version_number`, `status`, `base_rations`, `yield_pct`, `prep_time_minutes`, `total_cost`, `cost_per_ration`, `instructions`, `published_at`, `published_by`, `created_by` |
| `recipe_items` | `id`, `establishment_id`, `version_id`, `product_id`, `quantity_base`, `waste_pct`, `sort_order` |

`status`: `DRAFT`, `PUBLISHED`, `ARCHIVED`. Una versión `PUBLISHED` no se modifica; los cambios
generan una versión nueva que referencia a la anterior y hay **como máximo un borrador por receta**
(índice único parcial). `quantity_base` es la cantidad **por ración** en unidad base y `waste_pct` se
suma a ella (ADR-033); `base_rations` es el rendimiento del lote y `yield_pct` el rendimiento tras
cocción, informativo. `current_version_id` apunta a la última versión publicada y solo se actualiza al
publicar. La inmutabilidad se protege con triggers, igual que el kardex.

### 3.4 Producción

| Tabla | Campos principales |
|---|---|
| `production_plans` | `id`, `establishment_id`, `service_date`, `service_type`, `status`, `created_by`, `created_at`, `updated_at` |
| `production_plan_items` | `id`, `establishment_id`, `plan_id`, `recipe_version_id`, `planned_rations` |
| `production_batches` | `id`, `establishment_id`, `plan_item_id`, `recipe_version_id`, `planned_rations`, `rations`, `status`, `started_at`, `started_by`, `finished_at`, `finished_by`, `deviation_reason`, `created_at` |

`service_type`: `LUNCH`, `DINNER`. Estado del plan: `PENDING`, `IN_PROGRESS`, `READY`, `SERVED`,
`CANCELLED`; estado del batch: `IN_PROGRESS`, `READY`, `SERVED`, `CANCELLED`. En el MVP hay un único
batch por ítem de plan (`uk_production_batches_item`) y el estado del plan se recalcula desde sus
batches. El plan es único por establecimiento, fecha y servicio, y sus ítems solo admiten versiones
**publicadas**. Un ítem con batch no se modifica ni se borra, y un batch terminado solo puede pasar de
`READY` a `SERVED`; los triggers de V6 lo impiden también por SQL (ADR-036).

### 3.5 Calidad

| Tabla | Campos principales |
|---|---|
| `temperature_logs` | `id`, `establishment_id`, `kind`, `equipment`, `value_c`, `min_c`, `max_c`, `within_range`, `actor_id`, `recorded_at`, `created_at` |
| `waste_records` | `id`, `establishment_id`, `product_id`, `lot_id`, `quantity_base`, `unit_cost`, `cost`, `reason`, `actor_id`, `occurred_at` |
| `incidents` | `id`, `establishment_id`, `severity`, `description`, `status`, `resolution`, `opened_by`, `closed_by`, `opened_at`, `closed_at` |

En `temperature_logs` el rango (`min_c`, `max_c`) forma parte de cada lectura, no de una configuración
por equipo: así una lectura pasada conserva el criterio con el que se juzgó. `within_range` es una
columna generada por PostgreSQL (`value_c BETWEEN min_c AND max_c`) y no puede contradecir a los
valores. Una lectura fuera de rango se guarda; la alerta es su consecuencia.

`waste_records` es la cabecera de la merma: guarda la cantidad total, el PMP aplicado y el coste
congelado. Cada lote consumido deja su propio `stock_movements` de tipo `WASTE_OUT` con
`reference_type=waste_record` y el `id` de la cabecera. La merma consume lotes FIFO o uno concreto y
**puede retirar lotes caducados**; producción sigue sin consumirlos (ADR-039).

`incidents` no se reabre en el MVP: la restricción `ck_incidents_closure` exige que una incidencia
`CLOSED` tenga resolución, usuario y fecha de cierre, y que una abierta no los tenga.

## 4. Flujos críticos

### 4.1 Entrada de stock

1. Validar producto activo, proveedor activo, unidad de entrada y factor de conversión.
2. Convertir la cantidad a unidad base (3 decimales) y el precio a coste por unidad base
   (4 decimales, `HALF_UP`).
3. Abrir transacción; bloquear la fila de `products` (`SELECT ... FOR UPDATE`, ADR-022).
4. Resolver `receivedAt` (por defecto ahora, nunca futura) y validar `expiresAt` (ADR-029).
5. Crear `stock_lots` con coste unitario, saldo inicial y caducidad.
6. Recalcular PMP: `(stock_value_actual + cantidad × coste) / (stock_actual + cantidad)`; si el saldo
   previo no es positivo, el PMP pasa a ser el coste de la entrada (ADR-028).
7. Actualizar `current_stock` y `current_avg_cost` del producto.
8. Insertar `stock_movements` tipo `PURCHASE_IN` con `balance_after` y `occurred_at = receivedAt`.
9. Auditar el movimiento y, si cambió el coste, el cambio de PMP (ADR-031).

Una recepción de compra no puede quedar en un producto archivado ni en un proveedor desactivado.

### 4.2 Ajuste manual de inventario

1. Validar producto activo, tipo (`ADJUSTMENT_IN`/`ADJUSTMENT_OUT`), cantidad, unidad y motivo
   (5 a 200 caracteres).
2. Abrir transacción y bloquear el producto.
3. Valorar al PMP vigente; el ajuste **no** modifica el coste medio (ADR-027).
4. Entrada: crear lote sin proveedor ni caducidad. Salida: consumir lotes FIFO por caducidad,
   incluidos los caducados (ADR-030).
5. Si la salida deja saldo negativo y `allow_negative=false`, responder **409**
   `INSUFFICIENT_STOCK` sin escribir nada.
6. Actualizar `current_stock`, insertar el movimiento con su motivo y auditar.

**Concurrencia (NFR-INT-01).** El bloqueo de la fila del producto serializa las entradas y salidas del
mismo producto: dos descuentos simultáneos no pueden leer el mismo saldo, y el segundo vuelve a
comprobarlo antes de escribir. `StockConcurrencyIntegrationTest` lo verifica con ocho y diez hilos
reales contra PostgreSQL: sin actualizaciones perdidas, sin stock negativo y con el PMP correcto. En
operaciones de varios productos (F5), el orden de bloqueo por `product_id` sigue siendo obligatorio.

### 4.3 Descuento por producción

1. Cargar la versión de receta usada (nunca la "actual": la que se registró).
2. Expandir ingredientes incluyendo merma; agregar por producto.
3. Abrir una **única** transacción.
4. Bloquear los productos implicados en orden de `product_id` para evitar interbloqueos.
5. Verificar caducidad de lotes y stock suficiente según política.
6. Consumir lotes FIFO por caducidad e insertar movimientos `PRODUCTION_OUT`.
7. Actualizar stock; si algo falla, rollback completo y error 409.
8. Insertar `production_batches` y auditar.

### 4.4 Coste de una receta

```text
consumo_i(N)  = round(q_i × (1 + merma_i/100) × N, 3)
coste_ración  = round(Σ consumo_i(1) × coste_base_i, 4)
coste_lote(N) = round(coste_ración × N, 4)
```

- `q_i` es la cantidad **por ración** en unidad base y `coste_base_i` el PMP vigente por unidad base
  (ADR-033). El redondeo ocurre una sola vez por magnitud, con las escalas de `Quantities` (3 y 4
  decimales, `HALF_UP`).
- Al publicar se congelan `cost_per_ration` y `total_cost` en `recipe_versions`, con un corte
  consistente del PMP: los productos implicados se bloquean en orden de `product_id` (ADR-035).
- En consulta se devuelven las dos cifras: `frozen*` (solo en publicadas) y `current*` (siempre, con
  el PMP de hoy). Un borrador no tiene coste congelado.
- Un informe histórico usa el coste congelado, nunca el precio de hoy.
- Si falta el coste de algún ingrediente, `current*` vale `null` y se listan los productos que faltan:
  no se publica una cifra a medias. Publicar exige coste completo.

### 4.5 Versionado

```text
REC-001 v1 (PUBLISHED) ──► REC-001 v2 (PUBLISHED) ──► REC-001 v3 (DRAFT)
```

1. `POST /recipes` crea la receta con su v1 en `DRAFT`.
2. El borrador se edita **en sitio**: cabecera (`PATCH`) e ingredientes (`PUT items`).
3. Solo puede haber **un borrador por receta** (índice único parcial en BD).
4. `POST /versions` clona la última versión, incluidos sus ingredientes, como nuevo `DRAFT`.
5. `POST /publish` exige ≥1 ingrediente, sin productos repetidos, receta activa y todos los productos
   activos; congela el coste y actualiza `recipes.current_version_id`.
6. `DELETE /versions/{id}` solo descarta borradores y nunca la única versión de la receta.

Nunca se actualiza una versión publicada: la API responde `RESOURCE_IMMUTABLE(409)` y los triggers de
la migración V5 rechazan `UPDATE`/`DELETE` de la versión y de sus ingredientes, también por SQL
directo. Las producciones guardan `recipe_version_id`, por lo que el histórico sigue siendo
reproducible. Una receta no se borra: se archiva con `active=false`.

### 4.6 Escalado y merma

`GET /recipes/{id}/versions/{versionId}/scale?rations=N` es una consulta de solo lectura: expande los
ingredientes a N raciones, devuelve el coste del lote y **no** congela nada. La merma es aditiva
(`q × (1 + m/100)`, máximo 99,99 %) y se aplica antes de escalar; el rendimiento tras cocción
(`yield_pct`) es informativo y no altera el coste. La misma expansión alimenta el descuento de
producción de F5, agregando por producto.

### 4.7 Informes de producción

Los dos informes son de solo lectura, exigen `from` y `to` (días de negocio en `Europe/Madrid`) y
rechazan periodos de más de 31 días. La ventana se traduce a `[00:00 del primer día, 00:00 del día
siguiente al último)` en UTC, con el desfase de verano aplicado.

- **Desviaciones (PRD-05).** Toma los batches con `finished_at` dentro del periodo y devuelve solo los
  que tienen `rations ≠ planned_rations`, con plan, servicio, receta, versión, raciones, motivo y
  responsable. Una ejecución sin desviación no aparece.
- **Consumo teórico vs real (ECO-03).** Por cada batch terminado, expande la versión usada con las
  raciones reales **sin merma** y agrega por producto con un único redondeo. El consumo real son los
  movimientos `PRODUCTION_OUT` de esos batches; los ajustes son
  `ADJUSTMENT_OUT − ADJUSTMENT_IN` del periodo. Así `desviación = merma + ajustes` y el informe
  explica la diferencia entre lo que dicta la receta y lo que salió del stock.

El informe de consumo resuelve las sumas con SQL agregado y no carga el kardex completo; el de
desviaciones filtra por `finished_at` en la base y solo carga los batches del periodo (ADR-037).

### 4.8 Merma de calidad

1. Validar producto, cantidad en unidad base (> 0), motivo (5–200 caracteres) y lote opcional.
2. Abrir transacción; guardar la cabecera provisional con coste cero para obtener su id (ADR-039).
3. Bloquear el producto y tomar el PMP vigente.
4. Consumir el lote indicado o los lotes con saldo en orden FIFO, **incluidos los caducados**;
   insertar un `WASTE_OUT` por lote con su coste y `reference_type=waste_record`.
5. Si no hay existencias y `allow_negative=false`, responder **409** `INSUFFICIENT_STOCK` y revertir
   la cabecera.
6. Actualizar `current_stock`, completar la cabecera con el PMP y auditar `WASTE_RECORDED` con el
   saldo anterior y posterior.

La merma es la vía para retirar un lote caducado; producción mantiene la prohibición de consumirlo
(§4.3). El detalle por lote queda en el kardex y la cabecera conserva el coste congelado al PMP.

## 5. Multi-tenencia

- `establishment_id` en todas las tablas de negocio.
- El MVP resuelve el establecimiento con un valor de configuración y lo propaga en cada operación.
- No hay endpoint para crear establecimientos ni aislamiento dinámico por petición.
- Evolución: filtro global de Hibernate o discriminador por usuario, sin cambiar el esquema.

## 6. Seguridad

- `Authorization: Bearer <access token>`; endpoints públicos: `/health`, `/ready`, `/v3/api-docs/**`,
  `/swagger-ui/**` y `POST /api/v1/auth/{login,refresh,logout}`.
- `@PreAuthorize` por acción; permission set centralizado en `Permission` y `Role`, probado con
  `RoleMatrixTest`. Cada permiso viaja en el claim `authorities` del token.
- Un endpoint sin anotación se considera cerrado: `EndpointAuthorizationTest` falla si aparece uno, y
  cualquier otra ruta exige autenticación (`anyRequest().authenticated()`).
- Refresh token opaco de 256 bits almacenado como hash SHA-256; rotación en cada uso y detección de
  reutilización: si se presenta un token ya rotado, se revoca la familia completa del usuario.
- Access token JWT HS256 de 15 minutos (`SecuritySettings` rechaza vidas mayores). Sin sesión ni
  cookies: `SessionCreationPolicy.STATELESS`.
- Contraseñas Argon2id; política mínima de 12 caracteres y bloqueo de 15 minutos tras 5 fallos
  (ADR-016, ADR-017). El bloqueo caduca solo y reinicia el contador.
- Errores sin filtrar detalles internos; `requestId` para correlación. El 401 y el 403 se emiten con
  el mismo cuerpo uniforme desde la cadena de filtros.
- Datos personales mínimos: nombre de usuario y nombre visible. Sin DNI, sin fichajes.

### 6.1 Matriz de permisos

| Permiso | ADMIN | KITCHEN_MANAGER | COOK | STOREKEEPER |
|---|:--:|:--:|:--:|:--:|
| `users:read` / `users:write` | ✔ / ✔ | ✔ / — | — / — | — / — |
| `inventory:read` / `inventory:write` | ✔ / ✔ | ✔ / ✔ | ✔ / — | ✔ / ✔ |
| `suppliers:read` / `suppliers:write` | ✔ / ✔ | ✔ / ✔ | — / — | ✔ / ✔ |
| `recipes:read` / `recipes:write` | ✔ / ✔ | ✔ / ✔ | ✔ / — | ✔ / — |
| `production:read` / `production:write` | ✔ / ✔ | ✔ / ✔ | ✔ / ✔ | — / — |
| `quality:read` / `quality:write` | ✔ / ✔ | ✔ / ✔ | ✔ / ✔ | ✔ / — |
| `reporting:read` | ✔ | ✔ | ✔ | ✔ |

Solo `ADMIN` escribe usuarios: es la frontera que protege el resto del sistema. Un usuario nunca se
borra; se desactiva, y la operación se rechaza si dejaría el establecimiento sin administradores
activos.

### 6.2 Endpoints de autenticación, usuarios, catálogo y stock

| Método y ruta | Permiso | Notas |
|---|---|---|
| `POST /api/v1/auth/login` | público | Devuelve `accessToken`, `refreshToken` y caducidades |
| `POST /api/v1/auth/refresh` | público (refresh token) | Rota el refresh; reutilizar uno rotado revoca la familia |
| `POST /api/v1/auth/logout` | público (refresh token) | Idempotente; revoca el refresh presentado |
| `GET /api/v1/users` | `users:read` | Paginado (`page` base 0, `size` máximo 100) |
| `POST /api/v1/users` | `users:write` | Contraseña validada por `PasswordPolicy` (400 `WEAK_PASSWORD`) |
| `GET /api/v1/users/{id}` | `users:read` | Solo del establecimiento del token |
| `PATCH /api/v1/users/{id}` | `users:write` | Edición parcial; `active:false` es la baja lógica |
| `GET /api/v1/product-categories` | `inventory:read` | Paginado y ordenado por nombre |
| `POST /api/v1/product-categories` | `inventory:write` | Nombre único; repetido → 409 `DUPLICATE_NAME` |
| `GET /api/v1/product-categories/{id}` | `inventory:read` | Solo del establecimiento del token |
| `PATCH /api/v1/product-categories/{id}` | `inventory:write` | Renombrado; no hay borrado |
| `GET /api/v1/products` | `inventory:read` | Filtros `active` y `categoryId`, paginado |
| `POST /api/v1/products` | `inventory:write` | Alta con unidad base y conversiones |
| `GET /api/v1/products/{id}` | `inventory:read` | Incluye conversiones, stock y alerta de mínimo |
| `PATCH /api/v1/products/{id}` | `inventory:write` | Edición parcial; archivar exige stock cero |
| `GET /api/v1/products/{id}/conversions` | `inventory:read` | Conversiones vigentes del producto |
| `PUT /api/v1/products/{id}/conversions` | `inventory:write` | Reemplaza el conjunto; vacío las elimina |
| `GET /api/v1/suppliers` | `suppliers:read` | Paginado y ordenado por nombre |
| `POST /api/v1/suppliers` | `suppliers:write` | Nombre único; repetido → 409 `DUPLICATE_NAME` |
| `GET /api/v1/suppliers/{id}` | `suppliers:read` | Solo del establecimiento del token |
| `PATCH /api/v1/suppliers/{id}` | `suppliers:write` | Edición parcial; `active:false` es la baja lógica |
| `POST /api/v1/products/{id}/purchases` | `inventory:write` | Entrada con proveedor, cantidad, unidad, precio, recepción y caducidad; 201 |
| `POST /api/v1/products/{id}/adjustments` | `inventory:write` | Ajuste `IN`/`OUT` con motivo obligatorio; 201 |
| `GET /api/v1/products/{id}/movements` | `inventory:read` | Kardex paginado, más reciente primero; sin edición ni borrado (405) |
| `GET /api/v1/products/{id}/lots` | `inventory:read` | Lotes y saldo restante, paginado |
| `GET /api/v1/inventory/alerts` | `inventory:read` | Stock bajo mínimo y caducidad próxima con valor en riesgo |

### 6.3 Producción e informes

| Método y ruta | Permiso | Notas |
|---|---|---|
| `GET /api/v1/production-plans` | `production:read` | Paginado; filtros por fecha y estado |
| `POST /api/v1/production-plans` | `production:write` | Un plan por fecha y servicio; duplicado → 409 `RESOURCE_IN_USE` |
| `GET /api/v1/production-plans/{id}` | `production:read` | Plan, ítems y estado derivado |
| `PATCH /api/v1/production-plans/{id}` | `production:write` | Solo con el plan en `PENDING` |
| `DELETE /api/v1/production-plans/{id}` | `production:write` | Solo `PENDING` y sin ejecuciones; 204 |
| `PUT /api/v1/production-plans/{id}/items` | `production:write` | Sustituye los ítems; solo versiones publicadas y plan `PENDING` |
| `DELETE /api/v1/production-plans/{id}/items/{itemId}` | `production:write` | Solo con el plan en `PENDING` y sin batch |
| `GET /api/v1/production-plans/{id}/requirements` | `production:read` | Necesidades agregadas por producto, con un único redondeo (PRD-02) |
| `POST /api/v1/production-plans/{planId}/items/{itemId}/batches` | `production:write` | Arranca la ejecución; 201 y sin descontar stock todavía |
| `GET /api/v1/production-batches/{id}` | `production:read` | Estado, raciones, desviación y responsables |
| `PATCH /api/v1/production-batches/{id}` | `production:write` | `READY`, `CANCELLED` o `SERVED`; el stock se descuenta al pasar a `READY` |
| `GET /api/v1/production-reports/deviations?from=&to=` | `reporting:read` | Desviaciones no nulas del periodo (PRD-05) |
| `GET /api/v1/production-reports/consumption?from=&to=` | `reporting:read` | Teórico, producción, merma, ajustes y desviación por producto (ECO-03) |

Los informes aceptan como máximo 31 días naturales y se agrupan por fecha de negocio en
`Europe/Madrid`.

### 6.4 Calidad y trazabilidad

| Método y ruta | Permiso | Notas |
|---|---|---|
| `POST /api/v1/temperature-logs` | `quality:write` | Lectura con su rango; fuera de rango se guarda y genera alerta; 201 |
| `GET /api/v1/temperature-logs` | `quality:read` | Paginado, más reciente primero |
| `GET /api/v1/temperature-logs/{id}` | `quality:read` | Solo del establecimiento del token |
| `GET /api/v1/quality/temperature-alerts` | `quality:read` | Lecturas fuera de rango, máximo las 100 más recientes |
| `POST /api/v1/waste-records` | `quality:write` | Merma en unidad base con lote opcional; descuenta stock y se valora al PMP; 201 |
| `GET /api/v1/waste-records` | `quality:read` | Paginado, más reciente primero |
| `GET /api/v1/waste-records/{id}` | `quality:read` | Solo del establecimiento del token |

El rango viaja en cada lectura y `recordedAt` es opcional (por defecto ahora, nunca futura). El actor
sale del token y queda en la lectura y en la auditoría.

La merma consume el lote indicado o los lotes con saldo en orden FIFO, incluidos los caducados, y puede
dejar saldo negativo solo si el producto lo permite. La cabecera se valora con el PMP y cada lote
consumido deja su movimiento `WASTE_OUT` (ADR-039).

### 6.5 Auditoría

`audit_logs` es append-only: sin endpoint de edición ni borrado. Se registra en la misma transacción
que la operación auditada, con `actor_id` tomado del token (`CurrentActor`), nunca del cuerpo de la
petición. Desde V7 un trigger rechaza `UPDATE` y `DELETE` también por SQL directo. Acciones ya
cubiertas: `USER_CREATED`, `USER_UPDATED`, `USER_ROLE_CHANGED`, `USER_DEACTIVATED`, `LOGIN_FAILED`,
`REFRESH_TOKEN_REUSED`, `CATEGORY_CREATED`, `CATEGORY_UPDATED`, `PRODUCT_CREATED`, `PRODUCT_UPDATED`,
`PRODUCT_ARCHIVED`, `PRODUCT_CONVERSIONS_UPDATED`, `SUPPLIER_CREATED`, `SUPPLIER_UPDATED`,
`STOCK_PURCHASE_RECORDED`, `STOCK_ADJUSTMENT_RECORDED`, `STOCK_COST_CHANGED`,
`TEMPERATURE_LOGGED` y `WASTE_RECORDED`.

### 6.6 Arranque del primer administrador

`AdminBootstrap` garantiza la fila de `establishments` y crea un `ADMIN` solo si no hay ningún usuario
y existe `BOOTSTRAP_ADMIN_PASSWORD`. Sin contraseña configurada no se crea nada: no hay credenciales
por defecto. La decisión y sus alternativas están en ADR-019.

## 7. API

- Prefijo `/api/v1`.
- Recursos en plural: `/products`, `/recipes`, `/production-plans`.
- Errores: `{ "code", "message", "details", "requestId" }`.
- Paginación: `page` (base 0), `size`; respuesta con `content`, `page`, `size`, `total`.
- Fechas ISO 8601 en UTC (`2026-10-30T21:30:00Z`).
- Sin `DELETE` en el catálogo: la baja es `PATCH` con `active=false`. Un producto con stock responde
  409 `RESOURCE_IN_USE`; un nombre repetido, 409 `DUPLICATE_NAME`.
- `baseUnit` viaja como `G`, `ML` o `UNIT`. Una unidad de compra incompatible responde 400
  `UNIT_INCOMPATIBLE`, tanto al configurar el producto como al mover stock.
- Un ajuste que dejaría saldo negativo con la política de bloqueo responde 409
  `INSUFFICIENT_STOCK`; un ajuste sin motivo válido, 400 `VALIDATION_ERROR`.
- `/inventory/alerts` no se pagina: cada lista se limita a 100 elementos y la respuesta incluye
  `lowStockTotal` y `expiringTotal` con el total real.
- Sin token, cualquier endpoint de negocio responde 401 `UNAUTHORIZED`. INV-05 dice «sin usuario →
  400»; se mantiene 401 porque es lo correcto para una petición no autenticada.
- OpenAPI generado; no se documenta a mano en Markdown.

## 8. Persistencia y rendimiento

- `numeric(12,3)` cantidades, `numeric(12,4)` costes.
- Índices previstos: `stock_movements(product_id, occurred_at DESC, id DESC)`,
  `stock_lots(product_id, expires_at, received_at)`, `app_users(username)`,
  `audit_logs(entity_type, entity_id)`, `production_plans(service_date)`.
- Sin caché en el MVP; si hace falta, primero medir.
- Informes: SQL agregado y solo lectura; no cargar entidades completas para sumar.

## 9. Observabilidad y operación

- Logs JSON con `requestId`, usuario y módulo; sin datos sensibles.
- `/health` (proceso) y `/ready` (base de datos y migraciones).
- Backups: `pg_dump` diario del contenedor o del servidor; restauración probada en F7.
- Imagen Docker: multi-stage, JRE 25, usuario no root, healthcheck.

## 10. Pendiente

| Tema | Estado |
|---|---|
| Estrategia de despliegue | PENDIENTE (F7) |
| Retención y base legal de datos personales | PENDIENTE |
| Adminer en desarrollo | PENDIENTE |
| Subrecetas (REC-07) | v1 — el modelo de `recipe_items` se ampliará con componentes |
