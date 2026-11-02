# Installation and updates

The SDK uses Maven coordinates `com.raims:raims-java` and requires Java 11 or
newer. The first distribution is on
[GitHub](https://github.com/raimshq/raims-java/releases/latest). It includes the
JAR, POM, sources, Javadoc, a manifest and `SHA256SUMS`. The package is not yet
available on Maven Central.

## Download the current release

Use GitHub CLI to download all assets from the current stable release into a
new folder:

```sh
gh release download --repo raimshq/raims-java --pattern '*' --dir raims-java-release
```

You can also download files from the releases page. Asset names stay the same
between versions; the POM and manifest identify the version and source.
Before installing, compare the hashes with `SHA256SUMS`. On systems that
provide `sha256sum`, run this command in the downloaded folder:

```sh
sha256sum -c SHA256SUMS
```

In PowerShell, use `Get-FileHash -Algorithm SHA256` to check each file.
Checksums detect differences between files; also verify the official repository,
tag and release commit.

## Install in your local Maven repository

Run this command from the folder containing `raims-java-release`:

```sh
mvn install:install-file -Dfile=raims-java-release/raims-java.jar -DpomFile=raims-java-release/raims-java.pom
```

Add a `com.raims:raims-java` dependency to the consuming project, using the
version declared in the downloaded POM. The POM retains the SDK dependencies,
which Maven resolves when building the application. Do not copy only the JAR
to the classpath without resolving those dependencies.

The `raims-java-sources.jar` and `raims-java-javadoc.jar` assets let you inspect
code and documentation in your editor. Your development environment can attach
them to the library.

## Update

Download the current stable release into another folder, verify its hashes
and repeat the local installation. Then update the dependency version in the
consuming project and run your integration tests. The download selects the
current release at that moment; it does not update projects automatically.

Use the SDK on the server. Keep the API key outside source code, the browser
and logs. The base URL is the same in both environments; the company,
environment and permissions come from the key validated by the API.

## Build and validate source

Contributors can check generation and prepare a package with Python, Maven
and a compatible JDK:

```sh
python scripts/generate.py --check
mvn package
```

The package, sources, and Javadoc are written to
`../.tmp/raims-java-sdk/build`. Use the generated JAR and POM together when
installing the library in your local Maven repository.
