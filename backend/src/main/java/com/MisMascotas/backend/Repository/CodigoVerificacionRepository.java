package com.MisMascotas.backend.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MisMascotas.backend.Entity.CodigoVerificacion;

public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, UUID> {
    Optional<CodigoVerificacion> findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(UUID usuarioId);
}