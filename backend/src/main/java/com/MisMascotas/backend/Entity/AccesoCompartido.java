package com.MisMascotas.backend.Entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** Un vínculo alcanza todas las mascotas activas del propietario, no las de sus invitados. */
@Entity
@Table(name = "acceso_compartido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccesoCompartido {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_acceso", updatable = false, nullable = false)
    private UUID idAcceso;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propietario_id", nullable = false)
    private Usuario propietario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitado_id", nullable = false)
    private Usuario invitado;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 30)
    @Builder.Default
    private PerfilCompartido perfil = PerfilCompartido.Cuidador;

    @Column(name = "fecha_ingreso", nullable = false, updatable = false)
    private Instant fechaIngreso;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @PrePersist
    protected void onCreate() {
        if (fechaIngreso == null) fechaIngreso = Instant.now();
    }
}
