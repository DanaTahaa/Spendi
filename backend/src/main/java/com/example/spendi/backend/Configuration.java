package com.example.spendi.backend;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

public record Configuration(String host, int port, String dbUrl, String dbUser, String dbPassword, Authenticator authenticator) {
    @Override public String toString() { return "Configuration[host=" + host + ", port=" + port + "]"; }
    public static Configuration from(Map<String, String> env) {
        String host = env.getOrDefault("SPENDI_HOST", "127.0.0.1");
        int port = Integer.parseInt(env.getOrDefault("SPENDI_PORT", "8080"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid SPENDI_PORT");
        Authenticator authenticator;
        if ("true".equals(env.get("SPENDI_DEV_AUTH"))) {
            try {
                if (!InetAddress.getByName(host).isLoopbackAddress()) throw new IllegalArgumentException("Development auth requires a loopback bind address");
            } catch (java.net.UnknownHostException exception) { throw new IllegalArgumentException("Invalid bind address"); }
            String token = required(env, "SPENDI_DEV_TOKEN");
            if (token.length() < 32) throw new IllegalArgumentException("SPENDI_DEV_TOKEN must contain at least 32 random characters");
            byte[] expected = token.getBytes(StandardCharsets.UTF_8);
            authenticator = supplied -> {
                if (supplied == null || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) throw Authenticator.unauthorized();
                return "local-demo";
            };
        } else {
            authenticator = JwtAuthenticator.remote(required(env, "SPENDI_OIDC_ISSUER"), required(env, "SPENDI_OIDC_AUDIENCE"), required(env, "SPENDI_JWKS_URI"));
        }
        String url = required(env, "SPENDI_DB_URL");
        if (!url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("A PostgreSQL JDBC URL is required");
        return new Configuration(host, port, url, required(env, "SPENDI_DB_USER"), required(env, "SPENDI_DB_PASSWORD"), authenticator);
    }
    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}
