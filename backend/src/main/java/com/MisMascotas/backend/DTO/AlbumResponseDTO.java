package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.UUID;

public record AlbumResponseDTO(
    UUID idAlbum,
    String nombre,
    String descripcion,
    UUID mascotaId,
    UUID creadoPorId,
    Instant creadoEn,
    int cantidadFotos
) {}
