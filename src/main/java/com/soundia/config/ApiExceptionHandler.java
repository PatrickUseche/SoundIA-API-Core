package com.soundia.config;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Manejo centralizado de errores. Al cliente solo se le devuelven mensajes genericos; el detalle
 * tecnico (URLs internas, trazas) queda unicamente en los logs del servidor.
 *
 * Extiende ResponseEntityExceptionHandler para que las excepciones propias de Spring MVC (ruta
 * inexistente, metodo no permitido, tipo de contenido no soportado, parametro mal formado...) conserven
 * su codigo HTTP correcto en lugar de caer en el 500 generico.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Errores del motor de IA que son culpa de la peticion del usuario y se le muestran tal cual
    // (archivo ilegible, demasiado grande, imposible de analizar, pista inexistente).
    private static final Set<Integer> PASSTHROUGH_STATUSES = Set.of(400, 404, 413, 422);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse("Validacion fallida", detalles));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Cuerpo de peticion ilegible: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("El cuerpo de la peticion no es un JSON valido"));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("Falta el campo '" + e.getRequestPartName() + "' en la peticion"));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of("El archivo supera el tamano maximo permitido"));
    }

    /** Resto de excepciones estandar de Spring MVC: se conserva su codigo con un cuerpo uniforme. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.of(messageFor(status)));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Peticion invalida";
            case 404 -> "Recurso no encontrado";
            case 413 -> "El archivo supera el tamano maximo permitido";
            case 405 -> "Metodo no permitido";
            case 406 -> "Formato de respuesta no soportado";
            case 415 -> "Tipo de contenido no soportado";
            default -> status.is4xxClientError() ? "Peticion invalida" : "Error interno del servidor";
        };
    }

    // Estos mensajes los escribe la propia aplicacion para el usuario, por eso se devuelven tal cual.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleIaEngineClientError(HttpClientErrorException e) {
        if (!PASSTHROUGH_STATUSES.contains(e.getStatusCode().value())) {
            // Un 401/403/429... de Django indica un problema entre servicios, no del usuario.
            return handleIaEngineFailure(e);
        }
        return ResponseEntity.status(e.getStatusCode())
                .body(ErrorResponse.of(upstreamMessage(e.getResponseBodyAsString())));
    }

    /** Extrae el campo "error" del JSON de Django; si no hay, un mensaje generico (nunca el cuerpo crudo). */
    private static String upstreamMessage(String body) {
        try {
            JsonNode error = JSON.readTree(body).path("error");
            if (error.isString() && !error.asString().isBlank()) {
                return error.asString();
            }
        } catch (RuntimeException ignored) {
            // el cuerpo no es JSON (p. ej. una pagina HTML de error)
        }
        return "El motor de IA rechazo la peticion";
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleIaEngineFailure(RestClientException e) {
        log.error("Fallo al comunicarse con el motor de IA", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("El motor de IA no esta disponible"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Error inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Error interno del servidor"));
    }
}
