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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.BadgeRequestDTO;
import com.MisMascotas.backend.DTO.BadgeResponseDTO;
import com.MisMascotas.backend.Service.BadgeService;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    @PostMapping("/mascotas/{mascotaId}/badges")
    public ResponseEntity<BadgeResponseDTO> crear(@PathVariable UUID mascotaId,
                                                  @Valid @RequestBody BadgeRequestDTO request,
                                                  Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        BadgeRequestDTO requestNormalizado = new BadgeRequestDTO(
                mascotaId,
                request.texto(),
                request.emoji()
        );
        BadgeResponseDTO nuevaBadge = badgeService.crear(requestNormalizado, usuarioAutenticadoId);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevaBadge);
    }

    @GetMapping("/mascotas/{mascotaId}/badges")
    public ResponseEntity<List<BadgeResponseDTO>> listarPorMascota(@PathVariable UUID mascotaId,
                                                                   Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(badgeService.listarPorMascota(mascotaId, usuarioAutenticadoId));
    }

    @GetMapping("/badges/{id}")
    public ResponseEntity<BadgeResponseDTO> obtenerPorId(@PathVariable UUID id,
                                                         Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(badgeService.obtenerPorId(id, usuarioAutenticadoId));
    }

    @DeleteMapping("/badges/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id,
                                         Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        badgeService.eliminar(id, usuarioAutenticadoId);
        return ResponseEntity.noContent().build();
    }
}