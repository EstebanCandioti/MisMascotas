package com.MisMascotas.backend.Entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evento_auditoria_pendiente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoAuditoriaPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_evento", nullable = false, updatable = false)
    private UUID idEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Usuario actor;

    @Column(name = "entidad_afectada", nullable = false, length = 50)
    private String entidadAfectada;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_accion", nullable = false, length = 30)
    private TipoAccionAuditoria tipoAccion;

    @Column(name = "id_entidad")
    private UUID idEntidad;

    @Column(name = "valor_anterior", columnDefinition = "text")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "text")
    private String valorNuevo;

    @Column(name = "fecha_evento", nullable = false, updatable = false)
    private Instant fechaEvento;

    @Column(name = "procesado_en")
    private Instant procesadoEn;

    @Column(name = "intentos_procesamiento", nullable = false)
    @Builder.Default
    private int intentosProcesamiento = 0;

    @Column(name = "ultimo_intento_en")
    private Instant ultimoIntentoEn;

    @Column(name = "ultimo_error", columnDefinition = "text")
    private String ultimoError;

    @PrePersist
    protected void onCreate() {
        if (fechaEvento == null) {
            fechaEvento = Instant.now();
        }
    }

    public void marcarProcesado(Instant instante) {
        intentosProcesamiento++;
        ultimoIntentoEn = instante;
        procesadoEn = instante;
        ultimoError = null;
    }

    public void registrarFallo(Instant instante, String detalle) {
        intentosProcesamiento++;
        ultimoIntentoEn = instante;
        ultimoError = detalle;
    }
}
