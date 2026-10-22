package com.soundia.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartSessionRequest(
        @NotBlank(message = "name es obligatorio")
        @Size(max = 100, message = "name no puede superar los 100 caracteres")
        String name
        ) {
}
