package com.example.ecomm.mcp.catalog;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Shared Jackson 3 mapper, so every response and tool payload is serialized the same way. */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            // The catalog may grow fields before this server knows about them; ignore extras.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private Json() {
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static String pretty(Object value) {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }
}
