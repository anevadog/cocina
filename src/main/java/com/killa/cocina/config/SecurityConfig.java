package com.killa.cocina.config;

import com.killa.cocina.modules.auth.domain.SecuritySettings;
import com.killa.cocina.shared.error.ApiErrorWriter;
import com.killa.cocina.shared.error.ErrorCode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Seguridad de la API: Argon2id, JWT HS256 y autorización por permisos (NFR-SEG-01/02, PER-03).
 *
 * <p>La cadena es sin estado: no hay sesión ni cookies. Todo endpoint exige autenticación salvo la
 * lista blanca explícita (salud, documentación y autenticación). Además, cada controlador declara su
 * permiso con {@code @PreAuthorize}: un endpoint nuevo sin anotación queda inaccesible para
 * cualquiera que no sea administrador de todos los permisos (PER-05), y
 * {@code EndpointAuthorizationTest} falla si alguien lo olvida.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Rutas públicas: solo lo imprescindible para arrancar, documentar y autenticarse. */
    private static final String[] PUBLIC_PATHS = {
            "/health",
            "/ready",
            "/error",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private static final String[] PUBLIC_AUTH_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    };

    /** Argon2id con los parámetros recomendados por Spring Security 5.8 y posteriores. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Clave HS256 compartida.
     *
     * <p>Falla al arrancar si el secreto no está o es demasiado corto: es preferible un error de
     * configuración visible a arrancar con una firma débil.</p>
     */
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Falta cocina.security.jwt.secret (JWT_SECRET): la API no arranca sin él.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "cocina.security.jwt.secret debe tener al menos 32 bytes para HS256.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey secretKey) {
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Parámetros de sesión resueltos desde configuración y validados al construir el bean. */
    @Bean
    public SecuritySettings securitySettings(JwtProperties jwt, LoginProperties login) {
        return new SecuritySettings(jwt.accessTtl(), jwt.refreshTtl(), login.maxFailedAttempts(),
                login.lockDuration());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            ApiErrorWriter errorWriter) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) -> errorWriter.write(
                request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                "Necesitas autenticarte para acceder a este recurso.");
        AccessDeniedHandler deniedHandler = (request, response, exception) -> errorWriter.write(
                request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "No tienes permiso para realizar esta operación.");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // PER-05: sin permiso declarado, no hay acceso anónimo.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));

        return http.build();
    }

    /** Las autoridades del token son los códigos de {@code Permission}, sin prefijo adicional. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("authorities");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
