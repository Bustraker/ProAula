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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Entity
@Table(name = "horario", indexes = @Index(name = "idx_horario_ruta_salida", columnList = "ruta_id, hora_salida"))
@Data
public class Horario {

    public enum Sentido { IDA, VUELTA }
    public enum TipoDia { HABIL, SABADO, FESTIVO }
    public enum Estado { ACTIVO, INACTIVO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sentido sentido;

    @NotNull
    @Column(name = "hora_salida", nullable = false)
    private LocalTime horaSalida;

    @Column(name = "hora_llegada")
    private LocalTime horaLlegada;

    @Min(1)
    @Column(name = "frecuencia_min")
    private Integer frecuenciaMinutos;

    @Column(name = "dias_semana", length = 255)
    private String diasSemana;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dia", nullable = false, length = 10)
    private TipoDia tipoDia = TipoDia.HABIL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado = Estado.ACTIVO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id", nullable = false)
    @JsonIgnore
    private Ruta ruta;
}
