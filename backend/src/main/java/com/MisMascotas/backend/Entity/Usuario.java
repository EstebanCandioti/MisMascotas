package com.MisMascotas.backend.Entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_usuario", nullable = false, updatable = false)
    private UUID idUsuario;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "es_premium", nullable = false)
    private boolean esPremium = false;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @OneToMany(mappedBy = "propietario")
    private List<Mascota> mascotas = new ArrayList<>();

    @OneToMany(mappedBy = "usuario")
    private List<Suscripcion> suscripciones = new ArrayList<>();

    @OneToMany(mappedBy = "creadoPor")
    private List<Recordatorio> recordatoriosCreados = new ArrayList<>();

    @OneToMany(mappedBy = "confirmadoPor")
    private List<Recordatorio> recordatoriosConfirmados = new ArrayList<>();

    @OneToMany(mappedBy = "registradoPor")
    private List<EventoClinico> eventosClinicosRegistrados = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }
}