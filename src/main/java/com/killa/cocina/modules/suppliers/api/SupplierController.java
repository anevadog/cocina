package com.killa.cocina.modules.suppliers.api;

import com.killa.cocina.modules.suppliers.domain.SupplierModification;
import com.killa.cocina.modules.suppliers.domain.SupplierRegistration;
import com.killa.cocina.modules.suppliers.domain.SupplierService;
import com.killa.cocina.modules.suppliers.domain.SupplierView;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proveedores.
 *
 * <p>Nunca se borran: se desactivan con {@code PATCH active=false} para conservar la trazabilidad
 * de los lotes que suministraron.</p>
 */
@RestController
@RequestMapping("/api/v1/suppliers")
@Validated
public class SupplierController {

    private final SupplierService supplierService;

    private final CurrentActor currentActor;

    public SupplierController(SupplierService supplierService, CurrentActor currentActor) {
        this.supplierService = supplierService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('suppliers:read')")
    public PageResponse<SupplierResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(supplierService.list(actor.establishmentId(), page, size), SupplierResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('suppliers:write')")
    public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request) {
        CurrentActor.Actor actor = requireActor();
        SupplierView created = supplierService.create(
                actor.establishmentId(), actor.userId(), new SupplierRegistration(request.name(), request.contact()));
        return SupplierResponse.from(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('suppliers:read')")
    public SupplierResponse find(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return SupplierResponse.from(supplierService.find(actor.establishmentId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('suppliers:write')")
    public SupplierResponse update(@PathVariable long id, @Valid @RequestBody UpdateSupplierRequest request) {
        CurrentActor.Actor actor = requireActor();
        SupplierView updated = supplierService.update(
                actor.establishmentId(),
                actor.userId(),
                id,
                new SupplierModification(request.name(), request.contact(), request.active()));
        return SupplierResponse.from(updated);
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
