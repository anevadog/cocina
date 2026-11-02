# Seguridad y protección de datos

> Revisión del bloque C de F7 (NFR-SEG-01/04, NFR-DAT-01, NFR-DIS-01). No sustituye a una auditoría:
> es la lista de comprobación del MVP y el punto de partida de las decisiones pendientes.
> Última revisión: 2026-11-02.

## 1. Superficie expuesta

| Elemento | Expuesto | Control |
|---|---|---|
| `80/tcp` | Sí | Caddy redirige a HTTPS |
| `443/tcp`, `443/udp` | Sí | TLS de Caddy + cabeceras de seguridad |
| `22/tcp` | Sí | Solo clave SSH, sin root remoto; restringir por IP si es posible |
| API `:8080` | No | Solo en la red interna de Compose; Caddy es el único que la alcanza |
| PostgreSQL `:5432` | No | Sin `ports`; acceso solo por `docker compose exec` o túnel SSH temporal |
| Consola de Caddy (`:2019`) | No | `admin off` en el `Caddyfile` |
| Swagger/OpenAPI | No | Apagados en `prod` (`API_DOCS_ENABLED=false`) |
| `/health`, `/ready` | Sí (públicos) | Sin datos sensibles: estado del proceso y de la base |

`/health` y `/ready` son públicos a propósito (PLA-04). No revelan versiones, usuarios ni configuración.
Si en el futuro se añade un endpoint de métricas, no debe publicarse sin autenticación.

## 2. Transporte y cabeceras

- TLS 1.2+ terminado en Caddy, con certificado de Let's Encrypt renovado automáticamente.
- HTTP redirige a HTTPS; HSTS de un año con `includeSubDomains`.
- `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`,
  `Permissions-Policy` restrictiva y CSP `default-src 'none'`.
- Límite de cuerpo de 1 MB en el proxy (la API no acepta ficheros).
- La API confía en `X-Forwarded-*` solo porque está detrás de Caddy y no publica `:8080`.

## 3. Autenticación y autorización

Resumen; el detalle está en `docs/arquitectura.md` §6 y en los ADR-008/016/017/018.

- Contraseñas con **Argon2id** y política de longitud mínima; nunca se registran.
- **Access token JWT HS256 de 15 min**; refresh token rotatorio con hash en base de datos y
  revocación de la familia si se reutiliza uno rotado.
- Bloqueo temporal tras 5 intentos fallidos.
- RBAC por permiso en cada controlador (`@PreAuthorize`), con `EndpointAuthorizationTest` que falla si
  un endpoint queda sin declarar. Públicos solo `auth`, `/health`, `/ready` y la documentación.
- El token incluye `username` y autoridades; no incluye nombre completo ni rol como dato adicional.
- **Pendiente de valorar**: limitación por IP en el proxy (hoy solo hay bloqueo por cuenta).

## 4. Gestión de secretos

| Secreto | Dónde vive | Rotación |
|---|---|---|
| `JWT_SECRET` | `.env` del VPS (permisos `600`) | Rotar invalida todas las sesiones; hacerlo en ventana de mantenimiento |
| `POSTGRES_PASSWORD` | `.env` + volumen de PostgreSQL | Cambiar en base y en `.env` a la vez |
| `BOOTSTRAP_ADMIN_PASSWORD` | `.env` solo en el primer arranque | Vaciar tras crear el administrador |
| Credenciales de rclone | Configuración local de rclone (`~/.config/rclone`) | Clave limitada al bucket; rotar si se sospecha fuga |
| Correo ACME | `.env` | No es secreto, pero debe ser real |

Reglas:

- `.env` está en `.gitignore`; solo se versionan `.env.example` y `.env.production.example`, sin valores.
- `scripts/check-secrets.sh` falla el build si entra un `.env`, una clave PEM, un `.p12`/`.jks` o un
  volcado de `backups/`. Se ejecuta en CI antes de `verify`.
- Los logs no incluyen contraseñas ni tokens; los errores de autenticación devuelven un mensaje
  genérico.
- No se copian secretos a la imagen: se inyectan por entorno en tiempo de ejecución.

## 5. Contenedores y host

- Imagen de API: multi-stage, JRE 25, usuario `app` sin privilegios, `HEALTHCHECK` contra `/ready`.
- En producción: `read_only`, `tmpfs` para `/tmp`, `cap_drop: ALL`, `no-new-privileges`, límites de
  memoria y logs rotados (10 MB × 3).
- PostgreSQL con volumen persistente y healthcheck; sin puerto publicado.
- Host: firewall `ufw` (22/80/443), SSH por clave, actualizaciones automáticas, reloj en UTC.
- Las imágenes usan etiquetas mayores (`postgres:18`, `caddy:2-alpine`, `eclipse-temurin:25-*`). Antes
  de cada actualización se reconstruye y se prueba; no hay actualización automática de imágenes.

## 6. Datos personales (NFR-DAT-01)

### 6.1 Inventario

| Tabla | Datos | Finalidad | Sensibilidad |
|---|---|---|---|
| `app_users` | `username`, `full_name`, `password_hash`, rol, estado, intentos y bloqueo | Identificar al empleado y autorizar operaciones | Alta: nombre y credencial |
| `refresh_tokens` | `user_id`, hash del token, caducidad, revocación | Mantener la sesión de forma revocable | Media: seudónimo + credencial |
| `audit_logs` | `actor_id`, acción, entidad y `before_json`/`after_json` | Trazabilidad y rendición de cuentas | Alta: los snapshots de usuario incluyen `username` y `fullName` |
| `stock_movements` | `actor_id`, motivo libre | Trazabilidad del kardex | Media: seudónimo y posible texto libre |
| `production_plans`, `production_batches` | `created_by`, fechas y cantidades | Planificación y ejecución | Baja |
| `temperature_logs`, `waste_records` | `actor_id`, motivo/observaciones | Calidad y seguridad alimentaria | Media: seudónimo y posible texto libre |
| `incidents` | `opened_by`, `closed_by`, descripción y resolución libres | Gestión de incidencias | Alta: el texto libre puede contener nombres |
| `suppliers` | `name`, `contact` | Contacto comercial | Media: el contacto puede ser una persona |

