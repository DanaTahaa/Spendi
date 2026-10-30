package com.example.spendi.client;

import java.io.IOException;

public final class ApiException extends IOException {
    public final int status;
    public final String code;
    public ApiException(int status, String code) {
        super("API request failed (HTTP " + status + ")");
        this.status = status;
        this.code = code;
    }
}
