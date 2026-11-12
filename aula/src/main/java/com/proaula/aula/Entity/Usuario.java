package com.proaula.aula.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Entity
@Getter
@Setter
@ToString(exclude = {"password", "roles", "conductor", "viajes", "pasajes", "notificaciones"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 4, max = 20, message = "El nombre de usuario debe tener entre 4 y 20 caracteres")
    @Column(nullable = false, unique = true, length = 20)
    private String username;
    
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    private String provider = "LOCAL";
    private String providerId;
    private boolean passwordSetupRequired;
    
    @Column(nullable = false)
    private String role = "ROLE_USER"; // Valor por defecto

    @Column(nullable = false)
    private boolean emailVerified = true;

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres; // Agregado
    
    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos; // Agregado
    
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe ser una dirección de email válida")
    @Column(nullable = false, unique = true, length = 254)
    private String email; // Agregado

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rol_usuario",
        joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "rol_id")
    )
    private Set<Rol> roles = new HashSet<>();

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY)
    private Conductor conductor;

    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    private List<Viaje> viajes = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    private List<Pasaje> pasajes = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    private List<Notificacion> notificaciones = new java.util.ArrayList<>();

    public void setRole(String role) {
        String normalizedRole = role == null || role.isBlank() ? "ROLE_USER" : role.trim();
        if (!normalizedRole.startsWith("ROLE_")) {
            normalizedRole = "ROLE_" + normalizedRole;
        }
        this.role = normalizedRole.toUpperCase(Locale.ROOT);
    }

    public void setRoles(Set<Rol> roles) {
        this.roles = roles == null ? new HashSet<>() : roles;
        if (this.roles.isEmpty()) {
            this.role = "ROLE_USER";
            return;
        }
        this.role = this.roles.stream()
            .map(Rol::getNombre)
            .filter(nombre -> nombre != null && !nombre.isBlank())
            .map(nombre -> nombre.trim())
            .map(nombre -> nombre.startsWith("ROLE_") ? nombre : "ROLE_" + nombre)
            .map(nombre -> nombre.toUpperCase(Locale.ROOT))
            .findFirst()
            .orElse("ROLE_USER");
    }

    public boolean hasRole(String expectedRole) {
        if (expectedRole == null || expectedRole.isBlank()) {
            return false;
        }
        String normalizedExpected = expectedRole.trim();
        if (!normalizedExpected.startsWith("ROLE_")) {
            normalizedExpected = "ROLE_" + normalizedExpected;
        }
        final String finalNormalizedExpected = normalizedExpected.toUpperCase(Locale.ROOT);
        return finalNormalizedExpected.equalsIgnoreCase(this.role)
            || this.roles.stream()
                .map(Rol::getNombre)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .anyMatch(nombre -> finalNormalizedExpected.equalsIgnoreCase(
                    nombre.startsWith("ROLE_") ? nombre : "ROLE_" + nombre));
    }
}