package com.example.spendi.backend

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.*
import spock.lang.Shared
import spock.lang.Specification

class AuthSpec extends Specification {
    @Shared def key = new RSAKeyGenerator(2048).keyID('test-key').generate()
    @Shared def authenticator = new JwtAuthenticator('https://issuer.example', 'spendi-api', new ImmutableJWKSet(new JWKSet(key.toPublicJWK())))

    String token(Map claims = [:], JWSAlgorithm algorithm = JWSAlgorithm.RS256) {
        def builder = new JWTClaimsSet.Builder().issuer(claims.get('iss', 'https://issuer.example'))
                .audience(claims.get('aud', 'spendi-api')).subject(claims.get('sub', 'alice'))
        if (claims.get('exp', true) != false) builder.expirationTime(new Date(System.currentTimeMillis() + (claims.get('ttl', 120) as long) * 1000))
        if (claims.nbf) builder.notBeforeTime(new Date(System.currentTimeMillis() + 3600000))
        def jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID('test-key').build(), builder.build())
        jwt.sign(new RSASSASigner(key))
        jwt.serialize()
    }

    def 'verified access token supplies the owner subject'() {
        expect:
        authenticator.subject(token()) == 'alice'
    }

    def 'invalid claims never authorize a request'() {
        when:
        authenticator.subject(token(claims))
        then:
        def error = thrown(ApiException)
        error.status == 401
        where:
        claims << [[iss: 'https://attacker.example'], [aud: 'different-api'], [sub: ''], [sub: 'x' * 256],
                   [ttl: -120], [exp: false], [nbf: true]]
    }

    def 'unsigned tokens and unsupported algorithms are rejected'() {
        when:
        authenticator.subject(supplied)
        then:
        thrown(ApiException)
        where:
        supplied << [null, 'malformed', new PlainJWT(new JWTClaimsSet.Builder().subject('alice').build()).serialize(), 'x' * 8193]
    }

    def 'RSA signature using an unexpected algorithm is rejected'() {
        when:
        authenticator.subject(token([:], JWSAlgorithm.RS512))
        then:
        thrown(ApiException)
    }

    def 'development credentials cannot bind to a public address'() {
        when:
        Configuration.from([SPENDI_HOST: '0.0.0.0', SPENDI_DEV_AUTH: 'true', SPENDI_DEV_TOKEN: 'x' * 64,
                            SPENDI_DB_URL: 'jdbc:postgresql://localhost/spendi', SPENDI_DB_USER: 'test', SPENDI_DB_PASSWORD: 'test'])
        then:
        thrown(IllegalArgumentException)
    }

    def 'missing production identity configuration fails closed'() {
        when:
        Configuration.from([:])
        then:
        thrown(IllegalArgumentException)
    }
}
