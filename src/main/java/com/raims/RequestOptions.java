package com.raims;

import java.time.Duration;

/** Per-request credential, idempotency and transport settings. */
public final class RequestOptions {
    private final String apiKey;
    private final String idempotencyKey;
    private final Duration timeout;
    private final Integer maxNetworkRetries;

    private RequestOptions(Builder builder) {
        apiKey = builder.apiKey;
        idempotencyKey = builder.idempotencyKey;
        timeout = builder.timeout;
        maxNetworkRetries = builder.maxNetworkRetries;
    }

    public static Builder builder() { return new Builder(); }
    public String getApiKey() { return apiKey; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Duration getTimeout() { return timeout; }
    public Integer getMaxNetworkRetries() { return maxNetworkRetries; }
    @Override public String toString() { return "RequestOptions{credentials=redacted}"; }

    public static String validateApiKey(String value) {
        if (value == null || !value.matches("[\\x21-\\x7e]{1,4096}")) {
            throw new IllegalArgumentException("apiKey must be a non-empty bearer credential without whitespace.");
        }
        return value;
    }

    public static Duration validateTimeout(Duration value) {
        if (value == null || value.compareTo(Duration.ofMillis(1)) < 0 || value.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("timeout must be between 1 millisecond and 5 minutes.");
        }
        return value;
    }

    public static int validateRetries(int value) {
        if (value < 0 || value > 5) throw new IllegalArgumentException("maxNetworkRetries must be between 0 and 5.");
        return value;
    }

    public static final class Builder {
        private String apiKey;
        private String idempotencyKey;
        private Duration timeout;
        private Integer maxNetworkRetries;

        public Builder setApiKey(String value) { apiKey = validateApiKey(value); return this; }
        public Builder setIdempotencyKey(String value) {
            if (value == null || !value.matches("[\\x21-\\x7e]{1,255}")) {
                throw new IllegalArgumentException("idempotencyKey must contain 1 to 255 visible ASCII characters.");
            }
            idempotencyKey = value; return this;
        }
        public Builder setTimeout(Duration value) { timeout = validateTimeout(value); return this; }
        public Builder setMaxNetworkRetries(int value) { maxNetworkRetries = validateRetries(value); return this; }
        public RequestOptions build() { return new RequestOptions(this); }
        @Override public String toString() { return "RequestOptions.Builder{credentials=redacted}"; }
    }
}
