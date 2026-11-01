package com.killa.cocina.modules.inventory.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de conversiones de unidad. */
interface ProductUnitConversionJpaRepository extends JpaRepository<ProductUnitConversionEntity, Long> {

    List<ProductUnitConversionEntity> findByProductIdOrderByUnitAsc(long productId);
}
