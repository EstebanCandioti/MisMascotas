package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Repository.*;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.*;

/** PostgreSQL dedicado. Nunca usa la conexion de desarrollo ni un proveedor real. */
@SpringBootTest(properties={
    "spring.datasource.url=${QA_COBRO_DB_URL}","spring.datasource.username=${QA_COBRO_DB_USERNAME}",
    "spring.datasource.password=${QA_COBRO_DB_PASSWORD}","spring.jpa.hibernate.ddl-auto=validate",
    "jwt.secret=secreto-ficticio-de-pruebas-con-mas-de-32-caracteres",
    "mercadopago.webhook.secret=secreto-ficticio-ciclo",
    "mercadopago.notificaciones.scheduler-habilitado=false","auditoria.pendientes.procesamiento-habilitado=false",
    "suscripciones.planes.premium-mensual.importe=2500.00"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_POSTGRES_CICLO_IT",matches="true")
class SuscripcionCicloPostgresIT {
    @jakarta.annotation.Resource ProcesadorNotificacionMercadoPagoService procesador;
    @MockitoSpyBean NotificacionMercadoPagoRepository notificaciones;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @jakarta.annotation.Resource SuscripcionRepository suscripciones;
    @jakarta.annotation.Resource UsuarioRepository usuarios;
    @jakarta.annotation.Resource EstadoRepository estados;
    @jakarta.annotation.Resource PagoRepository pagos;
    @jakarta.annotation.Resource SuscripcionVencimientoService vencimientos;
    @jakarta.annotation.Resource SuscripcionReservaService reservas;
    @jakarta.annotation.Resource SuscripcionService manual;
    @jakarta.annotation.Resource JdbcTemplate sql;
    @jakarta.annotation.Resource PlatformTransactionManager gestor;
    @MockitoBean MercadoPagoGateway proveedor;
    @MockitoBean Clock reloj;
    @MockitoSpyBean EventoAuditoriaPendienteService auditoria;
    static final Instant INICIO=Instant.parse("2030-01-31T20:00:00Z");
    final AtomicReference<Instant> ahora=new AtomicReference<>(INICIO);
    final List<String> creadas=new ArrayList<>();
    Usuario u;Suscripcion s;String prefijo;

    @BeforeEach void preparar(){
        assertThat(System.getenv("QA_COBRO_DB_URL")).startsWith("jdbc:postgresql://127.0.0.1:").contains("/qa_notificaciones_mercado_pago");
        when(reloj.instant()).thenAnswer(i->ahora.get());ahora.set(INICIO);prefijo="ciclo-qa-"+UUID.randomUUID();
        u=new Usuario();u.setEmail(prefijo+"@example.test");u.setNombre("QA ciclo");u.setPasswordHash("hash-qa");u=usuarios.saveAndFlush(u);
        s=new Suscripcion();s.setUsuario(u);s.setTipoPlan("PREMIUM_MENSUAL");s.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION","PENDIENTE").orElseThrow());s.setIdSuscripcionMercadopago(prefijo);s=suscripciones.saveAndFlush(s);
        remoto("authorized");
    }
    @AfterEach void limpiar(){
        if(u==null)return;
        for(String n:creadas)sql.update("DELETE FROM notificacion_mercado_pago WHERE id_notificacion = ?",n);
        sql.update("DELETE FROM evento_auditoria_pendiente WHERE id_entidad = ? OR id_entidad IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id = ?) OR id_entidad IN (SELECT p.id_pago FROM pago p JOIN suscripcion s ON s.id_suscripcion = p.suscripcion_id WHERE s.usuario_id = ?)",u.getIdUsuario(),u.getIdUsuario(),u.getIdUsuario());
        sql.update("DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id = ?)",u.getIdUsuario());
        sql.update("DELETE FROM suscripcion WHERE usuario_id = ?",u.getIdUsuario());usuarios.deleteById(u.getIdUsuario());
    }
    @Test void primerCobroDosRenovacionesYRepeticionesNoDuplican(){
        for(int i=1;i<=3;i++){
            String f=factura(i,"processed","approved",INICIO.plusSeconds((i-1)*86400));
            assertThat(procesador.evaluar(nueva(f)).motivo()).isEqualTo(i==1?"ACTIVADA":"RENOVADA");
            assertThat(procesador.evaluar(nueva(f)).motivo()).isEqualTo("YA_CONFIRMADA");
        }
        assertThat(cantidadPagos()).isEqualTo(3);assertThat(eventos()).isEqualTo(6);
        assertThat(actual().getFechaVencimiento()).isEqualTo(Instant.parse("2030-04-28T20:00:00Z"));
        assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
    }
    @Test void manualAntesDelWebhookVinculaFacturaSinExtenderOtraVez(){
        String f=factura(1,"processed","approved",INICIO);
        when(proveedor.buscarCuotas(prefijo)).thenReturn(List.of(new CuotaConsultada(prefijo,prefijo+"-p1")));
        Instant siguiente=INICIO.plusSeconds(86400*31);
        when(proveedor.consultarPreapproval(prefijo)).thenReturn(new PreapprovalConsultado(prefijo,s.getIdSuscripcion().toString(),"authorized",null,new BigDecimal("2500"),"ARS",1,"months",siguiente,INICIO));
        assertThat(manual.confirmarPrimerCobro(u.getIdUsuario(),s.getIdSuscripcion()).estado().name()).isEqualTo("ACTIVADA");
        Instant vencimiento=actual().getFechaVencimiento();
        assertThat(actual().getProximoRecargo()).isEqualTo(siguiente).isNotEqualTo(vencimiento);
        assertThat(procesador.evaluar(nueva(f)).motivo()).isEqualTo("YA_CONFIRMADA");
        assertThat(actual().getFechaVencimiento()).isEqualTo(vencimiento);assertThat(cantidadPagos()).isEqualTo(1);assertThat(eventos()).isEqualTo(2);
    }
    @Test void dosConfirmacionesConcurrentesAcreditanUnaSolaCuota() throws Exception {
        String f=factura(1,"processed","approved",INICIO);var n=nueva(f);var inicio=new CountDownLatch(1);
        try(var h=Executors.newFixedThreadPool(2)){
            Callable<String> trabajo=()->{inicio.await(5,TimeUnit.SECONDS);return procesador.evaluar(n).motivo();};
            var a=h.submit(trabajo);var b=h.submit(trabajo);inicio.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("ACTIVADA","YA_CONFIRMADA");
        }
        assertThat(cantidadPagos()).isEqualTo(1);assertThat(eventos()).isEqualTo(2);
    }
    @Test void falloDespuesDeEncolarReviertePagoVigenciaYAuditoria(){
        String f=factura(1,"processed","approved",INICIO);
        EventoAuditoriaPendienteService destino=AopTestUtils.getUltimateTargetObject(auditoria);
        doAnswer(i->{Object valor=i.callRealMethod();if("suscripcion".equals(i.getArgument(1)))throw new IllegalStateException("QA despues del encolado");return valor;})
                .when(destino).encolar(any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->procesador.evaluar(nueva(f))).isInstanceOf(IllegalStateException.class);
        assertThat(cantidadPagos()).isZero();assertThat(eventos()).isZero();assertThat(actual().getFechaVencimiento()).isNull();
        assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();
    }
    @Test void reemplazoDuranteLaConsultaImpideAcreditarLaReservaAnterior(){
        String f=factura(1,"processed","approved",INICIO);
        when(proveedor.consultarPago(prefijo+"-p1")).thenAnswer(i->{reservas.reemplazarTrasCancelacionConfirmada(u.getIdUsuario(),s.getIdSuscripcion(),prefijo);return pago(1,"approved",INICIO);});
        assertThat(procesador.evaluar(nueva(f)).estado()).isEqualTo(Estado.REVISION);assertThat(cantidadPagos()).isZero();
        assertThat(actual().isReemplazada()).isTrue();
    }
    @Test void rechazoRecyclingSeVerificaDiariamenteYWebhookNuevoLoAdelanta(){
        String f=factura(1,"recycling","rejected",INICIO);var primera=nueva(f);var otra=nueva(f);
        scheduler().ejecutar();
        assertThat(sql.queryForObject("SELECT proximo_procesamiento_en FROM notificacion_mercado_pago WHERE id_notificacion = ?",java.sql.Timestamp.class,primera.idNotificacion()).toInstant()).isEqualTo(INICIO.plusSeconds(86400));
        verify(proveedor,times(1)).consultarFacturaAutorizada(f);assertThat(cantidadPagos()).isZero();
        ahora.set(INICIO.plusSeconds(60));scheduler().ejecutar();verify(proveedor,times(1)).consultarFacturaAutorizada(f);
        factura(1,"processed","approved",INICIO);var nueva=nueva(f);scheduler().ejecutar();
        for(var n:List.of(primera,otra,nueva))assertThat(notificaciones.buscar(n.idNotificacion()).orElseThrow().procesadaEn()).isNotNull();
        assertThat(cantidadPagos()).isEqualTo(1);assertThat(eventos()).isEqualTo(2);
        assertThat(notificaciones.estadoPrevio(nueva.idNotificacion()).primerPendiente()).isEqualTo(INICIO);
    }
    @Test void limiteDeDiezDiasHaceUltimaConsultaYUnaNotificacionPosteriorPuedeConfirmar(){
        String f=factura(1,"recycling","rejected",INICIO);var n=nueva(f);scheduler().ejecutar();
        ahora.set(INICIO.plusSeconds(864000));scheduler().ejecutar();
        assertThat(sql.queryForObject("SELECT estado_procesamiento FROM notificacion_mercado_pago WHERE id_notificacion = ?",String.class,n.idNotificacion())).isEqualTo("REVISION");
        verify(proveedor,times(2)).consultarFacturaAutorizada(f);
        factura(1,"processed","approved",INICIO);var posterior=nueva(f);scheduler().ejecutar();
        assertThat(notificaciones.buscar(posterior.idNotificacion()).orElseThrow().procesadaEn()).isNotNull();assertThat(cantidadPagos()).isEqualTo(1);
        assertThat(notificaciones.buscar(n.idNotificacion()).orElseThrow().procesadaEn()).isNull();
    }
    @Test void waitingForGatewaySeConsultaCadaCincoMinutos(){
        String f=factura(1,"waiting for gateway","in_process",INICIO);var n=nueva(f);scheduler().ejecutar();
        assertThat(sql.queryForObject("SELECT proximo_procesamiento_en FROM notificacion_mercado_pago WHERE id_notificacion = ?",java.sql.Timestamp.class,n.idNotificacion()).toInstant()).isEqualTo(INICIO.plusSeconds(300));
        ahora.set(INICIO.plusSeconds(299));scheduler().ejecutar();verify(proveedor,times(1)).consultarFacturaAutorizada(f);
        ahora.set(INICIO.plusSeconds(300));scheduler().ejecutar();verify(proveedor,times(2)).consultarFacturaAutorizada(f);
    }
    @Test void cancelacionPausaYReactivacionConservanCobertura(){
        procesador.evaluar(nueva(factura(1,"processed","approved",INICIO)));Instant vence=actual().getFechaVencimiento();
        remoto("paused");assertThat(cambio().motivo()).isEqualTo("SUSCRIPCION_PAUSADA");
        remoto("authorized");assertThat(cambio().motivo()).isEqualTo("SUSCRIPCION_REACTIVADA");
        remoto("cancelled");assertThat(cambio().motivo()).isEqualTo("SUSCRIPCION_CANCELADA");
        assertThat(actual().getFechaVencimiento()).isEqualTo(vence);assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
        assertThat(actual().getEstadoMercadopago()).isEqualTo("cancelled");
        long antes=eventos();cambio();assertThat(eventos()).isEqualTo(antes);
        assertThat(manual.confirmarPrimerCobro(u.getIdUsuario(),s.getIdSuscripcion()).estado().name()).isEqualTo("YA_CONFIRMADA");
    }
    @Test void autorizacionSinPagoNoActiva(){assertThat(cambio().motivo()).isEqualTo("SUSCRIPCION_AUTORIZADA");assertThat(actual().getFechaVencimiento()).isNull();assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();}
    @Test void vencimientoEsIdempotenteYRenovarDespuesRestauraPremium(){
        procesador.evaluar(nueva(factura(1,"processed","approved",INICIO)));Instant vence=actual().getFechaVencimiento();ahora.set(vence);
        vencimientos.vencer(u.getIdUsuario());long antes=eventos();vencimientos.vencer(u.getIdUsuario());assertThat(eventos()).isEqualTo(antes);
        assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();
        procesador.evaluar(nueva(factura(2,"processed","approved",vence.plusSeconds(60))));
        assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
    }
    @Test void renovarConcurrenteConVencerTerminaConLaNuevaCobertura() throws Exception {
        procesador.evaluar(nueva(factura(1,"processed","approved",INICIO)));Instant vence=actual().getFechaVencimiento();ahora.set(vence);
        var n=nueva(factura(2,"processed","approved",vence));var inicio=new CountDownLatch(1);
        try(var h=Executors.newFixedThreadPool(2)){
            var a=h.submit(()->{inicio.await();vencimientos.vencer(u.getIdUsuario());return true;});
            var c=h.submit(()->{inicio.await();return procesador.evaluar(n);});inicio.countDown();a.get(15,TimeUnit.SECONDS);assertThat(c.get(15,TimeUnit.SECONDS).motivo()).isEqualTo("RENOVADA");
        }
        assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
        assertThat(actual().getFechaVencimiento()).isAfter(vence);
    }
    @Test void migracionRepetidaConservaFilasRlsYUnicidad() throws Exception {
        var n=nueva(factura(1,"recycling","rejected",INICIO));scheduler().ejecutar();
        Instant fecha=notificaciones.estadoPrevio(n.idNotificacion()).primerPendiente();
        String migracion=Files.readString(Path.of("../DB/migraciones/20261118_ciclo_suscripciones.sql"));sql.execute(migracion);sql.execute(migracion);
        assertThat(notificaciones.estadoPrevio(n.idNotificacion()).primerPendiente()).isEqualTo(fecha);
        assertThat(sql.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.notificacion_mercado_pago'::regclass",Boolean.class)).isTrue();
        assertThat(sql.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='pago' AND indexdef LIKE '%UNIQUE%' AND indexdef LIKE '%id_factura_mercadopago%'",Long.class)).isPositive();
    }
    @Test void laBaseImpideDosPagosConLaMismaFactura(){
        String f=factura(1,"processed","approved",INICIO);procesador.evaluar(nueva(f));
        var duplicado=new com.MisMascotas.backend.Entity.Pago();duplicado.setSuscripcion(s);
        duplicado.setIdTransaccionMercadopago(prefijo+"-otro-pago");duplicado.setIdFacturaMercadopago(f);
        duplicado.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO","APROBADO").orElseThrow());
        duplicado.setMonto(new BigDecimal("2500"));duplicado.setFechaPago(INICIO);
        assertThatThrownBy(()->pagos.saveAndFlush(duplicado)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(cantidadPagos()).isEqualTo(1);
    }
    @Test void otraSuscripcionConCoberturaConservaPremiumYNoSeSeleccionaIndefinidamente(){
        procesador.evaluar(nueva(factura(1,"processed","approved",INICIO)));Instant vence=actual().getFechaVencimiento();
        var otra=new Suscripcion();otra.setUsuario(u);otra.setTipoPlan("PREMIUM_MENSUAL");
        otra.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION","CANCELADA").orElseThrow());
        otra.setFechaVencimiento(vence.plusSeconds(86400));otra=suscripciones.saveAndFlush(otra);
        var pagoOtra=new com.MisMascotas.backend.Entity.Pago();pagoOtra.setSuscripcion(otra);pagoOtra.setMonto(new BigDecimal("2500"));pagoOtra.setFechaPago(INICIO);
        pagoOtra.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO","APROBADO").orElseThrow());pagos.saveAndFlush(pagoOtra);
        ahora.set(vence);scheduler().ejecutar();
        assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();
        assertThat(suscripciones.buscarUsuariosConCoberturaVencida(ahora.get(),20)).doesNotContain(u.getIdUsuario());
    }
    @Test void falloDeVencimientoRevierteTantoElUsuarioComoLaSuscripcion(){
        procesador.evaluar(nueva(factura(1,"processed","approved",INICIO)));ahora.set(actual().getFechaVencimiento());
        long antes=eventos();EventoAuditoriaPendienteService destino=AopTestUtils.getUltimateTargetObject(auditoria);
        doAnswer(i->{Object valor=i.callRealMethod();if("usuario".equals(i.getArgument(1)))throw new IllegalStateException("QA vencimiento");return valor;})
                .when(destino).encolar(any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->vencimientos.vencer(u.getIdUsuario())).isInstanceOf(IllegalStateException.class);
        assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isTrue();assertThat(eventos()).isEqualTo(antes);
        assertThat(sql.queryForObject("SELECT e.nombre FROM suscripcion s JOIN estados e ON e.id_estado=s.estado_id WHERE s.id_suscripcion=?",String.class,s.getIdSuscripcion())).isEqualTo("ACTIVA");
    }

    @Test void webhookHttpFirmaRealCommitPostgresYReenvioSinDuplicar() throws Exception {
        String f=factura(1,"recycling","rejected",INICIO), id=prefijo+"-http";creadas.add(id);
        mvc.perform(solicitud(id,f)).andExpect(status().isOk());
        assertThat(notificaciones.buscar(id).orElseThrow().procesadaEn()).isNull();
        verify(proveedor,never()).consultarFacturaAutorizada(f);
        mvc.perform(solicitud(id,f)).andExpect(status().isOk());
        assertThat(sql.queryForObject("SELECT count(*) FROM notificacion_mercado_pago WHERE id_notificacion=?",Long.class,id)).isEqualTo(1);
        scheduler().ejecutar();
        assertThat(sql.queryForObject("SELECT estado_factura_mp FROM notificacion_mercado_pago WHERE id_notificacion=?",String.class,id)).isEqualTo("recycling");
        assertThat(sql.queryForObject("SELECT estado_pago_mp FROM notificacion_mercado_pago WHERE id_notificacion=?",String.class,id)).isEqualTo("rejected");
        mvc.perform(solicitud(prefijo+"-invalida",f).headers(h -> h.set("x-signature","invalida"))).andExpect(status().isUnauthorized());
        assertThat(notificaciones.buscar(prefijo+"-invalida")).isEmpty();
    }
    @Test void falloHttpPosteriorAlInsertRealRevierteLaRecepcionYDevuelve503() throws Exception {
        String id=prefijo+"-http-fallo";creadas.add(id);
        NotificacionMercadoPagoRepository destino=AopTestUtils.getUltimateTargetObject(notificaciones);
        doAnswer(i->{i.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("QA despues de insertar");})
                .when(destino).registrar(eq(id),anyString(),anyString());
        mvc.perform(solicitud(id,prefijo+"-f")).andExpect(status().isServiceUnavailable());
        assertThat(notificaciones.buscar(id)).isEmpty();
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud(String id,String recurso) throws Exception {
        String ts="1704908010",requestId="qa-ciclo-http";
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("secreto-ficticio-ciclo".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        String manifiesto="id:"+recurso.toLowerCase(java.util.Locale.ROOT)+";request-id:"+requestId+";ts:"+ts+";";
        String firma=java.util.HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return post("/webhooks/mercado-pago").param("data.id",recurso).header("x-request-id",requestId)
                .header("x-signature","ts="+ts+",v1="+firma).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"id\":\""+id+"\",\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":\""+recurso+"\"}}");
    }

    @Test void pagoRechazadoNoSostieneLaCoberturaDeOtraSuscripcion(){
        procesador.evaluar(nueva(factura(1,"processed","approved",INICIO)));Instant vence=actual().getFechaVencimiento();
        var otra=new Suscripcion();otra.setUsuario(u);otra.setTipoPlan("PREMIUM_MENSUAL");
        otra.setEstadoCatalogo(estados.findByEntidadAndNombre("SUSCRIPCION","ACTIVA").orElseThrow());otra.setFechaVencimiento(vence.plusSeconds(86400));otra=suscripciones.saveAndFlush(otra);
        var rechazado=new com.MisMascotas.backend.Entity.Pago();rechazado.setSuscripcion(otra);rechazado.setMonto(new BigDecimal("2500"));rechazado.setFechaPago(INICIO);
        rechazado.setEstadoCatalogo(estados.findByEntidadAndNombre("PAGO","RECHAZADO").orElseThrow());pagos.saveAndFlush(rechazado);
        assertThat(pagos.existePagoAprobado(otra.getIdSuscripcion())).isFalse();ahora.set(vence);scheduler().ejecutar();
        assertThat(usuarios.findById(u.getIdUsuario()).orElseThrow().isEsPremium()).isFalse();
    }

    ResultadoProcesamiento cambio(){return procesador.evaluar(new NotificacionMercadoPagoRepository.NotificacionMercadoPago("cambio", "subscription_preapproval",prefijo,null));}
    ProcesadorNotificacionesMercadoPagoProgramado scheduler(){return new ProcesadorNotificacionesMercadoPagoProgramado(notificaciones,procesador,reloj,gestor,20,suscripciones,vencimientos);}
    NotificacionMercadoPagoRepository.NotificacionMercadoPago nueva(String factura){String id=prefijo+"-n"+creadas.size();creadas.add(id);notificaciones.registrar(id,"subscription_authorized_payment",factura);return notificaciones.buscar(id).orElseThrow();}
    String factura(int numero,String estado,String pagoEstado,Instant fecha){
        String id=prefijo+"-f"+numero;
        when(proveedor.consultarFacturaAutorizada(id)).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new FacturaAutorizada(id,prefijo,s.getIdSuscripcion().toString(),prefijo+"-p"+numero,estado,1,fecha,null,new BigDecimal("2500"),"ARS");});
        when(proveedor.consultarPago(prefijo+"-p"+numero)).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return pago(numero,pagoEstado,fecha);});return id;
    }
    PagoConsultado pago(int numero,String estado,Instant fecha){return new PagoConsultado(prefijo+"-p"+numero,estado,new BigDecimal("2500"),"ARS","approved".equals(estado)?fecha:null);}
    void remoto(String estado){when(proveedor.consultarPreapproval(prefijo)).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new PreapprovalConsultado(prefijo,s.getIdSuscripcion().toString(),estado,null,new BigDecimal("2500"),"ARS",1,"months",INICIO.plusSeconds(86400*28),ahora.get());});}
    Suscripcion actual(){return suscripciones.findById(s.getIdSuscripcion()).orElseThrow();}
    long cantidadPagos(){return pagos.findBySuscripcion_IdSuscripcionOrderByFechaPagoAsc(s.getIdSuscripcion()).size();}
    long eventos(){return sql.queryForObject("SELECT count(*) FROM evento_auditoria_pendiente WHERE id_entidad = ? OR id_entidad = ? OR id_entidad IN (SELECT id_pago FROM pago WHERE suscripcion_id = ?)",Long.class,u.getIdUsuario(),s.getIdSuscripcion(),s.getIdSuscripcion());}
}
