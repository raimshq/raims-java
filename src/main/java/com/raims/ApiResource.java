package com.raims;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Read-only resource retaining future fields without exposing them in diagnostics. */
public class ApiResource {
    protected final JsonObject data;
    private final ApiResponse lastResponse;

    public ApiResource(JsonObject data, ApiResponse lastResponse) {
        this.data = Objects.requireNonNull(data, "data").deepCopy();
        this.lastResponse = lastResponse;
    }

    public final boolean hasField(String name) { return data.has(name); }
    public final boolean isNull(String name) { return data.has(name) && data.get(name).isJsonNull(); }
    public final JsonObject getRawJson() { return data.deepCopy(); }
    public final ApiResponse getLastResponse() { return lastResponse; }
    protected final String stringField(String name) { return present(name) ? data.get(name).getAsString() : null; }
    protected final Long longField(String name) { return present(name) ? data.get(name).getAsBigDecimal().longValueExact() : null; }
    protected final BigDecimal numberField(String name) { return present(name) ? data.get(name).getAsBigDecimal() : null; }
    protected final Boolean booleanField(String name) { return present(name) ? data.get(name).getAsBoolean() : null; }
    protected final JsonObject objectField(String name) { return present(name) ? data.getAsJsonObject(name).deepCopy() : null; }
    protected final JsonArray arrayField(String name) { return present(name) ? data.getAsJsonArray(name).deepCopy() : null; }
    protected final JsonElement jsonField(String name) { return data.has(name) ? data.get(name).deepCopy() : null; }
    protected static <T> List<T> readList(JsonElement value, Function<JsonElement, T> factory) {
        if (value == null || value.isJsonNull()) return null;
        List<T> result = new ArrayList<>();
        for (JsonElement child : value.getAsJsonArray()) result.add(child.isJsonNull() ? null : factory.apply(child.deepCopy()));
        return Collections.unmodifiableList(result);
    }
    protected static <T> Map<String, T> readMap(JsonElement value, Function<JsonElement, T> factory) {
        if (value == null || value.isJsonNull()) return null;
        Map<String, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> child : value.getAsJsonObject().entrySet()) {
            result.put(child.getKey(), child.getValue().isJsonNull() ? null : factory.apply(child.getValue().deepCopy()));
        }
        return Collections.unmodifiableMap(result);
    }
    private boolean present(String name) { return data.has(name) && !data.get(name).isJsonNull(); }

    @Override public final String toString() { return getClass().getSimpleName() + "{resource=redacted}"; }
}
