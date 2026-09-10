package com.MisMascotas.backend.Controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.LogAuditoriaResponseDTO;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Service.LogAuditoriaService;

@RestController
@RequestMapping("/auditoria")
public class LogAuditoriaController {

    private final LogAuditoriaService logAuditoriaService;

    public LogAuditoriaController(LogAuditoriaService logAuditoriaService) {
        this.logAuditoriaService = logAuditoriaService;
    }

    @GetMapping("/{entidad}/{idEntidad}")
    public ResponseEntity<List<LogAuditoriaResponseDTO>> obtenerPorEntidad(
            @PathVariable String entidad,
            @PathVariable UUID idEntidad) {
        return ResponseEntity.ok(logAuditoriaService.obtenerPorEntidad(entidad, idEntidad));
    }

    @GetMapping("/actor/{actorId}")
    public ResponseEntity<List<LogAuditoriaResponseDTO>> obtenerPorActor(@PathVariable UUID actorId) {
        return ResponseEntity.ok(logAuditoriaService.obtenerPorActor(actorId));
    }

    @GetMapping
    public ResponseEntity<Page<LogAuditoriaResponseDTO>> listar(
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) TipoAccionAuditoria accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "fechaHora,desc") String sort) {
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
        return ResponseEntity.ok(logAuditoriaService.listarConFiltros(entidad, accion, desde, hasta, pageable));
    }
}