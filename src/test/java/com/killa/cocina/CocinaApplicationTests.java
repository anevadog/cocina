package com.killa.cocina;

import static org.assertj.core.api.Assertions.assertThat;

import com.killa.cocina.shared.health.ReadinessController;
import com.killa.cocina.shared.web.RequestIdFilter;
import com.killa.cocina.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class CocinaApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void elContextoArrancaConLasPiezasDePlataforma() {
        assertThat(context.getBean(RequestIdFilter.class)).isNotNull();
        assertThat(context.getBean(ReadinessController.class)).isNotNull();
        assertThat(context.getBean("cocinaOpenApi")).isNotNull();
    }
}
