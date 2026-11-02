package com.proaula.aula.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.proaula.aula.Entity.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Long> {
    boolean existsByUsuario_Id(Long usuarioId);

    boolean existsByRuta_Id(Long rutaId);

    List<Viaje> findByUsuarioUsernameIgnoreCase(String username);
    Optional<Viaje> findByIdAndUsuarioUsernameIgnoreCase(Long id, String username);

    @Query("""
            select year(v.fecha), month(v.fecha), count(v)
            from Viaje v
            where v.fecha >= :inicio and v.fecha < :fin
            group by year(v.fecha), month(v.fecha)
            """)
    List<Object[]> contarPorMes(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("""
            select coalesce(ruta.nombre, viaje.nombreRuta), count(viaje)
            from Viaje viaje left join viaje.ruta ruta
            group by coalesce(ruta.nombre, viaje.nombreRuta)
            order by count(viaje) desc
            """)
    List<Object[]> contarPorRuta();
}