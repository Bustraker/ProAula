package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Barrio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BarrioRepository extends JpaRepository<Barrio, Long> {
    Optional<Barrio> findByNombre(String nombre);
    List<Barrio> findByLocalidad(String localidad);
    boolean existsByNombre(String nombre);
}
