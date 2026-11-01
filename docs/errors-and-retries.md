# Errors, idempotency and retries

HTTP and transport failures use exceptions in `com.raims.exception`. Check
the exception category and HTTP metadata to decide what the application should
do. A connection failure or timeout after sending does not prove that a write
was rejected.

The SDK does not generate idempotency keys or retry POST, PATCH, PUT or DELETE
automatically. Define and persist one key per intent before the call, and send
it through `RequestOptions` only for operations that accept it. After losing a
response, retain the key and the same parameters; query the resource before
creating a different intent. Changing data or the due date can change the
operation's meaning. Key expiration and retention follow the API contract,
without an idempotency cache in the SDK.

Reads are not retried by default. `setMaxNetworkRetries` allows up to five
extra attempts for network failures and transient read responses.
`Retry-After` determines the delay when accepted by the client; a delay above
the supported limit returns the failure so the application can decide when
to resume. An interrupted thread retains its interrupted status and ends the
operation. The budget includes every HTTP send: with zero retries, a call sends
at most once. Each SDK attempt produces its own diagnostic record. The policy
does not depend on the JVM's global retry configuration.

Fatal JVM errors, such as `OutOfMemoryError`, propagate without conversion to
connection failures or retries. They require diagnosis in the application;
increasing retries does not resolve an out-of-memory condition. Attempt
diagnostics retain available HTTP metadata and do not replace the original
fatal error.

Closing the client also interrupts waits between read attempts. An operation
already in progress ends with a cancellation `ConnectionException`, preserving
the last attempt's status and `Request-Id` when available. A new call made after
`close()` is still rejected before HTTP.

Resources' `getLastResponse()` and exception metadata preserve status,
`Request-Id`, `Retry-After` and the idempotency replay indicator when provided.
Do not log bodies, personal documents, temporary URLs or credentials when
reporting a failure. An error response without the expected envelope is still
reported as an HTTP failure. A success response with an incompatible structure
is a protocol failure.

HTTP `202` is accepted without treating it as completion. Follow the resource
through the appropriate queries and events before reporting that payment,
cancellation or refund has completed.
