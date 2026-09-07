package com.MisMascotas.backend.Controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.BadgeRequestDTO;
import com.MisMascotas.backend.DTO.BadgeResponseDTO;
import com.MisMascotas.backend.Service.BadgeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    @PostMapping("/mascotas/{mascotaId}/badges")
    public ResponseEntity<BadgeResponseDTO> crear(@PathVariable UUID mascotaId,
                                                  @Valid @RequestBody BadgeRequestDTO request) {
        BadgeRequestDTO requestNormalizado = new BadgeRequestDTO(
                mascotaId,
                request.texto(),
                request.emoji()
        );
        BadgeResponseDTO nuevaBadge = badgeService.crear(requestNormalizado);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevaBadge);
    }

    @GetMapping("/mascotas/{mascotaId}/badges")
    public ResponseEntity<List<BadgeResponseDTO>> listarPorMascota(@PathVariable UUID mascotaId) {
        return ResponseEntity.ok(badgeService.listarPorMascota(mascotaId));
    }

    @GetMapping("/badges/{id}")
    public ResponseEntity<BadgeResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(badgeService.obtenerPorId(id));
    }

    @DeleteMapping("/badges/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        badgeService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
