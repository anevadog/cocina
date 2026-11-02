# Despliegue en producción (P1)

> Procedimiento operativo del MVP: un VPS, Docker Compose y Caddy. Decisión en ADR-045.
> Última revisión: 2026-11-02.

## 1. Arquitectura del despliegue

```text
Internet
   │  80/443 (TLS)
   ▼
┌──────────────────────────────────────────────┐
│ VPS (Ubuntu LTS)                             │
│                                              │
│  ┌────────────┐   red interna   ┌──────────┐ │
│  │   Caddy    │ ──────────────► │   API    │ │
│  │ proxy/TLS  │                 │ :8080    │ │
│  └────────────┘                 └────┬─────┘ │
│                                      │       │
│                                 ┌────▼─────┐ │
│                                 │PostgreSQL│ │
│                                 │  :5432   │ │
│                                 └──────────┘ │
└──────────────────────────────────────────────┘
```

- **Caddy** es el único servicio con puertos publicados (`80`, `443`). Termina TLS, aplica
  cabeceras de seguridad y limita el cuerpo de la petición.
- **API**: imagen multi-stage (`Dockerfile`), JRE 25, usuario sin privilegios, sistema de ficheros
  de solo lectura, `cap_drop: ALL` y `no-new-privileges`.
- **PostgreSQL**: sin `ports`, solo accesible desde la red interna `backend` de Compose.
- **Flyway** aplica las migraciones al arrancar la API; `/ready` falla si el esquema no está al día.
- La documentación OpenAPI/Swagger está **apagada** en el perfil `prod` (`API_DOCS_ENABLED=false`).
- Para enseñar el producto sin datos reales ni escrituras existe una pila aparte de **demo pública de
  solo lectura** (`compose.demo.yaml` + `Caddyfile.demo`); no sustituye a producción. Procedimiento y
  límites en `docs/demo-publica.md` (ADR-047).

## 2. Requisitos y coste

| Elemento | Mínimo | Nota |
|---|---|---|
| VPS | 2 vCPU, 2 GB RAM, 40 GB SSD, Ubuntu 24.04 LTS | 4 GB dan margen para builds en el propio servidor |
| Red | IPv4 pública; IPv6 opcional | Caddy necesita `80` y `443` accesibles |
| Dominio | Un subdominio, por ejemplo `cocina.example.com` | Registro A (y AAAA si hay IPv6) |
| Backups | Almacenamiento S3-compatible fuera del VPS | Bucket privado, cifrado en reposo |

Coste orientativo (precios de referencia, no compromiso): VPS pequeño 4–6 €/mes, IPv4 0,5–1 €/mes,
backups del proveedor 1 €/mes, almacenamiento externo < 1 €/mes y dominio ~10 €/año. Total
**≤ 15 €/mes**, dentro de NFR-COST-01.

## 3. Preparación del VPS

1. **Acceso por clave y sin root remoto.** En `/etc/ssh/sshd_config`:
   `PermitRootLogin no`, `PasswordAuthentication no`. Reiniciar `ssh`.
2. **Firewall** (solo lo imprescindible):
   ```bash
   ufw default deny incoming
   ufw default allow outgoing
   ufw allow 22/tcp
   ufw allow 80/tcp
   ufw allow 443/tcp
   ufw allow 443/udp   # HTTP/3
   ufw enable
   ```
3. **Actualizaciones automáticas**: `apt install unattended-upgrades` y activar
   `Unattended-Upgrade::Automatic-Reboot "false"`.
4. **Zona horaria UTC** en el sistema (`timedatectl set-timezone UTC`). La zona de negocio
   (`Europe/Madrid`) se aplica en la aplicación, no en el reloj del servidor.
5. **Swap** en VPS de 2 GB (`fallocate -l 2G /swapfile`, `chmod 600`, `mkswap`, `swapon`, en
   `/etc/fstab`) para que un pico de build o de JVM no dispare el OOM killer.
