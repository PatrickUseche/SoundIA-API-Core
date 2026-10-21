package com.soundia.telemetry.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DjSessionLogDto(
        @NotNull(message = "controlId es obligatorio")
        @Min(value = 0, message = "controlId no puede ser negativo")
        Integer controlId,

        // Rango del protocolo MIDI estandar.
        @NotNull(message = "value es obligatorio")
        @Min(value = 0, message = "value debe estar entre 0 y 127")
        @Max(value = 127, message = "value debe estar entre 0 y 127")
        Integer value
        ){}
