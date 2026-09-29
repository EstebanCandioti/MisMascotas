package com.MisMascotas.backend.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.RefreshToken;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RefreshTokenInvalidoException;
import com.MisMascotas.backend.Repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

    private static final int BYTES_TOKEN = 64;
    private static final String MENSAJE_TOKEN_INVALIDO = "Refresh token invalido";

    @Value("${jwt.refresh-token-expiration-days}")
    private long diasExpiracionRefreshToken;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public RefreshTokenEmitido emitirParaUsuario(Usuario usuario) {
        return crearRefreshToken(usuario);
    }

    @Transactional(noRollbackFor = RefreshTokenInvalidoException.class)
    public RefreshTokenEmitido renovar(String tokenEnClaro) {
        RefreshToken tokenActual = buscarPorValorEnClaro(tokenEnClaro);
        Usuario usuario = tokenActual.getUsuario();

        if (tokenActual.getRevocadoEn() != null) {
            if (tokenActual.getReemplazadoPor() != null) {
                refreshTokenRepository.revocarActivosPorUsuario(usuario.getIdUsuario(), Instant.now());
            }
            throw new RefreshTokenInvalidoException(MENSAJE_TOKEN_INVALIDO);
        }

        if (!tokenActual.getExpiraEn().isAfter(Instant.now())) {
            throw new RefreshTokenInvalidoException(MENSAJE_TOKEN_INVALIDO);
        }

        RefreshTokenEmitido nuevoToken = crearRefreshToken(usuario);
        tokenActual.setRevocadoEn(Instant.now());
        tokenActual.setReemplazadoPor(nuevoToken.entidad());
        refreshTokenRepository.save(tokenActual);

        return nuevoToken;
    }

    @Transactional
    public Usuario revocarTokenActual(String tokenEnClaro) {
        RefreshToken refreshToken = buscarPorValorEnClaro(tokenEnClaro);
        Usuario usuario = refreshToken.getUsuario();

        if (refreshToken.getRevocadoEn() == null) {
            refreshToken.setRevocadoEn(Instant.now());
            refreshTokenRepository.save(refreshToken);
        }

        return usuario;
    }

    public String hashearToken(String tokenEnClaro) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            throw new RefreshTokenInvalidoException(MENSAJE_TOKEN_INVALIDO);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tokenEnClaro.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo hashear el refresh token", ex);
        }
    }

    private RefreshToken buscarPorValorEnClaro(String tokenEnClaro) {
        String tokenHash = hashearToken(tokenEnClaro);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RefreshTokenInvalidoException(MENSAJE_TOKEN_INVALIDO));
    }

    private RefreshTokenEmitido crearRefreshToken(Usuario usuario) {
        String tokenEnClaro = generarTokenSeguro();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuario);
        refreshToken.setTokenHash(hashearToken(tokenEnClaro));
        refreshToken.setExpiraEn(Instant.now().plus(diasExpiracionRefreshToken, ChronoUnit.DAYS));

        RefreshToken guardado = refreshTokenRepository.save(refreshToken);
        return new RefreshTokenEmitido(tokenEnClaro, guardado, usuario);
    }

    private String generarTokenSeguro() {
        byte[] bytes = new byte[BYTES_TOKEN];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record RefreshTokenEmitido(String token, RefreshToken entidad, Usuario usuario) {
    }
}
