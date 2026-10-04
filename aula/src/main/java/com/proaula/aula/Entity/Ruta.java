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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.ToString;
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