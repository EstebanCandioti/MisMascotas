package com.MisMascotas.backend.Security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final long DIAS_EXPIRACION = 30;

    @Value("${jwt.secret}")
    private String secret;

    public String generarToken(String email) {
        Instant ahora = Instant.now();
        Instant expiracion = ahora.plus(DIAS_EXPIRACION, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(email)
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

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extraerClaims(token);
            Date expiracion = claims.getExpiration();
            String email = claims.getSubject();

            return expiracion != null
                    && expiracion.after(new Date())
                    && email != null
                    && email.equals(userDetails.getUsername());
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