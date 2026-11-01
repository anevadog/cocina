package com.killa.cocina.modules.suppliers.infrastructure;

import com.killa.cocina.modules.suppliers.domain.Supplier;
import com.killa.cocina.modules.suppliers.domain.SupplierRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link SupplierRepository}. */
@Repository
class SupplierRepositoryAdapter implements SupplierRepository {

    private final SupplierJpaRepository suppliers;

    SupplierRepositoryAdapter(SupplierJpaRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Override
    public Optional<Supplier> findById(long establishmentId, long supplierId) {
        return suppliers.findByIdAndEstablishmentId(supplierId, establishmentId).map(SupplierEntity::toDomain);
    }

    @Override
    public boolean existsByName(long establishmentId, String name) {
        return suppliers.existsByEstablishmentIdAndName(establishmentId, name);
    }

    @Override
    public Supplier save(Supplier supplier) {
        SupplierEntity entity = supplier.id() == null
                ? SupplierEntity.from(supplier)
                : suppliers.findById(supplier.id()).orElseGet(() -> SupplierEntity.from(supplier));
        if (supplier.id() != null) {
            entity.apply(supplier);
        }
        return suppliers.save(entity).toDomain();
    }

    @Override
    public List<Supplier> findAll(long establishmentId, int page, int size) {
        return suppliers
                .findByEstablishmentIdOrderByNameAsc(
                        establishmentId,
                        PageRequest.of(page, size, Sort.by("name").ascending()))
                .stream()
                .map(SupplierEntity::toDomain)
                .toList();
    }

    @Override
    public long count(long establishmentId) {
        return suppliers.countByEstablishmentId(establishmentId);
    }
}
