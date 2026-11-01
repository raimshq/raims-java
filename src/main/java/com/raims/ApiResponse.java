package com.raims;

/** Metadata for one HTTP response. Headers and bodies are not retained. */
public final class ApiResponse {
    private final int statusCode;
    private final String requestId;
    private final boolean idempotencyReplayed;
    private final String retryAfter;

    public ApiResponse(int statusCode, String requestId, boolean idempotencyReplayed, String retryAfter) {
        this.statusCode = statusCode;
        this.requestId = safeHeader(requestId);
        this.idempotencyReplayed = idempotencyReplayed;
        this.retryAfter = safeHeader(retryAfter);
    }

    public int getStatusCode() { return statusCode; }
    public String getRequestId() { return requestId; }
    public boolean isIdempotencyReplayed() { return idempotencyReplayed; }
    public boolean getIdempotencyReplayed() { return idempotencyReplayed; }
    public String getRetryAfter() { return retryAfter; }

    public static String safeHeader(String value) {
        return value != null && value.matches("[\\x20-\\x7e]{1,256}") ? value : null;
    }

    @Override public String toString() {
        return "ApiResponse{statusCode=" + statusCode + ", requestId=" + requestId + "}";
    }
}
