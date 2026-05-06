package sn.rts.caisse.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import sn.rts.caisse.security.CustomUserDetailsService;
import sn.rts.caisse.security.JwtAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration Spring Security :
 *   - JWT stateless (aucune session côté serveur)
 *   - Endpoints publics : /api/auth/**, swagger, actuator/health
 *   - Restrictions de rôle au niveau URL (ADMIN, SUPERVISEUR, CAISSIER)
 *   - CORS pour Angular et JavaFX
 *
 * <p><b>Règle d'or Spring Security 6 :</b> tous les
 * {@code requestMatchers(...)} spécifiques DOIVENT être déclarés
 * <b>avant</b> {@code .anyRequest().authenticated()}. Sinon Spring
 * lève {@code IllegalStateException: Can't configure mvcMatchers after
 * anyRequest}.</p>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints publics
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Administration
                        .requestMatchers("/api/utilisateurs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/caisses/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/caisses/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/caisses/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")

                        // Audit : consultation réservée aux ADMIN
                        // (le @PreAuthorize("hasRole('ADMIN')") sur AuditLogController
                        //  fait déjà le travail, mais on double ici pour défense en profondeur)
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")

                        // Reporting / supervision
                        .requestMatchers("/api/reporting/**").hasAnyRole("ADMIN", "SUPERVISEUR")
                        .requestMatchers(HttpMethod.POST, "/api/journaux/*/valider")
                            .hasAnyRole("ADMIN", "SUPERVISEUR")

                        // Tout le reste nécessite une authentification
                        // ⚠ DOIT TOUJOURS RESTER LA DERNIÈRE LIGNE de authorizeHttpRequests
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // IMPORTANT : on utilise setAllowedOriginPatterns (pas setAllowedOrigins)
        // pour pouvoir matcher des plages d'IP du réseau local avec des wildcards.
        // Exemples valides dans CORS_ALLOWED_ORIGINS :
        //   http://192.168.*.*:8080
        //   http://10.0.*.*:8080
        //   http://*.rts.local:8080
        //   http://localhost:8080
        configuration.setAllowedOriginPatterns(
                Arrays.asList(allowedOrigins.split(",")));

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        // Content-Disposition indispensable pour que le frontend puisse lire
        // le nom du fichier .xlsx lors du téléchargement.
        configuration.setExposedHeaders(
                List.of("Authorization", "Content-Disposition"));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
