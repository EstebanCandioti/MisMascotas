package com.MisMascotas.backend.Mapper;

import com.MisMascotas.backend.DTO.LogAuditoriaResponseDTO;
import com.MisMascotas.backend.Entity.LogAuditoria;

public class LogAuditoriaMapper {

    private LogAuditoriaMapper() {
    }

    public static LogAuditoriaResponseDTO toDTO(LogAuditoria log) {
        return LogAuditoriaResponseDTO.builder()
                .idLog(log.getIdLog())
                .actorId(log.getActor() != null ? log.getActor().getIdUsuario() : null)
                .actorEmail(log.getActor() != null ? log.getActor().getEmail() : null)
                .tipoAccion(log.getTipoAccion())
                .entidadAfectada(log.getEntidadAfectada())
                .idEntidad(log.getIdEntidad())
                .valorAnterior(log.getValorAnterior())
                .valorNuevo(log.getValorNuevo())
                .fechaHora(log.getFechaHora())
                .build();
    }
}