package com.raims;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** Immutable JSON parameters. An absent field is different from an explicit null. */
public class ApiParams {
    private final JsonObject values;

    public ApiParams(JsonObject values) {
        this.values = value(Objects.requireNonNull(values, "values")).getAsJsonObject();
    }

    public final JsonObject toJson() { return values.deepCopy(); }

    /** Converts supported Java JSON values without reflecting application objects. */
    public static JsonElement value(Object value) {
        return convert(value, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static JsonElement convert(Object value, Set<Object> seen, int depth) {
        if (depth > 100) throw new IllegalArgumentException("Request parameters are nested too deeply.");
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof ApiParams) return ((ApiParams) value).toJson();
        if (value instanceof JsonElement) {
            JsonElement element = (JsonElement) value;
            if (element.isJsonNull()) return JsonNull.INSTANCE;
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isNumber()) return convert(primitive.getAsBigDecimal(), seen, depth + 1);
                return primitive.isString() ? convert(primitive.getAsString(), seen, depth + 1) : primitive.deepCopy();
            }
            if (!seen.add(value)) throw new IllegalArgumentException("Request parameters cannot contain cycles.");
            try {
                if (element.isJsonArray()) {
                    JsonArray result = new JsonArray();
                    for (JsonElement child : element.getAsJsonArray()) result.add(convert(child, seen, depth + 1));
                    return result;
                }
                JsonObject result = new JsonObject();
                for (Map.Entry<String, JsonElement> child : element.getAsJsonObject().entrySet()) {
                    convert(child.getKey(), seen, depth + 1);
                    result.add(child.getKey(), convert(child.getValue(), seen, depth + 1));
                }
                return result;
            } finally { seen.remove(value); }
        }
        if (value instanceof String) {
            String text = (String) value;
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (Character.isHighSurrogate(character)) {
                    if (++index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) {
                        throw new IllegalArgumentException("Request strings must contain valid Unicode.");
                    }
                } else if (Character.isLowSurrogate(character)) {
                    throw new IllegalArgumentException("Request strings must contain valid Unicode.");
                }
            }
            return new JsonPrimitive(text);
        }
        if (value instanceof Boolean) return new JsonPrimitive((Boolean) value);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger || value instanceof BigDecimal) return new JsonPrimitive((Number) value);
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) throw new IllegalArgumentException("Request numbers must be finite.");
            return new JsonPrimitive(BigDecimal.valueOf(number));
        }
        if (!seen.add(value)) throw new IllegalArgumentException("Request parameters cannot contain cycles.");
        try {
            if (value instanceof Map) {
                JsonObject result = new JsonObject();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String)) throw new IllegalArgumentException("JSON map keys must be strings.");
                    convert(entry.getKey(), seen, depth + 1);
                    result.add((String) entry.getKey(), convert(entry.getValue(), seen, depth + 1));
                }
                return result;
            }
            if (value instanceof Iterable) {
                JsonArray result = new JsonArray();
                for (Object child : (Iterable<?>) value) result.add(convert(child, seen, depth + 1));
                return result;
            }
            throw new IllegalArgumentException("Request parameters must contain only JSON values.");
        } finally { seen.remove(value); }
    }

    @Override public final String toString() { return getClass().getSimpleName() + "{parameters=redacted}"; }
}
