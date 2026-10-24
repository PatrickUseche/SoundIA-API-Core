package com.soundia.config;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.time.Duration;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Reintenta las consultas al motor de IA que fallan porque este se esta despertando o reiniciando.
 *
 * En la nube gratuita el motor se duerme tras unos minutos sin trafico y tarda cerca de un minuto en despertar, y se
 * reinicia en cada despliegue; mientras tanto el proxy responde 502, 503 o 504 o corta la conexion. Solo se reintentan
 * los metodos idempotentes (GET, PUT, DELETE...): repetirlos no cambia el resultado. Un POST no se reintenta aqui,
 * porque podria duplicar algo (la subida de una pista tiene su propio reintento en `TrackClient`).
 *
 * Debe ser el ultimo interceptor de la cadena: cada reintento vuelve a ejecutar la peticion real.
 */
public class TransientRetryInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TransientRetryInterceptor.class);

    static final Set<HttpMethod> IDEMPOTENT = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.PUT, HttpMethod.DELETE);
    static final Set<Integer> TRANSIENT_STATUSES = Set.of(502, 503, 504);

    private final int maxAttempts;
    private final Duration delay;

    /** @param delay espera tras el primer fallo; crece en cada intento (delay, 2 x delay, 3 x delay...). */
    public TransientRetryInterceptor(int maxAttempts, Duration delay) {
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!IDEMPOTENT.contains(request.getMethod())) {
            return execution.execute(request, body);
        }
        for (int attempt = 1;; attempt++) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                if (attempt >= maxAttempts || !TRANSIENT_STATUSES.contains(response.getStatusCode().value())) {
                    return response;
                }
                log.warn("El motor de IA respondio {} a {} {} (intento {} de {}). Se reintenta.",
                        response.getStatusCode().value(), request.getMethod(), request.getURI().getPath(),
                        attempt, maxAttempts);
                response.close();
            } catch (IOException e) {
                // Conexion rechazada o cortada; un timeout (el motor tardo demasiado) no se reintenta.
                if (attempt >= maxAttempts || !(e instanceof ConnectException || e instanceof SocketException)) {
                    throw e;
                }
                log.warn("No se pudo hablar con el motor de IA en {} {} (intento {} de {}): {}. Se reintenta.",
                        request.getMethod(), request.getURI().getPath(), attempt, maxAttempts, e.getMessage());
            }
            pause(delay.multipliedBy(attempt));
        }
    }

    private static void pause(Duration time) throws IOException {
        try {
            Thread.sleep(time.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido mientras se esperaba al motor de IA", interrupted);
        }
    }
}
