package com.killa.cocina.modules.quality.domain;

/** Naturaleza de una lectura de temperatura (CAL-02). */
public enum TemperatureKind {

    /** Cámara, nevera, abatidor u otro equipo de conservación. */
    EQUIPMENT,

    /** Temperatura de una preparación durante su elaboración o servicio. */
    PREPARATION;
}
