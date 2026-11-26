package com.MisMascotas.backend.DTO;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class IntervaloEnteroDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext contexto) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return contexto.reportInputMismatch(Integer.class, "intervaloValor debe ser un entero");
        }
        return parser.getIntValue();
    }
}
