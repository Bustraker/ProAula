package com.proaula.aula.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Data;

@Entity
@Table(
    indexes = @Index(name = "idx_parada_ruta_orden", columnList = "ruta_id, orden"),
    uniqueConstraints = @UniqueConstraint(name = "uk_parada_ruta_orden", columnNames = {"ruta_id", "orden"})
)
@Data
public class Parada {
    public enum Estado { ACTIVA, INACTIVA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String nombre; // Nombre de la parada
    
    @Size(max = 255)
    @Column(name = "direccion", length = 300)
    private String direccion;

    @Size(max = 255)
    private String ubicacion; // Ubicación aproximada
    
    @Size(max = 255)
    private String referencia; // Punto de referencia

    @Size(max = 100)
    private String barrio;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.ACTIVA;

    @Column(name = "accesible_movilidad_reducida", nullable = false)
    private Boolean accesibleMovilidadReducida = false;

    @Column(name = "tiene_sombra")
    private Boolean tieneSombra = false;

    @Column(name = "tiene_asientos")
    private Boolean tieneAsientos = false;

    @Column(name = "observaciones_accesibilidad", columnDefinition = "text")
    private String observacionesAccesibilidad;
    
    @Min(1)
    private Integer orden; // Orden de la parada en la ruta

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitud;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitud;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    @Size(max = 20)
    private String codigo;

    @ManyToOne
    @JoinColumn(name = "ruta_id")
    @JsonIgnoreProperties({"buses", "paradas"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Ruta ruta;

    @OneToMany(mappedBy = "parada", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private java.util.List<RutaParada> rutas = new java.util.ArrayList<>();

    @PrePersist
    void generarCodigoSiHaceFalta() {
        if (codigo == null || codigo.isBlank()) {
            codigo = CodigoInterno.generar("PAR-");
        }
    }
}