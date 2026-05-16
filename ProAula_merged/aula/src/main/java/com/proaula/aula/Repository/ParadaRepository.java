package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Parada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParadaRepository extends JpaRepository<Parada, Long> {
    // Operaciones CRUD automáticas
}