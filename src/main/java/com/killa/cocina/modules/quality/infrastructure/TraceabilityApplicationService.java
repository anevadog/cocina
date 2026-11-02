package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.inventory.domain.Product;
import com.killa.cocina.modules.inventory.domain.ProductRepository;
import com.killa.cocina.modules.inventory.domain.StockLot;
import com.killa.cocina.modules.inventory.domain.StockLotRepository;
import com.killa.cocina.modules.quality.domain.LotTraceabilityView;
import com.killa.cocina.modules.quality.domain.ProductionTraceCatalog;
import com.killa.cocina.modules.quality.domain.TraceabilityService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de trazabilidad de lotes (CAL-01).
 *
 * <p>El lote debe existir en el establecimiento; sus consumos se leen del kardex y se resuelven con
 * {@link ProductionTraceCatalog}. Un lote sin consumos devuelve la lista vacía.</p>
 */
@Service
@Transactional(readOnly = true)
public class TraceabilityApplicationService implements TraceabilityService {

    private final StockLotRepository lots;

    private final ProductRepository products;

    private final ProductionTraceCatalog production;

    public TraceabilityApplicationService(
            StockLotRepository lots, ProductRepository products, ProductionTraceCatalog production) {
        this.lots = lots;
        this.products = products;
        this.production = production;
    }

    @Override
    public LotTraceabilityView traceLot(long establishmentId, long lotId) {
        StockLot lot = lots.findById(establishmentId, lotId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "El lote " + lotId + " no existe."));
        Product product = products.findById(establishmentId, lot.productId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND, "El producto " + lot.productId() + " no existe."));

        return new LotTraceabilityView(
                lot.id(),
                lot.productId(),
                product.name(),
                product.baseUnit().label(),
                lot.receivedAt(),
                lot.expiresAt(),
                lot.initialQty(),
                lot.remainingQty(),
                production.consumptionOfLot(establishmentId, lotId));
    }
}
