package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import com.MisMascotas.backend.Entity.Usuario;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTests {
    @Mock UsuarioAutenticadoService autenticado;
    @Mock PremiumService premium;
    UsuarioService servicio;
    final Principal principal = () -> "perfil@example.test";

    @BeforeEach void preparar() {
        servicio = new UsuarioService(autenticado, premium);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void perfilUsaLaDecisionCentralYConservaLosDatosDelUsuario(boolean indicador, boolean efectivo) {
        var usuario = usuario(indicador);
        when(autenticado.obtenerUsuario(principal)).thenReturn(usuario);
        when(premium.esPremium(usuario.getIdUsuario())).thenReturn(efectivo);

        var respuesta = servicio.obtenerPerfilAutenticado(principal);

        assertThat(respuesta.idUsuario()).isEqualTo(usuario.getIdUsuario());
        assertThat(respuesta.nombre()).isEqualTo(usuario.getNombre());
        assertThat(respuesta.email()).isEqualTo(usuario.getEmail());
        assertThat(respuesta.esPremium()).isEqualTo(efectivo);
        assertThat(usuario.isEsPremium()).isEqualTo(indicador);
        verify(premium).esPremium(usuario.getIdUsuario());
    }

    @Test void noConsultaPremiumSiNoPuedeResolverElUsuarioAutenticado() {
        when(autenticado.obtenerUsuario(null)).thenThrow(new BadCredentialsException("Sin sesion"));
        assertThatThrownBy(() -> servicio.obtenerPerfilAutenticado(null)).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(premium);
    }

    @Test void unErrorDePersistenciaNoSeDisfrazaComoPerfilFree() {
        var usuario = usuario(true);
        when(autenticado.obtenerUsuario(principal)).thenReturn(usuario);
        var error = new DataAccessResourceFailureException("Fallo simulado");
        when(premium.esPremium(usuario.getIdUsuario())).thenThrow(error);
        assertThatThrownBy(() -> servicio.obtenerPerfilAutenticado(principal)).isSameAs(error);
        assertThat(usuario.isEsPremium()).isTrue();
    }

    private Usuario usuario(boolean indicador) {
        var usuario = new Usuario();
        usuario.setIdUsuario(UUID.randomUUID());
        usuario.setNombre("Perfil QA");
        usuario.setEmail(principal.getName());
        usuario.setEsPremium(indicador);
        return usuario;
    }
}
