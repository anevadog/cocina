#!/usr/bin/env sh
# Demostración reproducible de la API (PLA-05).
#
# Uso:
#   sh scripts/demo.sh          # arranca base demo, API y ejecuta el recorrido
#   sh scripts/demo.sh --stop   # para la API y borra la base de demostración
#
# No toca la base de desarrollo (`cocina`): usa un contenedor propio (`cocina-demo-db`) con
# la base `cocina_demo` en el puerto 55432 y la API en el 8081. Al terminar deja la API en
# marcha para poder seguir mostrando Swagger UI:
#
#   http://localhost:8081/swagger-ui.html
#
# Requisitos: Docker en marcha, JDK 25 y Git Bash/WSL. No usa jq ni Python.

set -eu

# Git Bash (MSYS) reescribe rutas; en Linux/macOS estas variables no tienen efecto.
MSYS_NO_PATHCONV=1
MSYS2_ARG_CONV_EXCL='*'
export MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL

DB_CONTAINER="${DEMO_DB_CONTAINER:-cocina-demo-db}"
DB_PORT="${DEMO_DB_PORT:-55432}"
API_PORT="${DEMO_API_PORT:-8081}"
BASE="http://localhost:${API_PORT}"
STATE_DIR=".demo"
PID_FILE="${STATE_DIR}/api.pid"
LOG_FILE="${STATE_DIR}/api.log"
DB_NAME="cocina_demo"
DB_USER="cocina"
DB_PASSWORD="demo"
ADMIN_USER="demo.admin"
ADMIN_PASSWORD="${DEMO_PASSWORD:-Demo2026!segura}"

say() {
    printf '\n\033[1m%s\033[0m\n' "$1"
}

note() {
    printf '  %s\n' "$1"
}

fail() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

stop_demo() {
    if [ -f "$PID_FILE" ]; then
        pid="$(cat "$PID_FILE")"
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null || true
        fi
        rm -f "$PID_FILE"
    fi
    docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
    echo "Demostración parada y base ${DB_NAME} eliminada."
}

if [ "${1:-}" = "--stop" ]; then
    stop_demo
    exit 0
fi

command -v docker >/dev/null 2>&1 || fail "Docker no está disponible en el PATH."
command -v curl >/dev/null 2>&1 || fail "curl no está disponible."
command -v java >/dev/null 2>&1 || fail "Java no está disponible; hace falta JDK 25."

mkdir -p "$STATE_DIR"

# Si quedó algo de una ejecución anterior, se limpia: los datos del demo son desechables.
if [ -f "$PID_FILE" ]; then
    note "Parando la API de una ejecución anterior…"
    stop_demo >/dev/null
fi

say "1/9 · Preparando PostgreSQL de demostración (${DB_NAME} en :${DB_PORT})"
docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$DB_CONTAINER" \
    -e POSTGRES_DB="$DB_NAME" \
    -e POSTGRES_USER="$DB_USER" \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -e TZ=UTC -e PGTZ=UTC \
    -p "127.0.0.1:${DB_PORT}:5432" \
    postgres:18 >/dev/null

i=0
while [ "$i" -lt 60 ]; do
    if docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
        break
    fi
    i=$((i + 1))
    sleep 1
done
[ "$i" -lt 60 ] || fail "PostgreSQL no arrancó en 60 s."
note "Base lista. No se usa la base de desarrollo."

say "2/9 · Arrancando la API con el perfil local,demo en :${API_PORT}"
JAR="$(ls target/cocina-*.jar 2>/dev/null | head -n 1 || true)"
if [ -z "$JAR" ]; then
    note "No hay jar compilado; ejecutando ./mvnw -DskipTests package…"
    ./mvnw -B -q -DskipTests package
    JAR="$(ls target/cocina-*.jar | head -n 1)"
fi
note "Jar: ${JAR}"

DB_HOST=127.0.0.1 DB_PORT="$DB_PORT" POSTGRES_DB="$DB_NAME" POSTGRES_USER="$DB_USER" \
POSTGRES_PASSWORD="$DB_PASSWORD" \
nohup java -jar "$JAR" \
    --spring.profiles.active=local,demo \
    --server.port="$API_PORT" \
    --cocina.bootstrap.admin-password= \
    >"$LOG_FILE" 2>&1 &
