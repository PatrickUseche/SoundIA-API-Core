package com.soundia.auth;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Emite y valida los tokens (JWT firmados con HS256). Hay dos clases, distinguidas por el claim "scope":
 * el token de acceso (para toda la API) y el ticket del stream (solo para abrir GET /api/v1/midi/stream, dura
 * unos segundos y asi el token de acceso nunca viaja en una URL).
 */
public class TokenService {

    public static final String SCOPE_ACCESS = "access";
    public static final String SCOPE_STREAM = "stream";

    private static final String ISSUER = "soundia";
    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;

    private final byte[] secret;
    private final Clock clock;

    public TokenService(String jwtSecret, Clock clock) {
        this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** Token firmado de la clase indicada para `subject`, con la vida indicada. */
    public IssuedToken issue(String subject, String scope, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .claim("scope", scope)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(ALGORITHM), claims);
            jwt.sign(new MACSigner(secret));
            return new IssuedToken(jwt.serialize(), expiresAt);
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    /**
     * El sujeto del token si es valido: firma correcta con HS256 (se rechaza cualquier otro algoritmo, incluido
     * "none"), emisor propio, no caducado y de la clase pedida. Si no, vacio (sin decir por que).
     */
    public Optional<String> validate(String token, String expectedScope) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!ALGORITHM.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            if (!ISSUER.equals(claims.getIssuer()) || !expectedScope.equals(claims.getStringClaim("scope"))) {
                return Optional.empty();
            }
            return Optional.ofNullable(claims.getSubject());
        } catch (ParseException | JOSEException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
