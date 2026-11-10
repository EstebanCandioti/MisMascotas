package com.MisMascotas.backend.Controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.MisMascotas.backend.DTO.MascotaRequestDTO;
import com.MisMascotas.backend.DTO.MascotaResponseDTO;
import com.MisMascotas.backend.Exception.GlobalExceptionHandler;
import com.MisMascotas.backend.Service.MascotaService;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

@ExtendWith(MockitoExtension.class)
class MascotaControllerTests {

    @Mock
    private MascotaService mascotaService;
    @Mock
    private UsuarioAutenticadoService usuarioAutenticadoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MascotaController(mascotaService, usuarioAutenticadoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rechazaExcesosYDatosInvalidosSinIntentarCrearMascotas() throws Exception {
        List<VarianteInvalida> variantes = List.of(
                new VarianteInvalida(cuerpo("N".repeat(101), "Perro", ""), "nombre"),
                new VarianteInvalida(cuerpo("Mascota QA", "E".repeat(51), ""), "especie"),
                new VarianteInvalida(cuerpo("Mascota QA", "Perro", "\"raza\":\"" + "R".repeat(101) + "\""), "raza"),
                new VarianteInvalida(cuerpo("Mascota QA", "Perro", "\"notas\":\"" + "O".repeat(256) + "\""), "notas"),
                new VarianteInvalida(cuerpo("Mascota QA", "Perro", "\"fechaNacimiento\":\"2999-01-01\""), "fechaNacimiento"),
                new VarianteInvalida(cuerpoAproximada("Mascota QA", "Perro", "\"edadValor\":-1,\"edadUnidad\":\"anios\""), "edadValor"),
                new VarianteInvalida(cuerpoAproximada("Mascota QA", "Perro", "\"edadValor\":1,\"edadUnidad\":\"siglos\""), "edadUnidad"),
                new VarianteInvalida(cuerpo("Mascota QA", "Perro", "\"pesoActual\":0"), "pesoActual"),
                new VarianteInvalida(cuerpo("Mascota QA", "Perro", "\"pesoActual\":-1"), "pesoActual"),
                new VarianteInvalida(cuerpo("Mascota QA", "Perro", "\"pesoActual\":10000"), "pesoActual"));

        for (VarianteInvalida variante : variantes) {
            mockMvc.perform(post("/mascotas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(variante.cuerpo()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("\"codigo\":\"ERROR_VALIDACION\"")))
                    .andExpect(content().string(containsString(variante.campo() + ":")));
        }

        verifyNoInteractions(mascotaService, usuarioAutenticadoService);
    }

    @Test
    void fechaDeNacimientoInexistenteDevuelveBadRequestSinIntentarCrear() throws Exception {
        mockMvc.perform(post("/mascotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(
                                "Mascota QA",
                                "Perro",
                                "\"fechaNacimiento\":\"2026-02-30\"")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"codigo\":\"ERROR_VALIDACION\"")))
                .andExpect(content().string(containsString(
                        "fechaNacimiento: La fecha de nacimiento no tiene un formato valido o no existe")));

        verifyNoInteractions(mascotaService, usuarioAutenticadoService);
    }

    @Test
    void aceptaLimitesExactosYVariantesValidasDelCatalogo() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        MascotaResponseDTO respuesta = new MascotaResponseDTO(
                UUID.randomUUID(),
                usuarioId,
                "Mascota QA",
                "Perro",
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                Instant.now());
        when(usuarioAutenticadoService.obtenerIdUsuario(any())).thenReturn(usuarioId);
        when(mascotaService.crear(any(MascotaRequestDTO.class), eq(usuarioId))).thenReturn(respuesta);

        List<String> variantes = new ArrayList<>();
        variantes.add(cuerpo(
                "N".repeat(100),
                "E".repeat(50),
                "\"raza\":\"" + "R".repeat(100) + "\",\"notas\":\"" + "O".repeat(255) + "\""));
        variantes.add(cuerpo("Mascota QA", "Perro", ""));
        variantes.add(cuerpo("Mascota QA", "Perro", "\"fechaNacimiento\":\"2020-02-29\""));
        variantes.add(cuerpoAproximada("Mascota QA", "Perro", "\"edadValor\":0,\"edadUnidad\":\"anios\""));
        for (String unidad : List.of("dias", "semanas", "meses", "anios")) {
            variantes.add(cuerpoAproximada(
                    "Mascota QA",
                    "Perro",
                    "\"edadValor\":1,\"edadUnidad\":\"" + unidad + "\""));
        }
        variantes.add(cuerpo("Mascota QA", "Perro", "\"pesoActual\":0.01"));
        variantes.add(cuerpo("Mascota QA", "Perro", "\"pesoActual\":9999.99"));

        for (String variante : variantes) {
            mockMvc.perform(post("/mascotas")
                            .principal(() -> "usuario.qa@mismascotas.test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(variante))
                    .andExpect(status().isCreated());
        }
    }

    private String cuerpo(String nombre, String especie, String camposOpcionales) {
        return cuerpo(nombre, especie, false, camposOpcionales);
    }

    private String cuerpoAproximada(String nombre, String especie, String camposOpcionales) {
        return cuerpo(nombre, especie, true, camposOpcionales);
    }

    private String cuerpo(String nombre, String especie, boolean fechaAproximada, String camposOpcionales) {
        String separador = camposOpcionales.isBlank() ? "" : ",";
        return """
                {
                  "nombre": "%s",
                  "especie": "%s",
                  "fechaAproximada": %s%s%s
                }
                """.formatted(nombre, especie, fechaAproximada, separador, camposOpcionales);
    }

    private record VarianteInvalida(String cuerpo, String campo) {}
}
