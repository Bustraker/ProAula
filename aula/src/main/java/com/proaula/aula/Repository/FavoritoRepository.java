package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Favorito;
import com.proaula.aula.Entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoritoRepository extends JpaRepository<Favorito, Long> {

    List<Favorito> findByUsuario_IdOrderByIdDesc(Long usuarioId);

    Optional<Favorito> findByUsuario_IdAndRuta_Id(Long usuarioId, Long rutaId);

    Optional<Favorito> findByUsuario_IdAndParada_Id(Long usuarioId, Long paradaId);

    Optional<Favorito> findByIdAndUsuario_Id(Long favoritoId, Long usuarioId);

    @Query("""
            select distinct favorito.usuario from Favorito favorito
            left join favorito.parada parada
            where favorito.ruta.id = :rutaId or parada.ruta.id = :rutaId
            """)
    List<Usuario> findDistinctUsuariosByRutaFavorita(@Param("rutaId") Long rutaId);
}
