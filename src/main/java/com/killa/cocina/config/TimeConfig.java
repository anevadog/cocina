package com.killa.cocina.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del sistema en UTC.
 *
 * <p>Se inyecta como bean para que los tests puedan fijar la hora y para que ningún componente
 * dependa de la zona horaria de la máquina (PLA-06).</p>
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
