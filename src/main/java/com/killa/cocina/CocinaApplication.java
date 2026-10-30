package com.killa.cocina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Punto de entrada de la API de gestión interna de cocina.
 *
 * <p>Se excluye {@link UserDetailsServiceAutoConfiguration}: los usuarios viven en
 * {@code app_users} y se autentican con {@code AuthService}, no con el usuario en memoria de
 * Spring Boot.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CocinaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CocinaApplication.class, args);
    }
}
