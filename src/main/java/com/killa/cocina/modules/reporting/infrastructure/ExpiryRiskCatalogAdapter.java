package com.killa.cocina.modules.reporting.infrastructure;

import com.killa.cocina.modules.inventory.domain.StockLotRepository;
import com.killa.cocina.modules.reporting.domain.ExpiringLotSnapshot;
import com.killa.cocina.modules.reporting.domain.ExpiryRiskCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Adaptador del puerto {@link ExpiryRiskCatalog} sobre el puerto publicado de inventario (ECO-06). */
@Component
class ExpiryRiskCatalogAdapter implements ExpiryRiskCatalog {

    private final StockLotRepository lots;

    ExpiryRiskCatalogAdapter(StockLotRepository lots) {
        this.lots = lots;
    }

    @Override
    public List<ExpiringLotSnapshot> expiringLots(long establishmentId, Instant until, int limit) {
        return lots.findExpiring(establishmentId, until, limit).stream()
                .map(row -> new ExpiringLotSnapshot(
                        row.lotId(),
                        row.productId(),
                        row.productName(),
                        row.expiresAt(),
                        row.remainingQty(),
                        row.unitCost()))
                .toList();
    }

    @Override
    public long countExpiring(long establishmentId, Instant until) {
        return lots.countExpiring(establishmentId, until);
    }

    @Override
    public BigDecimal valueExpiring(long establishmentId, Instant until) {
        return lots.valueExpiring(establishmentId, until);
    }
}
