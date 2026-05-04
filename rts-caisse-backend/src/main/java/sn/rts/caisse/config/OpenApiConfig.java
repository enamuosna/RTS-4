package sn.rts.caisse.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI : accessible sur /swagger-ui.html.
 * Active l'authentification "Bearer" dans l'UI Swagger.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI rtsCaisseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RTS Caisse - API")
                        .description("""
                                API REST du système de gestion de caisse de la Radiodiffusion
                                Télévision Sénégalaise. Consommée par le client lourd JavaFX
                                (guichets) et le client web Angular (administration).
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("RTS - Direction des Systèmes d'Information"))
                        .license(new License().name("Propriétaire RTS")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
