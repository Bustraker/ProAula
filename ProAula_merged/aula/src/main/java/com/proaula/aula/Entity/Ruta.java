package com.proaula.aula.Entity;

import java.time.LocalTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Data
public class Ruta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nombre;
    private LocalTime horaAproximada;

    @ElementCollection
    private List<String> barrios; // Agregado para barrios

    @OneToMany(mappedBy = "ruta", cascade = CascadeType.ALL)
    private List<Bus> buses;

    @OneToMany(mappedBy = "ruta", cascade = CascadeType.ALL)
    private List<Parada> paradas;
}