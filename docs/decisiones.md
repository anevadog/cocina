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
