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
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private Perfil perfil = Perfil.Cuidador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    private Estado estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "expira_en")
    private Instant expiraEn;

    @PrePersist
    protected void onCreate() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }

    public enum Perfil {
        Familia, Cuidador
    }

    @Transient
    public Perfil perfilPorDefecto() {
        return perfil;
    }
}

@Entity
@Table(name = "acceso_compartido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AccesoCompartido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_acceso", updatable = false, nullable = false)
    private UUID idAcceso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propietario_id", nullable = false)
    private Usuario propietario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitado_id", nullable = false)
    private Usuario invitado;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 30)
    @Builder.Default
    private Invitacion.Perfil perfil = Invitacion.Perfil.Cuidador;

    @Column(name = "fecha_ingreso", nullable = false, updatable = false)
    private Instant fechaIngreso;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @PrePersist
    protected void onCreate() {
        if (fechaIngreso == null) {
            fechaIngreso = Instant.now();
        }
    }
}