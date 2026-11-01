package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.RecipeFilter;
import com.killa.cocina.modules.recipes.domain.RecipeModification;
import com.killa.cocina.modules.recipes.domain.RecipeRegistration;
import com.killa.cocina.modules.recipes.domain.RecipeService;
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
 * Recetas y su cabecera (REC-01).
 *
 * <p>Crear una receta crea su primera versión en borrador. La receta nunca se borra: se archiva con
 * {@code PATCH active=false} y el historial de versiones se conserva.</p>
 */
@RestController
@RequestMapping("/api/v1/recipes")
@Validated
public class RecipeController {

    private final RecipeService recipeService;

    private final CurrentActor currentActor;

    public RecipeController(RecipeService recipeService, CurrentActor currentActor) {
        this.recipeService = recipeService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('recipes:read')")
    public PageResponse<RecipeResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(
                recipeService.list(actor.establishmentId(), new RecipeFilter(active, name), page, size),
                RecipeResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('recipes:write')")
    public RecipeResponse create(@Valid @RequestBody CreateRecipeRequest request) {
        CurrentActor.Actor actor = requireActor();
        return RecipeResponse.from(recipeService.create(
                actor.establishmentId(),
                actor.userId(),
                new RecipeRegistration(
                        request.name(),
                        request.category(),
                        request.baseRations() == null ? 0 : request.baseRations(),
                        request.yieldPct(),
                        request.prepTimeMinutes(),
                        request.instructions())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('recipes:read')")
    public RecipeResponse find(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return RecipeResponse.from(recipeService.find(actor.establishmentId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('recipes:write')")
    public RecipeResponse update(@PathVariable long id, @Valid @RequestBody UpdateRecipeRequest request) {
        CurrentActor.Actor actor = requireActor();
        return RecipeResponse.from(recipeService.update(
                actor.establishmentId(),
                actor.userId(),
                id,
                new RecipeModification(request.name(), request.category(), request.active())));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
