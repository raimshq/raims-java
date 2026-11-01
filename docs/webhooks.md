# Receiving webhooks

Authenticate the original bytes before interpreting or processing the event.
`WebhookUtility` uses `webhook-id`, `webhook-timestamp` and `webhook-signature`.
The signing secret is the `secret_key` returned when creating or rotating the
webhook, and differs from the API Bearer key. Keep it in a vault.

```java
import com.raims.WebhookUtility;
import com.google.gson.JsonObject;
import java.util.Map;

Map<String, String> headers = Map.of(
    "webhook-id", receivedDeliveryId,
    "webhook-timestamp", receivedTimestamp,
    "webhook-signature", receivedSignature);

JsonObject event = WebhookUtility.constructEvent(rawBodyBytes, headers, webhookSecret);
```

`rawBodyBytes` contains the bytes received by the server. Whitespace, Unicode
and line breaks are part of the signature; do not reserialize the JSON.
`verifySignature` authenticates without interpreting the body. `constructEvent`
also requires a valid UTF-8 JSON object. The application validates event data.

The default tolerance is 300 seconds into the past or future. Keep the clock
synchronized. Zero tolerance requires the same second and does not disable
the protection. Header names are case-insensitive; duplicate and ambiguous
values are rejected. Multiple `v1=` signatures can coexist during rotation.

Before returning success, check the event type, environment and resource, and
persist the work durably. Deduplicate by the body's `id`; `webhook-id` identifies
the delivery and does not replace the event ID. A valid signature does not
prevent redelivery within the tolerance window. If persistence fails, return
an HTTP error to allow another delivery. The event's `created_at` uses ISO 8601,
while the header timestamp uses Unix seconds.

See the [receiving guide](https://docs.raims.com/en/integracoes/webhooks/receber-eventos).
