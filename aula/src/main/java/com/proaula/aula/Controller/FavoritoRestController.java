package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.AvisoFavoritoService;
import com.proaula.aula.Service.FavoritoService;
import com.proaula.aula.Service.TrayectoFavoritoService;
import com.proaula.aula.dto.FavoritoRequest;
import com.proaula.aula.dto.FavoritoResponse;
import com.proaula.aula.dto.TrayectoFavoritoRequest;
import com.proaula.aula.dto.TrayectoFavoritoResponse;
import com.proaula.aula.exception.AulaException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/favoritos")
public class FavoritoRestController {

    private final FavoritoService favoritoService;
    private final TrayectoFavoritoService trayectoFavoritoService;
    private final AvisoFavoritoService avisoFavoritoService;
    private final UsuarioRepository usuarioRepository;

    public FavoritoRestController(FavoritoService favoritoService,
                                  TrayectoFavoritoService trayectoFavoritoService,
                                  AvisoFavoritoService avisoFavoritoService,
                                  UsuarioRepository usuarioRepository) {
        this.favoritoService = favoritoService;
        this.trayectoFavoritoService = trayectoFavoritoService;
        this.avisoFavoritoService = avisoFavoritoService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public List<FavoritoResponse> listar(Authentication authentication) {
        return favoritoService.listar(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<FavoritoResponse> agregar(
            @Valid @RequestBody FavoritoRequest solicitud,
            Authentication authentication) {
        return ResponseEntity.ok(favoritoService.agregar(solicitud, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        favoritoService.eliminar(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trayectos")
    public List<TrayectoFavoritoResponse> listarTrayectos(Authentication authentication) {
        return trayectoFavoritoService.listar(authentication.getName());
    }

    @PostMapping("/trayectos")
    public ResponseEntity<TrayectoFavoritoResponse> agregarTrayecto(
            @Valid @RequestBody TrayectoFavoritoRequest solicitud,
            Authentication authentication) {
        return ResponseEntity.ok(trayectoFavoritoService.agregar(solicitud, authentication.getName()));
    }

    @DeleteMapping("/trayectos/{id}")
    public ResponseEntity<Void> eliminarTrayecto(@PathVariable Long id, Authentication authentication) {
        trayectoFavoritoService.eliminar(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/notificaciones/{id}/leida")
    public ResponseEntity<Void> marcarAvisoLeido(@PathVariable Long id, Authentication authentication) {
        Usuario usuario = usuarioRepository.findByUsername(authentication.getName());
        if (usuario == null) {
            throw new AulaException("No se encontró el usuario autenticado.", "USER_NOT_FOUND");
        }
        avisoFavoritoService.marcarLeida(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
