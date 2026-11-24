package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalCreado;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.PreapprovalConsultado;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.SolicitudPreapproval;
import com.MisMascotas.backend.Repository.EstadoRepository;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Repository.UsuarioRepository;
import com.MisMascotas.backend.Service.SuscripcionService.EstadoAltaSuscripcion;

@SpringBootTest(properties = {
        "jwt.secret=secreto-ficticio-de-pruebas-con-mas-de-32-caracteres",
        "suscripciones.planes.premium-mensual.importe=2500.00"
})
@Import(SuscripcionConcurrenciaPostgresIT.ConfigProveedor.class)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_SUSCRIPCION_IT", matches = "true")
class SuscripcionConcurrenciaPostgresIT {

    @jakarta.annotation.Resource
    private SuscripcionService suscripcionService;
    @jakarta.annotation.Resource
    private SuscripcionRepository suscripcionRepository;
    @jakarta.annotation.Resource
    private UsuarioRepository usuarioRepository;
    @jakarta.annotation.Resource
    private EstadoRepository estadoRepository;
    @jakarta.annotation.Resource
    private ProveedorControlado proveedor;

    private Usuario usuario;
    private final List<Usuario> usuariosCreados = new ArrayList<>();

    @BeforeEach
    void preparar() {
        assertThat(estadoRepository.findByEntidadAndNombre("SUSCRIPCION", "PENDIENTE"))
                .as("Debe aplicarse el seed/migracion de SUSCRIPCION/PENDIENTE")
                .isPresent();
        usuario = nuevoUsuario("suscripcion-concurrencia");
        proveedor.reiniciar(true);
    }

    @AfterEach
    void limpiar() {
        for (Usuario usuarioCreado : usuariosCreados) {
            List<com.MisMascotas.backend.Entity.Suscripcion> suscripciones =
                    suscripcionRepository.findAllByUsuario_IdUsuario(usuarioCreado.getIdUsuario());
            suscripcionRepository.deleteAll(suscripciones);
            usuarioRepository.deleteById(usuarioCreado.getIdUsuario());
        }
    }

    @Test
    void mismoUsuarioYPlanCreaUnaReservaYUnPreapproval() throws Exception {
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> suscripcionService.iniciarAlta(
                    usuario.getIdUsuario(),
                    CatalogoPlanesSuscripcion.PREMIUM_MENSUAL));
            assertThat(proveedor.esperarPrimeraLlamada()).isTrue();

            var segunda = ejecutor.submit(() -> suscripcionService.iniciarAlta(
                    usuario.getIdUsuario(),
                    CatalogoPlanesSuscripcion.PREMIUM_MENSUAL));
            var resultadoSegunda = segunda.get(10, TimeUnit.SECONDS);
            proveedor.liberar();
            var resultadoPrimera = primera.get(10, TimeUnit.SECONDS);

