package com.proaula.aula.Repository;

import com.proaula.aula.Entity.AsignacionRutaBus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsignacionRutaBusRepository extends JpaRepository<AsignacionRutaBus, Long> {
    boolean existsByRuta_Id(Long rutaId);

    boolean existsByBus_Id(Long busId);

    List<AsignacionRutaBus> findByRutaIdAndEstado(Long rutaId, AsignacionRutaBus.Estado estado);
    List<AsignacionRutaBus> findByConductorIdAndEstado(Long conductorId, AsignacionRutaBus.Estado estado);
}
