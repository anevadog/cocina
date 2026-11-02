# Plan de trabajo

Fases incrementales. Cada fase termina con un cambio lógico completo, validado y documentado.
Ninguna fase se inicia sin aprobación explícita de la anterior.

Leyenda: `[ ]` pendiente · `[~]` en curso · `[x]` hecho

## Progreso

| Fase | Contenido | Estado | Estimación |
|---|---|---|---|
| F0 | Documentación, decisiones y estructura | [x] Hecho | 1–2 días |
| F1 | Esqueleto, entorno y CI | [x] | ~1 semana |
| F2 | Auth, usuarios y RBAC | [x] | ~1 semana |
| F3 | Inventario, lotes, kardex y PMP | [x] | ~2 semanas |
| F4 | Recetas, versiones y costes | [x] | ~2 semanas |
| F5 | Producción y descuento automático | [x] | ~1,5 semanas |
| F6 | Calidad y trazabilidad | [~] | ~1,5 semanas |
| F7 | Informes, endurecimiento y despliegue | [ ] | ~2 semanas |

Total orientativo MVP: **13–16 semanas** a tiempo parcial, una persona. Son estimaciones, no
compromisos.

## Definition of Done (aplica a toda tarea)

1. Requisito implementado según su criterio de aceptación.
2. Tests unitarios de dominio y de integración cuando haya persistencia.
3. `./mvnw verify` en verde.
4. Sin secretos, sin datos personales reales, sin `TODO` sin justificar.
5. Documentación afectada actualizada (requisitos si cambia el alcance; ADR si cambia una decisión).
6. `docs/plan.md` y `docs/memoria.md` actualizados.
7. Cambio listo para commit, agrupado con cambios relacionados.

---

## F0 — Documentación y decisiones

- [x] Analizar el proyecto y delimitar alcance
- [x] Definir requisitos con ID y criterio de aceptación
- [x] Decidir stack, persistencia y entorno
- [x] Crear documentación inicial (`AGENTS.md`, `README.md`, `docs/`)
- [x] Revisión del usuario
- [x] `git init` en `cocina/` y commits iniciales (los ejecuta el usuario): `5c25101`, `88259cb`
- [ ] Decidir política de retención de datos (PENDIENTE P2 — bloquea datos reales, no F1)

## F1 — Esqueleto, entorno y CI

Objetivo: una API que arranca, conecta a PostgreSQL y expone health + OpenAPI.

**Bloque A — sin Docker (hecho y verificado)**

