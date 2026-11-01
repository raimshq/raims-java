# Contract and maintenance

## Source and generation

`contracts/openapi.json` preserves the bytes of the immutable public revision
identified in `contracts/source.json`, including its URL, revision, SHA-256 and
version. The canonical source belongs to the Public API; the SDK consumes the
surface published in [raimshq/openapi](https://github.com/raimshq/openapi).

```sh
python scripts/generate.py
python scripts/generate.py --check
```

The generator is offline, verifies the checksum and requires
`contracts/service-map.json` to cover every operation exactly once. It
generates services, parameters, models, constants, service composition, HTTP
metadata and the reference. `--check` compares outputs without rewriting them
and detects stale generated files.

Inputs and responses have separate representations even when they share a
schema. Nested lists and maps preserve Java types; query parameter
descriptions are also included in builder Javadoc. References to scalar types,
lists and maps use the corresponding Java type, including nullable alias
chains. Extracting an enum into a component preserves the constants derived
from the field using it. Operation root responses remain objects; the
generator rejects contracts that change this requirement, which need a
separate compatibility decision.

When updating the snapshot, verify the public revision's manifest and checksum
before changing source files. Review types, nullability, required fields,
authentication, environment, statuses, idempotency and cursors. Regenerate
outputs and check consumer impact. Do not change the contract to invent an
endpoint or bypass an API restriction.

## Organization

| Path | Responsibility |
| --- | --- |
| `RaimsClient` | Isolated configuration and service composition |
| `service` | Generated typed resource methods |
| `param` | Generated immutable inputs with builders |
| `model` | Generated responses and nested objects |
| `internal/ApiRequestor` | HTTP transport, reads, retries and errors |
| `ApiResource` and `ApiParams` | JSON preservation and typed access |
| `RaimsCollection` | Cursors and iteration |
| `WebhookUtility` | Original-body authentication |
| `src/main/resources/com/raims/contract.json` | Generated metadata used for validation |

## Implementation impact

| Behavior | Source | Consumers | Persistence | Validation |
| --- | --- | --- | --- | --- |
| New Java public API client | Pinned public contract | Java applications | No migration | Every published operation and status |
| Pagination, errors and pending responses | Existing HTTP contract | Java services and models | None | Local transport and protocol scenarios |
| Receiving events | Published signing protocol | Integrator backend | Deduplication belongs to the integrator | Original bytes, clock checks and rotation |
| Support guidance | Public guides at the first distribution | Existing chat catalog | None | Pending public availability |

This delivery does not change the Public API contract or behavior. Existing
CLI, MCP and SDK consumers remain on the same contract and do not need
regeneration because of this client. The SDK uses only API keys; OAuth-only
parameters are not included in Java signatures.

## Local validation

`mvn verify` checks generated files and calls
`Developer-Toolkit/scripts/release-preflight/java_sdk.py`. The
`python scripts/check.py` launcher uses the same adapter to prepare and validate
the package. Maven does not maintain a second suite: Java test files, fixtures,
consumers and evidence are materialized only in `.tmp`.

Prepare Maven and JDKs 11, 21 and 25. The adapter accepts `--maven` and
`--jdk-home 11=path`, `--jdk-home 21=path` and `--jdk-home 25=path`. It also
recognizes `RAIMS_MAVEN`, `RAIMS_MAVEN_HOME`, `RAIMS_JAVA_11_HOME`,
`RAIMS_JAVA_21_HOME` and `RAIMS_JAVA_25_HOME`, along with tools already prepared
in `.tmp/raims-java-sdk/tools`. It does not install tools globally.

The gate covers every published operation and status, real loopback transport,
lost responses, retry budgets, pagination after failures, concurrency,
cancellation, closure, webhooks and the schema forms involved in generator
regressions. It checks artifacts and the package's effective dependencies,
including transitive dependencies, against the OSV database. Query errors or
vulnerabilities prevent approval; no real financial API is called. Regressions
also preserve fatal errors without retry, including memory exhaustion in an
isolated JVM, and close the client before and during retry waits, with multiple
operations or thread interruption. Contract copies also check scalar references
and constant stability.

The internal `--verify-built` mode receives the JAR already produced by Maven
and its dependency classpath, avoiding a recursive build. The internal
`raims.skipSdkCheck` option supports builds run by the adapter itself; it does
not approve the package or disable generated-file checks. Use the full gate
to assess a delivery. Source changes during the run invalidate approval.

## Build and distribution

Use Java 11 or newer and Maven. `mvn package` produces the JAR, sources and
Javadoc in `../.tmp/raims-java-sdk/build`. Validation dependencies and consumers
also stay in `.tmp`. Inspect all three artifacts and verify consumption of an
installed JAR in addition to building the project itself.

The initial distribution uses `raimshq/raims-java`, with source on `main` and
versioned GitHub releases. The Maven artifact coordinates are
`com.raims:raims-java`. Verify the JAR, POM, sources, Javadoc, manifest and hashes
after downloading the release. Installing the JAR with its POM does not depend
on the private Toolkit.

The source-publication profile contains no executable workflows and does not
change infrastructure. Run the `Commit` and `Deploy` gates before committing
and publishing, with SCA, SAST and source verification. This stage authorizes
GitHub only. Maven Central, signing and CodeBuild CI configuration still require
their own preparation and authorization. Do not announce a Maven Central
package before confirming the version and its installation.

At the first distribution, publish BR/EN guides at `integracoes/sdk-java` in
`raims-developer-docs`, using the version actually available. Record the
changelog on the actual availability date and regenerate the public catalog
already used by support. Validate guidance in both languages. The context
contract and financial tools do not change because of this library. Keep
the corresponding items in the private workspace backlog, maintained outside
Git, open until the package, published sources and guidance are confirmed.
