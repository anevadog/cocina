package com.killa.cocina.modules.quality.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de mermas. */
public interface WasteRecordRepository {

    WasteRecord save(WasteRecord record);

    Optional<WasteRecord> findById(long establishmentId, long wasteRecordId);

    /** Más recientes primero. */
    List<WasteRecord> findPage(long establishmentId, int page, int size);

    long count(long establishmentId);
}
