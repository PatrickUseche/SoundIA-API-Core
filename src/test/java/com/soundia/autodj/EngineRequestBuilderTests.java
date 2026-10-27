package com.soundia.autodj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EngineRequestBuilderTests {

    @Test
    void armaElCuerpoConLosCamposEnOrden() {
        Map<String, Object> body = EngineRequestBuilder.create().with("fromTrackId", 1L).with("toTrackId", 2L).build();

        assertEquals(Map.of("fromTrackId", 1L, "toTrackId", 2L), body);
        assertEquals(List.of("fromTrackId", "toTrackId"), List.copyOf(body.keySet()));
    }

    @Test
    void omiteElCampoOpcionalSiNoTieneValor() {
        Map<String, Object> body = EngineRequestBuilder.create().with("trackId", 7L).withIfPresent("limit", null).build();

        assertFalse(body.containsKey("limit"));
    }

    @Test
    void incluyeElCampoOpcionalSiTieneValor() {
        Map<String, Object> body = EngineRequestBuilder.create().with("trackId", 7L).withIfPresent("limit", 3).build();

        assertEquals(3, body.get("limit"));
    }

    @Test
    void elCuerpoNoSePuedeModificar() {
        Map<String, Object> body = EngineRequestBuilder.create().with("trackId", 7L).build();

        assertThrows(UnsupportedOperationException.class, () -> body.put("limit", 1));
    }
}
