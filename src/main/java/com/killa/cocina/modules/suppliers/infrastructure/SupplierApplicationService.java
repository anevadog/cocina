package com.killa.cocina.modules.suppliers.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.suppliers.domain.Supplier;
import com.killa.cocina.modules.suppliers.domain.SupplierModification;
import com.killa.cocina.modules.suppliers.domain.SupplierRegistration;
import com.killa.cocina.modules.suppliers.domain.SupplierRepository;
import com.killa.cocina.modules.suppliers.domain.SupplierService;
import com.killa.cocina.modules.suppliers.domain.SupplierView;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.pagination.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Casos de uso de proveedores.
 *
 * <p>Vive en {@code infrastructure} porque necesita transacción y serialización; el API solo ve
 * {@link SupplierService} (ADR-020).</p>
 */
@Service
@Transactional
public class SupplierApplicationService implements SupplierService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SupplierRepository suppliers;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public SupplierApplicationService(SupplierRepository suppliers, AuditRecorder audit,
            ObjectMapper objectMapper, Clock clock) {
        this.suppliers = suppliers;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public SupplierView create(long establishmentId, Long actorId, SupplierRegistration registration) {
        if (suppliers.existsByName(establishmentId, registration.name())) {
            throw duplicateName(registration.name());
        }
        Instant now = Instant.now(clock);
        Supplier saved = suppliers.save(new Supplier(null, establishmentId, registration.name(),
                registration.contact(), true, now, now));
        audit.record(establishmentId, actorId, AuditAction.SUPPLIER_CREATED, "supplier", saved.id(),
                null, snapshot(saved));
        return SupplierView.from(saved);
    }

    @Override
    public SupplierView update(long establishmentId, Long actorId, long supplierId,
            SupplierModification modification) {
        Supplier current = requireSupplier(establishmentId, supplierId);
        if (modification.isEmpty()) {
            return SupplierView.from(current);
        }
        Supplier updated = modification.applyTo(current, Instant.now(clock));
        if (!updated.name().equals(current.name())
                && suppliers.existsByName(establishmentId, updated.name())) {
            throw duplicateName(updated.name());
        }
        Supplier saved = suppliers.save(updated);
        audit.record(establishmentId, actorId, AuditAction.SUPPLIER_UPDATED, "supplier", saved.id(),
                snapshot(current), snapshot(saved));
        return SupplierView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierView find(long establishmentId, long supplierId) {
        return SupplierView.from(requireSupplier(establishmentId, supplierId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SupplierView> list(long establishmentId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<SupplierView> items = suppliers.findAll(establishmentId, safePage, safeSize).stream()
                .map(SupplierView::from)
                .toList();
        return new PageResult<>(items, suppliers.count(establishmentId), safePage, safeSize);
    }

    private Supplier requireSupplier(long establishmentId, long supplierId) {
        return suppliers.findById(establishmentId, supplierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "El proveedor " + supplierId + " no existe."));
    }

    private static ApiException duplicateName(String name) {
        return new ApiException(ErrorCode.DUPLICATE_NAME,
                "Ya existe un proveedor con el nombre '" + name + "'.");
    }

    private String snapshot(Supplier supplier) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", supplier.name());
        values.put("contact", supplier.contact());
        values.put("active", supplier.active());
        return objectMapper.writeValueAsString(values);
    }
}
