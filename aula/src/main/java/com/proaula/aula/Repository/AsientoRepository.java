package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Asiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsientoRepository extends JpaRepository<Asiento, Long> {
    boolean existsByBus_Id(Long busId);

    List<Asiento> findByBusIdOrderByNumeroAsc(Long busId);
}
