package com.proaula.aula.Entity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Data;

@Entity
@Table(indexes = @Index(name = "idx_ruta_verificada_nombre", columnList = "verificada, nombre"))
@Data
public class Ruta {
    public enum Estado { ACTIVA, INACTIVA, SUSPENDIDA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @NotBlank
    @Size(max = 150)
    private String nombre;

    @Size(max = 150)
    private String origen;

    @Size(max = 150)
    private String destino;

    @Column(name = "tiempo_estimado_min")
    private Integer tiempoEstimadoMinutos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.ACTIVA;

    @Column(name = "color_hex", length = 7)
    private String colorHex = "#3498db";

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(name = "accesible_movilidad_reducida", nullable = false)
    private Boolean accesibleMovilidadReducida = false;

    @Column(name = "observaciones_accesibilidad", columnDefinition = "text")
    private String observacionesAccesibilidad;

    private LocalTime horaAproximada;
    private Boolean verificada = false;

    @ElementCollection
    private List<String> barrios = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "ruta_barrios_ordenados", joinColumns = @JoinColumn(name = "ruta_id"))
    @OrderColumn(name = "posicion")
    @Column(name = "barrio", nullable = false)
    private List<String> barriosOrdenados = new ArrayList<>();

    @OneToMany(mappedBy = "ruta", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Bus> buses = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "ruta", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Parada> paradas = new ArrayList<>();

    @OneToMany(mappedBy = "ruta", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RutaParada> paradasEnRuta = new ArrayList<>();

    @OneToMany(mappedBy = "ruta", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Horario> horarios = new ArrayList<>();

    @PrePersist
    void generarCodigoSiHaceFalta() {
        if (codigo == null || codigo.isBlank()) {
            codigo = CodigoInterno.generar("RUT-");
        }
    }

    public boolean isOrdenBarriosConfirmado() {
        if (barrios == null || barriosOrdenados == null || barriosOrdenados.size() != barrios.size()
                || barriosOrdenados.stream().filter(barrio -> barrio != null && !barrio.isBlank()).distinct().count() < 2) {
            return false;
        }

        Map<String, Integer> cantidades = new HashMap<>();
        for (String barrio : barrios) {
            Integer cantidad = cantidades.get(barrio);
            cantidades.put(barrio, cantidad == null ? 1 : cantidad + 1);
        }
        for (String barrio : barriosOrdenados) {
            Integer cantidad = cantidades.get(barrio);
            if (cantidad == null) {
                return false;
            }
            if (cantidad == 1) {
                cantidades.remove(barrio);
            } else {
                cantidades.put(barrio, cantidad - 1);
            }
        }
        return cantidades.isEmpty();
    }
}