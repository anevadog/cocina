# Copias de seguridad y restauración (NFR-DIS-02)

> Procedimiento operativo de copias y restauración de la base de datos.
> Última revisión: 2026-11-02. La prueba de restauración de esa fecha está registrada al final.

## 1. Qué se protege y con qué garantías

| Concepto | Decisión |
|---|---|
| Objeto | Base de datos completa del establecimiento: esquema, datos, `flyway_schema_history` y `audit_logs` |
| Formato | Volcado lógico en formato *custom* de PostgreSQL (`pg_dump -Fc`), comprimido y restaurable con `pg_restore` |
| Frecuencia | Diaria en producción (NFR-DIS-02) |
| Retención | 30 días por defecto (`BACKUP_RETENTION_DAYS`) |
| RPO | 24 h: se acepta perder como máximo el día en curso |
| RTO | 4 h: el tiempo de restaurar un volumen de MVP es de minutos; el margen cubre la provisión del entorno (NFR-DIS-01) |
| Verificación | Suma SHA-256 de cada volcado y una restauración de prueba documentada (no basta con que el fichero exista) |

No se usa `pg_dumpall`: el MVP tiene un único establecimiento y un único rol de aplicación. Si en el
futuro hay varios establecimientos activos, se valorará un volcado por base o un `pg_dumpall` con
roles incluidos (ver P1 en `docs/decisiones.md`).

## 2. Copia manual

Los scripts asumen el PostgreSQL del `compose.yaml` y se ejecutan desde la raíz del repositorio con
Git Bash o WSL (no PowerShell: su redirección corrompe los binarios). En Linux/macOS funcionan tal cual.

```bash
sh scripts/backup.sh
```

Pasos que ejecuta:

1. `pg_dump -Fc` dentro del contenedor, a un fichero temporal de `/tmp`.
2. `docker compose cp` del volcado al directorio `backups/` del host (nunca por la salida estándar).
3. Comprobación de que el fichero no está vacío y cálculo de `*.sha256`.
4. Borrado de volcados con más de `BACKUP_RETENTION_DAYS` días (30 por defecto).

Variables: `POSTGRES_DB` (por defecto `cocina`), `POSTGRES_USER` (por defecto `cocina`),
`BACKUP_DIR` (`backups`), `BACKUP_RETENTION_DAYS` (30) y `COMPOSE_SERVICE` (`db`).

```bash
POSTGRES_DB=cocina_demo BACKUP_DIR=D:/copias sh scripts/backup.sh
```

`backups/` está en `.gitignore`: un volcado contiene datos personales (nombres de usuarios) y no debe
llegar nunca al repositorio.

## 3. Programación

### Producción

El despliegue es un VPS con `compose.prod.yaml` (ADR-045). La copia diaria se programa con `cron` en
el host y se replica fuera del VPS con `scripts/backup-offsite.sh` (ADR-046). Hay que exportar
`COMPOSE_FILE=compose.prod.yaml`: sin esa variable, `docker compose` usa la pila de desarrollo.

```cron
# 03:30 UTC, fuera del horario de servicio.
30 3 * * * cd /srv/cocina && COMPOSE_FILE=compose.prod.yaml RCLONE_REMOTE=s3:cocina-backups sh scripts/backup.sh >> /var/log/cocina-backup.log 2>&1 && COMPOSE_FILE=compose.prod.yaml RCLONE_REMOTE=s3:cocina-backups sh scripts/backup-offsite.sh >> /var/log/cocina-backup.log 2>&1
```

La copia externa usa `rclone copy` a `$RCLONE_REMOTE/<hostname>/` y aplica la misma retención de 30
días en remoto. Requiere `rclone` instalado y un remoto configurado (`rclone config`), con un bucket
privado, cifrado en reposo y credenciales limitadas a ese prefijo. Procedimiento completo en
`docs/despliegue.md` §8.

El registro debe vigilarse: una copia que falla en silencio no es una copia. Como mínimo, se revisa el
tamaño del último volcado, su suma SHA-256 y que la copia externa esté en el bucket:

```bash
find /srv/cocina/backups -name 'cocina-*.dump' -mtime -1 | wc -l   # debe ser ≥ 1
RCLONE_REMOTE=s3:cocina-backups rclone lsf "$(hostname)" | tail
```

### Desarrollo

