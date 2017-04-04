package com.auth0.spring.security.api;

import com.auth0.jwk.*;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.InvalidClaimException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.spring.security.api.authentication.AuthenticationJsonWebToken;
import com.auth0.spring.security.api.authentication.PreAuthenticatedAuthenticationJsonWebToken;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAKey;
import java.util.Collections;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JwtAuthenticationProviderTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldCreateUsingWithSecret() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider("secret".getBytes(), "issuer", "audience");

        assertThat(provider, is(notNullValue()));
    }

    @Test
    public void shouldCreateUsingJWKProvider() throws Exception {
        JwkProvider jwkProvider = mock(JwkProvider.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");

        assertThat(provider, is(notNullValue()));
    }

    @Test
    public void shouldSupportJwkAuthentication() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider("secret".getBytes(), "issuer", "audience");

        assertThat(provider.supports(AuthenticationJsonWebToken.class), is(true));
    }

    // HS

    @Test
    public void shouldFailToAuthenticateUsingInvalidSecret() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider("secret".getBytes(), "issuer", "audience");
        String token = JWT.create()
                .withIssuer("issuer")
                .withAudience("audience")
                .sign(Algorithm.HMAC256("not-real-secret"));
        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(SignatureVerificationException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingSecretIfMissingAudienceClaim() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider("secret".getBytes(), "issuer", "audience");
        String token = JWT.create()
                .withIssuer("issuer")
                .sign(Algorithm.HMAC256("secret"));
        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidClaimException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingSecretIfMissingIssuerClaim() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider("secret".getBytes(), "issuer", "audience");
        String token = JWT.create()
                .withAudience("audience")
                .sign(Algorithm.HMAC256("secret"));
        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidClaimException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingSecretIfIssuerClaimDoesNotMatch() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider("secret".getBytes(), "issuer", "audience");
        String token = JWT.create()
                .withAudience("audience")
                .withIssuer("some")
                .sign(Algorithm.HMAC256("secret"));
        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidClaimException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingSecretIfAudienceClaimDoesNotMatch() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider("secret".getBytes(), "issuer", "audience");
        String token = JWT.create()
                .withAudience("some")
                .withIssuer("issuer")
                .sign(Algorithm.HMAC256("secret"));
        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidClaimException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldAuthenticateUsingSecret() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider("secret".getBytes(), "issuer", "audience");
        String token = JWT.create()
                .withAudience("audience")
                .withIssuer("issuer")
                .sign(Algorithm.HMAC256("secret"));
        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        Authentication result = provider.authenticate(authentication);

        assertThat(result, is(notNullValue()));
        assertThat(result, is(not(equalTo(authentication))));
    }


    //RS


    @Test
    public void shouldFailToAuthenticateUsingInvalidJWK() throws Exception {
        Jwk jwk = mock(Jwk.class);
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair1 = RSAKeyPair();
        KeyPair keyPair2 = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenReturn(jwk);
        when(jwk.getPublicKey()).thenReturn(keyPair1.getPublic());
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("audience")
                .withIssuer("issuer")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair2.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(SignatureVerificationException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingJWKIfMissingAudienceClaim() throws Exception {
        Jwk jwk = mock(Jwk.class);
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenReturn(jwk);
        when(jwk.getPublicKey()).thenReturn(keyPair.getPublic());
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withIssuer("issuer")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidClaimException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingJWKIfMissingIssuerClaim() throws Exception {
        Jwk jwk = mock(Jwk.class);
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenReturn(jwk);
        when(jwk.getPublicKey()).thenReturn(keyPair.getPublic());
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("audience")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidClaimException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingJWKIfMissingKeyIdClaim() throws Exception {
        Jwk jwk = mock(Jwk.class);
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenReturn(jwk);
        when(jwk.getPublicKey()).thenReturn(keyPair.getPublic());
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        String token = JWT.create()
                .withAudience("some")
                .withIssuer("issuer")
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("No kid found in jwt");
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingJWKIfIssuerClaimDoesNotMatch() throws Exception {
        Jwk jwk = mock(Jwk.class);
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenReturn(jwk);
        when(jwk.getPublicKey()).thenReturn(keyPair.getPublic());
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("audience")
                .withIssuer("some")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidClaimException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldFailToAuthenticateUsingJWKIfAudienceClaimDoesNotMatch() throws Exception {
        Jwk jwk = mock(Jwk.class);
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenReturn(jwk);
        when(jwk.getPublicKey()).thenReturn(keyPair.getPublic());
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("some")
                .withIssuer("issuer")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(BadCredentialsException.class);
        exception.expectMessage("Not a valid token");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidClaimException.class));
        provider.authenticate(authentication);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldFailToAuthenticateUsingJWKIfMissingProvider() throws Exception {
        Jwk jwk = mock(Jwk.class);

        JwkProvider jwkProvider = null;
        KeyPair keyPair = RSAKeyPair();
        when(jwk.getPublicKey()).thenReturn(keyPair.getPublic());
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("audience")
                .withIssuer("issuer")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(AuthenticationServiceException.class);
        exception.expectMessage("Missing jwk provider");
        provider.authenticate(authentication);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldFailToAuthenticateUsingJWKIfKeyIdDoesNotMatch() throws Exception {
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenThrow(SigningKeyNotFoundException.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("some")
                .withIssuer("issuer")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(AuthenticationServiceException.class);
        exception.expectMessage("Could not retrieve jwks from issuer");
        exception.expectCause(Matchers.<Throwable>instanceOf(SigningKeyNotFoundException.class));
        provider.authenticate(authentication);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldFailToAuthenticateUsingJWKIfPublicKeyIsInvalid() throws Exception {
        Jwk jwk = mock(Jwk.class);
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenReturn(jwk);
        when(jwk.getPublicKey()).thenThrow(InvalidPublicKeyException.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("some")
                .withIssuer("issuer")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(AuthenticationServiceException.class);
        exception.expectMessage("Could not retrieve public key from issuer");
        exception.expectCause(Matchers.<Throwable>instanceOf(InvalidPublicKeyException.class));
        provider.authenticate(authentication);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldFailToAuthenticateUsingJWKIfKeyIdCannotBeObtained() throws Exception {
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenThrow(JwkException.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("some")
                .withIssuer("issuer")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        exception.expect(AuthenticationServiceException.class);
        exception.expectMessage("Cannot authenticate with jwt");
        exception.expectCause(Matchers.<Throwable>instanceOf(JwkException.class));
        provider.authenticate(authentication);
    }

    @Test
    public void shouldAuthenticateUsingJWK() throws Exception {
        Jwk jwk = mock(Jwk.class);
        JwkProvider jwkProvider = mock(JwkProvider.class);

        KeyPair keyPair = RSAKeyPair();
        when(jwkProvider.get(eq("key-id"))).thenReturn(jwk);
        when(jwk.getPublicKey()).thenReturn(keyPair.getPublic());
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwkProvider, "issuer", "audience");
        Map<String, Object> keyIdHeader = Collections.singletonMap("kid", (Object) "key-id");
        String token = JWT.create()
                .withAudience("audience")
                .withIssuer("issuer")
                .withHeader(keyIdHeader)
                .sign(Algorithm.RSA256((RSAKey) keyPair.getPrivate()));

        Authentication authentication = PreAuthenticatedAuthenticationJsonWebToken.usingToken(token);

        Authentication result = provider.authenticate(authentication);

        assertThat(result, is(notNullValue()));
        assertThat(result, is(not(equalTo(authentication))));
    }

    private KeyPair RSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.genKeyPair();
    }
}