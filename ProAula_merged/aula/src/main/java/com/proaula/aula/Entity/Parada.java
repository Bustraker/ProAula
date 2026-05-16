package com.proaula.aula.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Data
public class Parada {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nombre; // Nombre de la parada
    
    private String ubicacion; // Ubicación aproximada
    
    private String referencia; // Punto de referencia
    
    private Integer orden; // Orden de la parada en la ruta

    @ManyToOne
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;
}