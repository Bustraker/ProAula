package com.proaula.aula.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.proaula.aula.Entity.AdminCode;

@Repository
public interface AdminCodeRepository extends JpaRepository<AdminCode, Long> {
    Optional<AdminCode> findByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCase(String codigo);
}
