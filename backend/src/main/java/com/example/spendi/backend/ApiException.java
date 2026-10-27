package com.example.spendi.backend;

public final class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public static ApiException invalid(String message) { return new ApiException(400, "invalid_request", message); }
    public static ApiException missing() { return new ApiException(404, "not_found", "Resource not found"); }
    public static ApiException conflict() { return new ApiException(409, "version_conflict", "Reload the resource before updating it"); }
}
