package com.MisMascotas.backend.Entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "recordatorio")
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_recordatorio", nullable = false, updatable = false)
    private UUID idRecordatorio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", nullable = false)
    private Mascota mascota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private Instant fechaHoraInicio;

    @Column(name = "modalidad", nullable = false, length = 50)
    private String modalidad = "unica";

    @Column(name = "intervalo_valor")
    private Integer intervaloValor;

    @Column(name = "intervalo_unidad", length = 20)
    private String intervaloUnidad;

    @Column(name = "dia_semana", length = 20)
    private String diaSemana;

    @Column(name = "fecha_fin")
    private Instant fechaFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por_id")
    private Usuario confirmadoPor;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    private Estado estado;

    @Column(name = "id_cliente", unique = true)
    private UUID idCliente;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;

    @PrePersist
    public void prePersist() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
        if (actualizadoEn == null) {
            actualizadoEn = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}