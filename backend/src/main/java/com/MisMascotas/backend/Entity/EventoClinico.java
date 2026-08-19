package com.MisMascotas.backend.Entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "evento_clinico")
public class EventoClinico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_evento", nullable = false, updatable = false)
    private UUID idEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", nullable = false)
    private Mascota mascota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    private Usuario registradoPor;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "fecha", nullable = false)
    private Instant fecha;

    @Column(name = "dosis", length = 100)
    private String dosis;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "diagnostico")
    private String diagnostico;

    @Column(name = "valor_peso", precision = 6, scale = 2)
    private BigDecimal valorPeso;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @OneToMany(mappedBy = "evento")
    private List<Adjunto> adjuntos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }
}