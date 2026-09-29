package com.MisMascotas.backend.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.MisMascotas.backend.Entity.RefreshToken;

public interface RefreshTokenRepository extends Repository<RefreshToken, UUID> {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken rt
            set rt.revocadoEn = :revocadoEn
            where rt.usuario.idUsuario = :usuarioId
              and rt.revocadoEn is null
            """)
    int revocarActivosPorUsuario(
            @Param("usuarioId") UUID usuarioId,
            @Param("revocadoEn") Instant revocadoEn);
}
