# Contributing

Report bugs through this repository's GitHub issues. Include:

- The SDK version or source commit.
- The runtime version and operating system.
- Expected and actual behavior.
- A minimal reproduction using synthetic data.

Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
Keep API keys, webhook secrets, customer data, and URLs that grant access to
resources out of public reports and examples.

## Working with the source

Use Java 11 or later, Maven, and Python.

From the SDK root:

```sh
python scripts/generate.py --check
mvn package
```

Keep changes focused and preserve public API compatibility. Regenerate
contract-derived files instead of editing them by hand. Preserve license
notices and attribution in contributions.
