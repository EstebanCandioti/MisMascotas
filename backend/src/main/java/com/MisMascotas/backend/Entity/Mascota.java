package com.MisMascotas.backend.Entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "mascota")
public class Mascota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_mascota", nullable = false, updatable = false)
    private UUID idMascota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_id", nullable = false)
    private Usuario propietario;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "especie", nullable = false, length = 50)
    private String especie;

    @Column(name = "raza", length = 100)
    private String raza;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "fecha_aproximada", nullable = false)
    private boolean fechaAproximada = false;

    @Column(name = "edad_valor")
    private Integer edadValor;

    @Column(name = "edad_unidad", length = 20)
    private String edadUnidad;

    @Column(name = "foto_perfil")
    private String fotoPerfil;

    @Column(name = "peso_actual", precision = 6, scale = 2)
    private BigDecimal pesoActual;

    @Column(name = "notas")
    private String notas;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "id_cliente", unique = true)
    private UUID idCliente;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    private Estado estado;

    @OneToMany(mappedBy = "mascota")
    private List<EventoClinico> eventosClinicos = new ArrayList<>();

    @OneToMany(mappedBy = "mascota")
    private List<Recordatorio> recordatorios = new ArrayList<>();

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