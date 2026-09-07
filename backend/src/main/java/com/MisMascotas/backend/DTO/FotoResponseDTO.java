package com.MisMascotas.backend.DTO;

import java.time.Instant;
import java.util.UUID;

public record FotoResponseDTO(
    UUID idFoto,
    UUID albumId,
    String urlArchivo,
    String formato,
    UUID subidaPorId,
    Instant creadoEn
) {}
