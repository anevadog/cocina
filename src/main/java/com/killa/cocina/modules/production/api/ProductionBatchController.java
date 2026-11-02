package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ejecución de la producción (PRD-03..05).
 *
 * <p>Cada ítem de plan admite un batch. Empezar no descuenta stock; terminar con {@code READY} sí,
 * y cancelar antes de terminar no deja rastro en el kardex. Tras {@code READY} solo se puede pasar a
 * {@code SERVED}: cualquier corrección de consumo se hace con un ajuste de inventario.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class ProductionBatchController {

    private final ProductionService productionService;

    private final CurrentActor currentActor;

    public ProductionBatchController(ProductionService productionService, CurrentActor currentActor) {
        this.productionService = productionService;
        this.currentActor = currentActor;
    }

    @PostMapping("/production-plans/{planId}/items/{itemId}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('production:write')")
    public ProductionBatchResponse start(@PathVariable long planId, @PathVariable long itemId) {
        CurrentActor.Actor actor = requireActor();
        return ProductionBatchResponse.from(
                productionService.startBatch(actor.establishmentId(), actor.userId(), planId, itemId));
    }

    @GetMapping("/production-batches/{batchId}")
    @PreAuthorize("hasAuthority('production:read')")
    public ProductionBatchResponse find(@PathVariable long batchId) {
        CurrentActor.Actor actor = requireActor();
        return ProductionBatchResponse.from(productionService.findBatch(actor.establishmentId(), batchId));
    }

    @PatchMapping("/production-batches/{batchId}")
    @PreAuthorize("hasAuthority('production:write')")
    public ProductionBatchResponse transition(
            @PathVariable long batchId, @Valid @RequestBody UpdateProductionBatchRequest request) {
        CurrentActor.Actor actor = requireActor();
        return ProductionBatchResponse.from(productionService.transitionBatch(
                actor.establishmentId(),
                actor.userId(),
                batchId,
                request.status(),
                request.rations(),
                request.deviationReason()));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
