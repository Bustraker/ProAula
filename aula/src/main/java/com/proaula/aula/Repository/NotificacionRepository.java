package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    boolean existsByUsuario_Id(Long usuarioId);

    List<Notificacion> findByUsuarioIdOrderByIdDesc(Long usuarioId);
    List<Notificacion> findByUsuarioIdAndLeidoFalseOrderByIdDesc(Long usuarioId);
    List<Notificacion> findTop5ByUsuario_IdAndTipoOrderByIdDesc(Long usuarioId, String tipo);
    long countByUsuario_IdAndTipoAndLeidoFalse(Long usuarioId, String tipo);
    Optional<Notificacion> findByIdAndUsuario_IdAndTipo(Long id, Long usuarioId, String tipo);
}
