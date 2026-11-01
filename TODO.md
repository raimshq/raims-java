# TODO

- [ ] Define the stable Java API before the first distribution. Identify the
  classes, methods, signatures and constants protected by compatibility,
  exclude internal helpers from that guarantee, and add an approved baseline
  to the canonical validator for checking future versions.
- [ ] Define typed pagination metadata and the return types of `nextPage()`
  and `previousPage()` before the first distribution. Preserve filters,
  options, null, navigation and recovery after failures; validate consumers
  and update the guides when this improvement is implemented.
- [ ] Measure executor and connection usage under isolated workloads before
  offering resource configuration. Keep tests and evidence in `.tmp` and
  choose the policy from measurements, preserving timeout, cancellation and
  the prohibition on automatic write retries.
- [ ] Automate future GitHub releases on authorized runners, linking the same
  commit to checks, asset hashes and the installation test. This automation
  must not publish to Maven Central.
- [ ] Prepare Maven Central distribution: confirm ownership of the `com.raims`
  namespace, the `raims-java` artifact, signing and the CI flow on authorized
  runners. Validate the same commit and verify a clean installation from the
  destination after authorized publication.
- [ ] Include the Java SDK in support knowledge sources at the first
  distribution. Create BR/EN guides at `integracoes/sdk-java` in
  `raims-developer-docs`, covering purpose, when to suggest Java, installation
  and updates, backend usage, access, permissions, environment from the key,
  simulations, availability and limits, pagination, idempotency, pending
  results, errors, webhooks and closure of the shared client. Check
  `Raims-Chat/docs/knowledge-sources.md`, regenerate the existing public catalog
  and validate guidance in both languages. The library does not create support
  states or financial queries: it requires no changes to the context contract
  or existing tools. Mark complete only after confirming the package, published
  sources, catalog and guidance.
- [ ] Publish the Java changelog entry in
  `raims-developer-docs/content/docs/changelog/post`, with the actual
  availability date and links to the guides and confirmed version. The local
  implementation remains pending, without announcing public availability.
