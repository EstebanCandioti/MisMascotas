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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.DTO.MascotaResponseDTO;
import com.MisMascotas.backend.Service.MascotaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/mascotas")
@RequiredArgsConstructor
public class MascotaController {

    private final MascotaService mascotaService;

    @PostMapping
    public ResponseEntity<MascotaResponseDTO> crear(@Valid @RequestBody MascotaRequestDTO request,
                                                   Principal principal) {
        UUID propietarioId = UUID.fromString(principal.getName());
        MascotaResponseDTO nuevaMascota = mascotaService.crear(request, propietarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevaMascota);
    }

    @GetMapping
    public ResponseEntity<List<MascotaResponseDTO>> listarPorUsuario(Principal principal) {
        UUID usuarioId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(mascotaService.listarPorUsuario(usuarioId));
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
