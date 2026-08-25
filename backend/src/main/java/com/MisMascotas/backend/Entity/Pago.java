package com.MisMascotas.backend.Entity;

import java.math.BigDecimal;
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
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_pago", nullable = false, updatable = false)
    private UUID idPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suscripcion_id", nullable = false)
    private Suscripcion suscripcion;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_pago", nullable = false)
    private Instant fechaPago;

    @Column(name = "id_transaccion_mercadopago", length = 150)
    private String idTransaccionMercadopago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    private Estado estadoCatalogo;

    @Column(name = "estado", nullable = false, length = 50)
    private String estado;
}