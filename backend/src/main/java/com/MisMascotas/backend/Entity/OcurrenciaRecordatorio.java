package com.MisMascotas.backend.Entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ocurrencia_recordatorio")
public class OcurrenciaRecordatorio {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_ocurrencia", nullable = false, updatable = false)
    private UUID idOcurrencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recordatorio_id", nullable = false, updatable = false)
    private Recordatorio recordatorio;

    @Column(name = "fecha_programada", nullable = false, updatable = false)
    private Instant fechaProgramada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por_id")
    private Usuario confirmadoPor;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;
}
