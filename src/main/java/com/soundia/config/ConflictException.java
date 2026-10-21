package com.soundia.config;

/** La operacion choca con el estado actual (por ejemplo, cerrar dos veces una sesion); el API responde 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
