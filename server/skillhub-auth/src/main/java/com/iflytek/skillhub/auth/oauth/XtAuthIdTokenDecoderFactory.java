package com.iflytek.skillhub.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/** Spring Security 6.2 has no EdDSA option for its OIDC decoder factory. */
@Component
public class XtAuthIdTokenDecoderFactory implements JwtDecoderFactory<ClientRegistration> {

    private final String issuer;
    private final OidcIdTokenDecoderFactory defaults = new OidcIdTokenDecoderFactory();
    private final ConcurrentMap<ClientRegistration, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public XtAuthIdTokenDecoderFactory(@Value("${OAUTH2_XTAUTH_BASE_URI:https://auth.example.com/api/auth}") String issuer) {
        this.issuer = issuer;
    }

    @Override
    public JwtDecoder createDecoder(ClientRegistration registration) {
        if (!XtAuthAuthorizationRequestCustomizer.REGISTRATION_ID.equals(registration.getRegistrationId())) {
            return defaults.createDecoder(registration);
        }
        return decoders.computeIfAbsent(registration, this::buildDecoder);
    }

    private JwtDecoder buildDecoder(ClientRegistration registration) {
        var verifiedRegistration = ClientRegistration.withClientRegistration(registration).issuerUri(issuer).build();
        var keys = remoteKeys(verifiedRegistration.getProviderDetails().getJwkSetUri());
        var processor = new DefaultJWTProcessor<SecurityContext>() {
            @Override
            public JWTClaimsSet process(SignedJWT jwt, SecurityContext context) throws BadJOSEException, JOSEException {
                var header = jwt.getHeader();
                if (!JWSAlgorithm.EdDSA.equals(header.getAlgorithm())) {
                    return super.process(jwt, context);
                }
                getJWSTypeVerifier().verify(header.getType(), context);
                var keyId = header.getKeyID();
                if (keyId == null || keyId.isBlank()) {
                    throw new BadJOSEException("xt-auth ID token is missing its signing key identifier");
                }
                var selector = new JWKSelector(new JWKMatcher.Builder()
                        .keyType(KeyType.OKP).curve(Curve.Ed25519).keyID(keyId).publicOnly(true)
                        .keyUses(KeyUse.SIGNATURE, null).algorithms(JWSAlgorithm.EdDSA, null).build());
                for (var key : keys.get(selector, context)) {
                    if (jwt.verify(new Ed25519Verifier((OctetKeyPair) key))) {
                        try {
                            return jwt.getJWTClaimsSet();
                        } catch (ParseException error) {
                            throw new BadJOSEException("Invalid xt-auth ID token claims", error);
                        }
                    }
                }
                throw new BadJOSEException("xt-auth ID token signature could not be verified");
            }
        };
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));

        var decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new OidcIdTokenValidator(verifiedRegistration)));
        return decoder;
    }

    private static RemoteJWKSet<SecurityContext> remoteKeys(String jwksUri) {
        try {
            return new RemoteJWKSet<>(URI.create(jwksUri).toURL(), new DefaultResourceRetriever(5_000, 5_000, 65_536));
        } catch (MalformedURLException error) {
            throw new IllegalArgumentException("Invalid xt-auth JWKS URI", error);
        }
    }
}
