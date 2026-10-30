package com.killa.cocina.shared.health;

/** Estado de una dependencia comprobada en {@code /ready}. */
public enum ReadinessStatus {

    /** La dependencia responde y está operativa. */
    UP,

    /** La dependencia no responde o no está preparada. */
    DOWN
}
