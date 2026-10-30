# Cocina — Sistema de gestión y control de cocina profesional

API REST para estandarizar y controlar la operación de la cocina de un restaurante: inventario con
lotes y caducidades, recetas técnicas versionadas con coste real, producción diaria con descuento
automático de stock, trazabilidad y permisos por puesto.

**Estado:** fase 0 (documentación). Todavía no hay código de aplicación. Ver `docs/memoria.md`.

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
| Docker Desktop + WSL2 | actual | **PENDIENTE de instalar** |
| Git | cualquiera reciente | Instalado |
| Maven | no hace falta | Se usa `./mvnw` (wrapper) |

No se necesita instalar Maven ni PostgreSQL de forma nativa: Maven se resuelve con el wrapper y
PostgreSQL corre en un contenedor.

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

## Puesta en marcha (cuando exista la Fase 1)

```bash
cp .env.example .env          # ajustar valores locales
docker compose up -d          # PostgreSQL
./mvnw spring-boot:run        # API en http://localhost:8080
```

- API: `http://localhost:8080/api/v1`
- OpenAPI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/health`

## Comandos habituales

```bash
./mvnw verify            # compilar, tests y comprobaciones de calidad
./mvnw test              # solo tests
./mvnw spring-boot:run   # arrancar en local
docker compose ps        # estado de los contenedores
docker compose logs -f db
```

En Windows con Git Bash, `./mvnw`; en CMD/PowerShell, `mvnw.cmd`.

## Estructura del repositorio

```text
cocina/
├─ AGENTS.md  CLAUDE.md  README.md
├─ docs/                    # requisitos, arquitectura, decisiones, plan, memoria
├─ src/main/java/com/killa/cocina/
│  ├─ config/               # seguridad, OpenAPI, Jackson, auditoría
│  ├─ common/               # errores, paginación, utilidades
│  └─ modules/              # un paquete por dominio
├─ src/main/resources/
│  ├─ application.yml
│  └─ db/migration/         # Flyway
├─ src/test/java/com/killa/cocina/
├─ compose.yaml             # PostgreSQL para desarrollo
└─ Dockerfile               # imagen de la aplicación (despliegue)
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
