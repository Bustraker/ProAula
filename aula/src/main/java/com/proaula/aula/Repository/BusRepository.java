package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {
    // Operaciones CRUD automáticas
}