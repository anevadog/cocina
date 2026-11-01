package com.killa.cocina.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del primer administrador (D1 de F2).
 *
 * <p>Si {@code adminPassword} está vacío no se crea nada: así los tests y los entornos reales no
 * reciben un administrador con contraseña por defecto.</p>
 */
@ConfigurationProperties(prefix = "cocina.bootstrap")
public record BootstrapProperties(String adminUsername, String adminPassword) {}
