package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    boolean existsByUsuario_Id(Long usuarioId);

    List<Notificacion> findByUsuarioIdOrderByIdDesc(Long usuarioId);
    List<Notificacion> findByUsuarioIdAndLeidoFalseOrderByIdDesc(Long usuarioId);
}
