package com.MisMascotas.backend.Security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.UsuarioRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final long DIAS_EXPIRACION = 30;
    private static final String CLAIM_ID_USUARIO = "idUsuario";
    private static final String CLAIM_PRE_AUTH = "pre_auth";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.pre-auth-expiration-minutes:10}")
    private long minutosExpiracionPreAuth;

    private final UsuarioRepository usuarioRepository;

    public JwtService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public String generarToken(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }

        String emailNormalizado = email.trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByEmail(emailNormalizado);
        if (usuario == null) {
            throw new IllegalArgumentException("No existe un usuario con el email indicado");
        }

        return generarToken(usuario);
    }

    public String generarToken(Usuario usuario) {
        validarUsuarioParaToken(usuario);

        Instant ahora = Instant.now();
        Instant expiracion = ahora.plus(DIAS_EXPIRACION, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim(CLAIM_ID_USUARIO, usuario.getIdUsuario().toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiracion))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generarTokenPreAuth(Usuario usuario) {
        validarUsuarioParaToken(usuario);

        Instant ahora = Instant.now();
        Instant expiracion = ahora.plus(minutosExpiracionPreAuth, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim(CLAIM_ID_USUARIO, usuario.getIdUsuario().toString())
                .claim(CLAIM_PRE_AUTH, true)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiracion))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String extraerEmail(String token) {
        try {
            return extraerClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public UUID extraerIdUsuario(String token) {
        try {
            return extraerIdUsuarioDesdeClaims(extraerClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public UUID validarTokenPreAuthYExtraerUsuarioId(String token) {
        try {
            Claims claims = extraerClaims(token);
            Date expiracion = claims.getExpiration();
            Boolean preAuth = claims.get(CLAIM_PRE_AUTH, Boolean.class);
            UUID usuarioId = extraerIdUsuarioDesdeClaims(claims);

            if (expiracion == null || !expiracion.after(new Date()) || !Boolean.TRUE.equals(preAuth) || usuarioId == null) {
                return null;
            }

            return usuarioId;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean esTokenPreAuth(String token) {
        try {
            return Boolean.TRUE.equals(extraerClaims(token).get(CLAIM_PRE_AUTH, Boolean.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extraerClaims(token);
            Date expiracion = claims.getExpiration();
            String email = claims.getSubject();
            UUID idUsuario = extraerIdUsuarioDesdeClaims(claims);
            Boolean preAuth = claims.get(CLAIM_PRE_AUTH, Boolean.class);

            return expiracion != null
                    && expiracion.after(new Date())
                    && email != null
                    && email.equals(userDetails.getUsername())
                    && idUsuario != null
                    && !Boolean.TRUE.equals(preAuth);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private UUID extraerIdUsuarioDesdeClaims(Claims claims) {
        String idUsuario = claims.get(CLAIM_ID_USUARIO, String.class);
        return idUsuario == null ? null : UUID.fromString(idUsuario);
    }

    private void validarUsuarioParaToken(Usuario usuario) {
        if (usuario == null || usuario.getEmail() == null || usuario.getIdUsuario() == null) {
            throw new IllegalArgumentException("El usuario no es valido para generar un token");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}