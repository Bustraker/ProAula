package com.proaula.aula.Repository;

import com.proaula.aula.Entity.AdminInviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AdminInviteCodeRepository extends JpaRepository<AdminInviteCode, Long> {
    Optional<AdminInviteCode> findByCodeHashAndEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfter(
        String codeHash, String email, LocalDateTime now);
}
