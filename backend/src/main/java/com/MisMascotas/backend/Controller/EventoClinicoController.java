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
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EventoClinicoController {

    private final EventoClinicoService eventoClinicoService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    @PostMapping("/mascotas/{mascotaId}/eventos-clinicos")
    public ResponseEntity<EventoClinicoResponseDTO> registrar(@PathVariable UUID mascotaId,
                                                             @Valid @RequestBody EventoClinicoRequestDTO request,
                                                             Principal principal) {
        UUID registradoPorId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        EventoClinicoRequestDTO requestNormalizado = new EventoClinicoRequestDTO(
                mascotaId,
                request.tipo(),
                request.fecha(),
                request.nombre(),
                request.dosis(),
                request.valorPeso(),
                request.motivo(),
                request.diagnostico(),
                request.observaciones()
        );

        EventoClinicoResponseDTO nuevoEvento = eventoClinicoService.registrar(requestNormalizado, registradoPorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoEvento);
    }

    @GetMapping("/mascotas/{mascotaId}/eventos-clinicos")
    public ResponseEntity<List<EventoClinicoResponseDTO>> listarPorMascota(@PathVariable UUID mascotaId,
                                                                           @RequestParam(required = false) String tipo,
                                                                           Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(eventoClinicoService.listarPorMascota(mascotaId, tipo, usuarioAutenticadoId));
    }

    @GetMapping("/eventos-clinicos/{id}")
    public ResponseEntity<EventoClinicoResponseDTO> obtenerPorId(@PathVariable UUID id,
                                                                Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(eventoClinicoService.obtenerPorId(id, usuarioAutenticadoId));
    }

    @PutMapping("/eventos-clinicos/{id}")
    public ResponseEntity<EventoClinicoResponseDTO> editar(@PathVariable UUID id,
                                                          @Valid @RequestBody EventoClinicoRequestDTO request,
                                                          Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(eventoClinicoService.editar(id, request, usuarioAutenticadoId));
    }

    @DeleteMapping("/eventos-clinicos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id,
                                         Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        eventoClinicoService.eliminar(id, usuarioAutenticadoId);
        return ResponseEntity.noContent().build();
    }
}