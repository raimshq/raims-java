package com.raims.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.raims.ApiParams;
import com.raims.ApiResource;
import com.raims.ApiResponse;
import com.raims.RaimsCollection;
import com.raims.RequestDiagnostics;
import com.raims.RequestOptions;
import com.raims.RaimsClient;
import com.raims.exception.ApiException;
import com.raims.exception.AuthenticationException;
import com.raims.exception.ConflictException;
import com.raims.exception.ConnectionException;
import com.raims.exception.InvalidRequestException;
import com.raims.exception.NotFoundException;
import com.raims.exception.PermissionException;
import com.raims.exception.RateLimitException;
import com.raims.exception.UnexpectedResponseException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/** Executes only the operations present in the pinned public contract. */
public final class ApiRequestor implements AutoCloseable {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final JsonObject CONTRACT = loadContract();
    private static final ThreadLocal<Boolean> IN_OBSERVER = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();
    private static final ExecutorService HTTP_WORKERS = Executors.newCachedThreadPool(work -> {
        Thread thread = new Thread(null, work, "RaimsJava-http-" + WORKER_SEQUENCE.incrementAndGet(), 0, false);
        thread.setDaemon(true);
        return thread;
    });
    private final String apiKey;
    private final String apiBase;
    private final Duration timeout;
    private final int maxNetworkRetries;
    private final Consumer<RequestDiagnostics> observer;
    private final CloseableHttpClient httpClient;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object retryWait = new Object();
    private final Set<HttpUriRequestBase> activeRequests = ConcurrentHashMap.newKeySet();

