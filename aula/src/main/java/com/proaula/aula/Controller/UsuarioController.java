package com.proaula.aula.Controller;

import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.UsuarioService;
import com.proaula.aula.Service.LegalConsentService;
import com.proaula.aula.config.JwtTokenProvider;
import com.proaula.aula.dto.UsuarioRegistroRequest;
import com.proaula.aula.dto.UsuarioRegistroResponse;
import com.proaula.aula.dto.UsuarioLoginRequest;
import com.proaula.aula.exception.AulaException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/usuarios")
@Validated
public class UsuarioController {
    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    private final UsuarioService usuarioService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LegalConsentService legalConsentService;

    public UsuarioController(UsuarioService usuarioService, JwtTokenProvider jwtTokenProvider,
                             LegalConsentService legalConsentService) {
        this.usuarioService = usuarioService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.legalConsentService = legalConsentService;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody UsuarioRegistroRequest request) {
        if (!Boolean.TRUE.equals(request.legalConsent())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Debes aceptar los términos y autorizar el tratamiento de datos."));
        }
        if (!Boolean.TRUE.equals(request.adultConfirmed())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Debes confirmar que tienes al menos 18 años."));
        }
        if (!LegalConsentService.CURRENT_VERSION.equals(request.legalVersion())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "La versión legal cambió. Actualiza la página e inténtalo de nuevo."));
        }
        if (request.password() == null
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La contraseña no puede superar 72 bytes en UTF-8."));
        }
        if (usuarioService.existsByUsername(request.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El nombre de usuario ya está en uso"));
        }
        if (usuarioService.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El correo electrónico ya está registrado"));
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setUsername(request.username().trim());
        nuevoUsuario.setPassword(request.password());
        nuevoUsuario.setNombres(request.nombres().trim());
        nuevoUsuario.setApellidos(request.apellidos().trim());
        nuevoUsuario.setEmail(request.email().trim());
        nuevoUsuario.setProvider("LOCAL");
        nuevoUsuario.setEmailVerified(true);
        nuevoUsuario.setRole("ROLE_USER");

        Usuario creado = usuarioService.register(nuevoUsuario);
        legalConsentService.recordAcceptance(creado, request.legalVersion(), true, "REGISTRATION");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(UsuarioRegistroResponse.from(creado));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody UsuarioLoginRequest credentials) {
        if (credentials.password() == null
                || credentials.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Credenciales no válidas."));
        }
        try {
            String username = credentials.username();
            String password = credentials.password();
            Usuario user = usuarioService.login(username, password);
            if (user != null) {
                String token = jwtTokenProvider.generateTokenFromUsername(
                        user.getUsername(),
                        AuthorityUtils.commaSeparatedStringToAuthorityList(user.getRole())
                );
                String menu = "ROLE_ADMIN".equals(user.getRole())
                        ? "Admin Menu: Full CRUD"
                        : "User Menu: View Info";

                Map<String, Object> safeUser = Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "role", user.getRole(),
                        "nombres", user.getNombres(),
                        "apellidos", user.getApellidos());
                Map<String, Object> response = Map.of(
                        "user", safeUser,
                        "menu", menu,
                        "token", token);
                return ResponseEntity.ok(response);
            }
            return respuestaCredencialesInvalidas();
        } catch (AulaException ex) {
            log.debug("Intento de inicio de sesión rechazado.");
            return respuestaCredencialesInvalidas();
        }
    }

    private ResponseEntity<Map<String, Object>> respuestaCredencialesInvalidas() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Usuario o contraseña incorrectos."));
    }
}