echo $! >"$PID_FILE"

i=0
while [ "$i" -lt 120 ]; do
    if curl -fsS "${BASE}/ready" >/dev/null 2>&1; then
        break
    fi
    i=$((i + 1))
    sleep 1
done
[ "$i" -lt 120 ] || fail "La API no respondió en /ready en 120 s. Revisa ${LOG_FILE}."
note "API preparada; migraciones V1–V7 aplicadas y datos demo sembrados."

# Extrae un campo de un JSON compacto. Suficiente para esta demo, sin dependencias.
field_str() {
    printf '%s' "$1" | sed -n 's/.*"'"$2"'":"\([^"]*\)".*/\1/p'
}

field_num() {
    printf '%s' "$1" | sed -n 's/.*"'"$2"'":\([0-9][0-9.]*\).*/\1/p'
}

say "3/9 · Login con ${ADMIN_USER}"
LOGIN_JSON="$(printf '{"username":"%s","password":"%s"}' "$ADMIN_USER" "$ADMIN_PASSWORD")"
# /ready puede responder mientras los ApplicationRunner aún siembran: se reintenta el login.
TOKEN=""
i=0
while [ "$i" -lt 60 ]; do
    LOGIN="$(curl -sS -X POST "${BASE}/api/v1/auth/login" \
        -H "Content-Type: application/json" -d "$LOGIN_JSON")"
    TOKEN="$(field_str "$LOGIN" accessToken)"
    [ -n "$TOKEN" ] && break
    i=$((i + 1))
    sleep 1
