package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Service.UsuarioService;
import com.proaula.aula.config.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proaula.aula.dto.UsuarioRegistroRequest;
import com.proaula.aula.dto.UsuarioRegistroResponse;
import org.mockito.ArgumentCaptor;
@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UsuarioController usuarioController;

    @Test
    void loginShouldNotExposePasswordInResponse() {
        Usuario user = new Usuario();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("encodedPassword");
        user.setRole("ROLE_USER");
        user.setEmail("alice@test.com");
        user.setNombres("Alice");
        user.setApellidos("Test");

        when(usuarioService.login("alice", "secret")).thenReturn(user);
        when(jwtTokenProvider.generateTokenFromUsername(eq("alice"), anyCollection()))
            .thenReturn("jwt-token");

        ResponseEntity<Map<String, Object>> response = usuarioController.login(Map.of(
            "username", "alice",
            "password", "secret"
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("token")).isEqualTo("jwt-token");

        Map<String, Object> safeUser = (Map<String, Object>) response.getBody().get("user");
        assertThat(safeUser).isNotNull();
        assertThat(safeUser.get("username")).isEqualTo("alice");
        assertThat(safeUser).doesNotContainKey("password");
    }

    @Test
    void registrationIgnoresClientSuppliedRoleAndDoesNotReturnPassword() {
        Usuario savedUser = new Usuario();
        savedUser.setId(2L);
        savedUser.setUsername("new-user");
        savedUser.setNombres("New");
        savedUser.setApellidos("User");
        savedUser.setEmail("new-user@test.com");
        when(usuarioService.register(any(Usuario.class))).thenReturn(savedUser);

        ResponseEntity<?> response = usuarioController.register(new UsuarioRegistroRequest(
            "new-user", "long-password", "New", "User", "new-user@test.com"));

        ArgumentCaptor<Usuario> userCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioService).register(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo("ROLE_USER");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(new UsuarioRegistroResponse(
            2L, "new-user", "New", "User", "new-user@test.com"));
        assertThat(response.getBody().toString()).doesNotContain("password");
    }
}
