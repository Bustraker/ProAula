package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {
    // Operaciones CRUD automáticas

    long countByEstado(Bus.Estado estado);

    long countByNivelOcupacionActual(Bus.NivelOcupacion nivelOcupacionActual);

    @EntityGraph(attributePaths = {"ruta", "agencia"})
    @Query("SELECT b FROM Bus b")
    List<Bus> findAllWithDetails();

    @EntityGraph(attributePaths = {"ruta", "agencia"})
    @Query("SELECT b FROM Bus b WHERE b.id = :id")
    Optional<Bus> findByIdWithDetails(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Bus bus SET bus.conductor = 'Anónimo' "
        + "WHERE bus.conductor IS NULL OR bus.conductor <> 'Anónimo'")
    int anonimizarConductores();

    boolean existsByRuta_Id(Long rutaId);
}