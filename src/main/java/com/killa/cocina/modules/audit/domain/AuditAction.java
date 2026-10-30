package com.killa.cocina.modules.audit.domain;

/**
 * Acciones sensibles que se auditan (CAL-06).
 *
 * <p>El valor se guarda tal cual en {@code audit_logs.action}; añadir una acción no exige migración
 * porque la columna es texto libre validado aquí.</p>
 */
public enum AuditAction {

    USER_CREATED,
    USER_UPDATED,
    USER_ROLE_CHANGED,
    USER_DEACTIVATED,
    LOGIN_FAILED,
    REFRESH_TOKEN_REUSED,

    CATEGORY_CREATED,
    CATEGORY_UPDATED,

    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_ARCHIVED,
    PRODUCT_CONVERSIONS_UPDATED,

    SUPPLIER_CREATED,
    SUPPLIER_UPDATED,

    STOCK_PURCHASE_RECORDED,
    STOCK_ADJUSTMENT_RECORDED,
    STOCK_COST_CHANGED
}
