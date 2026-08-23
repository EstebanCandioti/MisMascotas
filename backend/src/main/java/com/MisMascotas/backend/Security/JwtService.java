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

    @Value("${jwt.secret}")
    private String secret;

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
        if (usuario == null || usuario.getEmail() == null || usuario.getIdUsuario() == null) {
            throw new IllegalArgumentException("El usuario no es valido para generar un token");
        }

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

    public String extraerEmail(String token) {
        try {
            return extraerClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public UUID extraerIdUsuario(String token) {
        try {
            String idUsuario = extraerClaims(token).get(CLAIM_ID_USUARIO, String.class);
            return idUsuario == null ? null : UUID.fromString(idUsuario);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extraerClaims(token);
            Date expiracion = claims.getExpiration();
            String email = claims.getSubject();
            String idUsuario = claims.get(CLAIM_ID_USUARIO, String.class);

            return expiracion != null
                    && expiracion.after(new Date())
                    && email != null
                    && email.equals(userDetails.getUsername())
                    && idUsuario != null
                    && !idUsuario.isBlank();
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

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}