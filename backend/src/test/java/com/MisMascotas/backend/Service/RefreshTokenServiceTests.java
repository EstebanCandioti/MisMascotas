package com.MisMascotas.backend.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.Entity.RefreshToken;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.RefreshTokenInvalidoException;
import com.MisMascotas.backend.Repository.RefreshTokenRepository;
import com.MisMascotas.backend.Service.RefreshTokenService.RefreshTokenEmitido;

import jakarta.persistence.LockModeType;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    private static final String TOKEN_EN_CLARO = "refresh-token-ficticio-qa";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(refreshTokenService, "diasExpiracionRefreshToken", 14L);
    }

    @Test
    void renovarBloqueaElTokenAntesDeCrearUnUnicoReemplazo() {
        Usuario usuario = crearUsuario();
        RefreshToken tokenActual = crearTokenActivo(usuario);
        String hash = refreshTokenService.hashearToken(TOKEN_EN_CLARO);
        when(refreshTokenRepository.findByTokenHashConBloqueo(hash))
                .thenReturn(Optional.of(tokenActual));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        RefreshTokenEmitido resultado = refreshTokenService.renovar(TOKEN_EN_CLARO);

        assertSame(usuario, resultado.usuario());
        assertNotNull(resultado.token());
        assertNotNull(tokenActual.getRevocadoEn());
        assertSame(resultado.entidad(), tokenActual.getReemplazadoPor());

        InOrder orden = inOrder(refreshTokenRepository);
        orden.verify(refreshTokenRepository).findByTokenHashConBloqueo(hash);
        orden.verify(refreshTokenRepository).save(resultado.entidad());
        orden.verify(refreshTokenRepository).save(tokenActual);
        orden.verifyNoMoreInteractions();
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
        verify(refreshTokenRepository, never()).revocarActivosPorUsuario(any(), any());
    }

    @Test
    void reutilizarTokenReemplazadoRevocaLosActivosDeLaCuentaAntesDeRechazar() {
        Usuario usuario = crearUsuario();
        RefreshToken tokenReemplazado = crearTokenActivo(usuario);
        tokenReemplazado.setRevocadoEn(Instant.now().minus(1, ChronoUnit.MINUTES));
        tokenReemplazado.setReemplazadoPor(crearTokenActivo(usuario));
        String hash = refreshTokenService.hashearToken(TOKEN_EN_CLARO);
        when(refreshTokenRepository.findByTokenHashConBloqueo(hash))
                .thenReturn(Optional.of(tokenReemplazado));
        when(refreshTokenRepository.revocarActivosPorUsuario(any(), any())).thenReturn(1);

        assertThrows(
                RefreshTokenInvalidoException.class,
                () -> refreshTokenService.renovar(TOKEN_EN_CLARO));

        InOrder orden = inOrder(refreshTokenRepository);
        orden.verify(refreshTokenRepository).findByTokenHashConBloqueo(hash);
        orden.verify(refreshTokenRepository).revocarActivosPorUsuario(
                org.mockito.ArgumentMatchers.eq(usuario.getIdUsuario()),
                any(Instant.class));
        orden.verifyNoMoreInteractions();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void renovarConservaNoRollbackParaPersistirLaRevocacionPorReutilizacion() throws Exception {
        Method metodoRenovar = RefreshTokenService.class.getMethod("renovar", String.class);
        Transactional transaccion = metodoRenovar.getAnnotation(Transactional.class);

        assertNotNull(transaccion);
        assertTrue(Arrays.asList(transaccion.noRollbackFor())
                .contains(RefreshTokenInvalidoException.class));
        assertEquals(1, transaccion.noRollbackFor().length);
    }

    @Test
    void consultaDeRenovacionUsaBloqueoPesimistaDeEscritura() throws Exception {
        Method consulta = RefreshTokenRepository.class.getMethod(
                "findByTokenHashConBloqueo",
                String.class);
        Lock bloqueo = consulta.getAnnotation(Lock.class);

        assertNotNull(bloqueo);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, bloqueo.value());
    }

    private Usuario crearUsuario() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(UUID.randomUUID());
        usuario.setEmail("qa.auth.refresh@mismascotas.test");
        return usuario;
    }

    private RefreshToken crearTokenActivo(Usuario usuario) {
        RefreshToken token = new RefreshToken();
        token.setIdRefreshToken(UUID.randomUUID());
        token.setUsuario(usuario);
        token.setTokenHash(UUID.randomUUID().toString());
        token.setExpiraEn(Instant.now().plus(14, ChronoUnit.DAYS));
        return token;
    }
}