done
[ -n "$TOKEN" ] || fail "Login fallido tras 60 s: $LOGIN"
note "Token JWT emitido (caduca en 15 min): ${TOKEN%"${TOKEN#??????????????}"}…"
note "Refresh token rotatorio emitido; el access se renueva sin volver a pedir contraseña."

api() {
    curl -sS "${BASE}$1" -H "Authorization: Bearer ${TOKEN}"
}

api_post() {
    curl -sS -X POST "${BASE}$1" -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" ${2:+-d "$2"}
}

api_patch() {
    curl -sS -X PATCH "${BASE}$1" -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" -d "$2"
}

say "4/9 · Personas y permisos"
USERS="$(api /api/v1/users)"
note "Usuarios: $(field_num "$USERS" total) (ADMIN, KITCHEN_MANAGER, COOK y STOREKEEPER del seed)."
note "RBAC: cada operación se autoriza por permiso y queda atribuida a su actor."

say "5/9 · Inventario: catálogo, stock y alertas"
PRODUCTS="$(api '/api/v1/products?size=100')"
note "Productos: $(field_num "$PRODUCTS" total) en 4 categorías; cantidades en unidad base (g/ml/ud)."
ALERTS="$(api /api/v1/inventory/alerts)"
note "Bajo mínimo: $(field_num "$ALERTS" lowStockTotal) · próximos a caducar: $(field_num "$ALERTS" expiringTotal)."
note "PMP por producto y kardex inmutable: cada entrada, salida y ajuste deja su movimiento."

say "6/9 · Recetas publicadas y coste congelado (ECO-01)"
# El proveedor sube el tomate: nueva compra más cara, que mueve el PMP, y versión 2 de la salsa.
SUPPLIERS="$(api /api/v1/suppliers)"
SUPPLIER_ID="$(printf '%s' "$SUPPLIERS" | sed -n 's/.*"content":\[{"id":\([0-9]*\).*/\1/p')"
TOMATE_ID="$(printf '%s' "$PRODUCTS" | grep -o '"id":[0-9]*,"categoryId":[0-9]*,"name":"Tomate"' | head -n 1 | sed 's/.*"id":\([0-9]*\).*/\1/')"
[ -n "$SUPPLIER_ID" ] && [ -n "$TOMATE_ID" ] || fail "No se encontró el proveedor o el tomate del seed."
EXPIRES_AT="$(date -u -d '+8 days' +%Y-%m-%dT%H:%M:%SZ)"
PURCHASE="$(api_post "/api/v1/products/${TOMATE_ID}/purchases" \
    "{\"supplierId\":${SUPPLIER_ID},\"quantity\":10,\"unit\":\"kg\",\"unitPrice\":3.20,\"expiresAt\":\"${EXPIRES_AT}\"}")"
printf '%s' "$PURCHASE" | grep -q '"lotId"' || fail "No se pudo registrar la compra de tomate: $PURCHASE"
note "Compra: 10 kg de tomate a 3,20 €/kg (antes 2,10 €/kg); el PMP del tomate se recalcula."
SALSA="$(api '/api/v1/recipes?name=Salsa%20de%20tomate')"
SALSA_ID="$(printf '%s' "$SALSA" | sed -n 's/.*"content":\[{"id":\([0-9]*\).*/\1/p')"
[ -n "$SALSA_ID" ] || fail "No se encontró la receta Salsa de tomate."
DRAFT="$(api_post "/api/v1/recipes/${SALSA_ID}/versions")"
DRAFT_ID="$(printf '%s' "$DRAFT" | sed -n 's/^{"id":\([0-9]*\).*/\1/p')"
[ -n "$DRAFT_ID" ] || fail "No se pudo crear la versión 2 de la salsa: $DRAFT"
PUBLISHED="$(api_post "/api/v1/recipes/${SALSA_ID}/versions/${DRAFT_ID}/publish")"
printf '%s' "$PUBLISHED" | grep -q '"status":"PUBLISHED"' || fail "No se pudo publicar la versión 2: $PUBLISHED"
note "Salsa de tomate v2 publicada con el precio nuevo; la v1 conserva su coste congelado."

COSTS="$(api /api/v1/reports/recipe-costs)"
note "Recetas: $(field_num "$COSTS" recipeCount) · versiones publicadas: $(field_num "$COSTS" versionCount)."
printf '%s\n' "$COSTS" | sed 's/},{/}\
{/g' | while IFS= read -r line; do
    name="$(field_str "$line" recipeName)"
    [ -n "$name" ] || continue
    per="$(field_num "$line" costPerRation)"
    variation="$(field_num "$line" variationPct)"
    if [ -n "$variation" ]; then
        note "· ${name}: ${per} €/ración (variación ${variation} % sobre la versión anterior)"
    else
        note "· ${name}: ${per} €/ración (primera versión)"
    fi
done
note "El coste se congela al publicar: un cambio de PMP no reescribe el histórico."

say "7/9 · Caducidades y valor en riesgo (ECO-06)"
EXPIRY="$(api '/api/v1/reports/expiring-lots?days=30')"
note "Lotes en riesgo: $(field_num "$EXPIRY" totalLots) · valor: $(field_num "$EXPIRY" totalValue) €."
note "Incluye días restantes y saldo de cada lote; los caducados no se consumen en producción."

say "8/9 · Producción: plan, necesidades y ejecución con desviación"
SERVICE_DATE="$(TZ=Europe/Madrid date -d tomorrow +%F)"
PLANS="$(api "/api/v1/production-plans?serviceDate=${SERVICE_DATE}")"
PLAN_ID="$(printf '%s' "$PLANS" | sed -n 's/.*"content":\[{"id":\([0-9]*\).*/\1/p')"
[ -n "$PLAN_ID" ] || fail "No se encontró el plan demo para ${SERVICE_DATE}."
PLAN="$(api "/api/v1/production-plans/${PLAN_ID}")"
ITEM_ID="$(printf '%s' "$PLAN" | sed -n 's/.*"items":\[{"id":\([0-9]*\).*/\1/p')"
PLANNED="$(printf '%s' "$PLAN" | sed -n 's/.*"items":\[{"id":[0-9]*,[^}]*"plannedRations":\([0-9]*\).*/\1/p')"
note "Plan #${PLAN_ID} para ${SERVICE_DATE} (comida) con $(printf '%s' "$PLAN" | grep -o '"plannedRations"' | wc -l | tr -d ' ') ítems."
REQUIREMENTS="$(api "/api/v1/production-plans/${PLAN_ID}/requirements")"
note "Necesidades agregadas calculadas, un solo redondeo por producto:"
printf '%s\n' "$REQUIREMENTS" | sed 's/},{/}\
{/g' | while IFS= read -r line; do
    product="$(field_str "$line" productName)"
    [ -n "$product" ] || continue
    note "· ${product}: $(field_num "$line" quantity) $(field_str "$line" unit)"
done

note "Empezando el primer ítem del plan…"
BATCH="$(api_post "/api/v1/production-plans/${PLAN_ID}/items/${ITEM_ID}/batches")"
BATCH_ID="$(printf '%s' "$BATCH" | sed -n 's/^{"id":\([0-9]*\).*/\1/p')"
[ -n "$BATCH_ID" ] || fail "No se pudo crear el batch: $BATCH"
note "Batch #${BATCH_ID} en IN_PROGRESS: empezar todavía no descuenta stock."

PATATA_ID="$(printf '%s' "$PRODUCTS" | grep -o '"id":[0-9]*,"categoryId":[0-9]*,"name":"Patata"' | head -n 1 | sed 's/.*"id":\([0-9]*\).*/\1/')"
STOCK_BEFORE="$(field_num "$(api "/api/v1/products/${PATATA_ID}")" currentStock)"
REAL=$((PLANNED - 1))
READY="$(api_patch "/api/v1/production-batches/${BATCH_ID}" \
    "{\"status\":\"READY\",\"rations\":${REAL},\"deviationReason\":\"Se sirvieron ${REAL} raciones en lugar de ${PLANNED}\"}")"
DEVIATION="$(printf '%s' "$READY" | sed -n 's/.*"deviation":\(-\{0,1\}[0-9]*\).*/\1/p')"
note "Terminado con ${REAL} raciones reales de ${PLANNED} previstas (desviación ${DEVIATION})."
note "El stock se descuenta aquí, una sola vez por lote vigente."

STOCK_AFTER="$(field_num "$(api "/api/v1/products/${PATATA_ID}")" currentStock)"
note "Patata: ${STOCK_BEFORE} g → ${STOCK_AFTER} g (consumo de la receta, no del plan entero)."
MOVEMENTS="$(api "/api/v1/products/${PATATA_ID}/movements?size=100")"
note "Movimientos de patata: $(field_num "$MOVEMENTS" total), con $(printf '%s' "$MOVEMENTS" | grep -o 'PRODUCTION_OUT' | wc -l | tr -d ' ') de producción."

say "9/9 · Calidad y trazabilidad"
TEMPS="$(api /api/v1/temperature-logs)"
WASTE="$(api /api/v1/waste-records)"
INCIDENTS="$(api /api/v1/incidents)"
note "Temperaturas: $(field_num "$TEMPS" total) · mermas: $(field_num "$WASTE" total) · incidencias: $(field_num "$INCIDENTS" total)."
note "Cada registro lleva actor, fecha y, cuando aplica, movimiento de stock valorado al PMP."
note "Trazabilidad lote → preparación disponible en /api/v1/stock-lots/{id}/traceability."

say "Demostración lista"
note "Swagger UI:      ${BASE}/swagger-ui.html"
note "Login directo:   ${BASE}/swagger-ui/index.html#/auth-controller/login"
note "OpenAPI:         ${BASE}/v3/api-docs"
note "Health/ready:    ${BASE}/health · ${BASE}/ready"
note "Log de la API:   ${LOG_FILE}"
note ""
note "Cómo entrar en Swagger (no hay formulario de login):"
note "  1. Abre el enlace 'Login directo' y pulsa Try it out."
note "  2. Ejecuta el cuerpo con demo.admin / ${ADMIN_PASSWORD}."
note "  3. Copia accessToken y pégalo en Authorize (arriba a la derecha, sin 'Bearer')."
note ""
note "Parar y borrar:  sh scripts/demo.sh --stop"
