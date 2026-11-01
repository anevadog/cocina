package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.AdjustmentRegistration;
import com.killa.cocina.modules.inventory.domain.PurchaseRegistration;
import com.killa.cocina.modules.inventory.domain.StockService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import com.killa.cocina.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Movimientos de stock de un producto (INV-04, INV-05, INV-06).
 *
 * <p>El kardex es de solo lectura: no existe endpoint de edición ni borrado, y la base de datos
 * rechaza ambos (V4). Las correcciones se hacen con un ajuste con motivo.</p>
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class StockController {

    private final StockService stockService;

    private final CurrentActor currentActor;

    public StockController(StockService stockService, CurrentActor currentActor) {
        this.stockService = stockService;
        this.currentActor = currentActor;
    }

    @PostMapping("/{productId}/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:write')")
    public PurchaseResponse purchase(@PathVariable long productId, @Valid @RequestBody PurchaseRequest request) {
        CurrentActor.Actor actor = requireActor();
        return PurchaseResponse.from(stockService.purchase(
                actor.establishmentId(),
                actor.userId(),
                productId,
                new PurchaseRegistration(
                        request.supplierId(),
                        request.quantity(),
                        request.unit(),
                        request.unitPrice(),
                        request.receivedAt(),
                        request.expiresAt())));
    }

    @PostMapping("/{productId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:write')")
    public AdjustmentResponse adjust(@PathVariable long productId, @Valid @RequestBody AdjustmentRequest request) {
        CurrentActor.Actor actor = requireActor();
        return AdjustmentResponse.from(stockService.adjust(
                actor.establishmentId(),
                actor.userId(),
                productId,
                new AdjustmentRegistration(request.type(), request.quantity(), request.unit(), request.reason())));
    }

    @GetMapping("/{productId}/movements")
    @PreAuthorize("hasAuthority('inventory:read')")
    public PageResponse<StockMovementResponse> movements(
            @PathVariable long productId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(
                stockService.movements(actor.establishmentId(), productId, page, size), StockMovementResponse::from);
    }

    @GetMapping("/{productId}/lots")
    @PreAuthorize("hasAuthority('inventory:read')")
    public PageResponse<StockLotResponse> lots(
            @PathVariable long productId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(
                stockService.lots(actor.establishmentId(), productId, page, size), StockLotResponse::from);
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
