package com.raims;

import com.raims.internal.ApiRequestor;
import java.time.Duration;
import java.util.function.Consumer;

/** Client configuration is isolated per instance. Reuse a client across requests. */
public final class RaimsClient extends RaimsServices implements AutoCloseable {
    public static final String VERSION = "0.1.0";
    private final ApiRequestor requestor;

    public RaimsClient(String apiKey) {
        this(new Builder().setApiKey(apiKey));
    }

    private RaimsClient(Builder builder) {
        this(new ApiRequestor(builder.apiKey, builder.apiBase, builder.timeout,
                builder.maxNetworkRetries, builder.requestObserver));
    }

    private RaimsClient(ApiRequestor requestor) {
        super(requestor);
        this.requestor = requestor;
    }

    /** Closes the client's pool and cancels its active HTTP requests. */
    @Override public void close() { requestor.close(); }

    public static Builder builder() { return new Builder(); }

    @Override public String toString() { return "RaimsClient[configuration redacted]"; }

    public static final class Builder {
        private String apiKey;
        private String apiBase = "https://api.raims.com";
        private Duration timeout = Duration.ofSeconds(80);
        private int maxNetworkRetries;
        private Consumer<RequestDiagnostics> requestObserver;

        public Builder setApiKey(String value) { apiKey = value; return this; }
        public Builder setApiBase(String value) { apiBase = value; return this; }
        public Builder setTimeout(Duration value) { timeout = value; return this; }
        public Builder setMaxNetworkRetries(int value) { maxNetworkRetries = value; return this; }
        public Builder setRequestObserver(Consumer<RequestDiagnostics> value) { requestObserver = value; return this; }
        public RaimsClient build() { return new RaimsClient(this); }
        @Override public String toString() { return "RaimsClient.Builder[configuration redacted]"; }
    }
}
