package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtException;

class XtAuthIdTokenDecoderFactoryTest {

    @Test
    void validatesEd25519SignatureIssuerAndAudience() throws Exception {
        var key = new OctetKeyPairGenerator(Curve.Ed25519).keyID("xtauth-test-key").generate();
        var otherKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID(key.getKeyID()).generate();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var contents = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/api/auth/jwks", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, contents.length);
                exchange.getResponseBody().write(contents);
            }
        });
        server.start();
        try {
            var issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/auth";
            var registration = ClientRegistration.withRegistrationId("xtauth")
                    .clientId("astronhub-sso").clientSecret("fixture-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri(issuer + "/oauth2/authorize").tokenUri(issuer + "/oauth2/token")
                    .jwkSetUri(issuer + "/jwks").scope("openid").build();
            var decoder = new XtAuthIdTokenDecoderFactory(issuer).createDecoder(registration);

            assertThat(decoder.decode(token(key, issuer, "astronhub-sso")).getSubject()).isEqualTo("employee");
            assertThatThrownBy(() -> decoder.decode(token(otherKey, issuer, "astronhub-sso")))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decoder.decode(token(key, "https://other.example", "astronhub-sso")))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decoder.decode(token(key, issuer, "other-client")))
                    .isInstanceOf(JwtException.class);
        } finally {
            server.stop(0);
        }
    }

    private static String token(OctetKeyPair key, String issuer, String audience) throws Exception {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject("employee").issuer(issuer).audience(audience)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new Ed25519Signer(key));
        return jwt.serialize();
    }
}
