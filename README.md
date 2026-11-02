# Cocina — Sistema de gestión y control de cocina profesional

API REST para estandarizar y controlar la operación de la cocina de un restaurante: inventario con
lotes y caducidades, recetas técnicas versionadas con coste real, producción diaria con descuento
automático de stock, trazabilidad y permisos por puesto.

**Estado:** F7 cerrada (bloques A, B y C). La API arranca, aplica migraciones V1–V7 en PostgreSQL 18,
expone `/health`, `/ready` y OpenAPI, e incluye autenticación JWT con refresh rotatorio, RBAC por rol,
auditoría append-only protegida por trigger, inventario con lotes y PMP, recetas versionadas con coste,
producción con descuento atómico, temperaturas con alertas, mermas, incidencias, trazabilidad
lote → preparación, informes de costes y caducidades, seed de datos demo, copias de seguridad con
restauración probada, una medición de rendimiento repetible y una pila de producción con
`compose.prod.yaml` y Caddy. **245 tests** en verde más una comprobación de rendimiento opt-in. Queda
P2 (base legal y retención) antes de datos reales de empleados. Ver `docs/memoria.md`.

## Documentación

| Documento | Contenido |
|---|---|
| `AGENTS.md` | Contexto, reglas y convenciones. **Empieza aquí** |
| `docs/requisitos.md` | Requisitos funcionales y no funcionales con criterios de aceptación |
| `docs/arquitectura.md` | Módulos, modelo de datos y flujos críticos |
| `docs/decisiones.md` | Registro de decisiones técnicas (ADR) |
| `docs/plan.md` | Fases, tareas y progreso |
| `docs/memoria.md` | Estado actual y punto de continuación |
| `docs/backups.md` | Copias de seguridad, restauración y prueba registrada |
| `docs/rendimiento.md` | Método y resultados de NFR-REN-01/02 |
| `docs/despliegue.md` | VPS, TLS, primer despliegue, actualización, backups y operación |
| `docs/seguridad.md` | Endurecimiento, gestión de secretos y datos personales |

## Requisitos del entorno

| Herramienta | Versión | Estado en la máquina de desarrollo |
|---|---|---|
| JDK (Temurin) | 25 LTS | Instalado |
| Docker Desktop + WSL2 | Docker 29.9.2, Compose v5.6.1, WSL 3.0.1.0 | Instalado y verificado |
| Git | cualquiera reciente | Instalado |
| Maven | no hace falta | Lo gestiona el wrapper (3.9.16) |

No se necesita instalar Maven ni PostgreSQL de forma nativa: Maven se resuelve con el wrapper y
PostgreSQL 18 corre en un contenedor.

> **Puerto:** el contenedor se publica en **5433** para no chocar con un PostgreSQL nativo que ya
> escuche en 5432. Se puede cambiar con `DB_PORT` en `.env`.

### Instalar Docker (Windows, PowerShell como administrador)

```powershell
wsl --install --no-distribution
winget install --id Docker.DockerDesktop -e --accept-package-agreements --accept-source-agreements
```

Reinicia si se solicita y verifica:

```bash
docker --version
docker compose version
```

## Puesta en marcha

```bash
cp .env.example .env          # opcional: valores locales; sin .env se usan los del compose
docker compose up -d          # PostgreSQL 18 en localhost:5433
./mvnw spring-boot:run        # API en http://localhost:8080 (aplica las migraciones al arrancar)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # con logs DEBUG
```

- Health: `http://localhost:8080/health` (proceso, sin tocar la base de datos)
- Readiness: `http://localhost:8080/ready` (base de datos y migraciones; 503 si algo está caído)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Si el 5433 también estuviera ocupado, cambia `DB_PORT` en `.env` y vuelve a levantar el compose.

### Primer administrador y arranque seguro

- En el perfil `local` se crea `admin` con la contraseña de desarrollo `cambiar_en_local` (ver
  `application-local.yml`), solo si la tabla de usuarios está vacía.
- En cualquier otro perfil la API **no arranca** sin `JWT_SECRET` de al menos 32 bytes, y no crea
  ningún usuario salvo que definas `BOOTSTRAP_ADMIN_PASSWORD`. No hay credenciales por defecto.

```bash
# token de acceso
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"cambiar_en_local"}'

# uso del token
curl -s http://localhost:8080/api/v1/users -H "Authorization: Bearer <accessToken>"
```

### Datos de demostración

