package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;

/**
 * Resultado del descuento de stock por una merma (CAL-03).
 *
 * <p>La cabecera de la merma se valora al PMP vigente; los movimientos {@code WASTE_OUT} de cada
 * lote conservan el coste del lote para que el kardex siga siendo reconstruible.</p>
 */
public record WasteConsumption(
        long productId,
        String productName,
        BigDecimal quantityBase,
        BigDecimal unitCost,
        BigDecimal cost,
        BigDecimal stockBefore,
        BigDecimal stockAfter,
        BigDecimal avgCost) {}
