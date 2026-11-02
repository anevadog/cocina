package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionService;
import com.killa.cocina.modules.production.domain.ProductionStatus;
import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import com.killa.cocina.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Planes de producción por fecha y servicio (PRD-01..02).
 *
 * <p>El plan fija las versiones publicadas de las recetas. Mientras está pendiente se pueden
 * sustituir sus ítems, cancelarlo o borrarlo; en cuanto empieza la ejecución, el histórico manda.</p>
 */
@RestController
@RequestMapping("/api/v1/production-plans")
@Validated
public class ProductionPlanController {

    private final ProductionService productionService;

    private final CurrentActor currentActor;

    public ProductionPlanController(ProductionService productionService, CurrentActor currentActor) {
        this.productionService = productionService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('production:read')")
    public PageResponse<ProductionPlanResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(
                productionService.list(actor.establishmentId(), serviceDate, page, size), ProductionPlanResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('production:write')")
    public ProductionPlanResponse create(@Valid @RequestBody CreateProductionPlanRequest request) {
        CurrentActor.Actor actor = requireActor();
        return ProductionPlanResponse.from(
                productionService.create(actor.establishmentId(), actor.userId(), request.toDomain()));
    }

    @GetMapping("/{planId}")
    @PreAuthorize("hasAuthority('production:read')")
    public ProductionPlanResponse find(@PathVariable long planId) {
        CurrentActor.Actor actor = requireActor();
        return ProductionPlanResponse.from(productionService.find(actor.establishmentId(), planId));
    }

    @PatchMapping("/{planId}")
    @PreAuthorize("hasAuthority('production:write')")
    public ProductionPlanResponse update(
            @PathVariable long planId, @Valid @RequestBody UpdateProductionPlanRequest request) {
        if (request.status() != ProductionStatus.CANCELLED) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation(
                            "status", "solo se admite CANCELLED; el resto de estados los fija la ejecución")));
        }
        CurrentActor.Actor actor = requireActor();
        return ProductionPlanResponse.from(productionService.cancel(actor.establishmentId(), actor.userId(), planId));
    }

    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('production:write')")
    public void delete(@PathVariable long planId) {
        CurrentActor.Actor actor = requireActor();
        productionService.delete(actor.establishmentId(), actor.userId(), planId);
    }

    @PutMapping("/{planId}/items")
    @PreAuthorize("hasAuthority('production:write')")
    public ProductionPlanResponse replaceItems(
            @PathVariable long planId, @Valid @RequestBody ReplaceProductionItemsRequest request) {
        CurrentActor.Actor actor = requireActor();
        return ProductionPlanResponse.from(productionService.replaceItems(
                actor.establishmentId(),
                actor.userId(),
                planId,
                request.items().stream().map(ProductionItemRequest::toDomain).toList()));
    }

    @DeleteMapping("/{planId}/items/{itemId}")
    @PreAuthorize("hasAuthority('production:write')")
    public ProductionPlanResponse deleteItem(@PathVariable long planId, @PathVariable long itemId) {
        CurrentActor.Actor actor = requireActor();
        return ProductionPlanResponse.from(
                productionService.deleteItem(actor.establishmentId(), actor.userId(), planId, itemId));
    }

    @GetMapping("/{planId}/requirements")
    @PreAuthorize("hasAuthority('production:read')")
    public ProductionRequirementsResponse requirements(@PathVariable long planId) {
        CurrentActor.Actor actor = requireActor();
        return ProductionRequirementsResponse.from(productionService.requirements(actor.establishmentId(), planId));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
