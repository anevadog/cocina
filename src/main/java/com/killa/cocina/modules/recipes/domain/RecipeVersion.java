package com.killa.cocina.modules.recipes.domain;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Versión de una receta (REC-05).
 *
 * <p>Los costes solo existen cuando la versión está publicada: se congelan al publicar (REC-04).
 * Mientras es borrador se edita en sitio; una vez publicada solo se lee.</p>
 *
 * @param baseRations raciones que rinde el lote base
 * @param yieldPct rendimiento tras cocción, informativo (REC-01)
 */
public record RecipeVersion(
        Long id,
        long establishmentId,
        long recipeId,
        int versionNumber,
        RecipeVersionStatus status,
        int baseRations,
        BigDecimal yieldPct,
        Integer prepTimeMinutes,
        String instructions,
        BigDecimal totalCost,
        BigDecimal costPerRation,
        Instant publishedAt,
        Long publishedBy,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static RecipeVersion draft(
            long establishmentId,
            long recipeId,
            int versionNumber,
            RecipeRegistration registration,
            long createdBy,
            Instant now) {
        return new RecipeVersion(
                null,
                establishmentId,
                recipeId,
                versionNumber,
                RecipeVersionStatus.DRAFT,
                registration.baseRations(),
                registration.yieldPct(),
                registration.prepTimeMinutes(),
                registration.instructions(),
                null,
                null,
                null,
                null,
                createdBy,
                now,
                now);
    }

    /** Clona la cabecera de una versión anterior como borrador nuevo. */
    public RecipeVersion cloneAsDraft(int nextVersionNumber, long createdBy, Instant now) {
        return new RecipeVersion(
                null,
                establishmentId,
                recipeId,
                nextVersionNumber,
                RecipeVersionStatus.DRAFT,
                baseRations,
                yieldPct,
                prepTimeMinutes,
                instructions,
                null,
                null,
                null,
                null,
                createdBy,
                now,
                now);
    }

    public boolean isDraft() {
        return status.isDraft();
    }

    /**
     * Publica el borrador congelando sus costes (REC-04, REC-05).
     *
     * <p>La versión resultante ya no se edita: cualquier cambio exige una versión nueva. El coste se
     * calcula con el PMP bloqueado en la misma transacción (ADR-035).</p>
     */
    public RecipeVersion publish(BigDecimal totalCost, BigDecimal costPerRation, long publishedBy, Instant now) {
        if (!isDraft()) {
            throw new ApiException(
                    ErrorCode.RESOURCE_IMMUTABLE,
                    "La versión " + versionNumber + " ya está publicada y no se puede volver a publicar.");
        }
        return new RecipeVersion(
                id,
                establishmentId,
                recipeId,
                versionNumber,
                RecipeVersionStatus.PUBLISHED,
                baseRations,
                yieldPct,
                prepTimeMinutes,
                instructions,
                totalCost,
                costPerRation,
                now,
                publishedBy,
                createdBy,
                createdAt,
                now);
    }

    /** Sustituye la cabecera de un borrador; los valores llegan ya resueltos y validados. */
    public RecipeVersion withHeader(
            int baseRations, BigDecimal yieldPct, Integer prepTimeMinutes, String instructions, Instant now) {
        return new RecipeVersion(
                id,
                establishmentId,
                recipeId,
                versionNumber,
                status,
                baseRations,
                yieldPct,
                prepTimeMinutes,
                instructions,
                totalCost,
                costPerRation,
                publishedAt,
                publishedBy,
                createdBy,
                createdAt,
                now);
    }
}
