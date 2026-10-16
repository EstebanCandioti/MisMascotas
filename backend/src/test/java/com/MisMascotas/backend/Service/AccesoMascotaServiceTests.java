package com.MisMascotas.backend.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Exception.RecursoNoEncontradoException;
import com.MisMascotas.backend.Repository.MascotaRepository;

@ExtendWith(MockitoExtension.class)
class AccesoMascotaServiceTests {

    @Mock
    private MascotaRepository mascotaRepository;

    private AccesoMascotaService accesoMascotaService;

    @BeforeEach
    void setUp() {
        accesoMascotaService = new AccesoMascotaService(mascotaRepository);
    }

    @Test
    void verificarAccesoDevuelveMascotaActivaDelPropietario() {
        UUID mascotaId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Mascota mascota = crearMascota(usuarioId);
        when(mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(mascotaId))
                .thenReturn(Optional.of(mascota));

        Mascota resultado = accesoMascotaService.verificarAcceso(mascotaId, usuarioId);

        assertSame(mascota, resultado);
    }

    @Test
    void verificarAccesoDevuelveNoEncontradoParaMascotaInexistente() {
        UUID mascotaId = UUID.randomUUID();
        when(mascotaRepository.findByIdMascotaAndFechaEliminacionIsNull(mascotaId))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> accesoMascotaService.verificarAcceso(mascotaId, UUID.randomUUID()));
    }

    @Test
    void verificarAccesoDevuelveNoEncontradoParaMascotaEliminada() {
        Mascota mascota = crearMascota(UUID.randomUUID());
        mascota.setFechaEliminacion(Instant.now());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> accesoMascotaService.verificarAcceso(mascota, UUID.randomUUID()));
    }

    @Test
    void verificarAccesoDevuelveAccesoDenegadoParaOtroPropietario() {
        Mascota mascota = crearMascota(UUID.randomUUID());

        assertThrows(
                AccesoDenegadoException.class,
                () -> accesoMascotaService.verificarAcceso(mascota, UUID.randomUUID()));
    }

    @Test
    void obtenerMascotasAccesiblesDelegaElListadoActivoDelPropietario() {
        UUID usuarioId = UUID.randomUUID();
        List<Mascota> mascotas = List.of(crearMascota(usuarioId));
        when(mascotaRepository.findByPropietario_IdUsuarioAndFechaEliminacionIsNull(usuarioId))
                .thenReturn(mascotas);

        List<Mascota> resultado = accesoMascotaService.obtenerMascotasAccesibles(usuarioId);

        assertEquals(mascotas, resultado);
        verify(mascotaRepository).findByPropietario_IdUsuarioAndFechaEliminacionIsNull(usuarioId);
    }

    private Mascota crearMascota(UUID propietarioId) {
        Usuario propietario = new Usuario();
        propietario.setIdUsuario(propietarioId);

        Mascota mascota = new Mascota();
        mascota.setPropietario(propietario);
        return mascota;
    }
}
