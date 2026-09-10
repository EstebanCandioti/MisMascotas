package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.UUID;

import com.MisMascotas.backend.Entity.TipoAccionAuditoria;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogAuditoriaResponseDTO {
    private UUID idLog;
    private UUID actorId;
    private String actorEmail;
    private TipoAccionAuditoria tipoAccion;
    private String entidadAfectada;
    private UUID idEntidad;
    private String valorAnterior;
    private String valorNuevo;
    private Instant fechaHora;
}