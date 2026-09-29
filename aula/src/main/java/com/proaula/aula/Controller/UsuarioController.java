package com.proaula.aula.Controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.UsuarioService;
import com.proaula.aula.config.JwtTokenProvider;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Usuario usuario) {
        try {
            return ResponseEntity.ok(usuarioService.register(usuario));
        } catch (Exception e) {
            log.error("Error al registrar usuario", e);
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo completar el registro"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        try {
            String username = credentials != null ? credentials.get("username") : null;
            String password = credentials != null ? credentials.get("password") : null;
            Usuario user = usuarioService.login(username, password);
            if (user != null) {
                String token = jwtTokenProvider.generateTokenFromUsername(
                        user.getUsername(),
                        AuthorityUtils.commaSeparatedStringToAuthorityList(user.getRole())
                );
                String menu = "ROLE_ADMIN".equals(user.getRole())
                        ? "Admin Menu: Full CRUD"
                        : "User Menu: View Info";

                Map<String, Object> response = new HashMap<>();
                Map<String, Object> safeUser = new HashMap<>();
                safeUser.put("id", user.getId());
                safeUser.put("username", user.getUsername());
                safeUser.put("email", user.getEmail());
                safeUser.put("role", user.getRole());
                safeUser.put("nombres", user.getNombres());
                safeUser.put("apellidos", user.getApellidos());

                response.put("user", safeUser);
                response.put("menu", menu);
                response.put("token", token);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        } catch (Exception e) {
            log.warn("Error de autenticación para usuario: {}", credentials != null ? credentials.get("username") : null, e);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }
}
