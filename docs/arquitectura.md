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
| `product_categories` | `id`, `establishment_id`, `name` |
| `products` | `id`, `establishment_id`, `name`, `category_id`, `base_unit`, `min_stock`, `current_avg_cost`, `current_stock`, `allow_negative`, `active` |
| `product_unit_conversions` | `id`, `product_id`, `unit`, `factor_to_base` |
| `suppliers` | `id`, `establishment_id`, `name`, `contact`, `active` |
| `stock_lots` | `id`, `establishment_id`, `product_id`, `supplier_id`, `received_at`, `expires_at`, `unit_cost`, `initial_qty`, `remaining_qty` |
| `stock_movements` | `id`, `establishment_id`, `product_id`, `lot_id`, `type`, `quantity_base`, `unit_cost`, `balance_after`, `reason`, `reference_type`, `reference_id`, `actor_id`, `occurred_at` |

`base_unit`: `G`, `ML`, `UNIT`. Tipos de movimiento: `PURCHASE_IN`, `PRODUCTION_OUT`, `WASTE_OUT`,
`ADJUSTMENT_IN`, `ADJUSTMENT_OUT`.

`products.current_stock` es una desnormalización de rendimiento; la fuente de verdad es
`stock_movements`. Un test verifica que ambos coinciden.

### 3.3 Recetas

| Tabla | Campos principales |
|---|---|
| `recipes` | `id`, `establishment_id`, `name`, `category`, `active`, `current_version_id` |
| `recipe_versions` | `id`, `recipe_id`, `version_number`, `status`, `base_rations`, `total_cost`, `cost_per_ration`, `instructions`, `published_at`, `published_by` |
| `recipe_items` | `id`, `version_id`, `product_id`, `quantity_base`, `waste_pct`, `sort_order` |

`status`: `DRAFT`, `PUBLISHED`, `ARCHIVED`. Una versión `PUBLISHED` no se modifica; los cambios
generan una versión nueva que referencia a la anterior.

### 3.4 Producción

| Tabla | Campos principales |
|---|---|
| `production_plans` | `id`, `establishment_id`, `service_date`, `service_type`, `status` |
| `production_plan_items` | `id`, `plan_id`, `recipe_version_id`, `planned_rations` |
| `production_batches` | `id`, `establishment_id`, `plan_item_id`, `recipe_version_id`, `rations`, `status`, `started_at`, `finished_at`, `actor_id`, `deviation_reason` |

`service_type`: `LUNCH`, `DINNER`. `status`: `PENDING`, `IN_PROGRESS`, `READY`, `SERVED`,
`CANCELLED`.

### 3.5 Calidad

| Tabla | Campos principales |
|---|---|
| `temperature_logs` | `id`, `establishment_id`, `equipment`, `kind`, `value_c`, `min_c`, `max_c`, `within_range`, `actor_id`, `recorded_at`, `notes` |
| `waste_records` | `id`, `establishment_id`, `product_id`, `lot_id`, `quantity_base`, `reason`, `cost`, `actor_id`, `occurred_at` |
| `incidents` | `id`, `establishment_id`, `severity`, `description`, `status`, `resolution`, `opened_by`, `closed_by`, `opened_at`, `closed_at` |

## 4. Flujos críticos

### 4.1 Entrada de stock

1. Validar producto, unidad de entrada y factor de conversión.
2. Convertir a unidad base y redondear a 3 decimales.
3. Abrir transacción; bloquear la fila de `products` (`SELECT ... FOR UPDATE`).
4. Crear `stock_lots` con coste unitario y caducidad.
5. Insertar `stock_movements` tipo `PURCHASE_IN` con `balance_after`.
6. Recalcular PMP: `(stock_value_actual + cantidad × coste) / (stock_actual + cantidad)`.
7. Actualizar `current_stock` y `current_avg_cost`.
8. Auditar si cambia el coste.

### 4.2 Descuento por producción

1. Cargar la versión de receta usada (nunca la "actual": la que se registró).
2. Expandir ingredientes incluyendo merma; agregar por producto.
3. Abrir una **única** transacción.
4. Bloquear los productos implicados en orden de `product_id` para evitar interbloqueos.
5. Verificar caducidad de lotes y stock suficiente según política.
6. Consumir lotes FIFO por caducidad e insertar movimientos `PRODUCTION_OUT`.
7. Actualizar stock; si algo falla, rollback completo y error 409.
8. Insertar `production_batches` y auditar.

### 4.3 Coste de una receta

- Al publicar: congelar `total_cost` y `cost_per_ration` en `recipe_versions`.
- En consulta: recalcular con el PMP vigente si se pide "coste actual".
- Un informe histórico usa el coste congelado, nunca el precio de hoy.

### 4.4 Versionado

```text
REC-001 v1 (PUBLISHED) ──► REC-001 v2 (PUBLISHED) ──► REC-001 v3 (DRAFT)
```

Nunca se actualiza una versión publicada. Las producciones guardan `recipe_version_id`, por lo que el
histórico sigue siendo reproducible.

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

### 6.2 Endpoints de F2

| Método y ruta | Permiso | Notas |
|---|---|---|
| `POST /api/v1/auth/login` | público | Devuelve `accessToken`, `refreshToken` y caducidades |
| `POST /api/v1/auth/refresh` | público (refresh token) | Rota el refresh; reutilizar uno rotado revoca la familia |
| `POST /api/v1/auth/logout` | público (refresh token) | Idempotente; revoca el refresh presentado |
| `GET /api/v1/users` | `users:read` | Paginado (`page` base 0, `size` máximo 100) |
| `POST /api/v1/users` | `users:write` | Contraseña validada por `PasswordPolicy` (400 `WEAK_PASSWORD`) |
| `GET /api/v1/users/{id}` | `users:read` | Solo del establecimiento del token |
| `PATCH /api/v1/users/{id}` | `users:write` | Edición parcial; `active:false` es la baja lógica |

### 6.3 Auditoría

`audit_logs` es append-only: sin endpoint de edición ni borrado. Se registra en la misma transacción
que la operación auditada, con `actor_id` tomado del token (`CurrentActor`), nunca del cuerpo de la
petición. Acciones ya cubiertas: `USER_CREATED`, `USER_UPDATED`, `USER_ROLE_CHANGED`,
`USER_DEACTIVATED`, `LOGIN_FAILED` y `REFRESH_TOKEN_REUSED`.

### 6.4 Arranque del primer administrador

`AdminBootstrap` garantiza la fila de `establishments` y crea un `ADMIN` solo si no hay ningún usuario
y existe `BOOTSTRAP_ADMIN_PASSWORD`. Sin contraseña configurada no se crea nada: no hay credenciales
por defecto. La decisión y sus alternativas están en ADR-019.

## 7. API

- Prefijo `/api/v1`.
- Recursos en plural: `/products`, `/recipes`, `/production-plans`.
- Errores: `{ "code", "message", "details", "requestId" }`.
- Paginación: `page` (base 0), `size`; respuesta con `content`, `page`, `size`, `total`.
- Fechas ISO 8601 en UTC (`2026-10-30T21:30:00Z`).
- OpenAPI generado; no se documenta a mano en Markdown.

## 8. Persistencia y rendimiento

- `numeric(12,3)` cantidades, `numeric(12,4)` costes.
- Índices previstos: `stock_movements(product_id, occurred_at)`, `stock_lots(product_id, expires_at)`,
  `app_users(username)`, `audit_logs(entity_type, entity_id)`, `production_plans(service_date)`.
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
