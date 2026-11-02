#!/usr/bin/env sh
# Reinicia la demo pública: borra la base ficticia y vuelve a sembrarla.
#
# Uso: sh scripts/demo-public-reset.sh
# Recomendado por cron, una vez al día:
#   0 4 * * * cd /opt/cocina && sh scripts/demo-public-reset.sh >> /var/log/cocina-demo-reset.log 2>&1
#
# No toca la base de desarrollo ni la de producción: solo el volumen de compose.demo.yaml.

set -eu

cd "$(dirname "$0")/.."

COMPOSE="docker compose -f compose.demo.yaml --env-file .env.demo"
VOLUME="cocina-demo-db-data"

echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Reiniciando demo pública"

$COMPOSE stop api
$COMPOSE rm -sf db
docker volume rm "$VOLUME" >/dev/null 2>&1 || true
$COMPOSE up -d

# Espera a que la API esté lista (el seed corre en el arranque).
i=0
while [ "$i" -lt 120 ]; do
    if docker exec cocina-demo-api wget -q -O - http://127.0.0.1:8080/ready >/dev/null 2>&1; then
        echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Demo lista"
        exit 0
    fi
    i=$((i + 1))
    sleep 1
done

echo "ERROR: la demo no respondió en /ready en 120 s" >&2
exit 1
