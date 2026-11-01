package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.ProductFilter;
import com.killa.cocina.modules.inventory.domain.ProductModification;
import com.killa.cocina.modules.inventory.domain.ProductRegistration;
import com.killa.cocina.modules.inventory.domain.ProductService;
import com.killa.cocina.modules.inventory.domain.ProductView;
import com.killa.cocina.modules.inventory.domain.UnitConversion;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import com.killa.cocina.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
 * Catálogo de productos (INV-01, INV-02, INV-07).
 *
 * <p>El archivo es lógico ({@code PATCH active=false}) y exige stock cero: un producto con saldo se
 * ajusta antes de archivarlo. La unidad base no se modifica nunca.</p>
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    private final ProductService productService;

    private final CurrentActor currentActor;

    public ProductController(ProductService productService, CurrentActor currentActor) {
        this.productService = productService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:read')")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(
                productService.list(actor.establishmentId(), new ProductFilter(active, categoryId), page, size),
                ProductResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:write')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        CurrentActor.Actor actor = requireActor();
        ProductView created = productService.create(
                actor.establishmentId(),
                actor.userId(),
                new ProductRegistration(
                        request.name(),
                        request.categoryId(),
                        request.baseUnit(),
                        request.minStock(),
                        request.allowsNegative(),
                        conversions(request.conversionList())));
        return ProductResponse.from(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ProductResponse find(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return ProductResponse.from(productService.find(actor.establishmentId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ProductResponse update(@PathVariable long id, @Valid @RequestBody UpdateProductRequest request) {
        CurrentActor.Actor actor = requireActor();
        ProductView updated = productService.update(
                actor.establishmentId(),
                actor.userId(),
                id,
                new ProductModification(
                        request.name(),
                        request.categoryId(),
                        request.minStock(),
                        request.allowNegative(),
                        request.active()));
        return ProductResponse.from(updated);
    }

    @GetMapping("/{id}/conversions")
    @PreAuthorize("hasAuthority('inventory:read')")
    public List<ConversionResponse> conversions(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return productService.conversions(actor.establishmentId(), id).stream()
                .map(ConversionResponse::from)
                .toList();
    }

    @PutMapping("/{id}/conversions")
    @PreAuthorize("hasAuthority('inventory:write')")
    public List<ConversionResponse> replaceConversions(
            @PathVariable long id, @Valid @RequestBody ReplaceConversionsRequest request) {
        CurrentActor.Actor actor = requireActor();
        ProductView updated = productService.replaceConversions(
                actor.establishmentId(), actor.userId(), id, conversions(request.conversions()));
        return updated.conversions().stream().map(ConversionResponse::from).toList();
    }

    private static List<UnitConversion> conversions(List<ConversionRequest> requests) {
        return requests.stream().map(ConversionRequest::toDomain).toList();
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