6. **Docker Engine + Compose plugin** desde el repositorio oficial. Añadir el usuario de despliegue
   al grupo `docker` es equivalente a darle root: si no se acepta, usar `sudo docker`.
7. **Directorio de trabajo**: `/srv/cocina`, propietario del usuario de despliegue.

## 4. DNS y TLS

1. Crear el registro `A` (y `AAAA` si hay IPv6) del dominio hacia la IP del VPS.
2. Comprobar la propagación: `dig +short cocina.example.com`.
3. Caddy obtiene y renueva el certificado de Let's Encrypt automáticamente al arrancar, siempre que
   `80` y `443` estén abiertos y el DNS apunte al VPS. Los certificados viven en el volumen
   `caddy-data`; no se pierden al recrear el contenedor.
4. El correo de `ACME_EMAIL` recibe los avisos de expiración; debe ser real.

## 5. Primer despliegue

```bash
cd /srv/cocina
cp .env.production.example .env
chmod 600 .env
# Editar .env: dominio, correo, contraseñas y secretos.
openssl rand -base64 48     # JWT_SECRET
openssl rand -base64 24     # POSTGRES_PASSWORD
openssl rand -base64 24     # BOOTSTRAP_ADMIN_PASSWORD (solo el primer arranque)

docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps
```

El primer build tarda varios minutos (dependencias Maven e imágenes base). Después:

```bash
curl -fsS https://cocina.example.com/health
curl -fsS https://cocina.example.com/ready
```

`/health` responde 200 si el proceso vive; `/ready` responde 200 solo si la base y las migraciones
están correctas. Un 503 en `/ready` con el contenedor `Up` casi siempre significa migración pendiente
o credenciales de base incorrectas: revisar `logs api`.

Tras el primer arranque, **vaciar `BOOTSTRAP_ADMIN_PASSWORD`** en `.env` y recrear el contenedor. El
administrador ya existe y la aplicación no lo vuelve a crear.

## 6. Actualización y vuelta atrás

1. **Copia previa obligatoria**:
   ```bash
   COMPOSE_FILE=compose.prod.yaml sh scripts/backup.sh
   ```
2. Traer el código nuevo (`git pull` o el mecanismo de despliegue que se use).
3. Reconstruir y recrear:
   ```bash
   docker compose -f compose.prod.yaml up -d --build
   ```
4. Verificar `/ready`, login y una consulta de negocio. Revisar que Flyway aplicó las migraciones
   nuevas sin errores.
5. **Vuelta atrás**: las migraciones son inmutables y **solo avanzan**. Volver a una imagen anterior
   solo es seguro si el esquema nuevo es compatible hacia atrás; si no, hay que restaurar la copia
   previa en una base nueva y promoverla (ver `docs/backups.md`). No se edita una migración ya
   aplicada.

Etiquetar las imágenes por versión (`APP_VERSION=0.1.0` en `.env`) facilita identificar qué se
desplegó, pero el rollback se hace reconstruyendo desde el commit anterior.

## 7. Operación diaria

| Tarea | Comando |
|---|---|
| Estado | `docker compose -f compose.prod.yaml ps` |
| Logs de la API | `docker compose -f compose.prod.yaml logs -f --tail=200 api` |
| Logs del proxy | `docker compose -f compose.prod.yaml logs -f --tail=200 proxy` |
| Consumo | `docker stats --no-stream` |
| Consola de base | `docker compose -f compose.prod.yaml exec db psql -U cocina -d cocina` |
| Reiniciar la API | `docker compose -f compose.prod.yaml restart api` |
| Parar sin borrar datos | `docker compose -f compose.prod.yaml down` |

Los logs son JSON (ECS) con `requestId`, y Docker los rota a 10 MB × 3 por servicio. La base de datos
no se expone a Internet: para administrarla se usa `exec` o un túnel SSH explícito y temporal.

