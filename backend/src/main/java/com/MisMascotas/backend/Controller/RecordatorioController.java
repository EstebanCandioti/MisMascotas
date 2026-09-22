package com.MisMascotas.backend.Controller;

import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.DTO.RecordatorioResponseDTO;
import com.MisMascotas.backend.Service.RecordatorioService;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RecordatorioController {

    private final RecordatorioService recordatorioService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    @PostMapping("/mascotas/{mascotaId}/recordatorios")
    public ResponseEntity<RecordatorioResponseDTO> crear(@PathVariable UUID mascotaId,
                                                         @Valid @RequestBody RecordatorioRequestDTO request,
                                                         Principal principal) {
        UUID creadoPorId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        RecordatorioRequestDTO requestNormalizado = new RecordatorioRequestDTO(
                mascotaId,
                request.titulo(),
                request.tipo(),
                request.fechaHoraInicio(),
                request.modalidad(),
                request.intervaloValor(),
                request.intervaloUnidad(),
                request.diasSemana(),
                request.fechaFin()
        );
        RecordatorioResponseDTO nuevoRecordatorio = recordatorioService.crear(requestNormalizado, creadoPorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoRecordatorio);
    }

    @GetMapping("/mascotas/{mascotaId}/recordatorios")
    public ResponseEntity<List<RecordatorioResponseDTO>> listarPorMascota(@PathVariable UUID mascotaId,
                                                                          Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(recordatorioService.listarPorMascota(mascotaId, usuarioAutenticadoId));
    }

    @GetMapping("/recordatorios/{id}")
    public ResponseEntity<RecordatorioResponseDTO> obtenerPorId(@PathVariable UUID id,
                                                                Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(recordatorioService.obtenerPorId(id, usuarioAutenticadoId));
    }

    @PutMapping("/recordatorios/{id}")
    public ResponseEntity<RecordatorioResponseDTO> editar(@PathVariable UUID id,
                                                          @Valid @RequestBody RecordatorioRequestDTO request,
                                                          Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(recordatorioService.editar(id, request, usuarioAutenticadoId));
    }

    @PatchMapping("/recordatorios/{id}/estado")
    public ResponseEntity<RecordatorioResponseDTO> cambiarEstado(@PathVariable UUID id,
                                                                 @RequestParam String nuevoEstado,
                                                                 Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(recordatorioService.cambiarEstado(id, nuevoEstado, usuarioAutenticadoId));
    }

    @PatchMapping("/recordatorios/{id}/confirmacion")
    public ResponseEntity<RecordatorioResponseDTO> confirmar(@PathVariable UUID id,
                                                             Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(recordatorioService.confirmar(id, usuarioAutenticadoId));
    }

    @DeleteMapping("/recordatorios/{id}/confirmacion")
    public ResponseEntity<RecordatorioResponseDTO> desmarcarConfirmacion(@PathVariable UUID id,
                                                                         Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(recordatorioService.desmarcarConfirmacion(id, usuarioAutenticadoId));
    }

    @DeleteMapping("/recordatorios/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id,
                                         Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        recordatorioService.eliminar(id, usuarioAutenticadoId);
        return ResponseEntity.noContent().build();
    }
}