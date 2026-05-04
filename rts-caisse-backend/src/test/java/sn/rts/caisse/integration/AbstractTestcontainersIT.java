package sn.rts.caisse.integration;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Lance un conteneur PostgreSQL éphémère partagé par tous les tests qui héritent
 * de la même classe de base. Le conteneur est démarré <b>une seule fois</b> par
 * JVM (mécanisme de "Singleton Container") puis réutilisé par les tests, ce qui
 * réduit drastiquement le temps total de la suite.
 */
public abstract class AbstractTestcontainersIT {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rts_caisse_test")
            .withUsername("rts_test")
            .withPassword("rts_test")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

    /**
     * Initializer Spring qui injecte dynamiquement les propriétés de connexion
     * du conteneur dans le contexte de test.
     */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url="       + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username="  + POSTGRES.getUsername(),
                    "spring.datasource.password="  + POSTGRES.getPassword(),
                    // Flyway gère déjà le schéma, Hibernate ne fait que valider.
                    "spring.jpa.hibernate.ddl-auto=validate",
                    "spring.flyway.enabled=true"
            ).applyTo(context.getEnvironment());
        }
    }
}
