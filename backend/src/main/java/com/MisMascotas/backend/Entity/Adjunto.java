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
@Table(name = "adjunto")
public class Adjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_adjunto", nullable = false, updatable = false)
    private UUID idAdjunto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private EventoClinico evento;

    @Column(name = "url_archivo", nullable = false)
    private String urlArchivo;

    @Column(name = "formato", length = 20)
    private String formato;

    @Column(name = "subida_en", nullable = false, updatable = false)
    private Instant subidaEn;

    @Column(name = "id_cliente", unique = true)
    private UUID idCliente;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;

    @PrePersist
    public void prePersist() {
        if (subidaEn == null) {
            subidaEn = Instant.now();
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