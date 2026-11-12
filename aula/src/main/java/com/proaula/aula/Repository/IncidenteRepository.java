package com.proaula.aula.Repository;

import java.time.LocalDateTime;
import java.util.List;

import com.proaula.aula.Entity.Incidente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidenteRepository extends JpaRepository<Incidente, Long> {
    boolean existsByRuta_Id(Long rutaId);

    List<Incidente> findByRuta_IdAndEstadoInOrderByFechaReporteDescIdDesc(
            Long rutaId, List<String> estados);

    long countByEstadoIgnoreCase(String estado);

    @Query("""
            select incidente.tipo, count(incidente)
            from Incidente incidente
            group by incidente.tipo
            order by count(incidente) desc
            """)
    List<Object[]> contarPorTipo();

    @Query("""
            select incidente.estado, count(incidente)
            from Incidente incidente
            group by incidente.estado
            order by count(incidente) desc
            """)
    List<Object[]> contarPorEstado();

    @Query("""
            select year(incidente.fechaReporte), month(incidente.fechaReporte), count(incidente)
            from Incidente incidente
            where incidente.fechaReporte >= :inicio and incidente.fechaReporte < :fin
            group by year(incidente.fechaReporte), month(incidente.fechaReporte)
            """)
    List<Object[]> contarPorMes(
            @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query(value = """
            select incidente from Incidente incidente
            left join fetch incidente.ruta ruta
            where (:tipo is null or :tipo = '' or incidente.tipo = :tipo)
              and (:estado is null or :estado = '' or incidente.estado = :estado)
              and (:ruta is null or :ruta = ''
                   or lower(ruta.nombre) like lower(concat('%', :ruta, '%'))
                   or lower(ruta.codigo) like lower(concat('%', :ruta, '%'))
                   or exists (
                       select bus.id from Bus bus
                       where bus.ruta = ruta
                         and (lower(bus.placa) like lower(concat('%', :ruta, '%'))
                              or lower(bus.numeroInterno) like lower(concat('%', :ruta, '%')))))
            order by incidente.fechaReporte desc, incidente.id desc
            """,
            countQuery = """
                    select count(incidente) from Incidente incidente
                    left join incidente.ruta ruta
                    where (:tipo is null or :tipo = '' or incidente.tipo = :tipo)
                      and (:estado is null or :estado = '' or incidente.estado = :estado)
                      and (:ruta is null or :ruta = ''
                           or lower(ruta.nombre) like lower(concat('%', :ruta, '%'))
                           or lower(ruta.codigo) like lower(concat('%', :ruta, '%'))
                           or exists (
                               select bus.id from Bus bus
                               where bus.ruta = ruta
                                 and (lower(bus.placa) like lower(concat('%', :ruta, '%'))
                                      or lower(bus.numeroInterno) like lower(concat('%', :ruta, '%')))))
                    """)
    Page<Incidente> findForPanel(
            @Param("tipo") String tipo,
            @Param("ruta") String ruta,
            @Param("estado") String estado,
            Pageable pageable);
}
