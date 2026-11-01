package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.CategoryService;
import com.killa.cocina.modules.inventory.domain.CategoryView;
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
 * Categorías de producto (INV-01).
 *
 * <p>No hay borrado: una categoría en uso por productos se conserva para no romper el catálogo.</p>
 */
@RestController
@RequestMapping("/api/v1/product-categories")
@Validated
public class ProductCategoryController {

    private final CategoryService categoryService;

    private final CurrentActor currentActor;

    public ProductCategoryController(CategoryService categoryService, CurrentActor currentActor) {
        this.categoryService = categoryService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:read')")
    public PageResponse<CategoryResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(categoryService.list(actor.establishmentId(), page, size), CategoryResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:write')")
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        CurrentActor.Actor actor = requireActor();
        CategoryView created = categoryService.create(actor.establishmentId(), actor.userId(), request.name());
        return CategoryResponse.from(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:read')")
    public CategoryResponse find(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return CategoryResponse.from(categoryService.find(actor.establishmentId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:write')")
    public CategoryResponse rename(@PathVariable long id, @Valid @RequestBody UpdateCategoryRequest request) {
        CurrentActor.Actor actor = requireActor();
        return CategoryResponse.from(
                categoryService.rename(actor.establishmentId(), actor.userId(), id, request.name()));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
