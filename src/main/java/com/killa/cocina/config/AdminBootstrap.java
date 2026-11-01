package com.killa.cocina.config;

import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prepara el establecimiento y crea el primer administrador (D1 de F2).
 *
 * <p>Dos tareas, ambas idempotentes:</p>
 * <ol>
 *   <li>garantizar que existe la fila de {@code establishments} que exigen las claves ajenas;</li>
 *   <li>crear un administrador <strong>solo</strong> si no hay ningún usuario y se ha configurado
 *       {@code BOOTSTRAP_ADMIN_PASSWORD}. Sin contraseña configurada no se crea nada: nunca hay
 *       credenciales por defecto.</li>
 * </ol>
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private static final String ENSURE_ESTABLISHMENT = """
            insert into establishments (id, name, timezone, currency)
            values (?, ?, ?, ?)
            on conflict (id) do nothing
            """;

    private static final String SYNC_ESTABLISHMENT_SEQUENCE = """
            select setval('establishments_seq',
                          greatest((select coalesce(max(id), 0) from establishments), 1),
                          (select count(*) from establishments) > 0)
            """;

    private final JdbcTemplate jdbcTemplate;

    private final UserService userService;

    private final EstablishmentProperties establishment;

    private final BootstrapProperties bootstrap;

    public AdminBootstrap(
            JdbcTemplate jdbcTemplate,
            UserService userService,
            EstablishmentProperties establishment,
            BootstrapProperties bootstrap) {
        this.jdbcTemplate = jdbcTemplate;
        this.userService = userService;
        this.establishment = establishment;
        this.bootstrap = bootstrap;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureEstablishment();
        createFirstAdminIfNeeded();
    }

    private void ensureEstablishment() {
        jdbcTemplate.update(
                ENSURE_ESTABLISHMENT,
                establishment.id(),
                establishment.name(),
                establishment.timezone(),
                establishment.currency());
        // Si la fila se insertó con id explícito, la secuencia debe quedar por delante para que
        // una inserción posterior sin id no choque con la clave primaria.
        jdbcTemplate.queryForObject(SYNC_ESTABLISHMENT_SEQUENCE, Long.class);
    }

    private void createFirstAdminIfNeeded() {
        String password = bootstrap.adminPassword();
        if (password == null || password.isBlank()) {
            return;
        }
        if (userService.list(establishment.id(), 0, 1).total() > 0) {
            return;
        }
        String username =
                bootstrap.adminUsername() == null || bootstrap.adminUsername().isBlank()
                        ? "admin"
                        : bootstrap.adminUsername();
        userService.create(
                establishment.id(),
                null,
                new UserRegistration(username, "Administrador inicial", password, Role.ADMIN));
        log.info(
                "Creado el administrador inicial '{}' del establecimiento {}",
                username.toLowerCase(java.util.Locale.ROOT),
                establishment.id());
    }
}
