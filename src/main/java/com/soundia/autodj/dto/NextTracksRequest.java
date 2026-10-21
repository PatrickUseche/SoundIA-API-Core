package com.soundia.autodj.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NextTracksRequest(
        @NotNull(message = "trackId es obligatorio")
        @Positive(message = "trackId debe ser positivo")
        Long trackId,

        // Opcional: si no se envia, el motor usa 5.
        @Min(value = 1, message = "limit debe estar entre 1 y 20")
        @Max(value = 20, message = "limit debe estar entre 1 y 20")
        Integer limit
        ) {
}
