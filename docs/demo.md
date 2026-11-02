# Demostración reproducible

> Guion para enseñar la API con datos reales de demostración. No es una maqueta: el script
> arranca PostgreSQL, la API con el perfil `local,demo` y ejecuta el recorrido contra los
> endpoints reales. Última revisión: 2026-11-02.

## 1. Qué demuestra

| Bloque | Qué se ve |
|---|---|
| Seguridad | Login JWT, refresh rotatorio, RBAC por permiso y atribución de operaciones |
| Inventario | 10 productos en 4 categorías, stock en unidad base, PMP, kardex y alertas |
| Recetas | 3 recetas publicadas con coste congelado por ración (ECO-01) |
| Caducidades | Lotes en riesgo y valor económico (ECO-06) |
| Producción | Plan por fecha y servicio, necesidades agregadas, ejecución y desviación (PRD-01..05) |
| Calidad | Temperaturas, mermas, incidencias y trazabilidad lote → preparación |
| Operación | `/health`, `/ready` y Swagger UI en local |

No entra en datos personales reales ni en la pila de producción: usa la base desechable
`cocina_demo`, el puerto `55432` para PostgreSQL y el `8081` para la API.

## 2. Requisitos

- Docker en marcha.
- JDK 25 (`java` en el `PATH`) y el Maven Wrapper del repositorio.
- Git Bash, WSL o Linux. En Windows, **no** PowerShell: el script necesita `sh`.

El jar se reutiliza de `target/` si existe; si no, el script ejecuta
`./mvnw -B -q -DskipTests package`.

## 3. Cómo se lanza

```bash
sh scripts/demo.sh          # prepara todo, ejecuta el recorrido y deja la API en marcha
sh scripts/demo.sh --stop   # para la API y borra la base de demostración
```

Al terminar:

- **Swagger UI**: <http://localhost:8081/swagger-ui.html>
- **OpenAPI**: <http://localhost:8081/v3/api-docs>
- **Salud**: <http://localhost:8081/health> y <http://localhost:8081/ready>
- **Log**: `.demo/api.log` (ignorado por Git)

Credenciales del seed: `demo.admin` / `Demo2026!segura` (también existen `demo.manager`,
`demo.cook` y `demo.store` con la misma contraseña, cada uno con su rol).

## 4. Recorrido para presentarlo

El script imprime el recorrido completo. Esta es la salida real de una ejecución
(2026-11-02, PostgreSQL 18 en Docker, JDK 25):

```text
1/9 · Preparando PostgreSQL de demostración (cocina_demo en :55432)
  Base lista. No se usa la base de desarrollo.

2/9 · Arrancando la API con el perfil local,demo en :8081
  Jar: target/cocina-0.1.0-SNAPSHOT.jar
  API preparada; migraciones V1–V7 aplicadas y datos demo sembrados.

3/9 · Login con demo.admin
  Token JWT emitido (caduca en 15 min): eyJhbGciOiJIUz…
  Refresh token rotatorio emitido; el access se renueva sin volver a pedir contraseña.

4/9 · Personas y permisos
  Usuarios: 4 (ADMIN, KITCHEN_MANAGER, COOK y STOREKEEPER del seed).
  RBAC: cada operación se autoriza por permiso y queda atribuida a su actor.

5/9 · Inventario: catálogo, stock y alertas
  Productos: 10 en 4 categorías; cantidades en unidad base (g/ml/ud).
  Bajo mínimo: 0 · próximos a caducar: 1.
  PMP por producto y kardex inmutable: cada entrada, salida y ajuste deja su movimiento.

6/9 · Recetas publicadas y coste congelado (ECO-01)
  Recetas: 3 · versiones publicadas: 3.
  · Arroz con pollo: 1.2506 €/ración (primera versión)
  · Salsa de tomate: 1.2930 €/ración (primera versión)
  · Tortilla de patata: 0.8212 €/ración (primera versión)
  El coste se congela al publicar: un cambio de PMP no reescribe el histórico.

7/9 · Caducidades y valor en riesgo (ECO-06)
  Lotes en riesgo: 6 · valor: 201.6400 €.
  Incluye días restantes y saldo de cada lote; los caducados no se consumen en producción.

8/9 · Producción: plan, necesidades y ejecución con desviación
  Plan #1 para 2026-11-03 (comida) con 3 ítems.
  Necesidades agregadas calculadas, un solo redondeo por producto:
  · Patata: 2000.000 g
  · Cebolla: 1600.000 g
  · Tomate: 6000.000 g
  · Arroz: 1000.000 g
  · Sal: 106.000 g
  · Pollo: 1575.000 g
  · Aceite de oliva: 420.000 ml
  · Huevo: 16.000 ud
  Empezando el primer ítem del plan…
  Batch #1 en IN_PROGRESS: empezar todavía no descuenta stock.
  Terminado con 7 raciones reales de 8 previstas (desviación -1).
  El stock se descuenta aquí, una sola vez por lote vigente.
  Patata: 19700.000 g → 17950.000 g (consumo de la receta, no del plan entero).
  Movimientos de patata: 3, con 1 de producción.

9/9 · Calidad y trazabilidad
  Temperaturas: 1 · mermas: 1 · incidencias: 1.
  Cada registro lleva actor, fecha y, cuando aplica, movimiento de stock valorado al PMP.
  Trazabilidad lote → preparación disponible en /api/v1/stock-lots/{id}/traceability.

Demostración lista
  Swagger UI:      http://localhost:8081/swagger-ui.html
  OpenAPI:         http://localhost:8081/v3/api-docs
  Health/ready:    http://localhost:8081/health · http://localhost:8081/ready
  Log de la API:   .demo/api.log
  Parar y borrar:  sh scripts/demo.sh --stop
```

