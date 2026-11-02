package com.killa.cocina.modules.production.domain;

import java.util.Optional;

/** Puerto publicado por producción hacia las recetas (ADR-037). */
public interface RecipeCompositionCatalog {

    /** Solo devuelve versiones publicadas; un borrador no se puede planificar. */
    Optional<ProductionComposition> findPublished(long establishmentId, long versionId);
}