            assertThat(resultadoPrimera.estado()).isEqualTo(EstadoAltaSuscripcion.CHECKOUT_DISPONIBLE);
            assertThat(resultadoSegunda.estado()).isEqualTo(EstadoAltaSuscripcion.PENDIENTE_CONCILIACION);
        }

        var pendientes = suscripcionRepository.findAllByUsuario_IdUsuario(usuario.getIdUsuario());
        assertThat(pendientes).hasSize(1);
        assertThat(proveedor.cantidadLlamadas()).isEqualTo(1);
        assertThat(pendientes.getFirst().getIdSuscripcionMercadopago()).isNotBlank();
        assertThat(proveedor.referenciasExternas())
                .containsExactly(pendientes.getFirst().getIdSuscripcion().toString());
    }

    @Test
    void usuariosDistintosPuedenCrearAltasEnParalelo() throws Exception {
        Usuario segundoUsuario = nuevoUsuario("suscripcion-paralela");
        proveedor.reiniciar(false);
        CountDownLatch inicio = new CountDownLatch(1);

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> {
                inicio.await();
                return suscripcionService.iniciarAlta(
                        usuario.getIdUsuario(),
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);
            });
            var segunda = ejecutor.submit(() -> {
                inicio.await();
                return suscripcionService.iniciarAlta(
                        segundoUsuario.getIdUsuario(),
                        CatalogoPlanesSuscripcion.PREMIUM_MENSUAL);
            });
            inicio.countDown();

            assertThat(primera.get(10, TimeUnit.SECONDS).estado())
                    .isEqualTo(EstadoAltaSuscripcion.CHECKOUT_DISPONIBLE);
            assertThat(segunda.get(10, TimeUnit.SECONDS).estado())
                    .isEqualTo(EstadoAltaSuscripcion.CHECKOUT_DISPONIBLE);
        }

        assertThat(proveedor.cantidadLlamadas()).isEqualTo(2);
        assertThat(suscripcionRepository.findAllByUsuario_IdUsuario(usuario.getIdUsuario())).hasSize(1);
        assertThat(suscripcionRepository.findAllByUsuario_IdUsuario(segundoUsuario.getIdUsuario())).hasSize(1);
    }

    private Usuario nuevoUsuario(String prefijo) {
        Usuario nuevo = new Usuario();
        nuevo.setNombre("Usuario QA Suscripciones");
        nuevo.setEmail(prefijo + "+" + UUID.randomUUID() + "@example.test");
        nuevo.setPasswordHash("hash-ficticio-no-utilizable");
        nuevo.setActivo(true);
        Usuario guardado = usuarioRepository.saveAndFlush(nuevo);
        usuariosCreados.add(guardado);
        return guardado;
    }

    @TestConfiguration
    static class ConfigProveedor {

        @Bean
        @Primary
        ProveedorControlado proveedorControlado() {
            return new ProveedorControlado();
        }
    }

    static class ProveedorControlado implements MercadoPagoGateway {

        private final AtomicInteger llamadas = new AtomicInteger();
        private final ConcurrentLinkedQueue<String> referencias = new ConcurrentLinkedQueue<>();
        private volatile boolean bloquear;
        private volatile CountDownLatch primeraLlamada;
        private volatile CountDownLatch liberar;

        void reiniciar(boolean bloquear) {
            llamadas.set(0);
            referencias.clear();
            this.bloquear = bloquear;
            primeraLlamada = new CountDownLatch(1);
            liberar = new CountDownLatch(1);
        }

        @Override
        public void validarConfiguracion() {
            // Proveedor controlado: no usa credenciales reales.
        }

        @Override
        public PreapprovalCreado crearPreapproval(SolicitudPreapproval solicitud) {
            llamadas.incrementAndGet();
            referencias.add(solicitud.referenciaExterna());
            primeraLlamada.countDown();
            if (bloquear) {
                try {
                    if (!liberar.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("La prueba no libero la respuesta del proveedor");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Prueba interrumpida", ex);
                }
            }
            return new PreapprovalCreado(
                    "preapproval-" + solicitud.referenciaExterna(),
                    "https://checkout.example.test/" + solicitud.referenciaExterna());
        }

        @Override
        public PreapprovalConsultado consultarPreapproval(String idPreapproval) {
            throw new UnsupportedOperationException("Esta prueba solo cubre la creacion del preapproval");
        }

        @Override
        public void cancelarPreapproval(String idPreapproval) {
            throw new UnsupportedOperationException("Esta prueba solo cubre la creacion del preapproval");
        }

        @Override
        public List<CuotaConsultada> buscarCuotas(String idPreapproval) {
            throw new UnsupportedOperationException("Esta prueba solo cubre la creacion del preapproval");
        }

        @Override
        public PagoConsultado consultarPago(String idPago) {
            throw new UnsupportedOperationException("Esta prueba solo cubre la creacion del preapproval");
        }

        boolean esperarPrimeraLlamada() throws InterruptedException {
            return primeraLlamada.await(10, TimeUnit.SECONDS);
        }

        void liberar() {
            liberar.countDown();
        }

        int cantidadLlamadas() {
            return llamadas.get();
        }

        List<String> referenciasExternas() {
            return List.copyOf(referencias);
        }
    }
}
