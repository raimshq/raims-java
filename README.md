# Raims Java

Official Java SDK for the Raims public API.
Requires Java 11 or newer and uses Apache HttpComponents for HTTP and Gson for JSON.

## Install from GitHub

The initial distribution is available through
[this repository's releases](https://github.com/raimshq/raims-java/releases/latest).
The package is not yet published on Maven Central.

Download the assets from the current stable release with GitHub CLI:

```sh
gh release download --repo raimshq/raims-java --pattern '*' --dir raims-java-release
```

Install the JAR and its POM in your local Maven repository:

```sh
mvn install:install-file -Dfile=raims-java-release/raims-java.jar -DpomFile=raims-java-release/raims-java.pom
```

Add `com.raims:raims-java` to your project's dependencies, using the version
declared in the downloaded POM. Maven resolves transitive dependencies through
the POM. The consuming application needs Java 11 or newer; installing the JAR
does not require Python or the engineering Toolkit.

See [installation and updates](docs/installation.md) to verify hashes, use
sources and Javadoc, and update an existing installation.

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
- [Contract and maintenance](docs/maintenance.md)
- [Contributing](CONTRIBUTING.md) and [security](SECURITY.md)
- [API documentation](https://docs.raims.com/en/api)

Distributed under the [MIT license](LICENSE).
