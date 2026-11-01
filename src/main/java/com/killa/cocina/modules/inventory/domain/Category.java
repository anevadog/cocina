package com.killa.cocina.modules.inventory.domain;

import java.time.Instant;

/** Categoría de producto (INV-01). El nombre es único por establecimiento. */
public record Category(Long id, long establishmentId, String name, Instant createdAt, Instant updatedAt) {

    /** Nombre máximo admitido, alineado con {@code varchar(80)}. */
    private static final int MAX_NAME_LENGTH = 80;

    public Category {
        name = Names.normalize(name, MAX_NAME_LENGTH);
    }

    /** Alta de categoría con nombre ya validado. */
    public static Category create(long establishmentId, String name, Instant now) {
        return new Category(null, establishmentId, name, now, now);
    }

    public Category rename(String newName, Instant now) {
        return new Category(id, establishmentId, newName, createdAt, now);
    }
}
