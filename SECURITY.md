# Security

Use the SDK only on the backend and keep the API key and webhook secret in
protected storage. Do not log credentials, bodies, personal documents or
temporary links. Resources and parameters have redacted default
representations; `getRawJson()` and `toJson()` provide explicit access to their
contents.

The transport verifies TLS, blocks redirects and does not transfer external
files. The key determines the company and environment. The API enforces
permissions and financial rules. Verify the signature before processing a
webhook and deduplicate events in durable storage.

Report vulnerabilities to `security@raims.com`, including the version, impact
and a minimal reproduction without real data. Do not send keys or customer
information or publish these details in public issues.
