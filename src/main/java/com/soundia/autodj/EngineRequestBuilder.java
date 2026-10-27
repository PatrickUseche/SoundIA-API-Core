package com.soundia.autodj;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Patron Builder: arma el cuerpo JSON de una peticion al motor de IA campo a campo, y omite los opcionales que no
 * se indicaron. Conserva el orden en que se agregan los campos.
 */
public final class EngineRequestBuilder {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    private EngineRequestBuilder() {
    }

    public static EngineRequestBuilder create() {
        return new EngineRequestBuilder();
    }

    /** Agrega un campo obligatorio. */
    public EngineRequestBuilder with(String name, Object value) {
        fields.put(name, value);
        return this;
    }

    /** Agrega el campo solo si tiene valor; si es null el motor usa el suyo por defecto. */
    public EngineRequestBuilder withIfPresent(String name, Object value) {
        if (value != null) {
            fields.put(name, value);
        }
        return this;
    }

    /** El cuerpo ya armado; no se puede modificar. */
    public Map<String, Object> build() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
