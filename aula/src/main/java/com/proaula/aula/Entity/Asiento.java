package com.proaula.aula.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.validation.constraints.Size;
import lombok.Data;

@Entity
@Table(
    name = "asiento",
    indexes = @Index(name = "idx_asiento_bus", columnList = "bus_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_asiento_bus_numero", columnNames = {"bus_id", "numero"})
)
@Data
public class Asiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer numero;

    @Size(max = 30)
    @Column(length = 30)
    private String tipo;

    @Size(max = 30)
    @Column(nullable = false, length = 30)
    private String estado = "DISPONIBLE";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id", nullable = false)
    @JsonIgnore
    private Bus bus;
}
