package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Horario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HorarioRepository extends JpaRepository<Horario, Long> {
    boolean existsByRuta_Id(Long rutaId);

    List<Horario> findByRutaIdAndEstadoOrderByHoraSalidaAsc(Long rutaId, Horario.Estado estado);
}
