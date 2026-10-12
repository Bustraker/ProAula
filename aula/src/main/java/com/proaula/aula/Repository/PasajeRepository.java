package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Pasaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasajeRepository extends JpaRepository<Pasaje, Long> {
    boolean existsByUsuario_Id(Long usuarioId);

    boolean existsByRuta_Id(Long rutaId);

    boolean existsByBus_Id(Long busId);

    List<Pasaje> findByUsuarioIdOrderByFechaViajeDesc(Long usuarioId);
    List<Pasaje> findByRutaIdAndFechaViaje(Long rutaId, java.time.LocalDateTime fechaViaje);
}
