package com.killa.cocina.modules.quality.domain;

import com.killa.cocina.shared.pagination.PageResult;

/** Casos de uso de mermas (CAL-03, CAL-04). */
public interface WasteService {

    /** Registra la merma, descuenta stock y audita; calcula el coste con el PMP vigente. */
    WasteRecordView register(long establishmentId, Long actorId, WasteRegistration registration);

    WasteRecordView find(long establishmentId, long wasteRecordId);

    PageResult<WasteRecordView> list(long establishmentId, int page, int size);
}