**Monitorización mínima:** una sonda externa (el proveedor de VPS o un servicio gratuito) contra
`https://<dominio>/health` cada minuto, con alerta a las dos fallas consecutivas. `/ready` se usa para
el healthcheck interno y para el proxy, no para la sonda externa: un fallo de base ya lo detecta el
healthcheck y no conviene reiniciar la API por ello.

## 8. Copias de seguridad en producción

La copia local y la externa se programan con `cron` en el VPS. El script local usa
`COMPOSE_FILE=compose.prod.yaml`; sin esa variable, `docker compose` buscaría la pila de desarrollo.

```cron
# 03:30 UTC, fuera del horario de servicio.
30 3 * * * cd /srv/cocina && COMPOSE_FILE=compose.prod.yaml RCLONE_REMOTE=s3:cocina-backups sh scripts/backup.sh >> /var/log/cocina-backup.log 2>&1 && COMPOSE_FILE=compose.prod.yaml RCLONE_REMOTE=s3:cocina-backups sh scripts/backup-offsite.sh >> /var/log/cocina-backup.log 2>&1
```

- `scripts/backup.sh`: `pg_dump -Fc`, SHA-256 y retención local de 30 días.
- `scripts/backup-offsite.sh`: `rclone copy` a `RCLONE_REMOTE/<hostname>/` y retención remota de 30
  días. Requiere `rclone` instalado y un remoto configurado (`rclone config`).
- El bucket debe ser privado, con cifrado en reposo y credenciales limitadas a ese prefijo. No se
  guardan credenciales en el repositorio.
- **Vigilar la copia**: una copia que falla en silencio no es una copia. Comprobar a diario que el
  último `.dump` tiene menos de 24 h y un tamaño razonable:
  ```bash
  find /srv/cocina/backups -name 'cocina-*.dump' -mtime -1 | wc -l   # debe ser ≥ 1
  ```
- **Prueba de restauración** trimestral en una base nueva (`cocina_restore`), con los recuentos y el
  arranque real descritos en `docs/backups.md`. Después se elimina la base de prueba.

## 9. Lista de comprobación del despliegue

- [ ] DNS resuelve al VPS y `80`/`443` están abiertos.
- [ ] `.env` tiene permisos `600`, secretos generados y `BOOTSTRAP_ADMIN_PASSWORD` vacío tras el
      primer arranque.
- [ ] `https://<dominio>/health` y `/ready` responden 200.
- [ ] El certificado es válido (`openssl s_client -connect <dominio>:443 -servername <dominio>`).
- [ ] `docker compose -f compose.prod.yaml ps` muestra `db`, `api` y `proxy` sanos.
- [ ] La base no responde desde fuera del VPS (`nc -zv <ip> 5432` debe fallar).
- [ ] Swagger/OpenAPI devuelve 404 (`/swagger-ui.html`).
- [ ] Login correcto y una consulta de negocio devuelve datos.
- [ ] `cron` de backup ejecutado al menos una vez; el volcado y su `.sha256` existen y la copia
      externa está en el bucket.
- [ ] Sonda externa de `/health` configurada con alerta.
- [ ] Registro del despliegue anotado (fecha, versión desplegada y comprobaciones).

## 10. Riesgos aceptados

1. **Un solo servidor**: no hay alta disponibilidad; NFR-DIS-01 se apoya en el RTO de 4 h y en la
   restauración, no en redundancia. Un fallo del proveedor puede superar ese margen.
2. **Actualizaciones manuales**: el despliegue no es automático; requiere una persona que ejecute los
   pasos y verifique.
3. **Sin PITR**: el RPO es de 24 h (ADR-043). Se acepta para el MVP.
4. **Sin WAF ni limitación por IP**: hay bloqueo por cuenta tras intentos fallidos y límite de cuerpo,
   pero no protección volumétrica propia. Se confía en el proveedor del VPS.
5. **Datos personales**: antes de introducir datos reales de empleados hay que cerrar P2 (base legal y
   retención). Ver `docs/seguridad.md`.