    public ApiRequestor(String apiKey, String apiBase, Duration timeout, int maxNetworkRetries,
            Consumer<RequestDiagnostics> observer) {
        this.apiKey = RequestOptions.validateApiKey(apiKey);
        this.apiBase = validateBase(apiBase);
        this.timeout = RequestOptions.validateTimeout(timeout);
        this.maxNetworkRetries = RequestOptions.validateRetries(maxNetworkRetries);
        this.observer = observer;
        this.httpClient = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(Integer.MAX_VALUE).setMaxConnPerRoute(Integer.MAX_VALUE).build())
            .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement()
            .disableAuthCaching().disableConnectionState().disableContentCompression()
            .setDefaultRequestConfig(RequestConfig.custom().setAuthenticationEnabled(false).build())
            .build();
    }

    public <T extends ApiResource> T request(String operationId, Map<String, String> path, ApiParams params,
            RequestOptions options, BiFunction<JsonObject, ApiResponse, T> factory) {
        ensureOpen();
        JsonObject operation = CONTRACT.getAsJsonObject("operations").getAsJsonObject(operationId);
        if (operation == null) throw new IllegalArgumentException("The operation is not present in the pinned contract.");
        RequestOptions settings = options == null ? RequestOptions.builder().build() : options;
        if (settings.getIdempotencyKey() != null && !operation.get("idempotency").getAsBoolean()) {
            throw new IllegalArgumentException("This operation does not accept idempotencyKey.");
        }
        JsonObject values = params == null ? new JsonObject() : params.toJson();
        if (present(values, "after") && present(values, "before")) throw new IllegalArgumentException("after and before cannot be used together.");
        Map<String, String> paths = path == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(path));
        String endpoint = operation.get("path").getAsString();
        if (paths.size() != operation.getAsJsonArray("pathParams").size()) throw new IllegalArgumentException("Path arguments do not match the operation.");
        for (JsonElement nameValue : operation.getAsJsonArray("pathParams")) {
            String name = nameValue.getAsString(); String value = paths.get(name);
            if (value == null || value.isEmpty() || value.equals(".") || value.equals("..")
                    || value.matches("(?s).*[\\x00-\\x20\\x7f].*")) {
                throw new IllegalArgumentException("Path arguments must be non-empty identifiers without whitespace or control characters.");
            }
            ApiParams.value(value);
            endpoint = endpoint.replace("{" + name + "}", encode(value));
        }
        byte[] body = null;
        if (operation.get("body").getAsBoolean()) {
            body = values.toString().getBytes(StandardCharsets.UTF_8);
            if (present(operation, "maxBodyBytes") && body.length > operation.get("maxBodyBytes").getAsLong()) {
                throw new IllegalArgumentException("Request body exceeds the published size limit.");
            }
        } else {
            StringBuilder query = new StringBuilder();
            JsonObject allowed = operation.getAsJsonObject("query");
            for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
                if (!allowed.has(entry.getKey())) throw new IllegalArgumentException("This operation received an unsupported query parameter or body.");
                if (entry.getValue().isJsonNull()) continue;
                if (entry.getValue().isJsonArray()) {
                    for (JsonElement child : entry.getValue().getAsJsonArray()) appendQuery(query, entry.getKey(), child);
                } else appendQuery(query, entry.getKey(), entry.getValue());
            }
            if (query.length() != 0) endpoint += "?" + query;
        }
        String method = operation.get("method").getAsString();
        URI target = URI.create(apiBase + endpoint);
        int retries = method.equals("GET") ? (settings.getMaxNetworkRetries() == null ? maxNetworkRetries : settings.getMaxNetworkRetries()) : 0;
        Duration attemptTimeout = settings.getTimeout() == null ? timeout : settings.getTimeout();
        ApiResponse retryResponse = null;
        for (int attempt = 0; ; attempt++) {
            ensureRequestOpen(retryResponse);
            if (Thread.currentThread().isInterrupted()) throw new ConnectionException("The API request was cancelled.", retryResponse);
            long started = System.nanoTime();
            AtomicReference<ApiResponse> info = new AtomicReference<>();
            HttpUriRequestBase request = prepareRequest(method, target, body, settings, attemptTimeout);
            Future<WireResponse> pending = null;
            WireResponse response;
            activeRequests.add(request);
            try {
                ensureRequestOpen(retryResponse);
                pending = HTTP_WORKERS.submit(() -> httpClient.execute(request, received -> {
                    ApiResponse metadata = metadata(received.getCode(), received);
                    info.set(metadata);
                    return new WireResponse(received.getCode(), header(received, "content-type"),
                        readBody(received, request, metadata));
                }));
                response = pending.get(attemptTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException error) {
                request.cancel();
                if (pending != null) pending.cancel(true);
                Thread.currentThread().interrupt(); notifyObserver(operationId, operation, attempt, started, info.get(), null);
                throw new ConnectionException("The API request was cancelled.", info.get());
            } catch (TimeoutException | ExecutionException | CancellationException error) {
                request.cancel();
                if (pending != null) pending.cancel(true);
                Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
                if (cause instanceof Error) {
                    try { notifyObserver(operationId, operation, attempt, started, info.get(), null); }
                    catch (Error ignored) { /* Preserve the primary fatal failure if diagnostics also fail. */ }
                    throw (Error) cause;
                }
                if (cause instanceof UnexpectedResponseException) {
                    notifyObserver(operationId, operation, attempt, started, info.get(), null);
                    throw new UnexpectedResponseException(cause.getMessage(), info.get());
                }
                if (cause instanceof RuntimeException && !(cause instanceof CancellationException)) {
                    notifyObserver(operationId, operation, attempt, started, info.get(), null);
                    throw (RuntimeException) cause;
                }
                Long wait = !closed.get() && !(cause instanceof CancellationException) && attempt < retries ? retryDelay(attempt, null) : null;
                notifyObserver(operationId, operation, attempt, started, info.get(), wait);
                if (wait == null) throw new ConnectionException("The API connection failed or timed out.", info.get());
                retryResponse = info.get();
                pause(wait, retryResponse); continue;
            } finally {
                activeRequests.remove(request);
            }
            ApiResponse metadata = info.get();
            Long wait = attempt < retries && retryable(response.status) ? retryDelay(attempt, metadata.getRetryAfter()) : null;
            notifyObserver(operationId, operation, attempt, started, metadata, wait);
            if (wait != null) { retryResponse = metadata; pause(wait, retryResponse); continue; }
            JsonObject result = interpret(operation, response, metadata);
            T resource = factory.apply(result, metadata);
            if (resource instanceof RaimsCollection && operation.get("paginated").getAsBoolean()) {
                attachPagination(resource, operationId, paths, values, settings, factory);
            }
            return resource;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends ApiResource> void attachPagination(T resource, String operationId, Map<String, String> path,
            JsonObject params, RequestOptions options, BiFunction<JsonObject, ApiResponse, T> factory) {
        RaimsCollection<ApiResource> collection = (RaimsCollection<ApiResource>) resource;
        collection.attachPageLoader((cursor, previous) -> {
            JsonObject next = params.deepCopy(); next.remove(previous ? "after" : "before");
            next.addProperty(previous ? "before" : "after", cursor);
            return (RaimsCollection<ApiResource>) request(operationId, path, new ApiParams(next), options, factory);
        }, text(params, "after"), text(params, "before"));
    }

    private JsonObject interpret(JsonObject operation, WireResponse response, ApiResponse info) {
        int status = response.status; String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body)).toString();
        } catch (CharacterCodingException error) { throw new UnexpectedResponseException("The API response contains invalid UTF-8.", info); }
        if (status >= 300 && status < 400) throw new UnexpectedResponseException("The API returned a redirect; redirects are not followed.", info);
        if (status < 200 || status >= 300) {
            JsonObject error = null;
            try {
                JsonElement parsed = JsonSupport.parse(text);
                if (parsed.isJsonObject() && parsed.getAsJsonObject().has("error") && parsed.getAsJsonObject().get("error").isJsonObject()) {
                    error = parsed.getAsJsonObject().getAsJsonObject("error");
                }
            } catch (IllegalArgumentException ignored) { /* An edge may return a non-JSON error. */ }
            throw apiError(status, info, error);
        }
        JsonObject responses = operation.getAsJsonObject("responses");
        if (!responses.has(Integer.toString(status))) throw new UnexpectedResponseException("The API returned an undocumented success status.", info);
        JsonElement schemaValue = responses.get(Integer.toString(status));
        if (schemaValue.isJsonNull() || schemaValue.getAsJsonObject().size() == 0) {
            if (!text.trim().isEmpty()) throw new UnexpectedResponseException("The API returned a body for an empty response.", info);
            return new JsonObject();
        }
        String contentType = response.contentType;
        if (contentType != null && !contentType.matches("(?i)application/(?:[a-z0-9.+-]+\\+)?json(?:\\s*;.*)?")) {
            throw new UnexpectedResponseException("The API returned a non-JSON success response.", info);
        }
        JsonElement parsed;
        try { parsed = JsonSupport.parse(text); }
        catch (IllegalArgumentException error) { throw new UnexpectedResponseException("The API returned invalid JSON.", info); }
        if (!parsed.isJsonObject()) throw new UnexpectedResponseException("The API response must be a JSON object.", info);
        JsonSupport.validate(parsed, schemaValue.getAsJsonObject(), CONTRACT.getAsJsonObject("schemas"), info);
        return parsed.getAsJsonObject();
    }

    private void notifyObserver(String id, JsonObject operation, int attempt, long started, ApiResponse response, Long wait) {
        if (observer == null || IN_OBSERVER.get()) return;
        IN_OBSERVER.set(true);
        try {
            observer.accept(new RequestDiagnostics(id, operation.get("method").getAsString(), operation.get("path").getAsString(),
                attempt + 1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), response, wait));
        } catch (RuntimeException ignored) { /* Diagnostics must not change the request result. */ }
        finally { IN_OBSERVER.remove(); }
    }

    private static ApiException apiError(int status, ApiResponse response, JsonObject error) {
        String message = "The API request failed with HTTP " + status + ".";
        switch (status) {
            case 400: case 405: case 413: case 415: case 422: return new InvalidRequestException(message, response, error);
            case 401: return new AuthenticationException(message, response, error);
            case 403: return new PermissionException(message, response, error);
            case 404: return new NotFoundException(message, response, error);
            case 409: return new ConflictException(message, response, error);
            case 429: return new RateLimitException(message, response, error);
            default: return new ApiException(message, response, error);
        }
    }

    private static ApiResponse metadata(int status, MessageHeaders headers) {
        String id = safeHeader(headers, "request-id");
        return new ApiResponse(status, id, "true".equalsIgnoreCase(safeHeader(headers, "idempotency-replayed")), safeHeader(headers, "retry-after"));
    }
    private static String safeHeader(MessageHeaders headers, String name) {
        return ApiResponse.safeHeader(header(headers, name));
    }
    private static String header(MessageHeaders headers, String name) {
        Header[] values = headers.getHeaders(name);
        return values.length == 1 ? values[0].getValue() : null;
    }
    private static boolean retryable(int status) { return status == 408 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504; }
    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("The Raims client is closed.");
    }

    private void ensureRequestOpen(ApiResponse response) {
        if (closed.get()) throw new ConnectionException("The API request was cancelled.", response);
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            synchronized (retryWait) { retryWait.notifyAll(); }
            for (HttpUriRequestBase request : activeRequests) request.cancel();
            httpClient.close(CloseMode.IMMEDIATE);
        }
    }

    private HttpUriRequestBase prepareRequest(String method, URI target, byte[] body,
            RequestOptions settings, Duration attemptTimeout) {
        Timeout deadline = Timeout.ofMilliseconds(attemptTimeout.toMillis());
        HttpUriRequestBase request = new HttpUriRequestBase(method, target);
        request.setConfig(RequestConfig.custom().setConnectionRequestTimeout(deadline)
            .setResponseTimeout(deadline).setAuthenticationEnabled(false).setHardCancellationEnabled(true).build());
        request.setHeader("Authorization", "Bearer " + (settings.getApiKey() == null ? apiKey : settings.getApiKey()));
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", "RaimsJava/" + RaimsClient.VERSION);
        if (settings.getIdempotencyKey() != null) request.setHeader("Idempotency-Key", settings.getIdempotencyKey());
        if (body != null) request.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
        return request;
    }

    private static byte[] readBody(ClassicHttpResponse response, HttpUriRequestBase request, ApiResponse info)
            throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) return new byte[0];
        if (entity.getContentLength() > MAX_RESPONSE_BYTES) {
            request.cancel();
            throw new UnexpectedResponseException("The API response exceeds the supported size.", info);
        }
        try (InputStream input = entity.getContent(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (count > MAX_RESPONSE_BYTES - buffer.size()) {
                    request.cancel();
                    throw new UnexpectedResponseException("The API response exceeds the supported size.", info);
                }
                buffer.write(chunk, 0, count);
            }
            return buffer.toByteArray();
        } catch (IOException | RuntimeException error) {
            request.cancel();
            throw error;
        }
    }
    private static Long retryDelay(int attempt, String retryAfter) {
        double delay = Math.max(250, Math.min(2000, 250 * (1L << attempt)) * ThreadLocalRandom.current().nextDouble(0.5, 1));
        if (retryAfter != null) {
            Double seconds = null;
            try {
                seconds = retryAfter.matches("[0-9]+(?:\\.[0-9]+)?") ? Double.valueOf(retryAfter)
                    : Duration.between(Instant.now(), ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis() / 1000.0;
            } catch (NumberFormatException | DateTimeParseException | ArithmeticException ignored) { /* Use bounded backoff when the header is invalid. */ }
            if (seconds != null) {
                if (seconds > 30) return null;
                if (Double.isFinite(seconds)) delay = Math.max(delay, seconds * 1000);
            }
        }
        return (long) Math.ceil(delay);
    }
    private void pause(long millis, ApiResponse response) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        synchronized (retryWait) {
            while (true) {
                ensureRequestOpen(response);
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return;
                try { TimeUnit.NANOSECONDS.timedWait(retryWait, remaining); }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new ConnectionException("The API request was cancelled.", response);
                }
            }
        }
    }
    private static void appendQuery(StringBuilder query, String name, JsonElement value) {
        if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Query values must be scalar values or lists of scalar values.");
        if (query.length() != 0) query.append('&');
        query.append(encode(name)).append('=').append(encode(value.getAsString()));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~"); }
    private static boolean present(JsonObject object, String name) { return object.has(name) && !object.get(name).isJsonNull(); }
    private static String text(JsonObject object, String name) { return present(object, name) ? object.get(name).getAsString() : null; }
    private static String validateBase(String value) {
        if (value == null || value.matches("(?s).*[\\s\\\\].*")) throw new IllegalArgumentException("apiBase must be an HTTPS origin.");
        try {
            URI uri = new URI(value); String host = uri.getHost(); String scheme = uri.getScheme();
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host);
            if (host == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                    || !("https".equalsIgnoreCase(scheme) || ("http".equalsIgnoreCase(scheme) && loopback))) {
                throw new IllegalArgumentException("apiBase requires HTTPS without credentials, a path, query or fragment; HTTP is allowed only on loopback.");
            }
            return scheme.toLowerCase(java.util.Locale.ROOT) + "://" + uri.getRawAuthority();
        } catch (URISyntaxException error) { throw new IllegalArgumentException("apiBase must be an HTTPS origin."); }
    }
    private static JsonObject loadContract() {
        try (InputStream input = ApiRequestor.class.getResourceAsStream("/com/raims/contract.json")) {
            if (input == null) throw new IllegalStateException("The generated SDK contract is missing.");
            return JsonSupport.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | IllegalArgumentException error) { throw new IllegalStateException("The generated SDK contract could not be read."); }
    }

    private static final class WireResponse {
        private final int status;
        private final String contentType;
        private final byte[] body;

        private WireResponse(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }
}
