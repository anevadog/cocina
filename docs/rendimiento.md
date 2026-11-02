# Revisión de rendimiento (NFR-REN-01, NFR-REN-02)

> Medición de referencia del bloque B de F7. Los números son de una máquina de desarrollo, no de
> producción; sirven para detectar regresiones y para justificar el diseño, no como compromiso de SLA.
> Última medición: 2026-11-02.

## 1. Qué exige el requisito

| ID | Requisito | Umbral |
|---|---|---|
| NFR-REN-01 | Latencia de CRUD con 10 usuarios y 100 000 movimientos | p95 < 300 ms |
| NFR-REN-02 | Cálculo de necesidades de producción con 100 recetas y 500 raciones | < 2 s |

## 2. Cómo se mide

La comprobación es una prueba **opt-in** que no se ejecuta en `./mvnw verify`:

```bash
./mvnw -Dtest=PerformanceBaselineCheck -Dcocina.performance=true test
```

En Windows: `.\mvnw.cmd -Dtest=PerformanceBaselineCheck -Dcocina.performance=true test`.

`PerformanceBaselineCheck` levanta PostgreSQL 18 con Testcontainers y la API real con Tomcat en un
puerto aleatorio. No mide llamadas directas al servicio: hace peticiones HTTP autenticadas con JWT,
así que el percentil incluye seguridad, serialización, JPA y base de datos.

1. **Datos sintéticos**: se insertan por SQL (no por los servicios) para llegar a 100 000 movimientos
   en segundos. Las reglas de negocio de esos datos ya están cubiertas por los tests de integración;
   aquí solo importa el volumen.
2. **Calentamiento**: 5 ciclos por endpoint antes de medir (JIT, pool de conexiones y cachés).
3. **Carga**: 10 clientes concurrentes × 40 ciclos. En cada ciclo se llama a cuatro lecturas:
   `GET /products`, `GET /products/{id}/movements`, `GET /inventory/alerts` y
   `GET /reports/recipe-costs`. Total: 1 600 peticiones, 400 muestras por endpoint.
4. **Escritura**: otros 10 clientes × 40 ajustes de stock concurrentes sobre el mismo producto,
   400 muestras. Es el caso más pesimista para los bloqueos de fila.
5. **Percentiles**: nearest-rank sobre la muestra ordenada, sin descartar valores atípicos.

### Volumen sembrado

| Dato | Cantidad |
|---|---:|
| Productos | 20 |
| Lotes | 100 |
| Movimientos de stock | 100 000 (sobre el producto 1) |
| Recetas publicadas | 100 |
| Ingredientes por receta | 5 |
| Ítems del plan | 100 |
| Raciones planificadas | 500 (5 por receta) |

### Entorno de la medición

| Elemento | Valor |
|---|---|
| Máquina | Windows, Docker Desktop, 13,8 GB de memoria disponibles para el motor |
| JDK | Temurin 25.0.4.1 |
| PostgreSQL | 18.6 en contenedor `postgres:18` |
| API | Spring Boot 4.1.2, Tomcat 11, Hikari con 10 conexiones |

## 3. Resultados

### NFR-REN-01 — lecturas con 10 clientes y 100 000 movimientos

| Endpoint | p50 | p95 | p99 | máx |
|---|---:|---:|---:|---:|
| `GET /api/v1/products?size=20` | 17 ms | **30 ms** | 33 ms | 42 ms |
| `GET /api/v1/products/{id}/movements?size=20` | 46 ms | **55 ms** | 59 ms | 73 ms |
| `GET /api/v1/inventory/alerts` | 4 ms | **9 ms** | 10 ms | 12 ms |
| `GET /api/v1/reports/recipe-costs` | 59 ms | **93 ms** | 99 ms | 104 ms |

### NFR-REN-01 — escritura concurrente (ajuste de stock)

| Operación | p50 | p95 | p99 | máx |
|---|---:|---:|---:|---:|
| `POST /api/v1/products/{id}/adjustments` (10 clientes) | 37 ms | **50 ms** | 55 ms | 61 ms |

### NFR-REN-02 — necesidades de producción

| Operación | p50 | p95 | máx | Umbral |
|---|---:|---:|---:|---:|
| `GET /api/v1/production-plans/{id}/requirements` (100 recetas, 500 raciones) | 129 ms | **136 ms** | 137 ms | 2 000 ms |

Todos los percentiles quedan por debajo del umbral, con margen amplio: el peor caso de lectura
(`recipe-costs`, 93 ms) usa menos de un tercio del presupuesto de 300 ms, y las necesidades de
producción usan el 7 % del umbral de 2 s.

## 4. Lectura de los resultados

- **Kardex**: 55 ms de p95 con 100 000 movimientos confirma que `ix_stock_movements_product
  (product_id, occurred_at DESC, id DESC)` cubre el listado paginado y que el `count` no obliga a
  recorrer la tabla. Es el endpoint más sensible al volumen y el que primero se degradaría.
- **Informe de costes**: 93 ms para 100 versiones publicadas con 500 ingredientes es el coste de leer
  y agrupar en memoria la evolución de cada receta. Con 1 000 versiones el tiempo crecería de forma
  lineal; si eso ocurre, el siguiente paso es una vista de lectura o materializar el importe por
  versión, no recalcular con el PMP actual (ADR-041).
- **Alertas**: 9 ms porque el volumen sembrado tiene 20 productos y 100 lotes. La consulta recorre
  lotes con saldo y caducidad; con decenas de miles de lotes hay que volver a medir este endpoint en
  concreto, aunque el requisito solo fija el volumen de movimientos.
- **Escritura**: 50 ms de p95 con 10 clientes sobre el mismo producto indica que el bloqueo pesimista
  de la fila (ADR-022) no crea una cola apreciable a esta escala; el ajuste sigue siendo una
  transacción corta.
- **Necesidades**: 136 ms con 100 recetas y 500 raciones; la agregación se hace con una sola consulta
  y un único redondeo por producto (ADR-036). Hay margen de más de 10× antes de acercarse a 2 s.

## 5. Límites de esta revisión

1. Una sola ejecución y una sola máquina: los percentiles son una referencia, no una garantía. El
   ruido del entorno (antivirus, Docker, otras apps) puede moverlos decenas de milisegundos.
2. No hay prueba de resistencia (soak) ni de memoria: no se busca fuga de recursos, solo latencia.
3. El dataset se inserta por SQL; no incluye el coste de mantener conversiones, auditoría ni lotes
   coherentes con `current_stock`, porque eso ya lo cubren los tests funcionales.
4. No se mide la latencia de escritura de recetas ni de producción completa (solo un ajuste), que
   tienen más trabajo transaccional. Si se quiere ampliar, se añaden escenarios al mismo check.
5. El check no es un *gate* de CI: duplica el tiempo de la suite y depende de los recursos del
   *runner*. Se ejecuta antes de cerrar un bloque que toque rutas calientes o esquema.

## 6. Cuándo repetirla

- Al cambiar índices, consultas de kardex, alertas o informes.
- Al añadir una migración que toque tablas grandes (`stock_movements`, `stock_lots`,
  `recipe_versions`).
- Antes de desplegar en producción (P1), sobre el hardware real y con una copia anonimizada.
- Si en producción el p95 de una ruta supera 150 ms de forma sostenida: hay margen, pero es la señal
  para revisar el plan de consulta antes de que llegue a 300 ms.
