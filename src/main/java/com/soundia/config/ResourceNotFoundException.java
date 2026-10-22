package com.soundia.config;

/** No existe el recurso pedido; el API responde 404 con este mensaje. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
