#!/usr/bin/env sh
# Copia de seguridad lógica de PostgreSQL (NFR-DIS-02).
#
# Uso:
#   sh scripts/backup.sh                 # vuelca la base POSTGRES_DB (cocina)
#   POSTGRES_DB=cocina_demo sh scripts/backup.sh
#
# Requisitos: Docker en marcha, contenedor del compose y Git Bash/WSL (no PowerShell:
# la redirección de PowerShell corrompe los binarios).
#
# En producción, exportar COMPOSE_FILE=compose.prod.yaml: por defecto `docker compose` usa
# `compose.yaml`, que es la pila de desarrollo y no tiene el contenedor `cocina-db`.
#
# El volcado se hace dentro del contenedor y se copia al host con `docker compose cp`
# para que el flujo binario nunca dependa de la shell.

set -eu

# Git Bash (MSYS) reescribe rutas como /tmp/x a rutas de Windows antes de pasarlas a docker.
# Estas variables desactivan esa conversión; en Linux/macOS no tienen efecto.
MSYS_NO_PATHCONV=1
MSYS2_ARG_CONV_EXCL='*'
export MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL

# `docker compose` lee COMPOSE_FILE del entorno. Se fija solo si no venía ya definido.
COMPOSE_FILE="${COMPOSE_FILE:-compose.yaml}"
export COMPOSE_FILE

SERVICE="${COMPOSE_SERVICE:-db}"
DB_NAME="${POSTGRES_DB:-cocina}"
DB_USER="${POSTGRES_USER:-cocina}"
BACKUP_DIR="${BACKUP_DIR:-backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="cocina-${DB_NAME}-${STAMP}.dump"
CONTAINER_FILE="/tmp/${FILE}"

mkdir -p "$BACKUP_DIR"

# -Fc: formato custom, comprimido y restaurable con pg_restore de forma selectiva.
docker compose exec -T "$SERVICE" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --file="$CONTAINER_FILE"
docker compose cp "${SERVICE}:${CONTAINER_FILE}" "${BACKUP_DIR}/${FILE}"
docker compose exec -T "$SERVICE" rm -f "$CONTAINER_FILE"

if [ ! -s "${BACKUP_DIR}/${FILE}" ]; then
  echo "ERROR: el volcado está vacío: ${BACKUP_DIR}/${FILE}" >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && sha256sum "$FILE" > "${FILE}.sha256")
fi

# Retención: se borran los volcados (y sus sumas) más antiguos que N días.
find "$BACKUP_DIR" -name 'cocina-*.dump' -mtime "+${RETENTION_DAYS}" -delete
find "$BACKUP_DIR" -name 'cocina-*.dump.sha256' -mtime "+${RETENTION_DAYS}" -delete

echo "Backup creado: ${BACKUP_DIR}/${FILE}"
ls -lh "${BACKUP_DIR}/${FILE}"
