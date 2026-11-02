# Decisiones técnicas (ADR)

Una entrada por decisión con contexto, opciones, decisión y consecuencias. Estado: `Aceptada`,
`PENDIENTE` o `Sustituida`.

| ADR | Decisión | Estado |
|---|---|---|
| ADR-001 | Java 25 LTS + Spring Boot 4.1.x | Aceptada |
| ADR-002 | PostgreSQL 18 como base única | Aceptada |
| ADR-003 | Docker Compose para desarrollo | Aceptada |
| ADR-004 | Maven Wrapper en lugar de Maven global | Aceptada |
| ADR-005 | Flyway y `ddl-auto: validate` | Aceptada |
| ADR-006 | Spring Data JPA como persistencia principal | Aceptada |
| ADR-007 | Monolito modular, no microservicios | Aceptada |
| ADR-008 | JWT con refresh rotatorio y Argon2id | Aceptada |
| ADR-009 | MVP reducido y alcance por fases | Aceptada |
| ADR-010 | PMP para valoración y lotes FIFO para caducidad | Aceptada |
| ADR-011 | Unidad base y `BigDecimal` para cantidades | Aceptada |
| ADR-012 | `establishment_id` desde la primera migración | Aceptada |
| ADR-013 | Paquete base `com.killa.cocina` | Aceptada |
| ADR-014 | Repositorio en `cocina/`, no en la carpeta de usuario | Aceptada |
| ADR-015 | Sin Lombok | Aceptada |
| ADR-016 | Política de contraseñas: longitud mínima, sin caducidad | Aceptada |
| ADR-017 | Bloqueo temporal de 15 minutos tras 5 intentos | Aceptada |
| ADR-018 | Refresh token en el cuerpo JSON | Aceptada |
| ADR-019 | Primer administrador por configuración, no por migración | Aceptada |
| ADR-020 | Casos de uso transaccionales en `infrastructure` tras un puerto de dominio | Aceptada |
| ADR-021 | Escalas y redondeo únicos para cantidades y costes | Aceptada |
| ADR-022 | Bloqueo pesimista por producto en cada movimiento de stock | Aceptada |
| ADR-023 | Conversiones por producto y rechazo entre dimensiones | Aceptada |
| ADR-024 | Política de stock negativo configurable por producto | Aceptada |
| ADR-025 | Lotes con caducidad opcional y ventana de aviso | Aceptada |
| ADR-026 | Motivo de ajuste y tipos de movimiento de F3 | Aceptada |
| ADR-027 | Los ajustes se valoran al PMP vigente y no lo modifican | Aceptada |
| ADR-028 | Con stock no positivo, la compra fija el PMP | Aceptada |
| ADR-029 | Fechas de recepción, caducidad y `occurred_at` del movimiento | Aceptada |
| ADR-030 | Los ajustes de salida consumen lotes FIFO, incluidos los caducados | Aceptada |
| ADR-031 | Una fila de auditoría por movimiento y otra si cambia el coste | Aceptada |
| ADR-032 | Spotless para el formato y JaCoCo en modo informe (P5) | Aceptada |
| ADR-033 | Cantidad por ración, merma aditiva y un único redondeo al escalar | Aceptada |
| ADR-034 | Borrador editable y versión publicada inmutable | Aceptada |
| ADR-035 | El coste se congela con los productos bloqueados en orden | Aceptada |
| ADR-036 | Plan por servicio, batch por ítem y estado derivado | Aceptada |
| ADR-037 | Puertos de producción, bloqueo ordenado e informes de consumo | Aceptada |
| ADR-038 | Rango de temperatura por lectura y alerta derivada | Aceptada |
| ADR-039 | La merma se valora al PMP y puede retirar lotes caducados | Aceptada |
| ADR-040 | Incidencias sin reapertura y trazabilidad por puertos publicados | Aceptada |
| ADR-041 | Los informes económicos viven en un módulo de solo lectura | Aceptada |
| ADR-042 | Seed demo por servicios y en una sola transacción | Aceptada |
| ADR-043 | Copias lógicas con `pg_dump` y restauración en base nueva | Aceptada |
| ADR-044 | Rendimiento medido con un check opt-in, no en CI | Aceptada |

---

## ADR-001 — Java 25 LTS + Spring Boot 4.1.x

**Contexto:** backend transaccional con reglas de negocio fuertes; el usuario tiene orientación y
experiencia en Java; hay Temurin 25 instalado.

**Opciones:** TypeScript/NestJS · Python/FastAPI · PHP/Laravel · Java/Spring Boot.

**Decisión:** Java 25 LTS con Spring Boot 4.1.x (compatible con Java 17–27 y con soporte de primera
clase para 25).

**Consecuencias:** ecosistema maduro, seguridad y transacciones de serie; algo más de ceremonia y
arranque más lento que Node. Java 21 queda como red de seguridad si alguna librería no soportara 25.

---

## ADR-002 — PostgreSQL 18 como base única

**Contexto:** stock, costes y trazabilidad exigen precisión numérica, transacciones e informes.

**Opciones:** PostgreSQL · MySQL · SQLite · MongoDB.

**Decisión:** PostgreSQL 18, única base de datos. Sin caché ni buscador externos en el MVP.

**Consecuencias:** `numeric` exacto, constraints, `JSONB` para auditoría e informes con funciones de
ventana. SQLite se descarta por concurrencia y multi-tenencia; MongoDB por integridad relacional.

---

## ADR-003 — Docker Compose para desarrollo

**Contexto:** se necesita un PostgreSQL reproducible sin instalarlo en el sistema; el despliegue
futuro también se beneficia de contenedores.

**Opciones:** PostgreSQL nativo · Docker Compose · servicio gestionado en la nube.

**Decisión:** Docker Compose con PostgreSQL 18 (volumen persistente + healthcheck). Dockerfile
multi-stage para la aplicación.

**Consecuencias:** entorno reproducible y aislado; requiere WSL2 y ~4 GB de RAM. Docker Desktop es
gratuito para uso personal y pequeñas empresas (<250 empleados y <10 M$ de ingresos). El volumen no
sustituye a los backups: `pg_dump` programado.

---

## ADR-004 — Maven Wrapper en lugar de Maven global

**Contexto:** no hay `mvn` en PATH; el usuario tiene `.m2`, señal de experiencia con Maven.

**Opciones:** instalar Maven global · Gradle · Maven Wrapper.

