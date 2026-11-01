package com.raims;

/** Redacted metadata for one HTTP attempt. */
public final class RequestDiagnostics {
    private final String operationId;
    private final String method;
    private final String path;
    private final int attempt;
    private final long durationMillis;
    private final Integer statusCode;
    private final String requestId;
    private final Long retryDelayMillis;

    public RequestDiagnostics(String operationId, String method, String path, int attempt,
            long durationMillis, ApiResponse response, Long retryDelayMillis) {
        this.operationId = operationId; this.method = method; this.path = path;
        this.attempt = attempt; this.durationMillis = durationMillis;
        statusCode = response == null ? null : response.getStatusCode();
        requestId = response == null ? null : response.getRequestId();
        this.retryDelayMillis = retryDelayMillis;
    }

    public String getOperationId() { return operationId; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public int getAttempt() { return attempt; }
    public long getDurationMillis() { return durationMillis; }
    public Integer getStatusCode() { return statusCode; }
    public String getRequestId() { return requestId; }
    public boolean isWillRetry() { return retryDelayMillis != null; }
    public Long getRetryDelayMillis() { return retryDelayMillis; }
    @Override public String toString() {
        return "RequestDiagnostics{operationId=" + operationId + ", method=" + method + ", path=" + path
            + ", attempt=" + attempt + ", statusCode=" + statusCode + "}";
    }
}
