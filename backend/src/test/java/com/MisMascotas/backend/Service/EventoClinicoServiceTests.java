package com.MisMascotas.backend.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.DTO.EventoClinicoRequestDTO;
import com.MisMascotas.backend.DTO.EventoClinicoResponseDTO;
import com.MisMascotas.backend.Entity.EventoClinico;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.ValidacionRequestException;
import com.MisMascotas.backend.Repository.EventoClinicoRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class EventoClinicoServiceTests {

    private static final Instant FECHA_VALIDA = Instant.parse("2026-01-15T15:00:00Z");

    @Mock
    private EventoClinicoRepository eventoClinicoRepository;
    @Mock
    private MascotaRepository mascotaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AccesoMascotaService accesoMascotaService;

    private EventoClinicoService eventoClinicoService;
    private UUID mascotaId;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        eventoClinicoService = new EventoClinicoService(
                eventoClinicoRepository,
                mascotaRepository,
                usuarioRepository,
                accesoMascotaService);
        mascotaId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
    }

    @Test
    void aceptaNombreEnElLimiteDe255Caracteres() {
        EventoClinicoRequestDTO request = requestVacuna("N".repeat(255), null);

        EventoClinicoResponseDTO respuesta = registrarRequestValido(request);

        assertEquals(request.nombre(), respuesta.nombre());
    }

    @Test
    void rechazaNombreDe256CaracteresAntesDePersistir() {
        EventoClinicoRequestDTO request = requestVacuna("N".repeat(256), null);

        verificarExceso(request, "nombre: No puede superar los 255 caracteres");
    }

    @Test
    void aceptaDosisEnElLimiteDe100Caracteres() {
        EventoClinicoRequestDTO request = requestMedicacion("D".repeat(100));

        EventoClinicoResponseDTO respuesta = registrarRequestValido(request);

        assertEquals(request.dosis(), respuesta.dosis());
    }

    @Test
    void rechazaDosisDe101CaracteresAntesDePersistir() {
        EventoClinicoRequestDTO request = requestMedicacion("D".repeat(101));

        verificarExceso(request, "dosis: No puede superar los 100 caracteres");
    }

    @Test
    void aceptaObservacionesEnElLimiteDe255Caracteres() {
        EventoClinicoRequestDTO request = requestVacuna("Vacuna ficticia QA", "O".repeat(255));

        EventoClinicoResponseDTO respuesta = registrarRequestValido(request);

        assertEquals(request.observaciones(), respuesta.observaciones());
    }

    @Test
    void rechazaObservacionesDe256CaracteresAntesDePersistir() {
        EventoClinicoRequestDTO request = requestVacuna("Vacuna ficticia QA", "O".repeat(256));

        verificarExceso(request, "observaciones: No puede superar los 255 caracteres");
    }

    @Test
    void aceptaUnPesoNumericoValido() {
        EventoClinicoRequestDTO request = new EventoClinicoRequestDTO(
                mascotaId,
                "PESO",
                FECHA_VALIDA,
                null,
                null,
                new BigDecimal("12.50"),
                null,
                null,
                null);

        EventoClinicoResponseDTO respuesta = registrarRequestValido(request);

        assertEquals(new BigDecimal("12.50"), respuesta.valorPeso());
    }

    private EventoClinicoResponseDTO registrarRequestValido(EventoClinicoRequestDTO request) {
        Mascota mascota = new Mascota();
        mascota.setIdMascota(mascotaId);
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(usuarioId);
        usuario.setNombre("Usuario QA");

        when(accesoMascotaService.verificarAcceso(mascotaId, usuarioId)).thenReturn(mascota);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(eventoClinicoRepository.save(any(EventoClinico.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(eventoClinicoRepository
                .findFirstByMascota_IdMascotaAndTipoAndFechaEliminacionIsNullOrderByFechaDesc(
                        mascotaId,
                        "PESO"))
                .thenReturn(Optional.empty());

        EventoClinicoResponseDTO respuesta = eventoClinicoService.registrar(request, usuarioId);

        verify(eventoClinicoRepository).save(any(EventoClinico.class));
        return respuesta;
    }

    private void verificarExceso(EventoClinicoRequestDTO request, String mensajeEsperado) {
        ValidacionRequestException excepcion = assertThrows(
                ValidacionRequestException.class,
                () -> eventoClinicoService.registrar(request, usuarioId));

        assertEquals(mensajeEsperado, excepcion.getMessage());
        verifyNoInteractions(
                eventoClinicoRepository,
                mascotaRepository,
                usuarioRepository,
                accesoMascotaService);
    }

    private EventoClinicoRequestDTO requestVacuna(String nombre, String observaciones) {
        return new EventoClinicoRequestDTO(
                mascotaId,
                "VACUNA",
                FECHA_VALIDA,
                nombre,
                null,
                null,
                null,
                null,
                observaciones);
    }

    private EventoClinicoRequestDTO requestMedicacion(String dosis) {
        return new EventoClinicoRequestDTO(
                mascotaId,
                "MEDICACION",
                FECHA_VALIDA,
                "Medicacion ficticia QA",
                dosis,
                null,
                null,
                null,
                null);
    }
}
