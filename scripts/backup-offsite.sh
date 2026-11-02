#!/usr/bin/env sh
# Copia externa de los volcados locales con rclone (NFR-DIS-02).
#
# Uso:
#   RCLONE_REMOTE=s3:cocina-backups sh scripts/backup-offsite.sh
#
# Requisitos: rclone instalado y un remoto configurado con `rclone config`. El destino es
# $RCLONE_REMOTE/<hostname>/ para no mezclar varios servidores en el mismo cubo.
#
# La copia local la hace scripts/backup.sh; este script solo la replica fuera del VPS.

set -eu

# Git Bash (MSYS) reescribe rutas; en Linux/macOS estas variables no tienen efecto.
MSYS_NO_PATHCONV=1
MSYS2_ARG_CONV_EXCL='*'
export MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL

BACKUP_DIR="${BACKUP_DIR:-backups}"
RCLONE_REMOTE="${RCLONE_REMOTE:?define RCLONE_REMOTE, por ejemplo s3:cocina-backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
DEST="${RCLONE_REMOTE%/}/$(hostname)"

if ! command -v rclone >/dev/null 2>&1; then
    echo "ERROR: rclone no está instalado. Ver docs/despliegue.md." >&2
    exit 1
fi

if [ ! -d "$BACKUP_DIR" ]; then
    echo "ERROR: no existe el directorio de copias: $BACKUP_DIR" >&2
    exit 1
fi

# --checksum evita volver a subir lo que ya está igual. Solo se copian volcados y sus sumas.
rclone copy "$BACKUP_DIR" "$DEST" \
    --include 'cocina-*.dump' \
    --include 'cocina-*.dump.sha256' \
    --checksum

# Retención remota: se borran los volcados (y sus sumas) más antiguos que N días.
rclone delete "$DEST" \
    --include 'cocina-*.dump' \
    --include 'cocina-*.dump.sha256' \
    --min-age "${RETENTION_DAYS}d"

echo "Copia externa completada en ${DEST}:"
rclone lsf "$DEST" | tail -n 5
