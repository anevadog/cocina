package com.killa.cocina.modules.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Máquina de estados del batch de producción (PRD-04, PRD-05).
 *
 * <p>La regla central es que el stock se descuenta al terminar, no al empezar, y que una producción
 * terminada no vuelve atrás: el kardex es inmutable y las correcciones se hacen con un ajuste.</p>
 */
class ProductionBatchTest {

    private static final long ESTABLISHMENT = 1L;

    private static final long PLAN_ITEM = 10L;

    private static final long VERSION = 20L;

    private static final long ACTOR = 30L;

    private static final Instant START = Instant.parse("2026-11-02T09:00:00Z");

    private static final Instant FINISH = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    @DisplayName("Al empezar usa las raciones previstas y queda en IN_PROGRESS")
    void empiezaConLasRacionesPrevistas() {
        ProductionBatch batch = started();

        assertThat(batch.status()).isEqualTo(ProductionStatus.IN_PROGRESS);
        assertThat(batch.plannedRations()).isEqualTo(4);
        assertThat(batch.rations()).isEqualTo(4);
        assertThat(batch.startedAt()).isEqualTo(START);
        assertThat(batch.startedBy()).isEqualTo(ACTOR);
        assertThat(batch.finishedAt()).isNull();
        assertThat(batch.finishedBy()).isNull();
    }

    @Test
    @DisplayName("Terminar sin desviación guarda raciones, actor y fecha de fin")
    void terminaSinDesviacion() {
        ProductionBatch finished = started().finish(4, null, ACTOR, FINISH);

        assertThat(finished.status()).isEqualTo(ProductionStatus.READY);
        assertThat(finished.rations()).isEqualTo(4);
        assertThat(finished.deviation()).isZero();
        assertThat(finished.finishedAt()).isEqualTo(FINISH);
        assertThat(finished.finishedBy()).isEqualTo(ACTOR);
        assertThat(finished.deviationReason()).isNull();
    }

    @Test
    @DisplayName("Una desviación exige motivo y lo normaliza")
    void unaDesviacionExigeMotivo() {
        assertThatThrownBy(() -> started().finish(3, null, ACTOR, FINISH))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> started().finish(3, "   ", ACTOR, FINISH))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        ProductionBatch finished = started().finish(3, "  merma   en cocción  ", ACTOR, FINISH);

        assertThat(finished.rations()).isEqualTo(3);
        assertThat(finished.deviation()).isEqualTo(-1);
        assertThat(finished.deviationReason()).isEqualTo("merma en cocción");
    }

    @Test
    @DisplayName("Cumplir lo previsto admite motivo informativo")
    void admiteMotivoAunqueNoHayaDesviacion() {
        ProductionBatch finished = started().finish(4, "servicio completo", ACTOR, FINISH);

        assertThat(finished.deviation()).isZero();
        assertThat(finished.deviationReason()).isEqualTo("servicio completo");
    }

    @Test
    @DisplayName("Cancelar en curso no fija fecha de fin")
    void cancelaEnCurso() {
        ProductionBatch cancelled = started().cancel();

        assertThat(cancelled.status()).isEqualTo(ProductionStatus.CANCELLED);
        assertThat(cancelled.finishedAt()).isNull();
        assertThat(cancelled.finishedBy()).isNull();
        assertThat(cancelled.rations()).isEqualTo(4);
    }

    @Test
    @DisplayName("Solo se sirve lo que ya está listo, sin tocar el consumo")
    void soloSeSirveLoListo() {
        ProductionBatch ready = started().finish(3, "menos comensales", ACTOR, FINISH);
        ProductionBatch served = ready.serve();

        assertThat(served.status()).isEqualTo(ProductionStatus.SERVED);
        assertThat(served.rations()).isEqualTo(3);
        assertThat(served.deviationReason()).isEqualTo("menos comensales");
        assertThat(served.finishedAt()).isEqualTo(FINISH);
        assertThat(served.finishedBy()).isEqualTo(ACTOR);

        assertThatThrownBy(() -> started().serve())
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.RESOURCE_IN_USE));
    }

    @Test
    @DisplayName("Una vez lista o cancelada no se vuelve atrás ni se termina otra vez")
    void lasTransicionesTerminadasSonFinales() {
        ProductionBatch ready = started().finish(4, null, ACTOR, FINISH);

        assertThatThrownBy(() -> ready.finish(5, "otra vez", ACTOR, FINISH))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.RESOURCE_IN_USE));
        assertThatThrownBy(ready::cancel)
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.RESOURCE_IN_USE));

        ProductionBatch cancelled = started().cancel();
        assertThatThrownBy(cancelled::serve)
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.RESOURCE_IN_USE));
        assertThatThrownBy(() -> cancelled.finish(4, null, ACTOR, FINISH))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.RESOURCE_IN_USE));
    }

    @Test
    @DisplayName("Las raciones del batch están acotadas entre 1 y 10000")
    void acotaLasRaciones() {
        assertThatThrownBy(() -> ProductionBatch.start(ESTABLISHMENT, PLAN_ITEM, VERSION, 0, ACTOR, START))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> ProductionBatch.start(ESTABLISHMENT, PLAN_ITEM, VERSION, 10_001, ACTOR, START))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> started().finish(0, null, ACTOR, FINISH))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static ProductionBatch started() {
        return ProductionBatch.start(ESTABLISHMENT, PLAN_ITEM, VERSION, 4, ACTOR, START);
    }
}
