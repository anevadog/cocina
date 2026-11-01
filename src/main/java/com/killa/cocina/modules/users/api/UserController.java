package com.killa.cocina.modules.users.api;

import com.killa.cocina.modules.users.domain.UserModification;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.modules.users.domain.UserView;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import com.killa.cocina.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de usuarios (PER-01, PER-03).
 *
 * <p>Cada endpoint declara su permiso. La matriz de qué rol tiene qué permiso vive en
 * {@link com.killa.cocina.modules.users.domain.Role} y se verifica con {@code RoleMatrixTest} y
 * {@code EndpointAuthorizationTest}.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService userService;

    private final CurrentActor currentActor;

    public UserController(UserService userService, CurrentActor currentActor) {
        this.userService = userService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users:read')")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(userService.list(actor.establishmentId(), page, size), UserResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('users:write')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        CurrentActor.Actor actor = requireActor();
        UserView created = userService.create(
                actor.establishmentId(),
                actor.userId(),
                new UserRegistration(request.username(), request.fullName(), request.password(), request.role()));
        return UserResponse.from(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users:read')")
    public UserResponse find(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return UserResponse.from(userService.find(actor.establishmentId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('users:write')")
    public UserResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
        CurrentActor.Actor actor = requireActor();
        UserView updated = userService.update(
                actor.establishmentId(),
                actor.userId(),
                id,
                new UserModification(request.fullName(), request.role(), request.active()));
        return UserResponse.from(updated);
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
