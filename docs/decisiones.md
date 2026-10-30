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
