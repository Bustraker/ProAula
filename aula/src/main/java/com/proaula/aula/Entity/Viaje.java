package com.proaula.aula.Entity;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Entity
@Table(indexes = {
    @Index(name = "idx_viaje_usuario_fecha", columnList = "usuario_id, fecha"),
    @Index(name = "idx_viaje_ruta_id", columnList = "ruta_id")
})
@Data
public class Viaje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    private String nombreRuta;

    private LocalDate fecha;

    private LocalTime hora;

    @NotBlank
    @Size(max = 30)
    private String estado;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;
}
