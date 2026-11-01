# Contributing

Read `AGENTS.md` and the [maintenance guide](docs/maintenance.md). Preserve the
contract and regenerate outputs with `python scripts/generate.py`. Check
offline generation with `--check`, compile for Java 11 and validate the JAR,
sources and Javadoc. `mvn verify` runs the canonical Developer Toolkit validator
on JDKs 11, 21 and 25. The `python scripts/check.py` shortcut also prepares and
checks the package. See [local validation](docs/maintenance.md#local-validation)
for tool paths and dependency auditing.

Independent scenarios must cover every operation and success status, method,
path, query, JSON, empty responses and `202`. Also verify:

- Null, omission, false, zero, empty maps, future fields and exact integers.
- Builder types, responses, lists, maps and nested objects.
- Isolation between clients, per-request options and concurrent calls.
- Redirects, timeout, interruption, body limits and invalid UTF-8.
- Read-only retries, `Retry-After` and no retries for writes.
- Lost responses, counting sends and diagnostics, including when another
  library has already initialized global JVM retries.
- Navigation in both directions, filters, empty pages and cursor cycles.
- Iterator recovery after a temporary failure, client closure and cancellation
  of its in-flight requests.
- Schemas shared by inputs and responses, and nested collections in the generator.
- Scalar references, nullable aliases, referenced items and maps, preserving
  public constants through equivalent refactors.
- Fatal worker errors propagated without retry, and retry waits cancelled by
  closure or interruption while retaining available metadata.
- Webhooks with original bytes, Base62 secrets, clock checks, rotation and
  ambiguous headers.
- Redacted diagnostics and observers that fail or initiate other calls.

Use loopback receivers and isolated dependencies. Tests, simulators, consumers,
tools and reports belong only in `C:\server\raims\.tmp\raims-java-sdk`.
Do not call the real API, even with a Sandbox key, to validate the library.
Stop processes and verify that their ports are released.

Review all modified and new files, `git diff` and `git diff --cached`.
Preserve licenses and attribution. Leave changes uncommitted and unstaged;
remote creation, pushes, releases and publication are separate steps.