**Decisión:** Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`) commiteado.

**Consecuencias:** compilación reproducible sin instalar nada; Maven se descarga la primera vez.
Gradle se descarta por menor presencia en documentación de Spring, no por limitación técnica.

---

## ADR-005 — Flyway y `ddl-auto: validate`

**Contexto:** el esquema es un activo; Hibernate no debe modificarlo solo.

**Opciones:** `ddl-auto: update` · Flyway · Liquibase.

**Decisión:** Flyway con migraciones versionadas en `src/main/resources/db/migration` y
`ddl-auto: validate`.

**Consecuencias:** esquema auditable y reproducible; las migraciones aplicadas son inmutables. Coste:
cada cambio de entidad exige migración explícita.

---

## ADR-006 — Spring Data JPA como persistencia principal

**Contexto:** CRUD de dominio abundante y algunos informes agregados.

**Opciones:** JPA/Hibernate · jOOQ · MyBatis.

**Decisión:** JPA para el dominio; SQL nativo para informes; jOOQ valorable si el SQL se vuelve
inmanejable.

**Consecuencias:** desarrollo rápido y mapeo claro; hay que vigilar N+1, carga perezosa y consultas
implícitas. Los tests de integración usan PostgreSQL real (Testcontainers), no H2.

---

## ADR-007 — Monolito modular, no microservicios

**Contexto:** un restaurante, un equipo, un despliegue.

**Opciones:** monolito modular · microservicios · monolito sin fronteras.

**Decisión:** monolito modular con módulos por dominio y fronteras verificadas con ArchUnit.

**Consecuencias:** simplicidad operativa y transacciones locales; se evita el coste de red y
observabilidad distribuida. Si algún día hiciera falta separar, las fronteras ya están definidas.

---

## ADR-008 — JWT con refresh rotatorio y Argon2id

**Contexto:** API consumida por web y móvil; cuatro roles; sesiones de trabajo en cocina.

**Opciones:** sesiones de servidor · JWT · proveedor externo (Auth0/Clerk).

**Decisión:** access token JWT de ≤15 min, refresh token rotatorio con hash en base de datos,
contraseñas Argon2id y bloqueo tras intentos fallidos.

**Consecuencias:** API sin estado y compatible con móvil; hay que gestionar revocación y rotación.
Proveedor externo descartado por coste y dependencia.

---

## ADR-009 — MVP reducido y alcance por fases

**Contexto:** siete áreas funcionales son un producto de muchos meses; el riesgo principal es el
alcance.

**Decisión:** MVP con inventario, recetas, producción, permisos y calidad básica; el resto pasa a v1
o futuro (ver `docs/requisitos.md` §1).

**Consecuencias:** entrega antes y validación real en cocina. Se renuncia temporalmente a subrecetas,
checklists, informes avanzados y margen por plato.

---

## ADR-010 — PMP para valoración y lotes FIFO para caducidad

**Contexto:** hay que valorar stock y, a la vez, controlar caducidades por lote.

**Opciones:** FIFO puro · PMP puro · coste estándar.

**Decisión:** valoración por precio medio ponderado; consumo de lotes en orden FIFO por caducidad.

**Consecuencias:** coste estable y defendible; la trazabilidad por lote se mantiene. El coste de una
receta se congela al publicar su versión.

---

## ADR-011 — Unidad base y `BigDecimal`

**Contexto:** mezclar kg, g, l y ml con coma flotante produce errores de stock y de coste.

**Decisión:** cada producto tiene una unidad base (g, ml, ud); todo se almacena en ella con
`numeric(12,3)`. Dinero en `numeric(12,4)`. `BigDecimal` en Java, redondeo `HALF_UP` con escala
explícita.

**Consecuencias:** precisión y comparabilidad; las conversiones ocurren en el borde y se testean.

---

## ADR-012 — `establishment_id` desde la primera migración

**Contexto:** el MVP es de un solo restaurante, pero la visión contempla varios.

**Decisión:** todas las tablas de negocio llevan `establishment_id` NOT NULL desde el inicio; el MVP
opera con un único valor.

**Consecuencias:** añadir un segundo establecimiento no exige migración destructiva. No se implementa
aislamiento multi-tenant activo ni facturación por establecimiento.

---

## ADR-013 — Paquete base `com.killa.cocina`

**Decisión:** paquete raíz `com.killa.cocina`, módulos en `com.killa.cocina.modules.<dominio>`.

**Consecuencias:** nombres estables en migraciones, configuración y tests.

---

## ADR-014 — Repositorio en `cocina/`

**Contexto:** el repositorio detectado apunta a `C:\Users\Nevado_Negrete` (todo el perfil).

**Decisión:** repositorio independiente en `C:\Users\Nevado_Negrete\cocina`.

**Consecuencias:** historial limpio y `.gitignore` significativo. El repositorio de la carpeta de
usuario no se usa para el proyecto.

---

## ADR-015 — Sin Lombok

**Contexto:** reducir magia en el código y dependencias en el compilador.

**Decisión:** `record` para DTOs y código explícito en entidades; sin Lombok.

**Consecuencias:** más líneas, menos sorpresas y depuración directa. Si el volumen de mapeo crece, se
valorará MapStruct, no Lombok.

---

## ADR-016 — Política de contraseñas mínima y verificable

**Contexto:** NFR-SEG-01 exige política mínima y prohíbe registrar contraseñas; el personal de cocina
no va a usar gestores de contraseñas ni recordará reglas de composición.

**Opciones:** composición obligatoria (mayúsculas, símbolos, caducidad) · longitud mínima sin
caducidad · frases de paso con lista negra.

**Decisión:** mínimo 12 caracteres y máximo 128; se rechaza que la contraseña coincida con el usuario.
Sin caducidad periódica ni reglas de composición. Argon2id como función de hash.

**Consecuencias:** menos fricción y menos post-its junto al puesto; la longitud aporta más entropía que
la composición. Se renuncia a forzar símbolos. Si más adelante se añade lista negra de contraseñas
filtradas, se hará en `PasswordPolicy` sin tocar el contrato.

---

## ADR-017 — Bloqueo temporal tras cinco intentos fallidos

**Contexto:** NFR-SEG-01 pide bloqueo por intentos; bloquear para siempre exige intervención manual
del administrador y en una cocina eso significa dejar a alguien sin trabajar.

**Opciones:** bloqueo permanente · bloqueo temporal con caducidad · retardo progresivo sin bloqueo ·
CAPTCHA (descartado: no hay frontend en el MVP).

**Decisión:** cinco intentos fallidos bloquean la cuenta 15 minutos. El contador y `locked_until` viven
en `app_users`; al caducar, el siguiente acceso limpia ambos. Un login correcto reinicia el contador.
Los intentos se registran en auditoría (`LOGIN_FAILED`) y el bloqueo devuelve 423 `ACCOUNT_LOCKED`.

**Consecuencias:** el sistema se protege de fuerza bruta sin dejar cuentas muertas. Un atacante puede
bloquear a un compañero durante 15 minutos: se acepta el riesgo frente a la alternativa de no tener
límite. Los fallos se persisten aunque la petición termine en error (`noRollbackFor`), si no el
contador nunca subiría.

---

## ADR-018 — Refresh token en el cuerpo JSON

**Contexto:** NFR-SEG-02 exige refresh rotatorio revocable; el MVP no tiene frontend, solo consumidores
de API.

**Opciones:** cookie `HttpOnly` + `SameSite` · cuerpo JSON · cabecera propia.

**Decisión:** el refresh token viaja en el cuerpo JSON de `/auth/login`, `/auth/refresh` y
`/auth/logout`.

**Consecuencias:** el contrato es simple y funciona igual para un cliente móvil o de escritorio, y el
token no queda expuesto a CSRF porque no se envía automáticamente. El coste es que el cliente debe
guardarlo con cuidado; cuando exista frontend web se valorará cookie `HttpOnly` para el refresh.

---

## ADR-019 — Primer administrador por configuración, nunca por migración

**Contexto:** el sistema necesita un administrador inicial y una contraseña escrita en una migración
sería un secreto en el repositorio.

**Opciones:** usuario y contraseña fijos en la migración · endpoint público de primer arranque ·
variables de entorno consumidas por un `ApplicationRunner`.

**Decisión:** `AdminBootstrap` crea el administrador solo si no existe ningún usuario y
`BOOTSTRAP_ADMIN_PASSWORD` tiene valor; el usuario por defecto es `admin` y se puede cambiar con
`BOOTSTRAP_ADMIN_USERNAME`. El perfil `local` trae una contraseña de desarrollo explícitamente
marcada; el perfil por defecto no trae ninguna.

**Consecuencias:** ningún secreto en el repositorio y ningún endpoint de arranque expuesto. El
operador debe definir la variable una vez; si la olvida, el sistema arranca sin administrador y hay
que reiniciar con ella. El establecimiento semilla se crea de forma idempotente en el mismo paso y la
secuencia queda sincronizada.

---

## ADR-020 — Casos de uso transaccionales en `infrastructure` tras un puerto de dominio

**Contexto:** AGENTS.md §6 exige `api → domain` e `infrastructure → domain`, y el dominio sin Spring.
Los casos de uso necesitan `@Transactional`, el codificador de contraseñas y serialización.

**Opciones:** servicios de aplicación en `domain` con Spring (rompe la regla) · controladores con
lógica y repositorios inyectados (rompe la separación) · interfaces en `domain` e implementación
transaccional en `infrastructure`.

**Decisión:** la interfaz del caso de uso (`UserService`, `AuthService`) vive en `domain`; la
implementación con `@Service` y `@Transactional` vive en `infrastructure`. El dominio puede lanzar
`ApiException` (Java puro) que el manejador global traduce a HTTP.

**Consecuencias:** las reglas puras se prueban sin contexto y `api` no ve JPA. Hay un mapeo adicional
(dominio ↔ DTO ↔ entidad) y dos interfaces por módulo; a cambio, `ModuleBoundariesTest` se cumple sin
excepciones y el día que se cambie de persistencia solo se sustituye el adaptador.

---

## ADR-021 — Escalas y redondeo únicos para cantidades y costes

**Contexto:** el stock, el PMP y los costes de receta se calculan por caminos distintos. Si cada
servicio redondea a su manera, dos operaciones equivalentes dan saldos distintos y el kardex deja de
cuadrar.

**Opciones:** `double` (descartado por la regla 7.1) · `BigDecimal` redondeado en cada operación ·
`BigDecimal` con escala y redondeo centralizados.

**Decisión:** `Quantities` es la única definición de aritmética del inventario: cantidades
`numeric(12,3)` y costes `numeric(12,4)`, siempre `HALF_UP`. El factor de conversión usa
`numeric(18,6)` con la misma regla. El kardex guarda el coste unitario aplicado en cada movimiento,
de modo que un cambio posterior del PMP no reescribe el histórico.

**Consecuencias:** resultados deterministas y reproducibles entre servicios y tests; cualquier
cambio de redondeo se hace en un solo sitio. El redondeo sigue siendo una decisión de negocio: se
fija aquí y no se delega en la base de datos.

---

## ADR-022 — Bloqueo pesimista por producto en cada movimiento de stock

**Contexto:** NFR-INT-01 exige que dos descuentos simultáneos no dejen un saldo incorrecto. Leer el
stock, calcular el nuevo saldo y escribirlo sin protección permite perder actualizaciones.

**Opciones:** bloqueo optimista con `@Version` y reintentos · bloqueo pesimista de la fila del
producto (`SELECT … FOR UPDATE`) · aislamiento `SERIALIZABLE`.

**Decisión:** cada movimiento bloquea la fila de `products` dentro de su transacción
(`ProductJpaRepository.findForUpdate`) y calcula `balance_after` y `current_stock` en la misma
transacción. En F5, cuando una producción toca varios productos, se bloquean en orden de
`product_id` para evitar interbloqueos.

**Consecuencias:** los movimientos del mismo producto se serializan, lo que sobra para el volumen de
una cocina. No hace falta que el cliente reintente ni que la API exponga conflictos de versión. Hay
que respetar el orden de bloqueo cuando haya varios productos implicados.

---

## ADR-023 — Conversiones por producto y rechazo entre dimensiones

**Contexto:** INV-02 pide convertir unidades compatibles (g/kg, ml/l) y rechazar g → ml. Una tabla
global de unidades obliga a mantener factores que en la práctica son del producto (una caja de
tomate no pesa lo mismo que una de arroz).

**Opciones:** catálogo global de unidades con factores fijos · factor por producto hacia la unidad
base · convertir libremente y validar solo al mover stock.

**Decisión:** el factor se guarda por producto en `product_unit_conversions` y siempre apunta a su
unidad base. Las etiquetas conocidas tienen dimensión (g, kg, mg, oz, lb → masa; ml, l, cl, dl →
volumen; ud, u, unidad → unidad); declarar una conversión de otra dimensión devuelve
`UNIT_INCOMPATIBLE`. Las etiquetas propias del producto (`caja`, `saco`) se admiten y se asumen de
la dimensión del producto. La unidad base no se declara como conversión y no se puede cambiar.

**Consecuencias:** g → ml se rechaza también al configurar el producto, no solo al mover stock. Una
etiqueta inventada no se puede validar dimensionalmente: la responsabilidad es de quien configura el
catálogo. Cambiar la unidad base exige crear otro producto y ajustar el saldo.

---

## ADR-024 — Política de stock negativo configurable por producto

**Contexto:** INV-08 exige una política configurable. En una cocina hay productos que pueden quedar
momentáneamente en negativo (consumo antes de registrar la compra) y otros que nunca deberían.

**Opciones:** bloqueo global e inviolable · permitir siempre negativo · política por producto.

**Decisión:** `products.allow_negative` (por defecto `false`). Con el bloqueo activo, un movimiento
que dejaría saldo negativo devuelve **409** `INSUFFICIENT_STOCK` sin aplicar ningún cambio; la base
de datos lo respalda con la constraint `allow_negative OR current_stock >= 0`. Desde el bloque B de
F3, la comprobación se aplica en cada ajuste y movimiento.

**Consecuencias:** el catálogo puede marcar excepciones sin desactivar la protección general. Un
producto en negativo por excepción aparece en las alertas y debe regularizarse con una entrada.

---

## ADR-025 — Lotes con caducidad opcional y ventana de aviso

**Contexto:** CAL-01 y CAL-04 exigen trazabilidad por lote y bloqueo de caducados. No todos los
productos caducan: la sal, las conservas o el aceite no tienen una fecha útil.

**Opciones:** caducidad obligatoria · caducidad opcional · sin caducidad y solo alertas manuales.

**Decisión:** `stock_lots.expires_at` es opcional. Los lotes con fecha entran en la ventana de aviso
configurable (3 días por defecto). Un lote caducado no se consume en producción: el intento devuelve
**409** y no modifica stock. Los ajustes de inventario son la excepción (ADR-030).

**Consecuencias:** el modelo no obliga a inventar fechas. La ventana de aviso es configuración
operativa, no una constante del código, y se aplica en el bloque B (lotes y movimientos). El bloqueo
de caducados se activa con el consumo de producción en F5.

---

## ADR-026 — Motivo de ajuste y tipos de movimiento de F3

**Contexto:** INV-05 exige motivo en salidas y ajustes. Un catálogo cerrado de motivos añade
mantenimiento antes de conocer la operación real; un texto libre sin límites ensucia el kardex.

**Opciones:** catálogo cerrado de motivos · texto libre sin límites · texto libre acotado.

**Decisión:** `stock_movements.reason` es texto libre de 5 a 200 caracteres para ajustes y mermas.
Los tipos de movimiento de F3 son `PURCHASE_IN`, `ADJUSTMENT_IN` y `ADJUSTMENT_OUT`.
`PRODUCTION_OUT` se activa en F5 y `WASTE_OUT` en F6; el enum los define desde F3 pero no tienen
endpoint hasta su fase.

**Consecuencias:** la operación no queda bloqueada por un catálogo incompleto y el kardex conserva
un motivo legible. Cuando los motivos se repitan, se podrá normalizar en un catálogo sin romper el
histórico. El enum de tipos no admite valores sin fase asignada.

---

## ADR-027 — Los ajustes se valoran al PMP vigente y no lo modifican

**Contexto:** INV-05 exige ajustes manuales e INV-09 define el PMP como coste de las entradas. Un
ajuste puede entrar stock sin precio de compra, así que hay que decidir con qué coste se valora y si
ese coste puede alterar el PMP.

**Opciones:** valorar los ajustes a cero · valorar al PMP vigente sin modificarlo · valorar al PMP y
recalcularlo como si fuera una compra · exigir precio en el ajuste.

**Decisión:** `ADJUSTMENT_IN` se valora al PMP vigente del producto y **no** lo modifica;
`ADJUSTMENT_OUT` descuenta al PMP vigente. Solo `PURCHASE_IN` recalcula el PMP.

**Consecuencias:** un ajuste nunca cambia el coste medio por una vía sin factura, y el kardex conserva
el coste aplicado en cada fila. Si el saldo llega a cero, el PMP se mantiene como referencia hasta la
siguiente compra. Valorar a cero habría hundido el coste del producto; pedir precio habría convertido
un ajuste en una compra encubierta.

---

## ADR-028 — Con stock no positivo, la compra fija el PMP

**Contexto:** con `allow_negative=true` (ADR-024) un producto puede tener saldo negativo. La fórmula
del PMP divide por `stock_actual + cantidad`, que puede ser cero o negativo, y un saldo negativo no
representa valor recuperable.

**Opciones:** impedir compras mientras el saldo no sea positivo · usar el valor absoluto del saldo ·
dejar el PMP intacto · fijar el PMP al coste de la entrada.

**Decisión:** si `current_stock <= 0` antes de la compra, el nuevo PMP es el coste unitario de esa
compra. Con saldo positivo se aplica la media ponderada habitual.

**Consecuencias:** nunca se divide por cero ni se arrastra un coste sin sentido, y el producto vuelve
a tener una valoración fiable en cuanto se regulariza. El saldo negativo sigue siendo visible en el
kardex y en las alertas: la decisión afecta al coste, no oculta la deuda de stock.

---

## ADR-029 — Fechas de recepción, caducidad y `occurred_at` del movimiento

**Contexto:** el hecho puede registrarse más tarde que la recepción real (una factura del día
anterior). El kardex ordena por `occurred_at` y la caducidad depende de la recepción.

**Opciones:** exigir fecha de recepción · usar siempre el instante del registro · permitir fechas
pasadas y rechazar futuras.

**Decisión:** `receivedAt` es opcional y por defecto el instante actual; nunca puede ser futuro.
`expiresAt` es opcional, pero si existe debe ser posterior a `receivedAt`. El `occurred_at` del
movimiento es `receivedAt`, de modo que un registro tardío se coloca en su sitio del kardex. Los
ajustes se registran a la hora actual.

**Consecuencias:** se puede regularizar una recepción de ayer sin falsear el orden histórico. El
`balance_after` refleja el saldo en el momento del registro, no una reconstrucción del pasado: si se
inserta un movimiento retrodatado, el saldo de las filas posteriores no se recalcula (el kardex es
append-only). Para el MVP es aceptable porque la recepción se registra el mismo día del servicio.

---

## ADR-030 — Los ajustes de salida consumen lotes FIFO, incluidos los caducados

**Contexto:** CAL-04 bloquea el consumo de lotes caducados, pero un ajuste negativo por rotura o
recuento debe poder corregir el inventario aunque el stock esté caducado, o el saldo no cuadraría con
la realidad física.

**Opciones:** bloquear el ajuste si el lote está caducado · consumir solo lotes vigentes · consumir
FIFO sin excluir caducados.

**Decisión:** `ADJUSTMENT_OUT` consume lotes FIFO por caducidad y antigüedad, **incluidos los
caducados**. El bloqueo de CAL-04 se aplicará al consumo de producción (`PRODUCTION_OUT`) en F5, que
es donde el lote caducado no debe llegar al plato.

**Consecuencias:** el desglose de lotes sigue cuadrando con `current_stock` y un ajuste puede retirar
stock caducado, que es justo lo que hace una merma. El riesgo de consumir caducado por la vía de un
ajuste queda cubierto por el motivo obligatorio y la auditoría, no por el bloqueo.

---

## ADR-031 — Una fila de auditoría por movimiento y otra si cambia el coste

**Contexto:** la regla 7.8 exige auditar los ajustes de stock y los cambios de coste. Un movimiento
modifica varias cosas a la vez: saldo, lote y, en las compras, PMP.

**Opciones:** una fila por movimiento con todo · una fila por campo modificado · una fila por
movimiento más una específica de coste.

**Decisión:** cada compra o ajuste escribe una fila con la acción `STOCK_PURCHASE_RECORDED` o
`STOCK_ADJUSTMENT_RECORDED`, referida a `stock_movement` y con el stock y el coste antes y después. Si
el PMP cambia, se añade una fila `STOCK_COST_CHANGED` referida a `product`.

**Consecuencias:** se puede reconstruir el efecto de un movimiento con una sola consulta y, a la vez,
buscar cambios de coste sin recorrer todo el kardex. La auditoría duplica información derivable del
propio kardex a propósito: es un registro independiente y no debe depender de que el kardex se
interprete bien.

---

## ADR-032 — Spotless para el formato y JaCoCo en modo informe (P5)

**Contexto:** P5 quedó abierta al cerrar F1. `./mvnw verify` compilaba y ejecutaba tests, pero no
fijaba formato, análisis estático ni cobertura. Con F3 cerrada y F4 a la vista, el código ya es
suficiente para que el estilo se diversifique; corregirlo después cuesta más.

**Opciones:** no añadir nada (D) · Spotless + JaCoCo sin umbral (A) · A + umbral de cobertura y Maven
Enforcer (B) · B + Error Prone/NullAway (C).

**Decisión:** Spotless 3.10.4 con `palantir-java-format` 2.103.0 (4 espacios, 100 columnas), más
`trimTrailingWhitespace` y `endWithNewline`. La comprobación (`spotless:check`) se ejecuta en `verify`,
así que el CI la hereda sin cambios; `./mvnw spotless:apply` corrige. JaCoCo 0.8.16 genera el informe
en `verify` (`target/site/jacoco/index.html`), sin objetivo `check` ni umbral que rompa el build. El
primer reformateo va en un commit propio para no mezclar ruido con cambios funcionales.

**Consecuencias:** un solo estilo verificable y una cobertura visible desde el primer día, sin
introducir un servicio externo ni una cifra de cobertura inventada. El primer informe da 89,7 % de
instrucciones, 90,2 % de líneas y 66,1 % de ramas. Error Prone/NullAway queda fuera hasta comprobar su
compatibilidad con JDK 25 y Spring Boot 4.1; el umbral de cobertura se decidirá cuando haya datos de
varias fases.

---

## ADR-033 — Cantidad por ración, merma aditiva y un único redondeo al escalar

**Contexto:** REC-01 exige que cantidades y coste sean siempre por ración base y REC-02 que la merma
altere consumo y coste de forma verificable. REC-03 exige un escalado determinista (250 g × 3 = 750 g).
Hay dos formas de guardar la composición (por ración o por lote) y dos de aplicar la merma (sobre la
cantidad neta o sobre el bruto); elegirlas mal obliga a reinterpretar todos los datos después.

**Opciones:** cantidad del lote base (B) frente a cantidad por ración (A) · merma aditiva
`q × (1 + m/100)` frente a merma por rendimiento `q / (1 − m/100)`.

**Decisión:** la cantidad se guarda **por ración** en la unidad base del producto; `base_rations` es
el rendimiento del lote y `total_cost = cost_per_ration × base_rations`. La merma es **aditiva**: el
consumo por ración es `q × (1 + m/100)`. La expansión se calcula con aritmética exacta y se redondea
una sola vez al final, a 3 decimales; el coste, a 4 y `HALF_UP` (ADR-021). Una misma función
(`RecipeExpansion`) alimenta el escalado, el congelado de coste y, en F5, el descuento de producción.

**Consecuencias:** el coste por ración coincide literalmente con la suma de cantidad × coste unitario
(REC-04) y el escalado es reproducible al miligramo. La merma se explica como «un 10 % más» y no como
un descuento del bruto; eso queda documentado para no confundirla con el rendimiento de cocción
(`yield_pct`), que es informativo y no altera el coste.

---

## ADR-034 — Borrador editable y versión publicada inmutable

**Contexto:** REC-05 exige versionado inmutable para que una producción pasada siga siendo explicable.
Hay dos extremos: no versionar (editar en sitio siempre, perdiendo el histórico) o versionar todo al
mínimo cambio (ruido y fricción para quien cocina). Además, si una receta puede tener varias ramas de
borrador, «el borrador» deja de ser un concepto único y la API se vuelve ambigua.

**Opciones:** un borrador editable y publicadas inmutables (A) · varios borradores simultáneos (B) ·
sin borrador, solo versiones publicadas (C).

**Decisión:** (A). Cada receta tiene como máximo **un** `DRAFT`, garantizado por un índice único
parcial en BD, no solo por la aplicación. El borrador se edita en sitio con `PATCH` y `PUT items`; al
publicar pasa a `PUBLISHED` y queda inmutable en la aplicación y en la base de datos. `POST /versions`
clona la última versión con sus ingredientes. El borrador se puede descartar; la única versión de una
receta, no: para retirarla se archiva la receta, que conserva todo el historial.

**Consecuencias:** el flujo diario es barato (editar y publicar) sin perder trazabilidad, y dos
personas no pueden construir versiones paralelas sin acordarlo. La inmutabilidad no depende del código:
los triggers de V5 rechazan `UPDATE` y `DELETE` de una versión publicada y de sus ingredientes, incluso
por SQL directo. `recipes.current_version_id` marca la vigente; las publicadas anteriores siguen
`PUBLISHED` en el historial y no se reescriben.

---

## ADR-035 — El coste se congela con los productos bloqueados en orden

**Contexto:** publicar congela el PMP del momento. Si la lectura del coste y la escritura de la versión
no forman un corte consistente, una compra concurrente puede dejar el coste congelado calculado con
precios de dos instantes distintos. Si además dos publicaciones bloquean los mismos productos en orden
distinto, pueden interbloquearse.

**Opciones:** leer sin bloquear y aceptar el coste «casi» del momento (A) · bloquear en orden de
`product_id` (B) · bloquear toda la tabla de productos (C).

**Decisión:** (B). Antes de calcular, se bloquean con `SELECT … FOR UPDATE` los productos implicados,
siempre en orden ascendente de `product_id`, y el coste se calcula con esas filas ya bloqueadas, dentro
de la transacción que escribe la versión y `current_version_id`. Es el mismo criterio que el descuento
de producción de F5 (arquitectura §4.3), así que no hay dos políticas de bloqueo conviviendo.

**Consecuencias:** el coste congelado es reproducible y dos publicaciones simultáneas no se
interbloquean. El precio a pagar es que publicar bloquea durante un instante los productos de la receta
—aceptable porque publicar es una acción poco frecuente y de una sola persona— y no se puede publicar
mientras una compra mantiene el lock de un producto de esa receta: la segunda espera, no falla.

---

## ADR-036 — Plan por servicio, batch por ítem y estado derivado

**Contexto:** la producción se planifica para una fecha y un servicio, pero la ejecución real puede
empezar, terminar con menos raciones o cancelarse. Si el plan fuera también el registro de ejecución,
una corrección obligaría a reescribir el plan y se perdería el histórico. Si el estado del plan se
escribiera a mano, podría contradecir a sus ejecuciones.

**Opciones:** un único registro que cambia de estado (A) · plan de solo planificación y un batch de
ejecución por ítem (B) · varios batches simultáneos por ítem desde el MVP (C).

**Decisión:** (B). Un `production_plan` es único por `(establishment_id, service_date, service_type)`
e incluye ítems que fijan una versión **publicada** y las raciones previstas. Cada ítem tiene como
máximo un `production_batch` en el MVP. El batch nace en `IN_PROGRESS`; puede pasar a `READY` con las
raciones reales o a `CANCELLED`, y un `READY` puede pasar a `SERVED`. El stock se descuenta **solo** en
la transición a `READY`; cancelar no descuenta y, una vez listo, la corrección se hace con un ajuste,
no reescribiendo el batch. Si las raciones reales difieren de las previstas, el motivo es obligatorio
(5–200 caracteres) y se guarda con el batch.

El estado del plan (`PENDING`, `IN_PROGRESS`, `READY`, `SERVED`, `CANCELLED`) se recalcula desde sus
batches y se persiste como valor de filtro. Los ítems solo se sustituyen, y el plan solo se cancela o
borra, mientras está `PENDING`; los triggers de V6 congelan un ítem en cuanto tiene un batch y rechazan
borrar batches o modificar uno terminado salvo el paso `READY → SERVED`.

**Consecuencias:** el plan y la ejecución tienen responsabilidades separadas, el histórico es
reproducible y la base de datos impide estados imposibles. El coste es que el MVP no permite repetir
un mismo ítem en varios batches ni añadir ítems a un plan ya iniciado; ambas cosas pueden evolucionar
sin cambiar el modelo, porque el batch ya es la unidad de ejecución.

---

## ADR-037 — Puertos de producción, bloqueo ordenado e informes de consumo

**Contexto:** producción necesita leer recetas y productos y descontar stock. Si importara sus
entidades JPA, rompería las fronteras modulares (AGENTS.md §6). Además, una ejecución toca varios
productos y dos ejecuciones simultáneas podrían interbloquearse o sobrevender. Los informes deben
comparar el consumo teórico con el real sin cargar el kardex completo.

**Opciones:** llamar directamente a los repositorios JPA de recetas e inventario (A) · puertos de
dominio con adaptadores en infraestructura (B) · duplicar los datos necesarios en producción (C).

**Decisión:** (B). Producción define `RecipeCompositionCatalog` y `ProductCatalog`, implementados por
adaptadores que usan los puertos publicados de recetas e inventario. El descuento se delega en
`StockService.consumeForProduction`, que recibe el mapa de necesidades por producto y bloquea los
productos en orden ascendente de `product_id` antes de consumir lotes vigentes FIFO; un lote caducado
no se consume y un fallo de cualquier ingrediente revierte el batch completo. Los movimientos son
`PRODUCTION_OUT`, con `reference_type=production_batch` y `reference_id` del batch.

Los informes viven en el módulo `production` y son de solo lectura. El periodo se expresa en días de
negocio de `Europe/Madrid`, con un máximo de 31 días. El informe de desviaciones agrupa por el
`finished_at` del batch y solo lista desviaciones distintas de cero. El de consumo calcula, por
producto:

```text
teórico   = Σ cantidad_por_ración × raciones_reales          (sin merma, un solo redondeo)
producción = Σ PRODUCTION_OUT de los batches del periodo
merma      = producción − teórico
ajustes    = Σ ADJUSTMENT_OUT − Σ ADJUSTMENT_IN del periodo
real       = producción + ajustes
desviación = real − teórico = merma + ajustes
```

La comparación usa el consumo real de los batches terminados en el periodo y los ajustes por su
`occurred_at`, de modo que ambos caen en la misma ventana. Los informes no crean un módulo `reporting`
nuevo: reutilizan los puertos de producción e inventario y no escriben nada.

**Consecuencias:** las fronteras se verifican con ArchUnit, el orden de bloqueo es único en todo el
sistema y los informes cuadran por construcción. El coste es una consulta agregada de consumo y una
lectura filtrada de batches para desviaciones; además, la merma mostrada incluye el efecto del
redondeo de la expansión. Se acepta porque es la misma cifra que se descontó del stock y queda
documentada.

---

## ADR-038 — Rango de temperatura por lectura y alerta derivada

**Contexto:** CAL-02 exige registrar temperaturas de equipo y preparación y generar una alerta con
usuario y hora cuando la lectura está fuera de rango. Un rango configurable por equipo introduce una
tabla adicional, una pantalla de configuración que el MVP no tiene y el riesgo de reinterpretar el
pasado: si mañana cambia el rango de una cámara, las lecturas antiguas dejarían de ser comparables
con el criterio con el que se juzgaron.

**Opciones:** configuración por equipo (A) · rango fijo global por tipo (B) · rango en cada lectura
(C).

**Decisión:** (C). `temperature_logs` guarda `value_c`, `min_c` y `max_c`; `within_range` es una
columna generada por PostgreSQL con `value_c BETWEEN min_c AND max_c`. Una lectura fuera de rango no
es un error de validación: se persiste con `within_range=false` y aparece en
`GET /api/v1/quality/temperature-alerts` con actor y `recorded_at`. El rango debe ser creciente, las
temperaturas admiten dos decimales y `recorded_at` es opcional, nunca futura. La escritura se audita
como `TEMPERATURE_LOGGED`.

**Consecuencias:** cada lectura conserva su criterio y no hace falta configurar equipos en el MVP. El
coste es que el cliente debe enviar el rango en cada alta y que no se puede cambiar el criterio de una
lectura ya registrada; se corrige con otra lectura. Las alertas se limitan a las 100 más recientes
para que la consulta no crezca sin control.

---

## ADR-039 — La merma se valora al PMP y puede retirar lotes caducados

**Contexto:** CAL-03 exige registrar mermas con su coste y CAL-04 impide que un lote caducado se
consuma en producción. La merma es, precisamente, la vía para sacar del inventario un lote caducado o
en mal estado; si se le aplicara la misma prohibición que a producción, el stock caducado quedaría
atrapado y el inventario dejaría de ser real. Además, una merma puede abarcar varios lotes con costes
distintos y hay que decidir qué coste se congela.

**Opciones:** coste del lote concreto (A) · PMP vigente del producto (B) · coste medio de los lotes
consumidos (C). Para los caducados: bloquearlos también en la merma (A) o permitir retirarlos (B).

**Decisión:** el coste de la cabecera es el **PMP vigente** del producto en el momento de la merma,
congelado como `unit_cost` y multiplicado por la cantidad para obtener `cost` (escala 4, ADR-021). Cada
lote consumido deja su propio movimiento `WASTE_OUT` con el coste de ese lote, de modo que el kardex
sigue siendo reconstruible aunque la cabecera use el PMP. La merma consume el lote indicado o, sin
lote, los lotes con saldo en orden FIFO **incluidos los caducados**; producción mantiene
`findUsable`, que los excluye. Si el producto no permite saldo negativo y no hay existencias
suficientes, responde 409 `INSUFFICIENT_STOCK` sin escribir nada.

Para obtener el id que referencian los movimientos, la cabecera se inserta provisionalmente con coste
cero y se completa con el PMP en la misma transacción; un fallo de stock revierte también la cabecera.
La operación se audita como `WASTE_RECORDED` con el stock anterior y posterior.

**Consecuencias:** el coste de la merma es comparable con el resto de valoraciones del producto y no
depende de qué lote eligió el FIFO, mientras el kardex conserva el detalle real por lote. Se acepta la
inserción provisional a cambio de no inventar un identificador en la aplicación. Un lote caducado ya no
queda atrapado en el inventario: se retira con merma, que es justo la operación que CAL-04 quiere dejar
registrada.

---

## ADR-040 — Incidencias sin reapertura y trazabilidad por puertos publicados

**Contexto:** CAL-05 exige incidencias con severidad, estado y resolución, y CAL-01 exige responder qué
preparaciones consumieron un lote y cuándo. Una incidencia puede reabrirse si el problema reaparece,
pero el histórico debe seguir siendo fiable. La trazabilidad cruza calidad, inventario y producción, y
calidad no puede leer las tablas de producción directamente.

**Opciones:** reapertura con historial de estados (A) · cierre definitivo y nueva incidencia si
reaparece (B). Para la trazabilidad: consulta SQL que una las tablas de los tres módulos (A) · puerto
de producción resuelto por un adaptador de calidad (B).

**Decisión:** la incidencia nace en `OPEN` y solo admite `OPEN → IN_PROGRESS`, `OPEN → CLOSED` e
`IN_PROGRESS → CLOSED`. Volver a `OPEN` es un error de validación y cualquier cambio desde `CLOSED` es
un conflicto: el cierre es definitivo. La descripción y la severidad se fijan al abrir; solo cambian el
estado y, al cerrar, la resolución, que es obligatoria en ese momento. La apertura se audita como
`INCIDENT_OPENED` y cada transición como `INCIDENT_STATUS_CHANGED` con el estado anterior y posterior.

La trazabilidad parte de los movimientos `PRODUCTION_OUT` del lote en el kardex y resuelve batch → ítem
de plan → plan y versión publicada con los puertos de producción (`ProductionBatchRepository`,
`ProductionPlanItemRepository`, `ProductionPlanRepository` y `RecipeCompositionCatalog`). El resultado
se expone como `GET /api/v1/stock-lots/{lotId}/traceability` con `quality:read`; un lote sin consumos
devuelve la lista vacía y un lote inexistente, 404. No se añade un endpoint de lectura de auditoría en
F6.

**Consecuencias:** el histórico conserva un cierre por incidencia y una recaída se registra como
incidencia nueva, lo que evita reescribir fechas y resoluciones pasadas. La trazabilidad no duplica el
modelo de producción ni acopla calidad a sus entidades JPA; el precio es una resolución por movimiento
que se apoya en índices ya existentes y en el número acotado de consumos de un lote. Si en el futuro se
quiere reabrir, habrá que añadir un histórico de estados y revisar el trigger de cierre.

---

## ADR-041 — Los informes económicos viven en un módulo de solo lectura

**Contexto:** F7 exige el informe de costes por versión (ECO-01) y el de productos en riesgo de
caducidad con valor estimado (ECO-06). Los datos ya existen: las versiones publicadas guardan el coste
congelado y los lotes guardan saldo, coste y caducidad. La arquitectura declara un módulo `reporting`
que aún no tenía código, y los endpoints existentes cubren el detalle de una versión y las alertas
operativas, pero no una vista agregada con evolución ni un horizonte configurable.

**Opciones:** añadir los informes al módulo que ya tiene los datos (A) · crear `reporting` con puertos de
lectura hacia recetas e inventario (B) · consultar las tablas con SQL y vistas materializadas (C).

**Decisión:** se crea el módulo `reporting` con un `ReportingService` y dos puertos:
`RecipeCostCatalog` y `ExpiryRiskCatalog`. Sus adaptadores se apoyan en los repositorios de dominio de
recetas e inventario, nunca en entidades JPA ajenas, igual que producción (ADR-037). No hay escrituras,
tablas nuevas ni migración.

El informe de costes lee el coste congelado de cada versión publicada y calcula la variación frente a la
versión anterior de la misma receta. No recalcula el pasado con el PMP de hoy: el coste actual de una
versión sigue consultándose en su detalle (`current*`), y el histórico conserva los precios de su
momento. El informe de caducidades acepta un horizonte de 1 a 90 días (7 por defecto), ordena por
caducidad y devuelve hasta 500 lotes; `totalLots` y `totalValue` se calculan en SQL sobre todos los
lotes en riesgo para que el truncado no falsee el total. Ambos endpoints exigen `reporting:read`.

**Consecuencias:** los informes no duplican la aritmética de costes ni pueden desviarse del kardex, y el
módulo queda preparado para las exportaciones futuras sin acoplar recetas o inventario a la capa web de
otro dominio. El precio es una consulta agregada más en el puerto de versiones publicadas y otra de
suma en el de lotes. Si el volumen crece, el siguiente paso natural son vistas de lectura o caché, no
recalcular en cada petición.

---

## ADR-042 — Seed demo por servicios y en una sola transacción

**Contexto:** PLA-05 exige levantar una base vacía con un comando y datos que permitan probar el
producto. Un seed por SQL sería rápido, pero duplicaría las reglas de negocio (PMP, versiones
publicadas, auditoría, consumo de lotes) y podría crear datos que la aplicación nunca produciría. Un
seed por servicios es más lento, pero garantiza que el ejemplo pasa por las mismas validaciones que un
usuario real.

**Opciones:** `INSERT` directos en una migración (A) · servicios de dominio en un `ApplicationRunner`
con perfil propio (B) · ficheros de fixtures restaurados con `pg_restore` (C).

**Decisión:** `DemoDataSeeder`, activo solo con el perfil `demo`, ejecutado tras `AdminBootstrap`
(`@Order(100)` frente a `@Order(0)`) y anotado con `@Transactional`: o se siembra todo, o no queda
nada. Usa los servicios de usuarios, catálogo, stock, recetas, producción y calidad, y se salta si ya
hay productos. La contraseña de los usuarios demo se toma de `cocina.demo.password`
(`Demo2026!segura` por defecto) y **no** se escribe en el log.

**Consecuencias:** el seed tarda unos segundos y no puede ejecutarse sobre una base con datos; a
cambio, el resultado respeta la auditoría, el coste congelado y el kardex, y una interrupción no deja
un estado a medias que el guard confundiría con una siembra completa. Para volver a sembrar hay que
recrear la base (`docker compose down -v`), decisión deliberada: no se borran datos de negocio desde la
aplicación.

---

## ADR-043 — Copias lógicas con `pg_dump` y restauración en base nueva

**Contexto:** NFR-DIS-02 exige copia diaria y restauración probada. El MVP tiene una única base
PostgreSQL en un contenedor y el despliegue definitivo sigue pendiente (P1). Una copia física con
archivado de WAL daría PITR, pero exige infraestructura y operación que todavía no existen.

**Opciones:** copia física + WAL (A) · instantáneas del proveedor gestionado (B) · volcado lógico
`pg_dump -Fc` con scripts y prueba de restauración (C).

**Decisión:** volcado lógico diario en formato *custom* con `pg_dump -Fc`, suma SHA-256 y retención de
30 días; restauración con `pg_restore --no-owner` en una base **nueva** (nunca sobre la de trabajo),
con recuentos y arranque real como verificación. Los scripts viven en `scripts/backup.sh` y
`scripts/restore.sh`; `backups/` está en `.gitignore`. La programación y la copia externa se deciden con
P1.

**Consecuencias:** el RPO es de 24 h y no hay PITR; el RTO depende del tamaño de la base y de la
provisión del entorno, no del volcado. La restauración es portable entre versiones menores y permite
recuperar una tabla o una base completa. Los volcados contienen datos personales: deben guardarse
cifrados y con acceso restringido, y la copia externa sigue pendiente.

---

## ADR-044 — Rendimiento medido con un check opt-in, no en CI

**Contexto:** NFR-REN-01 y NFR-REN-02 exigen latencias con volúmenes concretos. Un *benchmark* en cada
`./mvnw verify` duplicaría el tiempo de la suite y sería inestable en máquinas compartidas; añadir
JMeter o Gatling incorpora una herramienta nueva para una necesidad puntual.

**Opciones:** *benchmark* siempre en CI (A) · herramienta externa de carga (B) · comprobación JUnit
opt-in contra la API real y Testcontainers (C).

**Decisión:** `PerformanceBaselineCheck`, una clase que no coincide con los patrones de Surefire y que
además exige `-Dcocina.performance=true`. Levanta PostgreSQL 18 y la API con Tomcat en un puerto
aleatorio, siembra 100 000 movimientos y 100 recetas, mide con 10 clientes concurrentes por HTTP y
afirma los p95 del requisito. Los resultados se registran en `docs/rendimiento.md`.

**Consecuencias:** la suite normal no se alarga ni depende de la carga de la máquina, pero la medición
es manual y hay que repetirla al tocar rutas calientes o índices. Es una referencia de regresión, no
una prueba de resistencia ni un SLA: el rendimiento en producción se valida en P1 con hardware real.
