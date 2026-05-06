package sn.rts.caisse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Point d'entrée du serveur de gestion de caisse RTS.
 * <p>
 * Fournit une API REST sécurisée par JWT pour :
 *   - le client lourd JavaFX (guichets)
 *   - le client web Angular (administration / reporting)
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class RtsCaisseApplication {

    public static void main(String[] args) {
        SpringApplication.run(RtsCaisseApplication.class, args);
    }
}
