package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("SELECT u FROM Usuario u WHERE LOWER(u.username) = LOWER(:username)")
    Usuario findByUsername(@Param("username") String username); // Para login

    Usuario findByEmail(String email); // Para verificar si el email ya existe
    Usuario findByProviderAndProviderId(String provider, String providerId);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    // Métodos para validar roles de administrador
    Usuario findByUsernameAndRole(String username, String role);
    
    int countByRole(String role);
}