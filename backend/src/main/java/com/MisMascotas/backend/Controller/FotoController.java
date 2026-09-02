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

import com.MisMascotas.backend.DTO.FotoRequestDTO;
import com.MisMascotas.backend.DTO.FotoResponseDTO;
import com.MisMascotas.backend.Service.FotoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FotoController {

    private final FotoService fotoService;

    @PostMapping("/albumes/{albumId}/fotos")
    public ResponseEntity<FotoResponseDTO> crear(@PathVariable UUID albumId,
                                                @Valid @RequestBody FotoRequestDTO request,
                                                Principal principal) {
        UUID subidaPorId = principal != null ? UUID.fromString(principal.getName()) : null;
        FotoResponseDTO nuevaFoto = fotoService.crear(albumId, request, subidaPorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevaFoto);
    }

    @GetMapping("/albumes/{albumId}/fotos")
    public ResponseEntity<List<FotoResponseDTO>> listarPorAlbum(@PathVariable UUID albumId) {
        return ResponseEntity.ok(fotoService.listarPorAlbum(albumId));
    }

    @GetMapping("/fotos/{id}")
    public ResponseEntity<FotoResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(fotoService.obtenerPorId(id));
    }

    @DeleteMapping("/fotos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        fotoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}