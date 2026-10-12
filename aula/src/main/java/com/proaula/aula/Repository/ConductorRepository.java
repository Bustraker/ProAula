package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Conductor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConductorRepository extends JpaRepository<Conductor, Long> {
    boolean existsByUsuario_Id(Long usuarioId);

    Optional<Conductor> findByUsuarioId(Long usuarioId);
    Optional<Conductor> findByNumeroIdentificacion(String numeroIdentificacion);
}
