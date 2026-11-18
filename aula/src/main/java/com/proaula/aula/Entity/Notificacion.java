package com.proaula.aula.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificacion")
@Data
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String titulo;

    @NotBlank
    @Column(nullable = false, columnDefinition = "text")
    private String mensaje;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String tipo;

    @NotNull
    @Column(nullable = false)
    private Boolean leido = false;

    @Column(name = "fecha_leido")
    private LocalDateTime fechaLeido;

    @Column(name = "canal_envio", nullable = false, length = 20)
    private String canalEnvio = "IN_APP";

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnore
    private Usuario usuario;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
        if (this.leido == null) {
            this.leido = false;
        }
        if (this.canalEnvio == null || this.canalEnvio.isBlank()) {
            this.canalEnvio = "IN_APP";
        }
    }
}
