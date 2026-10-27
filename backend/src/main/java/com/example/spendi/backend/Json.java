package com.example.spendi.backend;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.io.IOException;

public final class Json {
    private Json() { }
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    static {
        for (var input : new CoercionInputShape[] { CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean }) {
            MAPPER.coercionConfigFor(LogicalType.Textual).setCoercion(input, CoercionAction.Fail);
        }
        MAPPER.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
    }
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (IOException exception) { throw new IllegalStateException("JSON serialization failed", exception); }
    }
    public static <T> T read(String value, Class<T> type) {
        try {
            T result = MAPPER.readValue(value, type);
            if (result == null) throw ApiException.invalid("JSON object is required");
            return result;
        } catch (IOException exception) { throw ApiException.invalid("Invalid JSON or unsupported fields"); }
    }
}
