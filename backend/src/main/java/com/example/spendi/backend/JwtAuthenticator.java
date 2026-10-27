package com.example.spendi.backend;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.util.Set;

/** RS256 access tokens only. Keys come from the configured provider, never a token's jku URL. */
public final class JwtAuthenticator implements Authenticator {
    private final DefaultJWTProcessor<SecurityContext> processor;

    public JwtAuthenticator(String issuer, String audience, JWKSource<SecurityContext> keys) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) throw new IllegalArgumentException("Issuer and audience are required");
        processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        var verifier = new DefaultJWTClaimsVerifier<SecurityContext>(Set.of(audience),
                new JWTClaimsSet.Builder().issuer(issuer).build(), Set.of("sub", "iss", "aud", "exp"), Set.of());
        verifier.setMaxClockSkew(30);
        processor.setJWTClaimsSetVerifier(verifier);
    }

    public static JwtAuthenticator remote(String issuer, String audience, String jwksUri) {
        try {
            URI uri = URI.create(jwksUri);
            URI issuerUri = URI.create(issuer);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || !"https".equals(issuerUri.getScheme()) || issuerUri.getHost() == null) {
                throw new IllegalArgumentException("OIDC issuer and JWKS URI must use HTTPS");
            }
            var retriever = new DefaultResourceRetriever(2000, 2000, 100_000);
            return new JwtAuthenticator(issuer, audience, new RemoteJWKSet<>(uri.toURL(), retriever));
        } catch (java.net.MalformedURLException exception) { throw new IllegalArgumentException("Invalid JWKS URI"); }
    }

    @Override public String subject(String token) {
        try {
            if (token == null || token.length() > 8192) throw Authenticator.unauthorized();
            String subject = processor.process(token, null).getSubject();
            if (subject == null || subject.isBlank() || subject.length() > 255) throw Authenticator.unauthorized();
            return subject;
        } catch (Exception exception) { throw Authenticator.unauthorized(); }
    }
}
