package com.killa.cocina.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fronteras de la arquitectura modular (`docs/arquitectura.md` §2.1), verificadas en cada build.
 *
 * <p>Las reglas se aplican a los paquetes {@code ..modules..}. Mientras un módulo no exista, su
 * regla se cumple de forma vacía; en cuanto aparezca código que la incumpla, el build falla.</p>
 */
class ModuleBoundariesTest {

    private static final String ROOT_PACKAGE = "com.killa.cocina";

    private static JavaClasses classes;

    @BeforeAll
    static void importarClasesDeProduccion() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT_PACKAGE);
    }

    @Test
    void elDominioNoDependeDeSpringNiDePersistencia() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..modules..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void laCapaApiNoAccedeALaInfraestructura() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..modules..api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..modules..infrastructure..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void elDominioNoAccedeALaInfraestructura() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..modules..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..modules..infrastructure..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void laInfraestructuraNoDependeDeLaCapaApi() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..modules..infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..modules..api..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void elAnalisisTieneClasesQueRevisar() {
        assertThat(classes.size()).isPositive();
    }
}
