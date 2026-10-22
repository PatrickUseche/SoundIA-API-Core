package com.soundia.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige un token valido en todas las rutas /api/** salvo el login y el estado. El token de acceso va en la
 * cabecera "Authorization: Bearer ...". La unica excepcion es el stream en vivo, que el navegador abre con
 * EventSource (no puede enviar cabeceras): ahi se acepta un ticket de vida corta en ?ticket=..., y solo ahi.
 * Las peticiones OPTIONS (preflight de CORS) pasan sin token.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    static final String STREAM_PATH = "/api/v1/midi/stream";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String STATUS_PATH = "/api/v1/auth/status";
    private static final String BEARER = "Bearer ";

    private final TokenService tokens;

    public JwtAuthFilter(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        return LOGIN_PATH.equals(path) || STATUS_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isAuthorized(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"No autorizado\",\"detalles\":[]}");
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            return tokens.validate(header.substring(BEARER.length()).trim(), TokenService.SCOPE_ACCESS).isPresent();
        }
        if (HttpMethod.GET.matches(request.getMethod()) && STREAM_PATH.equals(request.getRequestURI())) {
            return tokens.validate(request.getParameter("ticket"), TokenService.SCOPE_STREAM).isPresent();
        }
        return false;
    }
}
