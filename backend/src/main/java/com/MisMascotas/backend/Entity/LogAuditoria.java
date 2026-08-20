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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "log_auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_log", updatable = false, nullable = false)
    private UUID idLog;

    // Nullable: si es null, la acción fue ejecutada por un proceso automático del sistema.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Usuario actor;

    @NotBlank
    @Column(name = "tipo_accion", nullable = false, length = 30)
    private String tipoAccion;

    @NotBlank
    @Column(name = "entidad_afectada", nullable = false, length = 50)
    private String entidadAfectada;

    @NotNull
    @Column(name = "id_entidad", nullable = false)
    private Integer idEntidad;

    @Column(name = "valor_anterior")
    private String valorAnterior;

    @Column(name = "valor_nuevo")
    private String valorNuevo;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private Instant fechaHora;

    @PrePersist
    protected void onCreate() {
        if (fechaHora == null) {
            fechaHora = Instant.now();
        }
    }
}
