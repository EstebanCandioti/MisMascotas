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
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.AlbumRequestDTO;
import com.MisMascotas.backend.DTO.AlbumResponseDTO;
import com.MisMascotas.backend.Service.AlbumService;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    @PostMapping("/mascotas/{mascotaId}/albumes")
    public ResponseEntity<AlbumResponseDTO> crear(@PathVariable UUID mascotaId,
                                                 @Valid @RequestBody AlbumRequestDTO request,
                                                 Principal principal) {
        UUID creadoPorId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        AlbumRequestDTO requestNormalizado = new AlbumRequestDTO(
                request.nombre(),
                request.descripcion(),
                mascotaId
        );
        AlbumResponseDTO nuevoAlbum = albumService.crear(requestNormalizado, creadoPorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoAlbum);
    }

    @GetMapping("/mascotas/{mascotaId}/albumes")
    public ResponseEntity<List<AlbumResponseDTO>> listarPorMascota(@PathVariable UUID mascotaId,
                                                                   Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(albumService.listarPorMascota(mascotaId, usuarioAutenticadoId));
    }

    @GetMapping("/albumes/{id}")
    public ResponseEntity<AlbumResponseDTO> obtenerPorId(@PathVariable UUID id,
                                                         Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(albumService.obtenerPorId(id, usuarioAutenticadoId));
    }

    @PutMapping("/albumes/{id}")
    public ResponseEntity<AlbumResponseDTO> editar(@PathVariable UUID id,
                                                  @Valid @RequestBody AlbumRequestDTO request,
                                                  Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        return ResponseEntity.ok(albumService.editar(id, request, usuarioAutenticadoId));
    }

    @DeleteMapping("/albumes/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id,
                                         Principal principal) {
        UUID usuarioAutenticadoId = usuarioAutenticadoService.obtenerIdUsuario(principal);
        albumService.eliminar(id, usuarioAutenticadoId);
        return ResponseEntity.noContent().build();
    }
}