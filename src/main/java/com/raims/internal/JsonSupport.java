package com.raims.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.raims.ApiResponse;
import com.raims.exception.UnexpectedResponseException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Map;

/** Strict JSON parsing and structural contract validation. */
public final class JsonSupport {
    private JsonSupport() {}

    public static JsonElement parse(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content.");
            return value;
        } catch (IOException | NumberFormatException error) {
            throw new IllegalArgumentException("Invalid JSON content.");
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 100) throw new IllegalArgumentException("JSON content is nested too deeply.");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IllegalArgumentException("Duplicate JSON property.");
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject(); return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray(); return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            case NUMBER:
                String token = reader.nextString();
                if (token.length() > 1024) throw new IllegalArgumentException("JSON number exceeds the supported length.");
                BigDecimal number = new BigDecimal(token);
                if (Math.abs((long) number.scale()) > 10000) throw new IllegalArgumentException("JSON number exceeds the supported scale.");
                return new JsonPrimitive(number);
            default: throw new IllegalArgumentException("Invalid JSON content.");
        }
    }

    public static void validate(JsonElement value, JsonObject schema, JsonObject schemas, ApiResponse response) {
        if (!matches(value, schema, schemas, 0)) {
            throw new UnexpectedResponseException("The API response does not match the published structure.", response);
        }
    }

    private static boolean matches(JsonElement value, JsonObject schema, JsonObject schemas, int depth) {
        if (depth > 100) return false;
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").getAsString();
            if (!ref.startsWith("#/components/schemas/")) return false;
            JsonElement target = schemas.get(ref.substring("#/components/schemas/".length()).replace("~1", "/").replace("~0", "~"));
            return target != null && matches(value, target.getAsJsonObject(), schemas, depth + 1);
        }
        for (String composition : new String[] {"anyOf", "oneOf"}) {
            if (schema.has(composition)) {
                for (JsonElement variant : schema.getAsJsonArray(composition)) {
                    if (matches(value, variant.getAsJsonObject(), schemas, depth + 1)) return true;
                }
                return false;
            }
        }
        if (schema.has("allOf")) {
            for (JsonElement variant : schema.getAsJsonArray("allOf")) {
                if (!matches(value, variant.getAsJsonObject(), schemas, depth + 1)) return false;
            }
        }
        boolean nullable = schema.has("nullable") && schema.get("nullable").getAsBoolean();
        String type = null;
        if (schema.has("type")) {
            JsonElement types = schema.get("type");
            if (types.isJsonArray()) {
                for (JsonElement child : types.getAsJsonArray()) {
                    String candidate = child.getAsString();
                    if (candidate.equals("null")) nullable = true; else type = candidate;
                }
            } else type = types.getAsString();
        }
        if (value == null || value.isJsonNull()) return nullable || "null".equals(type) || type == null;
        if (type == null) return true;
        if (type.equals("object")) {
            if (!value.isJsonObject()) return false;
            JsonObject object = value.getAsJsonObject();
            if (schema.has("required")) {
                for (JsonElement field : schema.getAsJsonArray("required")) if (!object.has(field.getAsString())) return false;
            }
            JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                JsonObject child = properties.has(field.getKey()) ? properties.getAsJsonObject(field.getKey()) : null;
                if (child == null && schema.has("additionalProperties") && schema.get("additionalProperties").isJsonObject()) {
                    child = schema.getAsJsonObject("additionalProperties");
                }
                if (child != null && !matches(field.getValue(), child, schemas, depth + 1)) return false;
            }
            return true;
        }
        if (type.equals("array")) {
            if (!value.isJsonArray()) return false;
            JsonObject child = schema.has("items") ? schema.getAsJsonObject("items") : new JsonObject();
            for (JsonElement item : value.getAsJsonArray()) if (!matches(item, child, schemas, depth + 1)) return false;
            return true;
        }
        if (!value.isJsonPrimitive()) return false;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (type.equals("string")) return primitive.isString();
        if (type.equals("boolean")) return primitive.isBoolean();
        if (type.equals("number")) return primitive.isNumber();
        if (type.equals("integer")) {
            if (!primitive.isNumber()) return false;
            try { primitive.getAsBigDecimal().longValueExact(); return true; }
            catch (ArithmeticException | NumberFormatException error) { return false; }
        }
        return false;
    }
}
