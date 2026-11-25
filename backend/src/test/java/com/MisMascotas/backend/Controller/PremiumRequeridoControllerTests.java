package com.MisMascotas.backend.Controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.MisMascotas.backend.Exception.GlobalExceptionHandler;
import com.MisMascotas.backend.Exception.PremiumRequeridoException;
import com.MisMascotas.backend.Service.PremiumService;

class PremiumRequeridoControllerTests {
    final PremiumService premium = mock(PremiumService.class);
    final UUID propietario = UUID.randomUUID();

    @Test void laExcepcionSeTraduceEn403ConCodigoUniforme() throws Exception {
        doThrow(new PremiumRequeridoException()).when(premium).exigirPremium(propietario);
        mvc().perform(get("/qa/premium"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PREMIUM_REQUERIDO"))
                .andExpect(jsonPath("$.mensaje").value("Esta operacion requiere Premium vigente"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test void laValidacionPositivaPermiteContinuar() throws Exception {
        mvc().perform(get("/qa/premium")).andExpect(status().isOk());
        verify(premium).exigirPremium(propietario);
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ConsumidorPrueba(premium, propietario))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** Consumidor exclusivo del test: no agrega una ruta a la aplicación. */
    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class ConsumidorPrueba {
        private final PremiumService premium;
        private final UUID propietario;
        ConsumidorPrueba(PremiumService premium, UUID propietario) {
            this.premium = premium; this.propietario = propietario;
        }
        @GetMapping("/qa/premium")
        Map<String, Boolean> consultar() {
            premium.exigirPremium(propietario);
            return Map.of("permitido", true);
        }
    }
}
