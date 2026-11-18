package com.proaula.aula;

import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.Service.LegalConsentService;

public final class LegalConsentTestSupport {

    private LegalConsentTestSupport() {
    }

    public static Usuario ensureConsentedUser(UsuarioRepository usuarioRepository,
                                              LegalConsentService legalConsentService,
                                              String username,
                                              String role) {
        Usuario usuario = usuarioRepository.findByUsername(username);
        if (usuario == null) {
            usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setEmail(username + "@example.test");
            usuario.setPassword("test-password-26");
            usuario.setNombres("Usuario");
            usuario.setApellidos("Prueba");
            usuario.setRole(role);
            usuario.setProvider("LOCAL");
            usuario = usuarioRepository.saveAndFlush(usuario);
        }
        if (!legalConsentService.hasCurrentConsent(username)) {
            legalConsentService.recordAcceptance(
                usuario, LegalConsentService.CURRENT_VERSION, true, "REGISTRATION");
        }
        return usuario;
    }

    public static void recordConsent(Usuario usuario, LegalConsentService legalConsentService) {
        if (!legalConsentService.hasCurrentConsent(usuario.getUsername())) {
            legalConsentService.recordAcceptance(
                usuario, LegalConsentService.CURRENT_VERSION, true, "REGISTRATION");
        }
    }
}
