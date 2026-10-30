package com.killa.cocina.modules.suppliers.domain;

import com.killa.cocina.shared.pagination.PageResult;

/** Casos de uso de proveedores. */
public interface SupplierService {

    SupplierView create(long establishmentId, Long actorId, SupplierRegistration registration);

    SupplierView update(long establishmentId, Long actorId, long supplierId,
            SupplierModification modification);

    SupplierView find(long establishmentId, long supplierId);

    PageResult<SupplierView> list(long establishmentId, int page, int size);
}
