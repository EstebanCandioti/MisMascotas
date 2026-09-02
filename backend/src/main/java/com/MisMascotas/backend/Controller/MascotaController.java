package com.MisMascotas.backend.Controller;

import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.DTO.MascotaResponseDTO;
import com.MisMascotas.backend.Service.MascotaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mascotas")
@RequiredArgsConstructor
public class MascotaController {

    private final MascotaService mascotaService;

    @PostMapping
    public ResponseEntity<MascotaResponseDTO> crear(@Valid @RequestBody MascotaRequestDTO request,
                                                  Principal principal) {
        UUID propietarioId = UUID.fromString(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(mascotaService.crear(request, propietarioId));
    }

    @GetMapping
    public ResponseEntity<List<MascotaResponseDTO>> listarPorPropietario(Principal principal) {
        UUID propietarioId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(mascotaService.listarPorPropietario(propietarioId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MascotaResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(mascotaService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MascotaResponseDTO> editar(@PathVariable UUID id,
                                                   @Valid @RequestBody MascotaRequestDTO request) {
        return ResponseEntity.ok(mascotaService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        mascotaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
