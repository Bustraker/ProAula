package com.proaula.aula.Repository;

import com.proaula.aula.Entity.TrayectoFavorito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrayectoFavoritoRepository extends JpaRepository<TrayectoFavorito, Long> {

    List<TrayectoFavorito> findByUsuario_IdOrderByFechaGuardadoDesc(Long usuarioId);

    Optional<TrayectoFavorito> findByIdAndUsuario_Id(Long trayectoId, Long usuarioId);
}
