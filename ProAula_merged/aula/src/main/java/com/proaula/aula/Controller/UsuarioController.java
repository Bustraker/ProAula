package com.proaula.aula.Controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Usuario usuario) {
        try {
            return ResponseEntity.ok(usuarioService.register(usuario));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        try {
            Usuario user = usuarioService.login(credentials.get("username"), credentials.get("password"));
            if (user != null) {
                String token = jwtTokenProvider.generateTokenFromUsername(
                        user.getUsername(),
                        AuthorityUtils.commaSeparatedStringToAuthorityList(user.getRole())
                );
                String menu = "ROLE_ADMIN".equals(user.getRole())
                        ? "Admin Menu: Full CRUD"
                        : "User Menu: View Info";
                return ResponseEntity.ok(Map.of("user", user, "menu", menu, "token", token));
            }
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }
}
