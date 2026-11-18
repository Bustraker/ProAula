package com.proaula.aula.Service;

import com.proaula.aula.Entity.LegalConsent;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.LegalConsentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class LegalConsentService {

    public static final String CURRENT_VERSION = "2026-11-11-v1";

    private final LegalConsentRepository legalConsentRepository;

    public LegalConsentService(LegalConsentRepository legalConsentRepository) {
        this.legalConsentRepository = legalConsentRepository;
    }

    @Transactional
    public void recordAcceptance(Usuario usuario, String version, boolean adultConfirmed, String source) {
        recordAcceptance(usuario, version, adultConfirmed, source, LocalDateTime.now());
    }

    @Transactional
    public void recordAcceptance(Usuario usuario, String version, boolean adultConfirmed, String source,
                                 LocalDateTime acceptedAt) {
        if (usuario == null || usuario.getId() == null) {
            throw new IllegalArgumentException("La cuenta debe existir antes de registrar la aceptación legal.");
        }
        if (!CURRENT_VERSION.equals(version)) {
            throw new IllegalArgumentException("La versión legal cambió. Recarga la página y vuelve a aceptar.");
        }
        if (!adultConfirmed) {
            throw new IllegalArgumentException("Debes confirmar que tienes al menos 18 años para crear una cuenta.");
        }
        if (acceptedAt == null) {
            throw new IllegalArgumentException("La fecha de aceptación es obligatoria.");
        }
        if (!"REGISTRATION".equals(source) && !"OAUTH2".equals(source) && !"ADMIN".equals(source)
                && !"RECONSENT".equals(source)) {
            throw new IllegalArgumentException("El origen de la aceptación legal no es válido.");
        }

        LegalConsent consent = new LegalConsent();
        consent.setUsuario(usuario);
        consent.setTermsVersion(CURRENT_VERSION);
        consent.setPrivacyVersion(CURRENT_VERSION);
        consent.setAdultConfirmed(true);
        consent.setConsentSource(source);
        consent.setAcceptedAt(acceptedAt);
        legalConsentRepository.save(consent);
    }

    @Transactional(readOnly = true)
    public boolean hasCurrentConsent(String username) {
        return username != null && legalConsentRepository
                .existsByUsuario_UsernameAndTermsVersionAndPrivacyVersionAndAdultConfirmedTrueAndRevokedAtIsNull(
                        username, CURRENT_VERSION, CURRENT_VERSION);
    }

    @Transactional
    public boolean revokeCurrentConsent(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("No se pudo identificar la cuenta para revocar la autorización.");
        }
        return legalConsentRepository.revokeCurrentConsents(
            username, CURRENT_VERSION, CURRENT_VERSION, LocalDateTime.now()) > 0;
    }
}
