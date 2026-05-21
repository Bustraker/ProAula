package com.proaula.aula.Repository;

import java.util.List;
import java.util.Optional;

import com.proaula.aula.Entity.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Long> {
    List<Viaje> findByUsuarioUsernameIgnoreCase(String username);
    Optional<Viaje> findByIdAndUsuarioUsernameIgnoreCase(Long id, String username);
}