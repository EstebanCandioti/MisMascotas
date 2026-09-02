package com.MisMascotas.backend.Controller;

import com.MisMascotas.backend.DTO.RecordatorioRequestDTO;
import com.MisMascotas.backend.DTO.RecordatorioResponseDTO;
import com.MisMascotas.backend.Service.RecordatorioService;
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

    @PostMapping("/mascotas/{mascotaId}/recordatorios")
    public ResponseEntity<RecordatorioResponseDTO> crear(@PathVariable UUID mascotaId,
                                                         @Valid @RequestBody RecordatorioRequestDTO request,
                                                         Principal principal) {
        UUID creadoPorId = UUID.fromString(principal.getName());
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
    public ResponseEntity<List<RecordatorioResponseDTO>> listarPorMascota(@PathVariable UUID mascotaId) {
        return ResponseEntity.ok(recordatorioService.listarPorMascota(mascotaId));
    }

    @GetMapping("/recordatorios/{id}")
    public ResponseEntity<RecordatorioResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(recordatorioService.obtenerPorId(id));
    }

    @PutMapping("/recordatorios/{id}")
    public ResponseEntity<RecordatorioResponseDTO> editar(@PathVariable UUID id,
                                                          @Valid @RequestBody RecordatorioRequestDTO request) {
        return ResponseEntity.ok(recordatorioService.editar(id, request));
    }

    @PatchMapping("/recordatorios/{id}/estado")
    public ResponseEntity<RecordatorioResponseDTO> cambiarEstado(@PathVariable UUID id,
                                                                @RequestParam String nuevoEstado) {
        return ResponseEntity.ok(recordatorioService.cambiarEstado(id, nuevoEstado));
    }

    @DeleteMapping("/recordatorios/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        recordatorioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}