package com.proaula.aula.Repository;

import com.proaula.aula.Entity.LegalConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface LegalConsentRepository extends JpaRepository<LegalConsent, Long> {
    boolean existsByUsuario_UsernameAndTermsVersionAndPrivacyVersionAndAdultConfirmedTrue(
            String username, String termsVersion, String privacyVersion);

    boolean existsByUsuario_UsernameAndTermsVersionAndPrivacyVersionAndAdultConfirmedTrueAndRevokedAtIsNull(
            String username, String termsVersion, String privacyVersion);

    boolean existsByUsuario_UsernameAndRevokedAtIsNotNull(String username);

    @Modifying
    @Query("""
        update LegalConsent consent
        set consent.revokedAt = :revokedAt
        where consent.usuario.username = :username
          and consent.termsVersion = :termsVersion
          and consent.privacyVersion = :privacyVersion
          and consent.adultConfirmed = true
          and consent.revokedAt is null
        """)
    int revokeCurrentConsents(@Param("username") String username,
                              @Param("termsVersion") String termsVersion,
                              @Param("privacyVersion") String privacyVersion,
                              @Param("revokedAt") LocalDateTime revokedAt);
}
