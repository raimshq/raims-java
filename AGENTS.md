# Raims Java SDK

Read README, CONTRIBUTING and docs/maintenance.md before changing the SDK.

- Write repository descriptions and Markdown documentation in English.
- Java 11 or newer, namespace `com.raims` and Maven artifact `com.raims:raims-java`.
- Consume only the public contract pinned in `contracts`, with its revision and
  SHA-256 verified. Do not import host code or private contracts.
- `scripts/generate.py` generates services, parameters, models, constants,
  `RaimsServices`, HTTP metadata and `docs/reference.md`. Do not edit these files
  manually. Each operation must be covered exactly once.
- Preserve JSON names, integer cents, Unix seconds, omission and null, future
  fields and pending responses. Do not generate idempotency keys or retry writes
  automatically. Configurable retries apply only to reads.
- The key determines the company and environment. Do not offer OAuth,
  environment selection or arbitrary headers. The transport does not follow
  redirects, upload files or download external URLs. Preserve TLS validation.
- Diagnostics and default representations must not expose credentials, resource
  IDs, query strings or payloads. Response metadata preserves `Request-Id`.
- Tests, simulators, tools, validation dependencies, builds and evidence belong
  exclusively in `C:\server\raims\.tmp\raims-java-sdk`. Do not include them
  in the repository or package.
- Check offline generation, Java 11 compilation, the complete contract, HTTP,
  pagination, webhooks and JAR consumption. Use isolated dependencies and do not
  call the real API during tests. Stop temporary processes before finishing.
- `mvn verify` and `scripts/check.py` use the canonical Developer Toolkit
  validator. Do not create a parallel suite in the SDK or treat the internal
  recursive-build bypass as validation. Preserve dependency auditing.
- The transport uses a per-client policy with automatic retries disabled.
  Only the SDK loop retries reads and emits the corresponding diagnostics.
  Do not depend on global JVM flags to prevent write retries.
- The client implements `AutoCloseable`. Preserve idempotent closure,
  cancellation of active operations and rejection of calls after closure.
- Leave changes uncommitted and unstaged. Remote repository creation, pushes,
  releases, Maven Central and guide publication require separate authorization.
- The initial distribution uses only source and assets on GitHub. Publishing a
  release does not authorize Maven Central, executable workflows or infrastructure
  changes. Preserve local gates and verification of files downloaded from the
  destination.
- Describe Raims features directly and preserve required licenses and
  attribution. Apply the humanizer skill to text intended for people.