- [x] `pom.xml` con Spring Boot 4.1.2, Java 25 y dependencias base
- [x] Maven Wrapper 3.3.4 + Maven 3.9.16 (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`), SHA-256 verificado
- [x] `application.yml` + perfiles `local`/`test`
- [x] `CocinaApplication`, Jackson (UTC y `BigDecimal`) y errores uniformes con `requestId` (PLA-02)
- [x] `/health` y `/ready`, con puerto `ReadinessIndicator` para las dependencias (PLA-04)
- [x] springdoc-openapi 3.2.0 (PLA-01)
- [x] Logs estructurados ECS con `requestId` en el MDC (NFR-OBS-01)
- [x] ArchUnit: reglas de frontera entre capas y módulos
- [x] `Dockerfile` multi-stage y `.dockerignore` — verificado con `docker build` y ejecución real
- [x] Workflow de CI con `./mvnw verify` — **sin ejecutar en remoto**: no hay remoto

**Bloque B — PostgreSQL, Flyway y Testcontainers (hecho y verificado)**

- [x] `compose.yaml` con PostgreSQL 18, volumen y healthcheck (host: 5433)
- [x] Dependencias de persistencia (JPA, `spring-boot-starter-flyway`, driver PostgreSQL) y
  `ddl-auto: validate`
- [x] Flyway: migración `V1__baseline.sql` (solo `establishments`)
- [x] `ReadinessIndicator` real que comprueba base de datos y migraciones
- [x] Testcontainers contra PostgreSQL 18 real
- [x] `Dockerfile` verificado: imagen, usuario no root y healthcheck `healthy`
- [ ] Ejecutar el workflow de CI en remoto (requiere publicar el repositorio)

**Riesgo de fase:** ninguno abierto. La única tarea sin validar es el workflow de CI, que necesita un
remoto Git; se comprobará al publicar el repositorio.

## F2 — Auth, usuarios y RBAC

- [x] Entidad `app_user` y `establishment` semilla (PER-01)
- [x] Argon2id y política de contraseñas (NFR-SEG-01)
- [x] Login, refresh rotatorio y logout (NFR-SEG-02)
- [x] Matriz de permisos por rol (PER-02, PER-03, PER-05)
- [x] `actor_id` en operaciones de negocio (PER-04)
- [x] Auditoría base reutilizable (CAL-06)
- [x] Tests: acceso denegado, token caducado, refresh reutilizado

## F3 — Inventario

**Bloque A — catálogo (hecho y verificado)**

- [x] Catálogo de productos y categorías (INV-01)
- [x] Unidades y conversiones con validación de compatibilidad (INV-02, INV-03)
- [x] Proveedores

**Bloque B — movimientos, lotes y PMP (hecho y verificado)**

- [x] Entradas con lote, caducidad y coste (INV-04, CAL-01)
- [x] Movimientos inmutables y saldo (INV-06)
- [x] PMP (INV-09)
- [x] Ajustes con motivo (INV-05)
- [x] Política de stock negativo (INV-08)
- [x] Alertas de mínimo y caducidad (INV-07, ECO-06)

**Bloque C — concurrencia (hecho y verificado)**

- [x] Movimientos simultáneos sobre el mismo producto sin perder actualizaciones (NFR-INT-01)
- [x] Salidas concurrentes sin cruzar el stock negativo (INV-08, NFR-INT-01)
- [x] Compras simultáneas con PMP correcto (INV-09)

**Fase cerrada.** Si el stock no es exacto aquí, todo lo posterior es decorativo.

## F4 — Recetas

**Bloque A — esquema y borradores (hecho y verificado)**

- [x] CRUD de recetas en borrador (REC-01)
- [x] Ingredientes, merma y conversión a unidad base (REC-02)
- [x] Un solo borrador por receta y borrador descartable (REC-05)

**Bloque B — coste y escalado (hecho y verificado)**

- [x] Cálculo de coste por ración y coste actual con el PMP vigente (REC-04)
- [x] Escalado a N raciones con redondeo determinista (REC-03)

**Bloque C — publicación e inmutabilidad (hecho y verificado)**

- [x] Publicación con congelado del coste y bloqueo ordenado de productos (REC-04, REC-05)
- [x] Versiones publicadas inmutables: API y triggers de BD (REC-05)
- [x] Clonado con ingredientes, historial y versión vigente (REC-05)
- [x] Consulta de coste histórico (ECO-01)
- [x] Tests de redondeo, merma e inmutabilidad

**Fase cerrada.**

## F5 — Producción

**Bloque A — esquema y planificación (PRD-01, PRD-02)**

- [x] Plan diario por fecha y servicio, sin duplicados (PRD-01)
- [x] Ítems sobre versiones publicadas y edición solo en `PENDING`
- [x] Necesidades agregadas por producto con un único redondeo (PRD-02)
- [x] Tests de plan, duplicados, versiones no publicadas y agregación

**Bloque B — ejecución y descuento transaccional (REC-06, PRD-03, PRD-04)**

- [x] Batch, máquina de estados y transiciones válidas (PRD-04)
- [x] Descuento atómico al pasar a `READY`, con productos bloqueados en orden
- [x] Cancelación sin descuento y corrección tras `READY` solo por ajuste
- [x] Fallo parcial sin rastro y concurrencia sin sobreventa
- [x] Lotes caducados fuera del consumo de producción

**Bloque C — desviaciones y consumo (PRD-05, ECO-03)**

- [x] Desviación previsto vs real con motivo obligatorio (PRD-05)
- [x] Consumo teórico vs real por producto (ECO-03)
- [x] Documentación: arquitectura, ADR-036 y ADR-037, memoria y AGENTS.md

**Fase cerrada.**

## F6 — Calidad y trazabilidad

- [x] Temperaturas con rangos y alertas (CAL-02)
- [ ] Mermas con coste (CAL-03)
- [ ] Bloqueo de lotes caducados (CAL-04)
- [ ] Incidencias (CAL-05)
- [ ] Trazabilidad lote → preparación (CAL-01)
- [ ] Auditoría completa de acciones sensibles (CAL-06)

## F7 — Informes, endurecimiento y despliegue

- [ ] Informe de costes y evolución (ECO-01)
- [ ] Productos en riesgo por caducidad (ECO-06)
- [ ] Revisión de rendimiento (NFR-REN-01/02)
- [ ] Backups documentados y restauración probada (NFR-DIS-02)
- [ ] Imagen Docker de producción y despliegue (PENDIENTE P1)
- [ ] Seed de datos demo (PLA-05)
- [ ] Revisión de seguridad y de datos personales (NFR-DAT-01)

## Fuera de este plan

INV-10/11, REC-07/08, PRD-06/07, CAL-07, ECO-02/04/05/08, REC-09, ECO-07, frontend, TPV y
multi-establecimiento activo. Se planificarán como v1 una vez validado el MVP.

## Estrategia de commits

- Un commit por cambio lógico completo y validado.
- Mensajes Conventional Commits en inglés.
- El usuario ejecuta `git add` y `git commit`. El agente no modifica el repositorio.
- Push cuando haya uno o varios commits validados y sea buen punto de respaldo.
