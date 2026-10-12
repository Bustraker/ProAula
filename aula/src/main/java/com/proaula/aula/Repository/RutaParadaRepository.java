package com.proaula.aula.Repository;

import com.proaula.aula.Entity.RutaParada;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RutaParadaRepository extends JpaRepository<RutaParada, Long> {
    boolean existsByRuta_Id(Long rutaId);

    @EntityGraph(attributePaths = "parada")
    List<RutaParada> findByRutaIdAndSentidoOrderByOrdenAsc(Long rutaId, RutaParada.Sentido sentido);
}
