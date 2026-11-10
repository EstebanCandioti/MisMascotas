package com.MisMascotas.backend.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MisMascotas.backend.DTO.RecordatorioResponseDTO;
import com.MisMascotas.backend.Entity.Estado;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Recordatorio;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.RecordatorioRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class RecordatorioServiceTests {

    @Mock
    private RecordatorioRepository recordatorioRepository;
    @Mock
    private EstadoRepository estadoRepository;
    @Mock
    private AccesoMascotaService accesoMascotaService;
    @Mock
    private EntityManager entityManager;

    private RecordatorioService recordatorioService;

    @BeforeEach
    void setUp() {
        recordatorioService = new RecordatorioService(
                recordatorioRepository,
                estadoRepository,
                accesoMascotaService,
                entityManager);
    }

    @Test
    void confirmarUnicoActivoLoCompletaYGuardaAutoria() {
        UUID usuarioId = UUID.randomUUID();
        Recordatorio recordatorio = prepararRecordatorio("unica", "ACTIVO");
        Estado completado = crearEstado("COMPLETADO");
        Usuario usuario = crearUsuario(usuarioId);
        prepararBusqueda(recordatorio);
        when(entityManager.getReference(Usuario.class, usuarioId)).thenReturn(usuario);
        when(estadoRepository.findByEntidadAndNombre("RECORDATORIO", "COMPLETADO"))
                .thenReturn(Optional.of(completado));

        RecordatorioResponseDTO respuesta = recordatorioService.confirmar(
                recordatorio.getIdRecordatorio(), usuarioId);

        assertEquals("COMPLETADO", respuesta.estado());
        assertEquals(usuarioId, respuesta.confirmadoPorId());
        assertNotNull(respuesta.confirmadoEn());
        verify(recordatorioRepository).save(recordatorio);
    }

    @Test
    void desmarcarUnicoCompletadoLoReactivaYLimpiaAutoria() {
        UUID usuarioId = UUID.randomUUID();
        Recordatorio recordatorio = prepararRecordatorio("unica", "COMPLETADO");
        recordatorio.setConfirmadoPor(crearUsuario(usuarioId));
        recordatorio.setConfirmadoEn(java.time.Instant.now());
        Estado activo = crearEstado("ACTIVO");
        prepararBusqueda(recordatorio);
        when(estadoRepository.findByEntidadAndNombre("RECORDATORIO", "ACTIVO"))
                .thenReturn(Optional.of(activo));

        RecordatorioResponseDTO respuesta = recordatorioService.desmarcarConfirmacion(
                recordatorio.getIdRecordatorio(), usuarioId);

        assertEquals("ACTIVO", respuesta.estado());
        assertNull(respuesta.confirmadoPorId());
        assertNull(respuesta.confirmadoEn());
        verify(recordatorioRepository).save(recordatorio);
    }

    @Test
    void confirmarUnicoInactivoNoLoReactivaNiLoCompleta() {
        UUID usuarioId = UUID.randomUUID();
        Recordatorio recordatorio = prepararRecordatorio("unica", "INACTIVO");
        prepararBusqueda(recordatorio);
        when(entityManager.getReference(Usuario.class, usuarioId)).thenReturn(crearUsuario(usuarioId));

        RecordatorioResponseDTO respuesta = recordatorioService.confirmar(
                recordatorio.getIdRecordatorio(), usuarioId);

        assertEquals("INACTIVO", respuesta.estado());
        verify(estadoRepository, never()).findByEntidadAndNombre("RECORDATORIO", "COMPLETADO");
        verify(estadoRepository, never()).findByEntidadAndNombre("RECORDATORIO", "ACTIVO");
    }

    @Test
    void desmarcarUnicoInactivoConservaSuEstado() {
        UUID usuarioId = UUID.randomUUID();
        Recordatorio recordatorio = prepararRecordatorio("unica", "INACTIVO");
        recordatorio.setConfirmadoPor(crearUsuario(usuarioId));
        recordatorio.setConfirmadoEn(java.time.Instant.now());
        prepararBusqueda(recordatorio);

        RecordatorioResponseDTO respuesta = recordatorioService.desmarcarConfirmacion(
                recordatorio.getIdRecordatorio(), usuarioId);

        assertEquals("INACTIVO", respuesta.estado());
        assertNull(respuesta.confirmadoPorId());
        assertNull(respuesta.confirmadoEn());
        verify(estadoRepository, never()).findByEntidadAndNombre("RECORDATORIO", "ACTIVO");
    }

    @Test
    void confirmarRecurrenteActivoNoCompletaLaSerie() {
        UUID usuarioId = UUID.randomUUID();
        Recordatorio recordatorio = prepararRecordatorio("recurrente", "ACTIVO");
        prepararBusqueda(recordatorio);
        when(entityManager.getReference(Usuario.class, usuarioId)).thenReturn(crearUsuario(usuarioId));

        RecordatorioResponseDTO respuesta = recordatorioService.confirmar(
                recordatorio.getIdRecordatorio(), usuarioId);

        assertEquals("ACTIVO", respuesta.estado());
        assertEquals(usuarioId, respuesta.confirmadoPorId());
        verify(estadoRepository, never()).findByEntidadAndNombre("RECORDATORIO", "COMPLETADO");
    }

    private void prepararBusqueda(Recordatorio recordatorio) {
        when(recordatorioRepository.findByIdRecordatorioAndFechaEliminacionIsNull(
                recordatorio.getIdRecordatorio())).thenReturn(Optional.of(recordatorio));
        when(recordatorioRepository.save(recordatorio)).thenReturn(recordatorio);
    }

    private Recordatorio prepararRecordatorio(String modalidad, String estadoNombre) {
        Recordatorio recordatorio = new Recordatorio();
        recordatorio.setIdRecordatorio(UUID.randomUUID());
        recordatorio.setMascota(crearMascota());
        recordatorio.setCreadoPor(crearUsuario(UUID.randomUUID()));
        recordatorio.setModalidad(modalidad);
        recordatorio.setEstado(crearEstado(estadoNombre));
        return recordatorio;
    }

    private Mascota crearMascota() {
        Mascota mascota = new Mascota();
        mascota.setIdMascota(UUID.randomUUID());
        return mascota;
    }

    private Usuario crearUsuario(UUID id) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        return usuario;
    }

    private Estado crearEstado(String nombre) {
        Estado estado = new Estado();
        estado.setEntidad("RECORDATORIO");
        estado.setNombre(nombre);
        return estado;
    }
}