No se recogen DNI, teléfono, correo, dirección, datos de salud ni datos de clientes. El alcance es
mínimo para operar la cocina. Los campos libres (incidencias, motivos de ajuste, mermas) son el punto
débil: la aplicación no puede impedir que alguien escriba un nombre; la formación debe indicar que no
se incluya información personal innecesaria.

### 6.2 Minimización aplicada

- `app_users` no guarda correo, teléfono ni documento de identidad.
- `refresh_tokens` guarda el **hash**, no el token.
- `audit_logs` guarda `actor_id`, no el nombre del actor en cada fila; el nombre solo aparece en los
  snapshots de alta/modificación de usuario.
- Los logs estructurados no incluyen usuario, contraseña ni token.

### 6.3 Retención propuesta (no decidida)

> **PENDIENTE P2.** Estos plazos son una propuesta para discutir con asesoría; no son una decisión
> legal. Hasta cerrar P2 no se introducen datos reales de empleados.

| Dato | Plazo propuesto | Destino al vencer |
|---|---|---|
| `app_users` | Mientras esté activo + 6 meses desde la baja | Seudonimizar `username`/`full_name`; conservar `id` y rol por integridad referencial |
| `refresh_tokens` | 30 días tras caducar o revocarse | Borrado físico (tarea de mantenimiento pendiente) |
| `audit_logs` | 4 años | Conservar: es un registro de seguridad inmutable; revisar base legal |
| Kardex, producción, calidad | 2 años | Archivar; anonimizar texto libre si procede |
| `suppliers.contact` | Mientras el proveedor esté activo + 1 año | Borrar el contacto, conservar el proveedor |
| Copias de seguridad | 30 días | Borrado por retención local y remota |
| Logs de contenedor | 10 MB × 3 por servicio | Rotación de Docker |

La tensión principal es **derecho de supresión frente a auditoría inmutable**: `audit_logs` no se puede
editar ni borrar (trigger de V7). La vía propuesta es no borrar al usuario, sino desactivarlo y
seudonimizar sus datos en `app_users`, manteniendo `actor_id` como referencia. Los snapshots históricos
de auditoría conservan el nombre; hay que decidir si eso es proporcionado o si se deja de incluir
`fullName` en los snapshots futuros (cambio de código, no de esquema). **No se implementa hasta P2.**

### 6.4 Base legal y derechos

- **PENDIENTE P2**: base jurídica (ejecución de contrato laboral, obligación legal de trazabilidad,
  interés legítimo en seguridad) y procedimiento de atención de derechos.
- El MVP no tiene interfaz de empleado ni auto-servicio: las solicitudes se atenderían por el
  administrador con apoyo técnico.
- Antes de datos reales: firmar el contrato de encargado del tratamiento con el proveedor del VPS y del
  almacenamiento, elegir región de la UE y documentar transferencias si las hay.
- El establecimiento es el responsable del tratamiento; el software es el medio, no el responsable.

## 7. Registro y observabilidad

- Logs JSON (ECS) con `requestId` por petición y sin datos personales.
- El `requestId` se acepta del cliente solo con formato acotado; si no, se genera uno.
- Los errores 5xx no filtran traza ni detalle interno al cliente (`include-stacktrace: never`).
- Retención de logs por rotación de Docker; si se envían fuera, aplicar la misma política que a los
  datos personales.

## 8. Dependencias y mantenimiento

- `./mvnw verify` en cada cambio (tests, formato, cobertura informativa).
- Actualizaciones de dependencias con revisión y `verify`; no hay Dependabot/Renovate configurado.
- Actualizaciones del sistema operativo automáticas; las de Docker y de la aplicación, manuales y
  verificadas.
- Las migraciones Flyway son inmutables; una corrección se hace con una migración nueva.

## 9. Lista de comprobación

- [x] TLS y redirección a HTTPS; HSTS y cabeceras de seguridad.
- [x] Base de datos sin puerto publicado.
- [x] Swagger/OpenAPI apagados en producción.
- [x] API sin root, solo lectura, sin capabilities y con límites.
- [x] Secretos fuera del repositorio y comprobación automática en CI.
- [x] Contraseñas Argon2id, access token ≤ 15 min y refresh rotatorio.
- [x] RBAC verificado por test en todos los endpoints.
- [x] Inventario de datos personales y minimización documentados.
- [ ] **P2**: base legal, plazos definitivos y procedimiento de derechos.
- [ ] Copia externa cifrada y probada en el proveedor elegido.
- [ ] Sonda externa con alerta y revisión periódica de backups.

## 10. Riesgos aceptados y pendientes

1. **P2 abierto**: sin base legal ni plazos validados no se usan datos reales de empleados.
2. **Auditoría inmutable con nombres en snapshots históricos**: decisión pendiente dentro de P2.
3. **Sin WAF ni límite por IP**: se confía en el proveedor y en el bloqueo por cuenta.
4. **Sin cifrado de aplicación**: el cifrado en reposo depende del proveedor del VPS y del bucket.
5. **Un solo administrador de sistema**: el VPS lo opera una persona; no hay separación de funciones.
6. **Sin gestión de vulnerabilidades automatizada**: las actualizaciones son manuales y verificadas.
