package com.MisMascotas.backend.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.DTO.MascotaResponseDTO;
import com.MisMascotas.backend.Entity.EventoClinico;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.TipoAccionAuditoria;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Repository.EventoClinicoRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class MascotaServiceTests {

    @Mock
    private MascotaRepository mascotaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AccesoMascotaService accesoMascotaService;
    @Mock
    private EventoClinicoRepository eventoClinicoRepository;
    @Mock
    private EventoAuditoriaPendienteService eventoAuditoriaPendienteService;

    private MascotaService mascotaService;
    private UUID mascotaId;
    private UUID usuarioId;
    private Mascota mascota;

    @BeforeEach
    void setUp() {
        mascotaService = new MascotaService(
                mascotaRepository,
                usuarioRepository,
                accesoMascotaService,
                eventoClinicoRepository,
                eventoAuditoriaPendienteService);
        mascotaId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();

        Usuario propietario = new Usuario();
        propietario.setIdUsuario(usuarioId);
        mascota = new Mascota();
        mascota.setIdMascota(mascotaId);
        mascota.setPropietario(propietario);
        mascota.setNombre("Mascota anterior");
        mascota.setEspecie("Perro");
    }

    @Test
    void editarConMedicionClinicaActivaConservaElPesoDerivadoYNoModificaElHistorial() {
        EventoClinico medicion = new EventoClinico();
        medicion.setIdEvento(UUID.randomUUID());
        medicion.setMascota(mascota);
        medicion.setTipo("PESO");
        medicion.setValorPeso(new BigDecimal("10.00"));
        when(accesoMascotaService.verificarAcceso(mascotaId, usuarioId)).thenReturn(mascota);
        when(eventoClinicoRepository
                .findFirstByMascota_IdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(
                        mascotaId,
                        "PESO"))
                .thenReturn(Optional.of(medicion));
        when(mascotaRepository.save(mascota)).thenReturn(mascota);

        MascotaResponseDTO respuesta = mascotaService.editar(
                mascotaId,
                requestEdicion(new BigDecimal("99.00")),
                usuarioId);

        assertEquals(new BigDecimal("10.00"), respuesta.pesoActual());
        assertEquals("Mascota editada", respuesta.nombre());
        assertEquals(new BigDecimal("10.00"), medicion.getValorPeso());
        verify(mascotaRepository).save(mascota);
        verify(eventoClinicoRepository)
                .findFirstByMascota_IdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(
                        mascotaId,
                        "PESO");
        verifyNoMoreInteractions(eventoClinicoRepository);
        verify(eventoAuditoriaPendienteService).encolar(
                usuarioId,
                "mascota",
                TipoAccionAuditoria.UPDATE,
                mascotaId,
                new MascotaResponseDTO(
                        mascotaId,
                        usuarioId,
                        "Mascota anterior",
                        "Perro",
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                respuesta);
    }

    @Test
    void editarSinMedicionesClinicasConservaElPesoEnviadoEnLaFicha() {
        when(accesoMascotaService.verificarAcceso(mascotaId, usuarioId)).thenReturn(mascota);
        when(eventoClinicoRepository
                .findFirstByMascota_IdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(
                        mascotaId,
                        "PESO"))
                .thenReturn(Optional.empty());
        when(mascotaRepository.save(mascota)).thenReturn(mascota);

        MascotaResponseDTO respuesta = mascotaService.editar(
                mascotaId,
                requestEdicion(new BigDecimal("99.00")),
                usuarioId);

        assertEquals(new BigDecimal("99.00"), respuesta.pesoActual());
    }

    @Test
    void editarRechazadaPorPermisosNoEncolaAuditoria() {
        when(accesoMascotaService.verificarAcceso(mascotaId, usuarioId))
                .thenThrow(new AccesoDenegadoException("Sin permisos"));

        assertThrows(AccesoDenegadoException.class, () -> mascotaService.editar(
                mascotaId,
                requestEdicion(new BigDecimal("12.00")),
                usuarioId));

        verifyNoInteractions(eventoAuditoriaPendienteService, eventoClinicoRepository, mascotaRepository);
    }

    @Test
    void unFalloAlEncolarSePropagaParaRevertirLaEdicion() {
        when(accesoMascotaService.verificarAcceso(mascotaId, usuarioId)).thenReturn(mascota);
        when(eventoClinicoRepository
                .findFirstByMascota_IdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(
                        mascotaId,
                        "PESO"))
                .thenReturn(Optional.empty());
        when(mascotaRepository.save(mascota)).thenReturn(mascota);
        doThrow(new IllegalStateException("No se pudo persistir la auditoria"))
                .when(eventoAuditoriaPendienteService)
                .encolar(
                        org.mockito.ArgumentMatchers.eq(usuarioId),
                        org.mockito.ArgumentMatchers.eq("mascota"),
                        org.mockito.ArgumentMatchers.eq(TipoAccionAuditoria.UPDATE),
                        org.mockito.ArgumentMatchers.eq(mascotaId),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        assertThrows(IllegalStateException.class, () -> mascotaService.editar(
                mascotaId,
                requestEdicion(new BigDecimal("12.00")),
                usuarioId));
    }

    private MascotaRequestDTO requestEdicion(BigDecimal pesoActual) {
        return new MascotaRequestDTO(
                "Mascota editada",
                "Perro",
                "Mestiza",
                LocalDate.of(2020, 2, 29),
                false,
                null,
                null,
                null,
                pesoActual,
                "Notas editadas");
    }
}
