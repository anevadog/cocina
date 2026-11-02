#!/usr/bin/env sh
# Comprueba que no haya secretos versionados (NFR-SEG-04).
#
# Uso: sh scripts/check-secrets.sh   (desde la raíz del repositorio)
#
# La comprobación es deliberadamente simple y sin dependencias: cubre las formas de fuga que
# de verdad importan en este repositorio (ficheros .env, claves privadas y volcados de datos).
# No sustituye a un escáner de secretos del proveedor, pero falla el build si algo de esto entra.

set -eu

status=0

fail() {
    echo "ERROR: $1" >&2
    status=1
}

# 1. Ficheros .env versionados: solo se permiten las plantillas.
versioned_env="$(git ls-files | grep -E '(^|/)\.env($|\.)' | grep -vE '\.env\.example$|\.env\.production\.example$|\.env\.demo\.example$' || true)"
if [ -n "$versioned_env" ]; then
    fail "hay ficheros .env versionados:"
    echo "$versioned_env" >&2
fi

# 2. Claves y almacenes de claves versionados.
versioned_keys="$(git ls-files | grep -E '\.(pem|key|p12|jks)$' || true)"
if [ -n "$versioned_keys" ]; then
    fail "hay claves o almacenes versionados:"
    echo "$versioned_keys" >&2
fi

# 3. Cabeceras de clave privada PEM dentro de cualquier fichero versionado.
if git grep -nE -- '-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----' -- . >/dev/null 2>&1; then
    fail "hay una clave privada PEM en el repositorio:"
    git grep -nE -- '-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----' -- . >&2 || true
fi

# 4. Copias de seguridad versionadas: contienen datos personales.
versioned_backups="$(git ls-files 'backups/*' || true)"
if [ -n "$versioned_backups" ]; then
    fail "hay copias de seguridad versionadas:"
    echo "$versioned_backups" >&2
fi

if [ "$status" -eq 0 ]; then
    echo "OK: no se han encontrado secretos versionados."
fi

exit "$status"
