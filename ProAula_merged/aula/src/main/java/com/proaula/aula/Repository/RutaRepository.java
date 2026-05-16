package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RutaRepository extends JpaRepository<Ruta, Long> {
    // Operaciones CRUD automáticas
    
    List<Ruta> findFirst4ByOrderByNombreAsc();
}