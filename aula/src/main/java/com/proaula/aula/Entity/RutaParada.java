package com.proaula.aula.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity
@Table(
    name = "ruta_parada",
    indexes = @Index(name = "idx_ruta_parada_orden", columnList = "ruta_id, orden"),
    uniqueConstraints = @UniqueConstraint(name = "uk_ruta_parada_sentido", columnNames = {"ruta_id", "parada_id", "sentido"})
)
@Data
public class RutaParada {

    public enum Sentido { IDA, VUELTA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer orden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sentido sentido = Sentido.IDA;

    @Column(name = "distancia_desde_origen_mt")
    private Integer distanciaDesdeOrigenMetros;

    @Column(name = "tiempo_aprox_desde_inicio_min")
    private Integer tiempoAproximadoDesdeInicioMinutos;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id", nullable = false)
    @JsonIgnore
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parada_id", nullable = false)
    @JsonIgnore
    private Parada parada;
}
