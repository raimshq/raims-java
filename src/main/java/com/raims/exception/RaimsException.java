package com.raims.exception;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.raims.ApiResponse;

/** Base exception. The default representation excludes server messages and payloads. */
public class RaimsException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ApiResponse response;
    private final JsonObject error;

    public RaimsException(String message) { this(message, null, null); }
    public RaimsException(String message, ApiResponse response) { this(message, response, null); }
    public RaimsException(String message, ApiResponse response, JsonObject error) {
        super(message);
        this.response = response;
        this.error = error == null ? null : error.deepCopy();
    }

    public ApiResponse getLastResponse() { return response; }
    public Integer getStatusCode() { return response == null ? null : response.getStatusCode(); }
    public String getRequestId() { return response == null ? null : response.getRequestId(); }
    public String getRetryAfter() { return response == null ? null : response.getRetryAfter(); }
    public String getCode() { return text("code"); }
    public String getApiMessage() { return text("message"); }
    /** Returns a copy of the field validation details, or null when no details array is present. */
    public JsonArray getDetails() {
        JsonElement details = error == null ? null : error.get("details");
        return details != null && details.isJsonArray() ? details.getAsJsonArray().deepCopy() : null;
    }
    public JsonObject getApiError() { return error == null ? null : error.deepCopy(); }
    private String text(String key) {
        JsonElement value = error == null ? null : error.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
}
