package com.soundia.auth;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Pone en marcha la autenticacion solo si soundia.auth.enabled=true. Con ella apagada no se crea ningun bean
 * (el API funciona como siempre, sin login).
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    @Bean
    @ConditionalOnProperty(name = "soundia.auth.enabled", havingValue = "true")
    public AuthService authService(AuthProperties properties, Clock clock) {
        return new AuthService(properties, clock);
    }

    /**
     * El filtro de autenticacion responde 401 antes de que Spring MVC aplique CORS, y sin cabeceras de CORS el
     * navegador mostraria un error de red en lugar de un 401. Este filtro va primero y con los mismos origenes
     * que WebConfig: aplica CORS tambien a esas respuestas (Spring MVC no lo repite si ya estan las cabeceras).
     */
    @Bean
    @ConditionalOnProperty(name = "soundia.auth.enabled", havingValue = "true")
    public FilterRegistrationBean<CorsFilter> authCorsFilter(
            @Value("${soundia.cors.allowed-origins:http://localhost:4200}") String[] allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(allowedOrigins).map(String::trim).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "soundia.auth.enabled", havingValue = "true")
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(AuthService authService) {
        FilterRegistrationBean<JwtAuthFilter> registration =
                new FilterRegistrationBean<>(new JwtAuthFilter(authService.tokens()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
