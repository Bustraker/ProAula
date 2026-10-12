package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Ruta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RutaRepository extends JpaRepository<Ruta, Long> {
    // Operaciones CRUD automáticas
    Optional<Ruta> findByCodigo(String codigo);
    
    @EntityGraph(attributePaths = "paradas")
    List<Ruta> findAllByVerificadaTrue();

    List<Ruta> findFirst4ByOrderByNombreAsc();
}