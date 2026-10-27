package com.soundia.autodj.dto;

import jakarta.validation.constraints.NotNull;

/** Enciende o apaga el modelo del estilo del DJ (apagado, el motor usa solo sus reglas). */
public record StyleModelRequest(
        @NotNull(message = "enabled es obligatorio")
        Boolean enabled
        ) {
}
