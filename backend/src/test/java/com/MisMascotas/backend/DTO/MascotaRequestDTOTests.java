package com.MisMascotas.backend.DTO;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MascotaRequestDTOTests {
    final JsonMapper json = JsonMapper.builder().build();

    @Test void distingueAusenciaNullYUrlSinExponerElIndicadorInterno() {
        var ausente = leer("{}");
        var retirada = leer("{\"fotoPerfil\":null}");
        var nueva = leer("{\"fotoPerfil\":\"https://example.test/foto.jpg\"}");
        assertThat(ausente.fotoPerfilInformada()).isFalse();
        assertThat(ausente.fotoPerfil()).isNull();
        assertThat(retirada.fotoPerfilInformada()).isTrue();
        assertThat(retirada.fotoPerfil()).isNull();
        assertThat(nueva.fotoPerfilInformada()).isTrue();
        assertThat(nueva.fotoPerfil()).isEqualTo("https://example.test/foto.jpg");
        assertThat(json.writeValueAsString(nueva)).doesNotContain("fotoPerfilInformada");
    }

    @Test void elClienteNoPuedeInventarLaPresenciaNiOcultarUnaUrl() {
        assertThat(leer("{\"fotoPerfilInformada\":true}").fotoPerfilInformada()).isFalse();
        assertThat(leer("{\"fotoPerfil\":null,\"fotoPerfilInformada\":false}").fotoPerfilInformada()).isTrue();
        assertThat(leer("{\"fotoPerfil\":\"https://example.test/f.jpg\",\"fotoPerfilInformada\":false}").fotoPerfilInformada()).isTrue();
    }

    private MascotaRequestDTO leer(String cuerpo) {
        var nodo = (tools.jackson.databind.node.ObjectNode) json.readTree(cuerpo);
        nodo.put("fechaAproximada", false);
        return json.treeToValue(nodo, MascotaRequestDTO.class);
    }
}
