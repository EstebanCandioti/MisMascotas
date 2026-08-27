package com.MisMascotas.backend.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.EventoClinicoRequestDTO;
import com.MisMascotas.backend.DTO.EventoClinicoResponseDTO;
import com.MisMascotas.backend.Service.EventoClinicoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EventoClinicoController {

    private final EventoClinicoService eventoClinicoService;

    @PostMapping("/mascotas/{mascotaId}/eventos-clinicos")
    public ResponseEntity<EventoClinicoResponseDTO> crear(@PathVariable UUID mascotaId,
                                                         @Valid @RequestBody EventoClinicoRequestDTO request,
                                                         Principal principal) {
        UUID registradoPorId = UUID.fromString(principal.getName());
        EventoClinicoRequestDTO requestNormalizado = new EventoClinicoRequestDTO(
                mascotaId,
                request.tipo(),
                request.fecha(),
                request.nombre(),
                request.dosis(),
                request.valorNumerico(),
                request.motivo(),
                request.diagnostico(),
                request.observaciones(),
                request.urlAdjunto()
        );
        EventoClinicoResponseDTO nuevoEvento = eventoClinicoService.crear(requestNormalizado, registradoPorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoEvento);
    }

    @GetMapping("/mascotas/{mascotaId}/eventos-clinicos")
    public ResponseEntity<List<EventoClinicoResponseDTO>> listarPorMascota(@PathVariable UUID mascotaId,
                                                                           @RequestParam(required = false) String tipo) {
        return ResponseEntity.ok(eventoClinicoService.listarPorMascota(mascotaId, tipo));
    }

    @GetMapping("/eventos-clinicos/{id}")
    public ResponseEntity<EventoClinicoResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(eventoClinicoService.obtenerPorId(id));
    }

    @PutMapping("/eventos-clinicos/{id}")
    public ResponseEntity<EventoClinicoResponseDTO> editar(@PathVariable UUID id,
                                                          @Valid @RequestBody EventoClinicoRequestDTO request) {
        return ResponseEntity.ok(eventoClinicoService.editar(id, request));
    }

    @DeleteMapping("/eventos-clinicos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        eventoClinicoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}