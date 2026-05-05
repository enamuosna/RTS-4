    package sn.rts.caisse.controller;

    import io.swagger.v3.oas.annotations.Operation;
    import io.swagger.v3.oas.annotations.security.SecurityRequirements;
    import io.swagger.v3.oas.annotations.tags.Tag;
    import jakarta.validation.Valid;
    import lombok.RequiredArgsConstructor;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestBody;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RestController;
    import sn.rts.caisse.dto.auth.AuthResponse;
    import sn.rts.caisse.dto.auth.LoginRequest;
    import sn.rts.caisse.service.AuthService;

    @RestController
    @RequestMapping("/api/auth")
    @RequiredArgsConstructor
    @Tag(name = "Authentification", description = "Connexion et émission de JWT")
    @SecurityRequirements // endpoints publics
    public class AuthController {

        private final AuthService authService;

        @PostMapping("/login")
        @Operation(summary = "Authentifier un utilisateur et retourner un JWT")
        public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
            return ResponseEntity.ok(authService.login(request));
        }
    }
