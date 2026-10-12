package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Parada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParadaRepository extends JpaRepository<Parada, Long> {
    boolean existsByRuta_Id(Long rutaId);
    List<Parada> findByRuta_IdOrderByOrdenAsc(Long rutaId);

    // Operaciones CRUD automáticas
}