package com.proaula.aula.Repository;

import com.proaula.aula.Entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("SELECT u FROM Usuario u WHERE LOWER(u.username) = LOWER(:username)")
    @EntityGraph(attributePaths = "roles")
    Usuario findByUsername(@Param("username") String username); // Para login

    @Query("SELECT u FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)")
    @EntityGraph(attributePaths = "roles")
    Usuario findByEmail(@Param("email") String email); // Para verificar si el email ya existe

    Usuario findByProviderAndProviderId(String provider, String providerId);

    @EntityGraph(attributePaths = "roles")
    @Query("SELECT u FROM Usuario u")
    java.util.List<Usuario> findAllWithRoles();
    
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM Usuario u WHERE LOWER(u.username) = LOWER(:username)")
    boolean existsByUsername(@Param("username") String username);

    boolean existsByUsernameIgnoreCase(String username);
    
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);
    
    // Métodos para validar roles de administrador
    Usuario findByUsernameAndRole(String username, String role);
    
    int countByRole(String role);
}