package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.Entity.EventoAuditoriaPendiente;
import com.MisMascotas.backend.Entity.Mascota;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Exception.AccesoDenegadoException;
import com.MisMascotas.backend.Repository.EventoAuditoriaPendienteRepository;
import com.MisMascotas.backend.Repository.MascotaRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;

@SpringBootTest(properties = {
        "jwt.secret=secreto-ficticio-de-pruebas-con-mas-de-32-caracteres",
        "suscripciones.planes.premium-mensual.importe=2500.00",
        "auditoria.pendientes.procesamiento-habilitado=false"
})
@Import(AuditoriaPendientePostgresIT.Configuracion.class)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_AUDITORIA_IT", matches = "true")
class AuditoriaPendientePostgresIT {

    private static final String RESTRICCION_FALLO_QA = "qa_forzar_fallo_log_auditoria";

    @jakarta.annotation.Resource
    private MascotaService mascotaService;
    @jakarta.annotation.Resource
    private ProcesadorEventoAuditoriaService procesador;
    @jakarta.annotation.Resource
    private EventoAuditoriaPendienteRepository eventoRepository;
    @jakarta.annotation.Resource
    private MascotaRepository mascotaRepository;
    @jakarta.annotation.Resource
    private UsuarioRepository usuarioRepository;
    @jakarta.annotation.Resource
    private JdbcTemplate jdbcTemplate;
    @jakarta.annotation.Resource
    private OperacionConRollback operacionConRollback;

    private final List<UUID> usuariosCreados = new ArrayList<>();
    private Usuario propietario;
    private Mascota mascota;

    @BeforeEach
    void preparar() {
        jdbcTemplate.execute("ALTER TABLE public.log_auditoria DROP CONSTRAINT IF EXISTS " + RESTRICCION_FALLO_QA);
        propietario = crearUsuario("propietario-auditoria");
        mascota = new Mascota();
        mascota.setPropietario(propietario);
        mascota.setNombre("Nombre anterior");
        mascota.setEspecie("Perro");
        mascota = mascotaRepository.saveAndFlush(mascota);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.execute("ALTER TABLE public.log_auditoria DROP CONSTRAINT IF EXISTS " + RESTRICCION_FALLO_QA);
        for (UUID usuarioId : usuariosCreados) {
            jdbcTemplate.update("""
                    DELETE FROM public.log_auditoria
                    WHERE evento_pendiente_id IN (
                        SELECT id_evento
                        FROM public.evento_auditoria_pendiente
                        WHERE actor_id = ?
                    )
                    """, usuarioId);
            jdbcTemplate.update("DELETE FROM public.evento_auditoria_pendiente WHERE actor_id = ?", usuarioId);
            jdbcTemplate.update("DELETE FROM public.mascota WHERE propietario_id = ?", usuarioId);
            jdbcTemplate.update("DELETE FROM public.usuario WHERE id_usuario = ?", usuarioId);
        }
    }

    @Test
    void edicionYEventoSeConfirmanJuntosYProducenUnSoloLog() {
        mascotaService.editar(mascota.getIdMascota(), solicitud("Nombre nuevo"), propietario.getIdUsuario());

        EventoAuditoriaPendiente evento = eventoDeLaMascota();
        assertThat(evento.getActor().getIdUsuario()).isEqualTo(propietario.getIdUsuario());
        assertThat(evento.getValorAnterior()).contains("Nombre anterior");
        assertThat(evento.getValorNuevo()).contains("Nombre nuevo");
        assertThat(evento.getProcesadoEn()).isNull();

        assertThat(procesador.procesar(evento.getIdEvento()))
                .isEqualTo(ProcesadorEventoAuditoriaService.ResultadoProcesamiento.PROCESADO);
        assertThat(procesador.procesar(evento.getIdEvento()))
                .isEqualTo(ProcesadorEventoAuditoriaService.ResultadoProcesamiento.OMITIDO);

        assertThat(contarLogs(evento.getIdEvento())).isEqualTo(1);
        assertThat(eventoRepository.findById(evento.getIdEvento()).orElseThrow().getProcesadoEn()).isNotNull();
    }

    @Test
    void rollbackPosteriorAEncolarRevierteEdicionYEvento() {
        assertThrows(IllegalStateException.class, () -> operacionConRollback.editarYFallar(
                mascota.getIdMascota(),
                solicitud("No debe persistir"),
                propietario.getIdUsuario()));

        Mascota recargada = mascotaRepository.findById(mascota.getIdMascota()).orElseThrow();
        assertThat(recargada.getNombre()).isEqualTo("Nombre anterior");
        assertThat(eventoRepository.findByIdEntidadOrderByFechaEvento(mascota.getIdMascota())).isEmpty();
    }

