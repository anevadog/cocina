package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.StockService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alertas operativas del inventario (INV-07, ECO-06): mínimo y caducidad próxima.
 *
 * <p>No se pagina: cada lista se limita a 100 elementos y la respuesta incluye el total real.</p>
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryAlertController {

    private final StockService stockService;

    private final CurrentActor currentActor;

    public InventoryAlertController(StockService stockService, CurrentActor currentActor) {
        this.stockService = stockService;
        this.currentActor = currentActor;
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('inventory:read')")
    public InventoryAlertsResponse alerts() {
        CurrentActor.Actor actor = currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
        return InventoryAlertsResponse.from(stockService.alerts(actor.establishmentId()));
    }
}
