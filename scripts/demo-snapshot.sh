#!/usr/bin/env sh
# Instantánea de datos ficticios para el recorrido visual (demo/recorrido/).
#
# Uso (con la demo de scripts/demo.sh en marcha):
#   sh scripts/demo-snapshot.sh            # escribe demo/recorrido/data.js
#
# Solo hace lecturas (GET) contra la API de la demo y guarda las respuestas tal cual. No guarda
# tokens, contraseñas ni el listado de usuarios: de la comprobación de permisos solo se conservan
# los códigos HTTP y el cuerpo de error. Requisitos: curl y Git Bash/WSL. No usa jq ni Python.

set -eu

MSYS_NO_PATHCONV=1
MSYS2_ARG_CONV_EXCL='*'
export MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL

API_PORT="${DEMO_API_PORT:-8081}"
BASE="http://localhost:${API_PORT}"
PASSWORD="${DEMO_PASSWORD:-Demo2026!segura}"
OUT="${1:-demo/recorrido/data.js}"

fail() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

curl -fsS "${BASE}/ready" >/dev/null 2>&1 || fail "La API de la demo no responde en ${BASE}. Ejecuta antes: sh scripts/demo.sh"

login() {
    curl -sS -X POST "${BASE}/api/v1/auth/login" -H "Content-Type: application/json" \
        -d "$(printf '{"username":"%s","password":"%s"}' "$1" "$PASSWORD")" \
        | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

TOKEN="$(login demo.admin)"
COOK_TOKEN="$(login demo.cook)"
[ -n "$TOKEN" ] && [ -n "$COOK_TOKEN" ] || fail "No se pudo iniciar sesión con los usuarios demo."

api() {
    body="$(curl -sS "${BASE}$1" -H "Authorization: Bearer ${TOKEN}")"
    case "$body" in
        '{"code":'*) fail "GET $1 devolvió un error: $body" ;;
    esac
    printf '%s' "$body"
}

first_id() {
    sed -n 's/.*"content":\[{"id":\([0-9]*\).*/\1/p'
}

product_id() {
    printf '%s' "$PRODUCTS" | grep -o "\"id\":[0-9]*,\"categoryId\":[0-9]*,\"name\":\"$1\"" | head -n 1 \
        | sed 's/.*"id":\([0-9]*\).*/\1/'
}

PRODUCTS="$(api '/api/v1/products?size=100')"
PATATA_ID="$(product_id Patata)"
TOMATE_ID="$(product_id Tomate)"
SERVICE_DATE="$(TZ=Europe/Madrid date -d tomorrow +%F)"
PLAN_ID="$(api "/api/v1/production-plans?serviceDate=${SERVICE_DATE}" | first_id)"
SALSA_ID="$(api '/api/v1/recipes?name=Salsa%20de%20tomate' | first_id)"
TORTILLA_ID="$(api '/api/v1/recipes?name=Tortilla%20de%20patata' | first_id)"
[ -n "$PATATA_ID" ] && [ -n "$TOMATE_ID" ] && [ -n "$PLAN_ID" ] && [ -n "$SALSA_ID" ] && [ -n "$TORTILLA_ID" ] \
    || fail "Faltan datos del seed o del recorrido; ejecuta sh scripts/demo.sh completo."

SALSA_VERSIONS="$(api "/api/v1/recipes/${SALSA_ID}/versions")"
SALSA_V1="$(printf '%s' "$SALSA_VERSIONS" | sed -n 's/.*{"id":\([0-9]*\),"versionNumber":1,.*/\1/p')"
SALSA_V2="$(printf '%s' "$SALSA_VERSIONS" | sed -n 's/.*{"id":\([0-9]*\),"versionNumber":2,.*/\1/p')"
[ -n "$SALSA_V2" ] || fail "No hay versión 2 de la salsa; usa la versión actual de scripts/demo.sh."
TORTILLA_V1="$(api "/api/v1/recipes/${TORTILLA_ID}/versions" | sed -n 's/^\[{"id":\([0-9]*\).*/\1/p')"
PATATA_LOT="$(api "/api/v1/products/${PATATA_ID}/lots" | first_id)"

status_of() {
    # Sin -o /dev/null: el curl nativo de Windows no conoce esa ruta en Git Bash.
    curl -sS -w '\n%{http_code}' "${BASE}/api/v1/users" ${1:+-H "Authorization: Bearer $1"} | tail -n 1
}

body_of() {
    curl -sS "${BASE}/api/v1/users" ${1:+-H "Authorization: Bearer $1"}
}

mkdir -p "$(dirname "$OUT")"
{
    printf '// Generado por scripts/demo-snapshot.sh. Datos ficticios; no editar a mano.\n'
    printf 'window.SNAPSHOT = {\n'
    printf '"capturedAt":"%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '"products":%s,\n' "$PRODUCTS"
    printf '"alerts":%s,\n' "$(api /api/v1/inventory/alerts)"
    printf '"recipeCosts":%s,\n' "$(api /api/v1/reports/recipe-costs)"
    printf '"expiring":%s,\n' "$(api '/api/v1/reports/expiring-lots?days=30')"
    printf '"plan":%s,\n' "$(api "/api/v1/production-plans/${PLAN_ID}")"
    printf '"requirements":%s,\n' "$(api "/api/v1/production-plans/${PLAN_ID}/requirements")"
    printf '"patataLots":%s,\n' "$(api "/api/v1/products/${PATATA_ID}/lots")"
    printf '"patataMovements":%s,\n' "$(api "/api/v1/products/${PATATA_ID}/movements?size=100")"
    printf '"tomateLots":%s,\n' "$(api "/api/v1/products/${TOMATE_ID}/lots")"
    printf '"tomateMovements":%s,\n' "$(api "/api/v1/products/${TOMATE_ID}/movements?size=100")"
    printf '"salsaV1":%s,\n' "$(api "/api/v1/recipes/${SALSA_ID}/versions/${SALSA_V1}")"
    printf '"salsaV2":%s,\n' "$(api "/api/v1/recipes/${SALSA_ID}/versions/${SALSA_V2}")"
    printf '"tortillaV1":%s,\n' "$(api "/api/v1/recipes/${TORTILLA_ID}/versions/${TORTILLA_V1}")"
    printf '"patataTraceability":%s,\n' "$(api "/api/v1/stock-lots/${PATATA_LOT}/traceability")"
    printf '"temperatures":%s,\n' "$(api /api/v1/temperature-logs)"
    printf '"waste":%s,\n' "$(api /api/v1/waste-records)"
    printf '"incidents":%s,\n' "$(api /api/v1/incidents)"
    printf '"auth":{"noToken":{"status":%s,"body":%s},' "$(status_of '')" "$(body_of '')"
    printf '"cook":{"status":%s,"body":%s},' "$(status_of "$COOK_TOKEN")" "$(body_of "$COOK_TOKEN")"
    printf '"admin":{"status":%s}}\n' "$(status_of "$TOKEN")"
    printf '};\n'
} >"$OUT"

echo "Instantánea escrita en ${OUT} ($(wc -c <"$OUT" | tr -d ' ') bytes)."
