package com.soundia.config;

import java.util.List;

/** Cuerpo JSON uniforme de todos los errores del API. */
public record ErrorResponse(String error, List<String> detalles) {

    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, List.of());
    }
}
