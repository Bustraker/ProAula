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
import lombok.ToString;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 4, max = 20, message = "El nombre de usuario debe tener entre 4 y 20 caracteres")
    @Column(nullable = false, unique = true, length = 20)
    private String username;
    
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    private String provider = "LOCAL";
    private String providerId;
    private boolean passwordSetupRequired;
    
    private String role = "ROLE_USER"; // Valor por defecto
    
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
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Rol> roles = new HashSet<>();

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conductor conductor;

    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Viaje> viajes = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Pasaje> pasajes = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Notificacion> notificaciones = new java.util.ArrayList<>();
}