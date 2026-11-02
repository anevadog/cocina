package com.killa.cocina.modules.reporting.domain;

import java.util.List;

/** Puerto de lectura de costes publicados, implementado sobre el módulo de recetas (ECO-01). */
public interface RecipeCostCatalog {

    /** Versiones publicadas del establecimiento, ordenadas por receta y número de versión. */
    List<RecipeCostSnapshot> publishedVersions(long establishmentId);
}