Con una base **vacía** (por ejemplo, tras `docker compose down -v`), el perfil `demo` crea usuarios,
catálogo, recetas publicadas, un plan de producción, temperaturas, una merma y una incidencia:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo
# o sobre el jar: java -jar target/cocina-0.1.0-SNAPSHOT.jar --spring.profiles.active=local,demo
```

- Usuarios: `demo.admin`, `demo.manager`, `demo.cook` y `demo.store`.
- Contraseña: `Demo2026!segura` por defecto; se cambia con `cocina.demo.password`
  (`COCINA_DEMO_PASSWORD` en el entorno) y nunca se escribe en el log.
- El seed es idempotente: si ya hay productos, no hace nada. No se ejecuta sin el perfil `demo`.

## Despliegue en producción

La pila de producción es un VPS con `compose.prod.yaml`: PostgreSQL, la API y **Caddy** como proxy con
TLS automático. La base de datos no publica puertos y la API corre sin privilegios y con el sistema de
ficheros de solo lectura. Decisión y consecuencias en ADR-045; procedimiento completo en
`docs/despliegue.md`.

```bash
cp .env.production.example .env    # completar dominio, correo y secretos; chmod 600
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps
curl -fsS https://<dominio>/ready
```

- La documentación OpenAPI/Swagger está apagada en producción (`API_DOCS_ENABLED=false`).
- La copia diaria se programa con `cron` y se replica fuera del VPS con
  `scripts/backup-offsite.sh` (rclone). Ver `docs/despliegue.md` §8 y `docs/backups.md`.
- Antes de datos reales de empleados hay que cerrar **P2** (base legal y retención); ver
  `docs/seguridad.md`.

## Comandos habituales

```bash
./mvnw verify            # compilar, tests (Testcontainers), formato, arquitectura y cobertura
./mvnw test              # solo tests — necesita Docker en marcha
./mvnw spotless:apply    # formatear el código (Spotless + palantir-java-format)
./mvnw spring-boot:run   # arrancar en local
./mvnw package           # generar el jar ejecutable en target/
./mvnw -Dtest=PerformanceBaselineCheck -Dcocina.performance=true test   # medición de rendimiento
sh scripts/backup.sh     # copia lógica en backups/ (Git Bash o WSL)
sh scripts/restore.sh backups/<archivo>.dump cocina_restore             # restaurar en base nueva
sh scripts/check-secrets.sh                                             # NFR-SEG-04 (también en CI)
docker compose ps        # estado de los contenedores
docker compose logs -f db
docker compose down      # parar PostgreSQL (añade -v para borrar los datos)
docker build -t cocina:local .   # imagen de la aplicación
```

En Windows con Git Bash, `./mvnw`; en CMD/PowerShell, `mvnw.cmd`.

El informe de cobertura queda en `target/site/jacoco/index.html`; `verify` no impone un umbral
(ADR-032). Si `spotless:check` falla, ejecuta `./mvnw spotless:apply` y revisa el diff.

## Estructura del repositorio

```text
cocina/
├─ AGENTS.md  CLAUDE.md  README.md
├─ docs/                        # requisitos, arquitectura, decisiones, plan, memoria, backups, rendimiento, despliegue y seguridad
├─ scripts/                     # backup.sh, restore.sh, backup-offsite.sh y check-secrets.sh
├─ .mvn/wrapper/                # configuración del Maven Wrapper (Maven 3.9.16)
├─ .github/workflows/ci.yml     # check-secrets + ./mvnw verify en GitHub Actions
├─ mvnw  mvnw.cmd  pom.xml
├─ src/main/java/com/killa/cocina/
│  ├─ CocinaApplication.java
│  ├─ config/                   # reloj UTC, seguridad (JWT), propiedades, bootstrap del admin
│  ├─ demo/                     # seed de datos de ejemplo (perfil demo, PLA-05)
│  ├─ shared/
│  │  ├─ error/                 # cuerpo de error uniforme (PLA-02)
│  │  ├─ health/                # /health y /ready (PLA-04)
│  │  ├─ pagination/            # página independiente de Spring (PLA-03)
│  │  ├─ security/              # actor autenticado leído del token (PER-04)
│  │  └─ web/                   # requestId (NFR-OBS-01)
│  └─ modules/                  # auth, users, audit, inventory, suppliers, recipes, production, quality, reporting
├─ src/main/resources/
│  ├─ application.yml  application-local.yml  application-test.yml  application-prod.yml
│  └─ db/migration/
│     ├─ V1__baseline.sql             # establishments
│     ├─ V2__users_auth_audit.sql     # app_users, refresh_tokens, audit_logs
│     ├─ V3__inventory_catalog.sql    # categorías, productos, conversiones y proveedores
│     ├─ V4__inventory_movements.sql  # lotes, kardex inmutable y trigger
│     ├─ V5__recipes.sql              # recetas, versiones y composición congelada
│     ├─ V6__production.sql           # planes, ítems y batches
│     └─ V7__quality.sql              # temperaturas, mermas, incidencias y auditoría protegida
├─ src/test/java/com/killa/cocina/
│  ├─ architecture/             # ArchUnit: fronteras y permisos por endpoint
│  ├─ infrastructure/           # migraciones sobre PostgreSQL real
│  ├─ modules/                  # dominio puro e integración de auth, users e inventory
│  └─ support/                  # contenedor PostgreSQL compartido (Testcontainers)
├─ compose.yaml                 # PostgreSQL 18 para desarrollo (host: 5433)
├─ compose.prod.yaml            # pila de producción: PostgreSQL, API y Caddy
├─ Caddyfile                    # TLS automático y cabeceras de seguridad
├─ Dockerfile                   # imagen multi-stage, usuario no root, healthcheck
└─ .env.example  .env.production.example   # plantillas; el .env real nunca se commitea
```

## Variables de entorno

Ver `.env.example` para desarrollo y `.env.production.example` para producción. Nunca se commitea
`.env` ni secretos reales; `scripts/check-secrets.sh` lo comprueba en CI.

## Convenciones

- Código en inglés; documentación y mensajes de API en español.
- Conventional Commits.
- Formato con Spotless (`./mvnw spotless:apply`); `verify` lo comprueba (ADR-032).
- Migraciones Flyway; `ddl-auto: validate`.
- Cantidades y dinero con `BigDecimal` (`numeric(12,3)` / `numeric(12,4)`).

## Licencia

PENDIENTE de decidir. No se distribuye todavía.
