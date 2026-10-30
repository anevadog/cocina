# Plan de trabajo

Fases incrementales. Cada fase termina con un cambio lógico completo, validado y documentado.
Ninguna fase se inicia sin aprobación explícita de la anterior.

Leyenda: `[ ]` pendiente · `[~]` en curso · `[x]` hecho

## Progreso

| Fase | Contenido | Estado | Estimación |
|---|---|---|---|
| F0 | Documentación, decisiones y estructura | [~] En curso | 1–2 días |
| F1 | Esqueleto, entorno y CI | [ ] | ~1 semana |
| F2 | Auth, usuarios y RBAC | [ ] | ~1 semana |
| F3 | Inventario, lotes, kardex y PMP | [ ] | ~2 semanas |
| F4 | Recetas, versiones y costes | [ ] | ~2 semanas |
| F5 | Producción y descuento automático | [ ] | ~1,5 semanas |
| F6 | Calidad y trazabilidad | [ ] | ~1,5 semanas |
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
- [~] Crear documentación inicial (`AGENTS.md`, `README.md`, `docs/`)
- [ ] Revisión del usuario
- [ ] `git init` en `cocina/` y primer commit (lo ejecuta el usuario)
- [ ] Decidir política de retención de datos (PENDIENTE P2)

## F1 — Esqueleto, entorno y CI

Objetivo: una API que arranca, conecta a PostgreSQL y expone health + OpenAPI.

- [ ] `pom.xml` con Spring Boot 4.1.x, Java 25, dependencias base y plugins de calidad
- [ ] Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`)
- [ ] `compose.yaml` con PostgreSQL 18, volumen y healthcheck
- [ ] `application.yml` + perfiles `local`/`test`; `ddl-auto: validate`
- [ ] Flyway: migración `V1__baseline.sql` (solo tablas base necesarias)
- [ ] `CocinaApplication`, configuración de Jackson (UTC, BigDecimal) y errores uniformes (PLA-02)
- [ ] `/health` y `/ready` (PLA-04)
- [ ] springdoc-openapi (PLA-01)
- [ ] Logs estructurados con `requestId` (NFR-OBS-01)
- [ ] ArchUnit: reglas de dependencia entre módulos
- [ ] `Dockerfile` multi-stage y `.dockerignore`
- [ ] CI: `./mvnw verify` en GitHub Actions
- [ ] Testcontainers funcionando contra PostgreSQL real

**Riesgo de fase:** Docker/WSL2 aún no instalado en la máquina. Bloquea la parte de base de datos.

## F2 — Auth, usuarios y RBAC

- [ ] Entidad `app_user` y `establishment` semilla (PER-01)
- [ ] Argon2id y política de contraseñas (NFR-SEG-01)
- [ ] Login, refresh rotatorio y logout (NFR-SEG-02)
- [ ] Matriz de permisos por rol (PER-02, PER-03, PER-05)
- [ ] `actor_id` en operaciones de negocio (PER-04)
- [ ] Auditoría base reutilizable (CAL-06)
- [ ] Tests: acceso denegado, token caducado, refresh reutilizado

## F3 — Inventario

- [ ] Catálogo de productos y categorías (INV-01)
- [ ] Unidades y conversiones con validación de compatibilidad (INV-02, INV-03)
- [ ] Proveedores
- [ ] Entradas con lote, caducidad y coste (INV-04, CAL-01)
- [ ] Movimientos inmutables y saldo (INV-06)
- [ ] PMP (INV-09)
- [ ] Ajustes con motivo (INV-05)
- [ ] Política de stock negativo (INV-08)
- [ ] Alertas de mínimo y caducidad (INV-07, ECO-06)
- [ ] Tests de concurrencia: dos descuentos simultáneos (NFR-INT-01)

**Fase crítica.** Si el stock no es exacto aquí, todo lo posterior es decorativo.

## F4 — Recetas

- [ ] CRUD de recetas en borrador (REC-01)
- [ ] Ingredientes, merma y escalado (REC-02, REC-03)
- [ ] Cálculo de coste por ración y congelado al publicar (REC-04)
- [ ] Publicación y versionado inmutable (REC-05)
- [ ] Consulta de coste histórico (ECO-01)
- [ ] Tests de redondeo y de inmutabilidad

## F5 — Producción

- [ ] Plan diario por servicio (PRD-01)
- [ ] Necesidades agregadas (PRD-02)
- [ ] Registro de producción con descuento transaccional (REC-06, PRD-03)
- [ ] Máquina de estados (PRD-04)
- [ ] Desviaciones con motivo (PRD-05)
- [ ] Consumo teórico vs real (ECO-03)
- [ ] Test: producción cancelada no descuenta; fallo parcial no deja rastro

## F6 — Calidad y trazabilidad

- [ ] Temperaturas con rangos y alertas (CAL-02)
- [ ] Mermas con coste (CAL-03)
- [ ] Bloqueo de lotes caducados (CAL-04)
- [ ] Incidencias (CAL-05)
- [ ] Trazabilidad lote → preparación (CAL-01)
- [ ] Auditoría completa de acciones sensibles (CAL-06)

## F7 — Informes, endurecimiento y despliegue

- [ ] Informe de costes y evolución (ECO-01, ECO-03)
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
