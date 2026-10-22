package com.soundia.config;

/** La peticion es invalida por una razon que el usuario puede corregir; el API responde 400 con este mensaje. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
