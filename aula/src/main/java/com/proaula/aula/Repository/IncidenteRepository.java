package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Incidente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidenteRepository extends JpaRepository<Incidente, Long> {
    boolean existsByRuta_Id(Long rutaId);
}
