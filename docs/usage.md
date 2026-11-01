# Using the client

## Configuration

```java
import com.raims.RaimsClient;
import java.time.Duration;

RaimsClient raims = RaimsClient.builder()
    .setApiKey(System.getenv("RAIMS_API_KEY"))
    .setTimeout(Duration.ofSeconds(80))
    .setMaxNetworkRetries(0)
    .setRequestObserver(attempt -> recordRequest(attempt))
    .build();
```

The default timeout is 80 seconds per attempt. The configurable maximum is
300 seconds. `setMaxNetworkRetries` accepts zero to five extra attempts and
applies only to reads. Each client retains its configuration and can be reused
across threads. Use builders from one thread at a time.

`setApiBase` accepts an HTTPS origin. HTTP is allowed only on loopback for
local validation. URLs with a path, credentials, query or fragment are rejected.
The transport preserves TLS validation and blocks redirects. Its automatic
retries are disabled per client; read attempts are decided and counted only
by the SDK, without relying on JVM flags.

`RequestOptions` allows per-request API keys, idempotency, timeout and retries.
The optional key replaces the credential only for that request; the application
must preserve isolation between companies and environments. The SDK does not
offer environment selection or arbitrary headers.

`RaimsClient` implements `AutoCloseable`. Use try-with-resources for short
scopes or call `close()` when shutting down the application's shared client.
Closure releases connections, cancels in-flight requests and can be repeated.
After closure, the client rejects new calls. Keep it open while iterating
through pages. Closure also wakes operations waiting for a retry delay; they
do not need to wait for that delay to end before cancellation completes.

## Parameters and responses

Typed parameters are in `com.raims.param` and use `builder()`. Omitting a
setter leaves the field absent. The `set<Field>Null()` pattern sends null when
the contract allows it. For partial updates, check the effect of null on the
selected field. Zero, false, empty lists and empty maps are preserved.
`build()` takes an independent copy of the values; subsequent changes to the
builder do not affect it.

Response models are in `com.raims.model` and provide typed getters. Integers
use `Long`, including cents and Unix seconds, without conversion to floating
point. JSON values of type number use `BigDecimal`. Nested objects, lists and
maps retain their types. Public value constants are in `EnumValues`; the
application can also receive future values.

`hasField(name)` distinguishes absence from presence. `isNull(name)` identifies
explicit null. `getRawJson()` provides a copy of the received fields, including
future fields. Treat this JSON as sensitive data. Default resource
representations omit their contents.

Each success structure is checked against the contract's HTTP status before
the model is created. New fields are preserved. Incompatible responses,
integers outside the 64-bit range, invalid JSON and bodies larger than 16 MiB
are rejected. Permissions, formats and business rules remain subject to the API.

When an operation allows different models by status, its `*Result` return type
combines the available getters. Check `getLastResponse().getStatusCode()` and
`hasField` before using a field specific to one result. `202` remains pending.
An empty-body result, such as webhook deletion with `204`, still contains HTTP
metadata.

## Pagination

`list()` returns a typed page with `getList()` and `getPage()`. The latter
contains JSON cursor metadata. `nextPage()` and `previousPage()` return null
when no page exists in that direction. Navigation and iteration preserve the
original call's filters, credentials and options.

`autoPagingIterable()` fetches pages as the application advances. Leaving the
loop stops further reads. `autoPagingToList(limit)` limits the number of items
loaded. Missing or repeated cursors accompanied by a next-page indicator cause
a protocol error, preventing a query loop. A failed page fetch does not advance
the iterator: the application can catch the exception and try to continue when
the read is available again.

## Files and uploads

Receipts, boleto documents and upload permissions are returned as API metadata.
The authenticated client does not download links, send files or forward the
Bearer key to those URLs. Transfer files separately using the procedure and
headers returned in the resource contract.

## Diagnostics

The observer receives only attempt metadata: method, parameterized path,
duration, attempt number, status and `Request-Id`. It contains no resource IDs,
query strings, bodies or credentials. An observer failure does not change the
operation's result or trigger another write. The SDK sends no external
telemetry or alerts. The application can aggregate failures and recovery from
this information without logging personal or financial data.
