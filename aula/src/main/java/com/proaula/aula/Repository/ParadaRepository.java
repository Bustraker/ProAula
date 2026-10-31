package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Parada;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParadaRepository extends JpaRepository<Parada, Long> {
    boolean existsByRuta_Id(Long rutaId);
    List<Parada> findByRuta_IdOrderByOrdenAsc(Long rutaId);

    @EntityGraph(attributePaths = "ruta")
    @Query("select parada from Parada parada left join parada.ruta ruta order by ruta.nombre, parada.orden, parada.id")
    List<Parada> findAllWithRutaOrderByRutaAndOrden();

    // Operaciones CRUD automáticas
}