# Installation and updates

The SDK uses Maven coordinates `com.raims:raims-java` and requires Java 11 or
newer.

## Maven

Add this dependency to your project's `pom.xml`. Maven uses Central by default:

```xml
<dependency>
  <groupId>com.raims</groupId>
  <artifactId>raims-java</artifactId>
  <version>0.1.1</version>
</dependency>
```

## Gradle

For Groovy DSL, add Maven Central and the dependency to `build.gradle`:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.raims:raims-java:0.1.1'
}
```

For Kotlin DSL, use `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.raims:raims-java:0.1.1")
}
```

Both build tools resolve the runtime dependencies from the SDK's POM. Editors
can also download its sources and Javadoc from Maven Central.

## GitHub release downloads

[GitHub releases](https://github.com/raimshq/raims-java/releases/latest) include
the JAR, POM, sources, Javadoc, a manifest and `SHA256SUMS`.

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

### Install a downloaded release locally

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

Choose a released version, update it in your Maven or Gradle dependency and
run your integration tests. Use a fixed version so that builds keep using the
version you have tested.

For GitHub downloads, download the release into another folder, verify its
hashes and repeat the local installation before updating the dependency.
Downloading a release does not update existing projects automatically.

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

The package, sources and Javadoc are written to the directory configured by
`raims.build.directory` in `pom.xml`. Use the generated JAR and POM together
when installing the library in your local Maven repository.
