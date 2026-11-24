package com.MisMascotas.backend.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.MisMascotas.backend.Entity.Suscripcion;
import com.MisMascotas.backend.Entity.Usuario;
import com.MisMascotas.backend.Integration.MercadoPagoGateway;
import com.MisMascotas.backend.Integration.MercadoPagoGateway.*;
import com.MisMascotas.backend.Repository.SuscripcionRepository;
import com.MisMascotas.backend.Service.ProcesadorNotificacionMercadoPagoService.*;

class SuscripcionCicloServiceTests {
    final MercadoPagoGateway proveedor=mock(MercadoPagoGateway.class);
    final SuscripcionRepository repositorio=mock(SuscripcionRepository.class);
    final SuscripcionCicloTransaccionalService tx=mock(SuscripcionCicloTransaccionalService.class);
    final UUID id=UUID.randomUUID();
    Suscripcion local;SuscripcionCicloService service;
    @BeforeEach void preparar(){
        local=new Suscripcion();local.setIdSuscripcion(id);local.setTipoPlan("PREMIUM_MENSUAL");local.setIdSuscripcionMercadopago("s");var u=new Usuario();u.setIdUsuario(UUID.randomUUID());local.setUsuario(u);
        service=new SuscripcionCicloService(proveedor,new CatalogoPlanesSuscripcion("2500","ARS",1,"months","QA"),repositorio,tx);
        when(proveedor.consultarPreapproval("s")).thenReturn(remoto("authorized",new BigDecimal("2500"),"ARS",1));
    }
    @ParameterizedTest @ValueSource(strings={"importeFactura","monedaFactura","importePago","monedaPago","fechaPago","estadoPago","estadoFactura","importePlan","monedaPlan","frecuenciaPlan","referenciaPlan","estadoPlan","idPago","referenciaFactura"})
    void datosFinancierosOIdentidadesDistintasNoSeRegistran(String caso){
        var f=new FacturaAutorizada("f","s","referenciaFactura".equals(caso)?"otra":id.toString(),"p",
                "estadoFactura".equals(caso)?"recycling":"processed",1,null,null,
                new BigDecimal("importeFactura".equals(caso)?"1":"2500"),"monedaFactura".equals(caso)?"USD":"ARS");
        var p=new PagoConsultado("idPago".equals(caso)?"otro":"p","estadoPago".equals(caso)?"rejected":"approved",
                new BigDecimal("importePago".equals(caso)?"1":"2500"),"monedaPago".equals(caso)?"USD":"ARS",
                "fechaPago".equals(caso)?null:Instant.EPOCH);
        if("importePlan".equals(caso))when(proveedor.consultarPreapproval("s")).thenReturn(remoto("authorized",BigDecimal.ONE,"ARS",1));
        if("monedaPlan".equals(caso))when(proveedor.consultarPreapproval("s")).thenReturn(remoto("authorized",new BigDecimal("2500"),"USD",1));
        if("frecuenciaPlan".equals(caso))when(proveedor.consultarPreapproval("s")).thenReturn(remoto("authorized",new BigDecimal("2500"),"ARS",2));
        if("estadoPlan".equals(caso))when(proveedor.consultarPreapproval("s")).thenReturn(remoto("pending",new BigDecimal("2500"),"ARS",1));
        if("referenciaPlan".equals(caso))when(proveedor.consultarPreapproval("s")).thenReturn(new PreapprovalConsultado("s","otra","authorized",null,new BigDecimal("2500"),"ARS",1,"months"));
        assertThat(service.registrar(local,f,p).estado()).isEqualTo(Estado.REVISION);verifyNoInteractions(tx);
    }
    @Test void condicionesDeSuscripcionModificadasNoSeSincronizan(){
        when(repositorio.findByIdSuscripcionMercadopagoConUsuario("s")).thenReturn(Optional.of(local));
        when(proveedor.consultarPreapproval("s")).thenReturn(remoto("authorized",BigDecimal.ONE,"ARS",1));
        assertThat(service.sincronizar("s").motivo()).isEqualTo("CONDICIONES_MODIFICADAS");verifyNoInteractions(tx);
    }
    PreapprovalConsultado remoto(String e,BigDecimal importe,String moneda,int frecuencia){return new PreapprovalConsultado("s",id.toString(),e,null,importe,moneda,frecuencia,"months");}
}
