package com.proaula.aula.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Data;

@Entity
@Table(indexes = @Index(name = "idx_bus_ruta_id", columnList = "ruta_id"))
@Data
public class Bus {
    public enum Estado { ACTIVO, MANTENIMIENTO, FUERA_SERVICIO }
    public enum NivelOcupacion { BAJA, MEDIA, ALTA, SIN_DATO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 10)
    @Size(max = 10)
    private String placa;

    @NotBlank
    @Size(max = 50)
    private String modelo;

    @Size(max = 30)
    private String color;

    @Size(max = 100)
    private String conductor; // O dueño del bus

    @Column(name = "numero_interno", nullable = false, unique = true, length = 20)
    private String numeroInterno;

    @Size(max = 50)
    @Column(length = 50)
    private String marca;

    @Column(name = "capacidad_pasajeros")
    private Integer capacidadPasajeros;

    private Integer anio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.ACTIVO;

    @Column(name = "accesible_movilidad_reducida", nullable = false)
    private Boolean accesibleMovilidadReducida = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_ocupacion_actual", length = 20)
    private NivelOcupacion nivelOcupacionActual = NivelOcupacion.SIN_DATO;

    @Column(name = "ultima_actualizacion_ocupacion")
    private java.time.LocalDateTime ultimaActualizacionOcupacion;

    @ManyToOne
    @JoinColumn(name = "ruta_id")
    @JsonIgnoreProperties({"buses", "paradas"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agencia_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Agencia agencia;

    @PrePersist
    void generarNumeroInternoSiHaceFalta() {
        if (numeroInterno == null || numeroInterno.isBlank()) {
            numeroInterno = CodigoInterno.generar("BUS-");
        }
    }
}