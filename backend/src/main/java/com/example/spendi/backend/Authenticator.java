package com.example.spendi.backend;

@FunctionalInterface
public interface Authenticator {
    String subject(String bearerToken);
    static ApiException unauthorized() { return new ApiException(401, "unauthorized", "A valid API access token is required"); }
}
