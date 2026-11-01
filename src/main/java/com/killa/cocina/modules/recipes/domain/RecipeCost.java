package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Coste calculado de una versión con los costes unitarios vigentes (REC-04, ECO-01).
 *
 * @param perRation coste por ración redondeado a 4 decimales; {@code null} si falta el coste de algún
 *     ingrediente, porque entonces la cifra no sería comparable con nada
 * @param missingProducts productos sin coste conocido
 */
public record RecipeCost(BigDecimal perRation, List<Long> missingProducts) {

    public RecipeCost {
        missingProducts = missingProducts == null ? List.of() : List.copyOf(missingProducts);
    }

    /** Un coste incompleto no se publica ni se compara: se avisa y se deja constancia. */
    public boolean complete() {
        return missingProducts.isEmpty();
    }
}
