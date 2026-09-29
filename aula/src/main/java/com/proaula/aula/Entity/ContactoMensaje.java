package com.proaula.aula.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Data
public class ContactoMensaje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    
    @NotBlank(message = "El apellido es obligatorio")
    private String apellido;
    
    @NotBlank(message = "El teléfono es obligatorio")
    private String telefono;
    
    @Email(message = "Debe ser una dirección de email válida")
    @NotBlank(message = "El email es obligatorio")
    private String email;
    
    @NotBlank(message = "El mensaje es obligatorio")
    private String mensaje;
}