### Qué contar en cada paso

1. **Login**: el access token dura 15 minutos y el refresh es rotatorio y revocable. El token
   incluye el rol y los permisos, no el nombre completo.
2. **Permisos**: los cuatro usuarios del seed representan los puestos reales. Una operación de
   almacén no la puede hacer un cocinero.
3. **Inventario**: todo se guarda en unidad base; las conversiones (kg, l, docena) se aplican en el
   borde. El kardex es inmutable y el PMP se recalcula solo con las compras.
4. **Coste**: cada versión publicada conserva su coste aunque después cambie el precio de compra.
   Es la diferencia entre «lo que cuesta hoy» y «lo que costó cuando se aprobó».
5. **Caducidades**: el valor en riesgo permite decidir qué gastar primero. Un lote caducado no se
   consume en producción: la API devuelve conflicto.
6. **Producción**: las necesidades se agregan por producto y se redondean una sola vez. Al terminar
   con 7 raciones en lugar de 8, se exige motivo y se descuenta lo real: la patata baja 1 750 g
   (7 × 250 g), no lo previsto.
7. **Calidad**: temperatura, merma e incidencia quedan con actor y fecha; la trazabilidad permite
   ir de un lote a la preparación en la que se usó.

## 5. Usar Swagger UI

1. Abrir <http://localhost:8081/swagger-ui.html>.
2. Ejecutar `POST /api/v1/auth/login` con `{"username":"demo.admin","password":"Demo2026!segura"}`.
3. Copiar `accessToken` y pulsar **Authorize**; pegar el token (sin `Bearer`).
4. Probar, por ejemplo:
   - `GET /api/v1/products`
   - `GET /api/v1/reports/recipe-costs`
   - `GET /api/v1/reports/expiring-lots?days=30`
   - `GET /api/v1/production-plans?serviceDate=<mañana>`
   - `GET /api/v1/inventory/alerts`
5. Para una transición: `POST /api/v1/production-plans/{planId}/items/{itemId}/batches` y después
   `PATCH /api/v1/production-batches/{batchId}` con `{"status":"READY","rations":7,...}`.

Swagger está activo porque el perfil es `local`. En `prod` está apagado
(`API_DOCS_ENABLED=false`, ADR-045).

## 6. Repetir y limpiar

Cada ejecución borra el contenedor `cocina-demo-db` y vuelve a sembrar la base: los números de la
tabla son siempre los mismos. La base de desarrollo `cocina` (puerto 5433) no se toca.

- Cambiar puertos: `DEMO_DB_PORT=55433 DEMO_API_PORT=8082 sh scripts/demo.sh`.
- Cambiar la contraseña: `DEMO_PASSWORD='...' sh scripts/demo.sh`.
- Si algo falla, revisar `.demo/api.log`; el script deja la API en marcha a propósito para poder
  seguir mostrando Swagger.

## 7. Limitaciones

- Es una demo de **API**, no de una interfaz de usuario: el producto del MVP es el contrato HTTP.
- Los datos son ficticios y desechables; no se usan datos personales reales.
- No demuestra TLS ni la pila de producción: eso está en `docs/despliegue.md` y se validó con
  `compose.prod.yaml` y Caddy.
- El rendimiento de esta demo no es una medición: ver `docs/rendimiento.md`.
