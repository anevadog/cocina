package com.killa.cocina.modules.suppliers.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de proveedores. */
public interface SupplierRepository {

    Optional<Supplier> findById(long establishmentId, long supplierId);

    boolean existsByName(long establishmentId, String name);

    Supplier save(Supplier supplier);

    List<Supplier> findAll(long establishmentId, int page, int size);

    long count(long establishmentId);
}
