package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.RequestEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.util.MultiValueMap;

/**
 * Protocol guard for the xt-auth (统一认证) login. xt-auth rejects confidential clients without
 * PKCE, so the guard proves both halves of the exchange on the real Spring Security version: the
 * authorize request must carry an S256 challenge, and the token request must answer it with the
 * matching verifier.
 */
class XtAuthAuthorizationRequestCustomizerTest {

    @Test
    void authorizeRequestCarriesTheS256ChallengeXtAuthRequires() {
        var authorizationRequest = resolveXtAuthAuthorizationRequest();

        assertThat(authorizationRequest.getAuthorizationRequestUri()).contains("code_challenge=");
        assertThat(authorizationRequest.getAuthorizationRequestUri()).contains("code_challenge_method=S256");

        // The registration declares openid, so this is a genuine OIDC flow and the nonce xt-auth
        // answers inside the id_token must be present.
        assertThat(authorizationRequest.getAttributes()).containsKey("nonce");

        String verifier = authorizationRequest.getAttribute(PkceParameterNames.CODE_VERIFIER);
        assertThat(verifier).isNotBlank();
        // RFC 7636 bounds the verifier between 43 and 128 unreserved characters.
        assertThat(verifier.length()).isBetween(43, 128);
    }

    @Test
    void tokenExchangeAnswersTheChallengeWithTheVerifier() {
        OAuth2AuthorizationRequest authorizationRequest = resolveXtAuthAuthorizationRequest();
        OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success("auth-code")
                .redirectUri(authorizationRequest.getRedirectUri())
                .state(authorizationRequest.getState())
                .build();
        var grantRequest = new OAuth2AuthorizationCodeGrantRequest(
                xtAuthRegistration(),
                new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse));

        // The standard token client (what DispatchingTokenResponseClient falls back to for
        // xt-auth) builds its request through this exact converter, so asserting on it proves
        // the wire behaviour without standing up an HTTP stack.
        RequestEntity<?> requestEntity =
                new OAuth2AuthorizationCodeGrantRequestEntityConverter().convert(grantRequest);

        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> formParameters = (MultiValueMap<String, String>) requestEntity.getBody();
        assertThat(formParameters).isNotNull();
        String verifier = authorizationRequest.getAttribute(PkceParameterNames.CODE_VERIFIER);
        assertThat(formParameters.getFirst(PkceParameterNames.CODE_VERIFIER)).isEqualTo(verifier);
        // The confidential client keeps its basic authentication alongside the verifier.
        assertThat(formParameters).doesNotContainKey("client_secret");
    }

    @Test
    void providersWithoutTheCustomizerStayPkceFree() {
        // The challenge must come from this bean, not accidentally from the framework: the
        // resolver applies PKCE by default only to client-authentication-method none.
        var resolver = resolverFor(githubRegistration(), new XtAuthAuthorizationRequestCustomizer());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");

        var authorizationRequest = resolver.resolve(request, "github");

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAuthorizationRequestUri()).doesNotContain("code_challenge");
    }

    private static OAuth2AuthorizationRequest resolveXtAuthAuthorizationRequest() {
        var resolver = resolverFor(xtAuthRegistration(), new XtAuthAuthorizationRequestCustomizer());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/xtauth");
        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request, "xtauth");
        assertThat(authorizationRequest).isNotNull();
        return authorizationRequest;
    }

    private static SkillHubOAuth2AuthorizationRequestResolver resolverFor(
            ClientRegistration registration,
            ProviderAuthorizationRequestCustomizer customizer
    ) {
        OAuthLoginFlowService flowService = new OAuthLoginFlowService(
                java.util.List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        return new SkillHubOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(registration),
                flowService,
                java.util.List.of(customizer)
        );
    }

    /** Mirrors the xtauth registration in application.yml: confidential client, OIDC scopes. */
    private static ClientRegistration xtAuthRegistration() {
        return ClientRegistration.withRegistrationId(XtAuthAuthorizationRequestCustomizer.REGISTRATION_ID)
                .clientId("xtauth-client")
                .clientSecret("secret")
                .authorizationUri("https://auth.example.com/api/auth/oauth2/authorize")
                .tokenUri("https://auth.example.com/api/auth/oauth2/token")
                .userInfoUri("https://auth.example.com/api/auth/oauth2/userinfo")
                .jwkSetUri("https://auth.example.com/api/auth/jwks")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userNameAttributeName("sub")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .scope("openid", "profile", "email")
                .clientName("统一认证")
                .build();
    }

    private static ClientRegistration githubRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId("client")
                .clientSecret("secret")
                .authorizationUri("https://example.test/oauth/authorize")
                .tokenUri("https://example.test/oauth/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://example.test/user")
                .userNameAttributeName("id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("read:user")
                .clientName("GitHub")
                .build();
    }
}
