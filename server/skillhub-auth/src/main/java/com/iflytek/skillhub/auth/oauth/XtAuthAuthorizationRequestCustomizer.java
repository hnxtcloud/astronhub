package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * Adds the PKCE (RFC 7636) S256 challenge that xt-auth (统一认证) requires from every
 * registered client.
 *
 * <p>Spring Security applies PKCE by default only to public registrations
 * ({@code client-authentication-method: none}), but xt-auth mandates it for confidential web
 * clients too, so {@code client_secret_basic} alone does not satisfy its authorize endpoint.
 * Spring's own PKCE applier stores the {@code code_verifier} as an authorization-request
 * attribute, and {@code OAuth2AuthorizationCodeGrantRequestEntityConverter} sends that attribute
 * back in the token exchange regardless of the client authentication method — delegating here
 * keeps the challenge and its verifier paired by construction.
 */
@Component
public class XtAuthAuthorizationRequestCustomizer implements ProviderAuthorizationRequestCustomizer {

    static final String REGISTRATION_ID = "xtauth";

    @Override
    public String getProvider() {
        return REGISTRATION_ID;
    }

    @Override
    public void customize(OAuth2AuthorizationRequest.Builder builder) {
        OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder);
    }
}
