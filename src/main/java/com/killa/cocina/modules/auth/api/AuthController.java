package com.killa.cocina.modules.auth.api;

import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.auth.domain.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación (NFR-SEG-02).
 *
 * <p>Los tres endpoints son públicos por diseño: el login no puede exigir token y el refresh/logout
 * se autentican con el propio refresh token, que es un secreto de un solo uso.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    private final EstablishmentProperties establishment;

    public AuthController(AuthService authService, EstablishmentProperties establishment) {
        this.authService = authService;
        this.establishment = establishment;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.from(authService.login(establishment.id(), request.username(),
                request.password()));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthResponse.from(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
