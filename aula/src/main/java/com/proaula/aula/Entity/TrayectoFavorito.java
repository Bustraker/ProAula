package com.proaula.aula.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "trayecto_favorito")
@Getter
@Setter
public class TrayectoFavorito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Usuario usuario;

    @Column(nullable = false, length = 255)
    private String origen;

    @Column(nullable = false, length = 255)
    private String destino;

    @Column(name = "rutas_nombres", nullable = false, length = 760)
    private String rutasNombres;

    @Column(name = "barrios_recorridos", nullable = false, length = 7800)
    private String barriosRecorridos;

    @Column(name = "distancia_estimada_km")
    private Double distanciaEstimadaKm;

    @Column(name = "tiempo_estimado_auto_min")
    private Integer tiempoEstimadoAutoMin;

    @Column(name = "paradas_count", nullable = false)
    private Integer paradasCount;

    @Column(name = "fecha_guardado", nullable = false, updatable = false)
    private LocalDateTime fechaGuardado;

    @PrePersist
    void asignarFechaGuardado() {
        if (fechaGuardado == null) {
            fechaGuardado = LocalDateTime.now();
        }
    }
}
