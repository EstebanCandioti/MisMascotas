package com.MisMascotas.backend.Entity;

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
@Table(name = "suscripcion")
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_suscripcion", nullable = false, updatable = false)
    private UUID idSuscripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "tipo_plan", nullable = false, length = 50)
    private String tipoPlan;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio;

    @Column(name = "fecha_vencimiento")
    private Instant fechaVencimiento;

    @Column(name = "proximo_recargo")
    private Instant proximoRecargo;

    @Column(name = "fecha_cancelacion")
    private Instant fechaCancelacion;

    @Column(name = "id_suscripcion_mercadopago", length = 150, unique = true)
    private String idSuscripcionMercadopago;

    @Column(name = "reemplazada", nullable = false)
    private boolean reemplazada;

    // Estado de autorizacion de cobros remoto, independiente de la cobertura pagada.
    @Column(name = "estado_mercadopago", length = 30)
    private String estadoMercadopago;

    @Column(name = "actualizada_mercadopago_en")
    private Instant actualizadaMercadopagoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    private Estado estadoCatalogo;

    @OneToMany(mappedBy = "suscripcion")
    private List<Pago> pagos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (fechaInicio == null) {
            fechaInicio = Instant.now();
        }
    }
}

