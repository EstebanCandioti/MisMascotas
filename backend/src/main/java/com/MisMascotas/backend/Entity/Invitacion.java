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
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.MisMascotas.backend.Util.NormalizadorEmail;

@Entity
@Table(name = "invitacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_invitacion", updatable = false, nullable = false)
    private UUID idInvitacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propietario_id", nullable = false)
    private Usuario propietario;

    @NotBlank
    @Email
    @Column(name = "email_invitado", nullable = false, length = 255)
    private String emailInvitado;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 30)
    @Builder.Default
    private PerfilCompartido perfil = PerfilCompartido.Cuidador;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_id", nullable = false)
    private Estado estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @NotNull
    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acceso_id", unique = true)
    private AccesoCompartido accesoCompartido;

    @PrePersist
    protected void onCreate() {
        emailInvitado = NormalizadorEmail.normalizar(emailInvitado);
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }

}