    @Test
    void edicionRechazadaPorPermisosNoDejaEvento() {
        Usuario ajeno = crearUsuario("usuario-ajeno-auditoria");

        assertThrows(AccesoDenegadoException.class, () -> mascotaService.editar(
                mascota.getIdMascota(),
                solicitud("No autorizado"),
                ajeno.getIdUsuario()));

        assertThat(eventoRepository.findByIdEntidadOrderByFechaEvento(mascota.getIdMascota())).isEmpty();
    }

    @Test
    void dosProcesadoresConcurrentesGeneranUnSoloLog() throws Exception {
        mascotaService.editar(mascota.getIdMascota(), solicitud("Nombre concurrente"), propietario.getIdUsuario());
        UUID eventoId = eventoDeLaMascota().getIdEvento();
        CountDownLatch inicio = new CountDownLatch(1);

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primero = ejecutor.submit(() -> {
                inicio.await();
                return procesador.procesar(eventoId);
            });
            var segundo = ejecutor.submit(() -> {
                inicio.await();
                return procesador.procesar(eventoId);
            });
            inicio.countDown();

            assertThat(List.of(
                    primero.get(10, TimeUnit.SECONDS),
                    segundo.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            ProcesadorEventoAuditoriaService.ResultadoProcesamiento.PROCESADO,
                            ProcesadorEventoAuditoriaService.ResultadoProcesamiento.OMITIDO);
        }

        assertThat(contarLogs(eventoId)).isEqualTo(1);
    }

    @Test
    void falloDelLogConservaLaEdicionYElSiguienteIntentoRecuperaElEvento() {
        mascotaService.editar(mascota.getIdMascota(), solicitud("Edicion confirmada"), propietario.getIdUsuario());
        EventoAuditoriaPendiente evento = eventoDeLaMascota();
        jdbcTemplate.execute("""
                ALTER TABLE public.log_auditoria
                ADD CONSTRAINT qa_forzar_fallo_log_auditoria
                CHECK (evento_pendiente_id IS NULL)
                """);

        RuntimeException error = assertThrows(RuntimeException.class, () -> procesador.procesar(evento.getIdEvento()));
        assertThat(procesador.registrarFallo(evento.getIdEvento(), error)).isEqualTo(1);
        assertThat(mascotaRepository.findById(mascota.getIdMascota()).orElseThrow().getNombre())
                .isEqualTo("Edicion confirmada");
        EventoAuditoriaPendiente fallido = eventoRepository.findById(evento.getIdEvento()).orElseThrow();
        assertThat(fallido.getProcesadoEn()).isNull();
        assertThat(fallido.getUltimoError()).isNotBlank();

        jdbcTemplate.execute("ALTER TABLE public.log_auditoria DROP CONSTRAINT " + RESTRICCION_FALLO_QA);
        assertThat(procesador.procesar(evento.getIdEvento()))
                .isEqualTo(ProcesadorEventoAuditoriaService.ResultadoProcesamiento.PROCESADO);
        assertThat(contarLogs(evento.getIdEvento())).isEqualTo(1);
    }

    private EventoAuditoriaPendiente eventoDeLaMascota() {
        return eventoRepository.findByIdEntidadOrderByFechaEvento(mascota.getIdMascota()).getFirst();
    }

    private int contarLogs(UUID eventoId) {
        Integer cantidad = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.log_auditoria WHERE evento_pendiente_id = ?",
                Integer.class,
                eventoId);
        return cantidad == null ? 0 : cantidad;
    }

    private Usuario crearUsuario(String prefijo) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Usuario QA Auditoria");
        usuario.setEmail(prefijo + "+" + UUID.randomUUID() + "@example.test");
        usuario.setPasswordHash("hash-ficticio-no-utilizable");
        usuario.setActivo(true);
        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        usuariosCreados.add(guardado.getIdUsuario());
        return guardado;
    }

    private MascotaRequestDTO solicitud(String nombre) {
        return new MascotaRequestDTO(
                nombre,
                "Perro",
                "Mestiza",
                LocalDate.of(2020, 2, 29),
                false,
                null,
                null,
                null,
                new BigDecimal("12.50"),
                "Notas QA");
    }

    @TestConfiguration
    static class Configuracion {

        @Bean
        OperacionConRollback operacionConRollback(MascotaService mascotaService) {
            return new OperacionConRollback(mascotaService);
        }
    }

    static class OperacionConRollback {

        private final MascotaService mascotaService;

        OperacionConRollback(MascotaService mascotaService) {
            this.mascotaService = mascotaService;
        }

        @Transactional
        public void editarYFallar(UUID mascotaId, MascotaRequestDTO solicitud, UUID actorId) {
            mascotaService.editar(mascotaId, solicitud, actorId);
            throw new IllegalStateException("Fallo posterior forzado por QA");
        }
    }
}