No hace falta programar nada. Antes de una migración o de un cambio destructivo en local:

```bash
sh scripts/backup.sh
```

## 4. Restauración

Nunca se restaura sobre la base de trabajo: el script crea una base nueva y lo rechaza si el destino
es `POSTGRES_DB`.

```bash
sh scripts/restore.sh backups/cocina-cocina-20261102T132408Z.dump cocina_restore
```

El script:

1. Comprueba el nombre de la base destino y que no sea la de trabajo.
2. Si la base destino ya existe, exige `RESTORE_FORCE=yes` y la recrea (la restauración es completa).
3. Copia el volcado al contenedor y ejecuta `pg_restore --no-owner --clean --if-exists`.
4. Imprime el recuento de tablas principales para compararlo con el origen.

### Lista de comprobación tras restaurar

1. **Recuentos**: comparar la salida del script con la misma consulta sobre la base de origen.
2. **Migraciones**: `select version, description from flyway_schema_history order by installed_rank`
   debe devolver V1–V7 y no debe haber migraciones pendientes.
3. **Arranque real**: levantar la API contra la base restaurada y hacer login con un usuario conocido.
   Un volcado puede tener los recuentos bien y las secuencias mal; el login y una consulta de negocio
   lo detectan.

```bash
POSTGRES_DB=cocina_restore ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo.admin","password":"<password>"}'
```

4. **Promoción**: si la restauración sustituye a producción, se para la API, se renombra la base
   actual (no se borra) y se renombra la restaurada. El tiempo de parada es el de los dos `ALTER
   DATABASE`; las copias se conservan al menos 24 h por si hay que volver atrás.

### Qué no cubre

- **PITR (point-in-time recovery)**: no hay archivado de WAL. El RPO real es 24 h, no minutos.
- **Cifrado en reposo**: el volcado se guarda tal cual. El directorio de copias debe estar cifrado o en
  un almacenamiento con cifrado y control de acceso.
- **Copia externa**: falta replicar a otro equipo o proveedor. Se decide con P1.
- **Roles y contraseñas de PostgreSQL**: `pg_dump -Fc` no incluye roles. La base restaurada usa el rol
  del cluster destino (`--no-owner`).

## 5. Prueba de restauración registrada

**Fecha:** 2026-11-02. **Entorno:** PostgreSQL 18.6 en Docker (compose local), JDK 25.0.4.1.

1. Base `cocina` con datos demo recién sembrados (perfil `demo`): 10 productos, 3 recetas publicadas,
   1 plan de producción, 1 lectura de temperatura, 1 merma, 1 incidencia y 11 movimientos de stock.
2. `sh scripts/backup.sh` → `backups/cocina-cocina-20261102T132408Z.dump` (98 KB) y
   `*.sha256`; `sha256sum -c` correcto.
3. `sh scripts/restore.sh backups/... cocina_restore` → `pg_restore` sin errores y base nueva creada.
4. Comparación de recuentos origen/destino, idénticos:

   | Tabla | Origen | Restaurada |
   |---|---:|---:|
   | `audit_logs` | 51 | 51 |
   | `incidents` | 1 | 1 |
   | `production_plans` | 1 | 1 |
   | `products` | 10 | 10 |
   | `recipes` | 3 | 3 |
   | `recipe_versions` | 3 | 3 |
   | `stock_lots` | 10 | 10 |
   | `stock_movements` | 11 | 11 |
   | `temperature_logs` | 1 | 1 |
   | `waste_records` | 1 | 1 |

5. `flyway_schema_history` en la copia: V1–V7, sin pendientes.
6. Arranque de la API contra `cocina_restore` con el perfil `demo`: `/ready` 200, login de
   `demo.admin` correcto, `GET /api/v1/recipes` = 3 y `GET /api/v1/reports/recipe-costs` con 3
   versiones. La copia es funcional, no solo coherente en recuentos.

La base de prueba se elimina después con `drop database cocina_restore with (force)`. El volcado de
ejemplo no se commitea (está ignorado por Git).

## 6. Pendientes

1. Configurar `rclone` y el bucket externo en el VPS; el script existe, pero la copia real depende de
   las credenciales del operador.
2. Alertas si el volcado diario falla o si el tamaño se desvía del habitual.
3. Decidir si el RPO de 24 h es suficiente o si se añade archivado de WAL (PITR).
