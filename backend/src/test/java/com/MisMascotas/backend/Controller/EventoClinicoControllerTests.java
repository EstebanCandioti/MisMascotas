package com.MisMascotas.backend.Controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.MisMascotas.backend.Exception.GlobalExceptionHandler;
import com.MisMascotas.backend.DTO.EventoClinicoRequestDTO;
import com.MisMascotas.backend.DTO.EventoClinicoResponseDTO;
import com.MisMascotas.backend.Service.EventoClinicoService;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

@ExtendWith(MockitoExtension.class)
class EventoClinicoControllerTests {

    @Mock
    private EventoClinicoService eventoClinicoService;
    @Mock
    private UsuarioAutenticadoService usuarioAutenticadoService;

    private MockMvc mockMvc;
    private UUID mascotaId;

    @BeforeEach
    void setUp() {
        EventoClinicoController controller = new EventoClinicoController(
                eventoClinicoService,
                usuarioAutenticadoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mascotaId = UUID.randomUUID();
    }

    @Test
    void fechaNoInterpretableDevuelveBadRequest() throws Exception {
        verificarFechaInvalida("no-es-un-instante");
    }

    @Test
    void fechaInexistenteDevuelveBadRequest() throws Exception {
        verificarFechaInvalida("2026-02-30T12:00:00Z");
    }

    @Test
    void pesoNoNumericoDevuelveBadRequestSinInvocarElServicio() throws Exception {
        mockMvc.perform(post("/mascotas/{mascotaId}/eventos-clinicos", mascotaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipo": "PESO",
                                  "fecha": "2026-01-15T12:00:00-03:00",
                                  "valorPeso": "no-es-numero"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"codigo\":\"ERROR_VALIDACION\"")))
                .andExpect(content().string(containsString("valorPeso: El valor de peso debe ser numerico")));

        verifyNoInteractions(eventoClinicoService, usuarioAutenticadoService);
    }

    @Test
    void fechaValidaConOffsetConservaElInstante() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Instant fechaEsperada = Instant.parse("2026-01-15T15:00:00Z");
        EventoClinicoResponseDTO respuesta = new EventoClinicoResponseDTO(
                UUID.randomUUID(),
                mascotaId,
                usuarioId,
                "Usuario QA",
                "VACUNA",
                fechaEsperada,
                "Vacuna ficticia QA",
                null,
                null,
                null,
                null,
                null,
                Instant.now());
        when(usuarioAutenticadoService.obtenerIdUsuario(any())).thenReturn(usuarioId);
        when(eventoClinicoService.registrar(any(EventoClinicoRequestDTO.class), eq(usuarioId)))
                .thenReturn(respuesta);

        mockMvc.perform(post("/mascotas/{mascotaId}/eventos-clinicos", mascotaId)
                        .principal(() -> "usuario.qa@mismascotas.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipo": "VACUNA",
                                  "fecha": "2026-01-15T12:00:00-03:00",
                                  "nombre": "Vacuna ficticia QA"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<EventoClinicoRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(EventoClinicoRequestDTO.class);
        verify(eventoClinicoService).registrar(requestCaptor.capture(), eq(usuarioId));
        assertEquals(fechaEsperada, requestCaptor.getValue().fecha());
    }

    private void verificarFechaInvalida(String fecha) throws Exception {
        String cuerpo = """
                {
                  "tipo": "VACUNA",
                  "fecha": "%s",
                  "nombre": "Vacuna ficticia QA"
                }
                """.formatted(fecha);

        mockMvc.perform(post("/mascotas/{mascotaId}/eventos-clinicos", mascotaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"codigo\":\"ERROR_VALIDACION\"")))
                .andExpect(content().string(containsString("fecha: La fecha no tiene un formato valido o no existe")));
    }
}
