package com.killa.cocina.modules.quality.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import com.killa.cocina.modules.inventory.domain.StockService;
import com.killa.cocina.modules.inventory.domain.WasteConsumption;
import com.killa.cocina.modules.quality.domain.WasteRecord;
import com.killa.cocina.modules.quality.domain.WasteRecordRepository;
import com.killa.cocina.modules.quality.domain.WasteRecordView;
import com.killa.cocina.modules.quality.domain.WasteRegistration;
import com.killa.cocina.modules.quality.domain.WasteService;
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
 * Casos de uso de mermas (CAL-03, CAL-04).
 *
 * <p>La cabecera se guarda provisionalmente con coste cero para obtener el id que referencian los
 * movimientos {@code WASTE_OUT}; el coste se completa con el PMP vigente en la misma transacción
 * (ADR-039). Si el descuento de stock falla, la cabecera también se revierte.</p>
 */
@Service
@Transactional
public class WasteApplicationService implements WasteService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String ENTITY_TYPE = "waste_record";

    private final StockService stock;

    private final WasteRecordRepository records;

    private final AuditRecorder audit;

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public WasteApplicationService(
            StockService stock,
            WasteRecordRepository records,
            AuditRecorder audit,
            ObjectMapper objectMapper,
            Clock clock) {
        this.stock = stock;
        this.records = records;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public WasteRecordView register(long establishmentId, Long actorId, WasteRegistration registration) {
        long actor = requireActor(actorId);
        Instant now = Instant.now(clock);

        WasteRecord provisional = records.save(WasteRecord.provisional(
                establishmentId,
                registration.productId(),
                registration.lotId(),
                registration.quantityBase(),
                registration.reason(),
                actor,
                now,
                now));

        WasteConsumption consumption = stock.consumeForWaste(
                establishmentId,
                actor,
                registration.productId(),
                registration.lotId(),
                registration.quantityBase(),
                provisional.id(),
                registration.reason(),
                now);

        WasteRecord saved = records.save(provisional.withCosts(consumption.unitCost(), consumption.cost()));
        audit.record(
                establishmentId,
                actor,
                AuditAction.WASTE_RECORDED,
                ENTITY_TYPE,
                saved.id(),
                stockSnapshot(consumption),
                wasteSnapshot(saved, consumption));
        return WasteRecordView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WasteRecordView find(long establishmentId, long wasteRecordId) {
        return WasteRecordView.from(records.findById(establishmentId, wasteRecordId)
                .orElseThrow(() ->
                        new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "La merma " + wasteRecordId + " no existe.")));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<WasteRecordView> list(long establishmentId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<WasteRecordView> items = records.findPage(establishmentId, safePage, safeSize).stream()
                .map(WasteRecordView::from)
                .toList();
        return new PageResult<>(items, records.count(establishmentId), safePage, safeSize);
    }

    private static long requireActor(Long actorId) {
        if (actorId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "El registro de mermas exige un usuario autenticado.");
        }
        return actorId;
    }

    private String stockSnapshot(WasteConsumption consumption) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("productId", consumption.productId());
        values.put("productName", consumption.productName());
        values.put("currentStock", consumption.stockBefore().toPlainString());
        values.put("currentAvgCost", consumption.avgCost().toPlainString());
        return objectMapper.writeValueAsString(values);
    }

    private String wasteSnapshot(WasteRecord record, WasteConsumption consumption) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("productId", consumption.productId());
        values.put("productName", consumption.productName());
        values.put("wasteRecordId", record.id());
        values.put("quantityBase", record.quantityBase().toPlainString());
        values.put("unitCost", record.unitCost().toPlainString());
        values.put("cost", record.cost().toPlainString());
        values.put("reason", record.reason());
        values.put("currentStock", consumption.stockAfter().toPlainString());
        values.put("currentAvgCost", consumption.avgCost().toPlainString());
        return objectMapper.writeValueAsString(values);
    }
}
