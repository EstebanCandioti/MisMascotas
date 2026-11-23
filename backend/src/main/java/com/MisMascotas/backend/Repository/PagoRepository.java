package com.MisMascotas.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MisMascotas.backend.Entity.Pago;

public interface PagoRepository extends JpaRepository<Pago, UUID> {
    Optional<Pago> findByIdTransaccionMercadopago(String idTransaccionMercadopago);

    Optional<Pago> findByIdFacturaMercadopago(String idFacturaMercadopago);

    @org.springframework.data.jpa.repository.Query("""
            select (count(p) > 0) from Pago p
            where p.suscripcion.idSuscripcion = :id
              and p.estadoCatalogo.entidad = 'PAGO' and p.estadoCatalogo.nombre = 'APROBADO'
            """)
    boolean existePagoAprobado(@org.springframework.data.repository.query.Param("id") UUID idSuscripcion);

    List<Pago> findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(UUID idSuscripcion);
}
