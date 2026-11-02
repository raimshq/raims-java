# Raims Java

Official Java SDK for the Raims public API.
Requires Java 11 or newer and uses Apache HttpComponents for HTTP and Gson for JSON.

## Installation

Add the SDK to your Maven project's `pom.xml`:

```xml
<dependency>
  <groupId>com.raims</groupId>
  <artifactId>raims-java</artifactId>
  <version>0.1.2</version>
</dependency>
```

For Gradle, use Maven Central and add the dependency:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.raims:raims-java:0.1.2'
}
```

Maven and Gradle resolve the SDK's dependencies from its POM. The consuming
application needs Java 11 or newer.

See [installation and updates](docs/installation.md) for Kotlin DSL, GitHub
release downloads, checksum verification, sources and Javadoc.

## First request

```java
import com.raims.RaimsClient;
import com.raims.model.CustomerListResponse;
import com.raims.param.ListCustomersParams;

try (RaimsClient raims = new RaimsClient(System.getenv("RAIMS_API_KEY"))) {
    CustomerListResponse customers = raims.customers().list(
        ListCustomersParams.builder().setPageSize(20).build());

    customers.getList().forEach(customer -> System.out.println(customer.getId()));
}
```

The base URL is `https://api.raims.com` for both Sandbox and Production.
The key determines the company, environment and permissions. Store it on the
server, outside source code, the browser and logs. Reuse the client across
requests and close it when your application or usage scope ends.

## Create a charge

The application must define and persist `customerId`, `chargeDueDate` and
`chargeIdempotencyKey` for the order before making the request. The due date
uses UTC Unix seconds. After losing a response, reuse the same key and all
parameters, including the due date.

```java
import com.raims.RaimsClient;
import com.raims.RequestOptions;
import com.raims.model.ChargeCreated;
import com.raims.param.CreateChargeParams;

try (RaimsClient raims = new RaimsClient(System.getenv("RAIMS_API_KEY"))) {
    ChargeCreated charge = raims.charges().create(
        CreateChargeParams.builder()
            .setCustomerId(customerId)
            .setAmountCents(15000)
            .setDueDate(chargeDueDate)
            .setTitle("Order 1234")
            .build(),
        RequestOptions.builder().setIdempotencyKey(chargeIdempotencyKey).build());

    int httpStatus = charge.getLastResponse().getStatusCode();
    String requestId = charge.getLastResponse().getRequestId();
}
```

`15000` represents R$150.00. The SDK does not generate idempotency keys or
retry writes automatically. HTTP `202` indicates a pending result: follow the
resource to determine the financial outcome.

## Pagination

```java
try (RaimsClient raims = new RaimsClient(System.getenv("RAIMS_API_KEY"))) {
    for (var customer : raims.customers().list().autoPagingIterable()) {
        processCustomer(customer);
    }
}
```

The iterator fetches subsequent pages as needed and preserves filters and
options. `nextPage()` and `previousPage()` provide explicit navigation.
`autoPagingToList(limit)` collects up to the limit chosen by the application.

## Resources

`accounts()`, `balances()`, `customers()`, `charges()`, `paymentLinks()`,
`refunds()`, `disputes()`, `statements()`, `withdrawals()`,
`withdrawalDestinations()`, `webhooks()`, `webhookEventTypes()`,
`simulations().charges()` and `simulations().refunds()`.

Use a Sandbox key for simulations. Services provide methods such as `create`,
`retrieve`, `update` and `list`, depending on the resource.

## Documentation

- [Installation and updates](docs/installation.md)
- [Configuration, types, responses and files](docs/usage.md)
- [Errors, idempotency and retries](docs/errors-and-retries.md)
- [Receiving webhooks](docs/webhooks.md)
- [Method reference](docs/reference.md)
- [Contributing](CONTRIBUTING.md) and [security](SECURITY.md)
- [Java SDK guide](https://docs.raims.com/en/integracoes/sdk-java)
- [API documentation](https://docs.raims.com/en/api)

Distributed under the [MIT license](LICENSE).
