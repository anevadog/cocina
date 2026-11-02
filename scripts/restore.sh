#!/usr/bin/env sh
# Restauración de una copia de seguridad lógica (NFR-DIS-02).
#
# Uso:
#   sh scripts/restore.sh backups/cocina-cocina-20261102T120000Z.dump
#   sh scripts/restore.sh backups/... cocina_otra
#
# Por seguridad:
#   - restaura en una base NUEVA (por defecto cocina_restore), nunca sobre la de trabajo;
#   - si la base destino ya existe, hay que confirmar con RESTORE_FORCE=yes, y aun así
#     se borra y se vuelve a crear: la restauración es completa, no incremental.
#
# Al terminar imprime el recuento de las tablas principales para compararlo con el
# origen (misma consulta en docs/backups.md).

set -eu

# Git Bash (MSYS) reescribe rutas como /tmp/x a rutas de Windows antes de pasarlas a docker.
# Estas variables desactivan esa conversión; en Linux/macOS no tienen efecto.
MSYS_NO_PATHCONV=1
MSYS2_ARG_CONV_EXCL='*'
export MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL

DUMP_FILE="${1:-}"
TARGET_DB="${2:-cocina_restore}"
SERVICE="${COMPOSE_SERVICE:-db}"
DB_USER="${POSTGRES_USER:-cocina}"
ADMIN_DB="${POSTGRES_ADMIN_DB:-postgres}"

if [ -z "$DUMP_FILE" ] || [ ! -f "$DUMP_FILE" ]; then
  echo "Uso: sh scripts/restore.sh <archivo.dump> [base_destino]" >&2
  exit 2
fi

case "$TARGET_DB" in
  (*[!a-z0-9_]*|'') echo "ERROR: nombre de base no válido: $TARGET_DB" >&2; exit 2 ;;
esac

if [ "$TARGET_DB" = "${POSTGRES_DB:-cocina}" ]; then
  echo "ERROR: la restauración no puede apuntar a la base de trabajo (${TARGET_DB})." >&2
  exit 2
fi

exists="$(docker compose exec -T "$SERVICE" psql -U "$DB_USER" -d "$ADMIN_DB" -tAc \
  "select 1 from pg_database where datname = '${TARGET_DB}'")"
if [ "$exists" = "1" ]; then
  if [ "${RESTORE_FORCE:-no}" != "yes" ]; then
    echo "ERROR: la base ${TARGET_DB} ya existe. Borra la base o usa RESTORE_FORCE=yes." >&2
    exit 2
  fi
  docker compose exec -T "$SERVICE" psql -U "$DB_USER" -d "$ADMIN_DB" -c \
    "drop database \"${TARGET_DB}\" with (force)"
fi

docker compose exec -T "$SERVICE" psql -U "$DB_USER" -d "$ADMIN_DB" -c \
  "create database \"${TARGET_DB}\""

docker compose cp "$DUMP_FILE" "${SERVICE}:/tmp/restore.dump"
# --no-owner: los roles del cluster de destino pueden no coincidir con los de origen.
docker compose exec -T "$SERVICE" pg_restore -U "$DB_USER" -d "$TARGET_DB" \
  --no-owner --clean --if-exists /tmp/restore.dump
docker compose exec -T "$SERVICE" rm -f /tmp/restore.dump

echo
echo "Restauración completada en ${TARGET_DB}. Recuentos:"
docker compose exec -T "$SERVICE" psql -U "$DB_USER" -d "$TARGET_DB" -c "
  select 'products' as tabla, count(*) from products
  union all select 'stock_lots', count(*) from stock_lots
  union all select 'stock_movements', count(*) from stock_movements
  union all select 'recipes', count(*) from recipes
  union all select 'recipe_versions', count(*) from recipe_versions
  union all select 'production_plans', count(*) from production_plans
  union all select 'temperature_logs', count(*) from temperature_logs
  union all select 'waste_records', count(*) from waste_records
  union all select 'incidents', count(*) from incidents
  union all select 'audit_logs', count(*) from audit_logs
  order by tabla;"
