package com.proaula.aula.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "asignacion_ruta_bus")
@Data
public class AsignacionRutaBus {

    public enum Turno { MANANA, TARDE, NOCHE, COMPLETO }
    public enum Estado { ACTIVA, FINALIZADA, SUSPENDIDA }

    @jakarta.persistence.Converter
    public static class TurnoConverter implements jakarta.persistence.AttributeConverter<Turno, String> {
        @Override
        public String convertToDatabaseColumn(Turno turno) {
            return turno == Turno.MANANA ? "MAÑANA" : turno == null ? null : turno.name();
        }

        @Override
        public Turno convertToEntityAttribute(String value) {
            return "MAÑANA".equals(value) ? Turno.MANANA : value == null ? null : Turno.valueOf(value);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDateTime fechaAsignacion = LocalDateTime.now();

    @NotNull
    @Convert(converter = TurnoConverter.class)
    @Column(nullable = false, columnDefinition = "enum('MAÑANA','TARDE','NOCHE','COMPLETO')")
    private Turno turno;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.ACTIVA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id", nullable = false)
    @JsonIgnore
    private Bus bus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id", nullable = false)
    @JsonIgnore
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conductor_id", nullable = false)
    @JsonIgnore
    private Conductor conductor;
}
