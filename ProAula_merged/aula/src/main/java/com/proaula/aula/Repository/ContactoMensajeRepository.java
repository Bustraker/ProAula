package com.proaula.aula.Repository;

import com.proaula.aula.Entity.ContactoMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContactoMensajeRepository extends JpaRepository<ContactoMensaje, Long> {
    
    @Query("SELECT c FROM ContactoMensaje c WHERE " +
           "LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.telefono) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.mensaje) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<ContactoMensaje> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrMessageContainingIgnoreCase(@Param("q") String q);
}