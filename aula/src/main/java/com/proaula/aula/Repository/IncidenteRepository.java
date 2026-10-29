package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Incidente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IncidenteRepository extends JpaRepository<Incidente, Long> {
    boolean existsByRuta_Id(Long rutaId);

    @Query("""
            select distinct incidente from Incidente incidente
            left join fetch incidente.ruta ruta
            left join ruta.buses bus
            where (:tipo is null or :tipo = '' or incidente.tipo = :tipo)
              and (:ruta is null or :ruta = ''
                   or lower(ruta.nombre) like lower(concat('%', :ruta, '%'))
                   or lower(ruta.codigo) like lower(concat('%', :ruta, '%'))
                   or lower(bus.placa) like lower(concat('%', :ruta, '%'))
                   or lower(bus.numeroInterno) like lower(concat('%', :ruta, '%')))
            order by incidente.fechaReporte desc
            """)
    List<Incidente> findForPanel(@Param("tipo") String tipo, @Param("ruta") String ruta);
}
