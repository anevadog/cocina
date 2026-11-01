package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.RecipeItemRegistration;
import com.killa.cocina.modules.recipes.domain.RecipeService;
import com.killa.cocina.modules.recipes.domain.RecipeVersionModification;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versiones de una receta (REC-01, REC-02, REC-05).
 *
 * <p>El borrador se edita en sitio; una versión publicada es inmutable y solo se consulta. Para
 * cambiarla se crea otra versión, que clona la última y conserva el historial.</p>
 */
@RestController
@RequestMapping("/api/v1/recipes/{recipeId}/versions")
public class RecipeVersionController {

    private final RecipeService recipeService;

    private final CurrentActor currentActor;

    public RecipeVersionController(RecipeService recipeService, CurrentActor currentActor) {
        this.recipeService = recipeService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('recipes:read')")
    public List<RecipeVersionSummaryResponse> versions(@PathVariable long recipeId) {
        CurrentActor.Actor actor = requireActor();
        return recipeService.versions(actor.establishmentId(), recipeId).stream()
                .map(RecipeVersionSummaryResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('recipes:write')")
    public RecipeVersionResponse createVersion(@PathVariable long recipeId) {
        CurrentActor.Actor actor = requireActor();
        return RecipeVersionResponse.from(
                recipeService.createVersion(actor.establishmentId(), actor.userId(), recipeId));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAuthority('recipes:read')")
    public RecipeVersionResponse version(@PathVariable long recipeId, @PathVariable long versionId) {
        CurrentActor.Actor actor = requireActor();
        return RecipeVersionResponse.from(recipeService.version(actor.establishmentId(), recipeId, versionId));
    }

    @PatchMapping("/{versionId}")
    @PreAuthorize("hasAuthority('recipes:write')")
    public RecipeVersionResponse updateVersion(
            @PathVariable long recipeId,
            @PathVariable long versionId,
            @Valid @RequestBody UpdateRecipeVersionRequest request) {
        CurrentActor.Actor actor = requireActor();
        return RecipeVersionResponse.from(recipeService.updateVersion(
                actor.establishmentId(),
                actor.userId(),
                recipeId,
                versionId,
                new RecipeVersionModification(
                        request.baseRations(), request.yieldPct(), request.prepTimeMinutes(), request.instructions())));
    }

    @PutMapping("/{versionId}/items")
    @PreAuthorize("hasAuthority('recipes:write')")
    public RecipeVersionResponse replaceItems(
            @PathVariable long recipeId,
            @PathVariable long versionId,
            @Valid @RequestBody ReplaceRecipeItemsRequest request) {
        CurrentActor.Actor actor = requireActor();
        List<RecipeItemRegistration> items =
                request.items().stream().map(RecipeItemRequest::toDomain).toList();
        return RecipeVersionResponse.from(
                recipeService.replaceItems(actor.establishmentId(), actor.userId(), recipeId, versionId, items));
    }

    @DeleteMapping("/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('recipes:write')")
    public void deleteDraft(@PathVariable long recipeId, @PathVariable long versionId) {
        CurrentActor.Actor actor = requireActor();
        recipeService.deleteDraft(actor.establishmentId(), actor.userId(), recipeId, versionId);
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
