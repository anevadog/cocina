# Cocina — Sistema de gestión y control de cocina profesional

API REST para estandarizar y controlar la operación de la cocina de un restaurante: inventario con
lotes y caducidades, recetas técnicas versionadas con coste real, producción diaria con descuento
automático de stock, trazabilidad y permisos por puesto.

**Estado:** F1 cerrada. La API arranca, aplica migraciones en PostgreSQL 18 y expone `/health`,
`/ready` y OpenAPI. Ver `docs/memoria.md`.

## Documentación

| Documento | Contenido |
|---|---|
| `AGENTS.md` | Contexto, reglas y convenciones. **Empieza aquí** |
| `docs/requisitos.md` | Requisitos funcionales y no funcionales con criterios de aceptación |
| `docs/arquitectura.md` | Módulos, modelo de datos y flujos críticos |
| `docs/decisiones.md` | Registro de decisiones técnicas (ADR) |
| `docs/plan.md` | Fases, tareas y progreso |
| `docs/memoria.md` | Estado actual y punto de continuación |

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

## Comandos habituales

```bash
./mvnw verify            # compilar, tests (Testcontainers) y verificación de arquitectura
./mvnw test              # solo tests — necesita Docker en marcha
./mvnw spring-boot:run   # arrancar en local
./mvnw package           # generar el jar ejecutable en target/
docker compose ps        # estado de los contenedores
docker compose logs -f db
docker compose down      # parar PostgreSQL (añade -v para borrar los datos)
docker build -t cocina:local .   # imagen de la aplicación
```

En Windows con Git Bash, `./mvnw`; en CMD/PowerShell, `mvnw.cmd`.

## Estructura del repositorio

```text
cocina/
├─ AGENTS.md  CLAUDE.md  README.md
├─ docs/                        # requisitos, arquitectura, decisiones, plan, memoria
├─ .mvn/wrapper/                # configuración del Maven Wrapper (Maven 3.9.16)
├─ .github/workflows/ci.yml     # ./mvnw verify en GitHub Actions
├─ mvnw  mvnw.cmd  pom.xml
├─ src/main/java/com/killa/cocina/
│  ├─ CocinaApplication.java
│  ├─ config/                   # reloj UTC y metadatos OpenAPI
│  ├─ shared/
│  │  ├─ error/                 # cuerpo de error uniforme (PLA-02)
│  │  ├─ health/                # /health y /ready (PLA-04)
│  │  └─ web/                   # requestId (NFR-OBS-01)
│  └─ modules/                  # un paquete por dominio (aún vacío)
├─ src/main/resources/
│  ├─ application.yml  application-local.yml  application-test.yml
│  └─ db/migration/V1__baseline.sql   # Flyway: tabla establishments
├─ src/test/java/com/killa/cocina/
│  ├─ architecture/             # reglas ArchUnit de fronteras entre módulos
│  ├─ infrastructure/           # migraciones sobre PostgreSQL real
│  └─ support/                  # contenedor PostgreSQL compartido (Testcontainers)
├─ compose.yaml                 # PostgreSQL 18 para desarrollo (host: 5433)
└─ Dockerfile                   # imagen multi-stage, usuario no root, healthcheck
```

## Variables de entorno

Ver `.env.example`. Nunca se commitea `.env` ni secretos reales.

## Convenciones

- Código en inglés; documentación y mensajes de API en español.
- Conventional Commits.
- Migraciones Flyway; `ddl-auto: validate`.
- Cantidades y dinero con `BigDecimal` (`numeric(12,3)` / `numeric(12,4)`).

## Licencia

PENDIENTE de decidir. No se distribuye todavía.
