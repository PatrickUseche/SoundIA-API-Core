package com.soundia.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class TokenServiceTests {

    private static final String SECRET = "una-clave-de-prueba-de-al-menos-32-caracteres!";

    private MutableClock clock;
    private TokenService tokens;

    @BeforeEach
    void crear() {
        clock = new MutableClock();
        tokens = new TokenService(SECRET, clock);
    }

    private String emitir(String scope, Duration ttl) {
        return tokens.issue("dj", scope, ttl).value();
    }

    @Test
    void unTokenRecienEmitidoEsValidoYDevuelveElUsuario() {
        String token = emitir(TokenService.SCOPE_ACCESS, Duration.ofHours(1));

        assertThat(tokens.validate(token, TokenService.SCOPE_ACCESS)).contains("dj");
    }

    @Test
    void devuelveCuandoCaduca() {
        TokenService.IssuedToken emitido = tokens.issue("dj", TokenService.SCOPE_ACCESS, Duration.ofMinutes(30));

        assertThat(emitido.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(30)));
    }

    @Test
    void caducaCuandoPasaSuVida() {
        String token = emitir(TokenService.SCOPE_ACCESS, Duration.ofMinutes(30));

        clock.advance(Duration.ofMinutes(29));
        assertThat(tokens.validate(token, TokenService.SCOPE_ACCESS)).isPresent();

        clock.advance(Duration.ofMinutes(1));
        assertThat(tokens.validate(token, TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void unTokenDeAccesoNoValeComoTicketDelStreamNiAlReves() {
        String acceso = emitir(TokenService.SCOPE_ACCESS, Duration.ofHours(1));
        String ticket = emitir(TokenService.SCOPE_STREAM, Duration.ofSeconds(30));

        assertThat(tokens.validate(acceso, TokenService.SCOPE_STREAM)).isEmpty();
        assertThat(tokens.validate(ticket, TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        String ajeno = new TokenService("otra-clave-distinta-de-al-menos-32-caracteres", clock)
                .issue("dj", TokenService.SCOPE_ACCESS, Duration.ofHours(1)).value();

        assertThat(tokens.validate(ajeno, TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void rechazaUnTokenAlteradoDespuesDeFirmarlo() {
        String token = emitir(TokenService.SCOPE_ACCESS, Duration.ofHours(1));
        String[] partes = token.split("\\.");
        // Se cambia una letra del contenido: la firma ya no coincide.
        char cambio = partes[1].charAt(10) == 'A' ? 'B' : 'A';
        String alterado = partes[0] + "." + partes[1].substring(0, 10) + cambio + partes[1].substring(11) + "." + partes[2];

        assertThat(tokens.validate(alterado, TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void rechazaUnTokenSinFirmaConAlgoritmoNone() throws Exception {
        PlainJWT sinFirma = new PlainJWT(claims("dj", TokenService.SCOPE_ACCESS, "soundia", 3600).build());

        assertThat(tokens.validate(sinFirma.serialize(), TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void soloAceptaHs256AunqueLaFirmaSeaCorrecta() throws Exception {
        // HS512 exige una clave de al menos 64 bytes; con ella la firma es correcta, pero el algoritmo no es el esperado.
        String claveLarga = "una-clave-larga-de-prueba-de-mas-de-sesenta-y-cuatro-caracteres-para-hs512-ok";
        TokenService conClaveLarga = new TokenService(claveLarga, clock);
        SignedJWT hs512 = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512),
                claims("dj", TokenService.SCOPE_ACCESS, "soundia", 3600).build());
        hs512.sign(new MACSigner(claveLarga.getBytes(StandardCharsets.UTF_8)));

        assertThat(conClaveLarga.validate(hs512.serialize(), TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void rechazaUnTokenDeOtroEmisor() throws Exception {
        SignedJWT ajeno = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                claims("dj", TokenService.SCOPE_ACCESS, "otro-servicio", 3600).build());
        ajeno.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

        assertThat(tokens.validate(ajeno.serialize(), TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void rechazaUnTokenSinCaducidad() throws Exception {
        JWTClaimsSet sinCaducidad = new JWTClaimsSet.Builder()
                .issuer("soundia").subject("dj").claim("scope", TokenService.SCOPE_ACCESS).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), sinCaducidad);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

        assertThat(tokens.validate(jwt.serialize(), TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void rechazaTextoQueNoEsUnToken() {
        assertThat(tokens.validate(null, TokenService.SCOPE_ACCESS)).isEmpty();
        assertThat(tokens.validate("", TokenService.SCOPE_ACCESS)).isEmpty();
        assertThat(tokens.validate("   ", TokenService.SCOPE_ACCESS)).isEmpty();
        assertThat(tokens.validate("no-es-un-token", TokenService.SCOPE_ACCESS)).isEmpty();
        assertThat(tokens.validate("a.b.c", TokenService.SCOPE_ACCESS)).isEmpty();
    }

    @Test
    void cadaTokenLlevaUnIdentificadorDistinto() throws Exception {
        SignedJWT a = SignedJWT.parse(emitir(TokenService.SCOPE_ACCESS, Duration.ofHours(1)));
        SignedJWT b = SignedJWT.parse(emitir(TokenService.SCOPE_ACCESS, Duration.ofHours(1)));

        assertThat(a.getJWTClaimsSet().getJWTID()).isNotEqualTo(b.getJWTClaimsSet().getJWTID());
    }

    private JWTClaimsSet.Builder claims(String subject, String scope, String issuer, long ttlSeconds) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer).subject(subject).claim("scope", scope)
                .expirationTime(Date.from(clock.instant().plusSeconds(ttlSeconds)));
    }
}
