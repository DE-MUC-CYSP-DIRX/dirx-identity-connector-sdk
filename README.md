# DirX Identity Connector SDK

Build provisioning connectors for [DirX Identity](https://www.dirx.solutions) in plain Java 21,
without depending on DirX Identity internals.

* **Small, stable core API.** It uses only the JDK and has no third-party dependencies.
* **Helper modules** for REST/JSON, SCIM 2.0 and JDBC targets.
* **Test kit** with reusable contract tests, so you can run your connector without DirX Identity.
* **Template and samples** that build with Gradle or Maven.

Licensed under the [Apache License 2.0](LICENSE).

## Modules

All artifacts use the groupId `solutions.dirx.identity`.

| Artifact | Purpose | Dependencies |
|---|---|---|
| `dirx-dxi-connector-sdk-api` | Connector API, data model, configuration, errors, discovery | JDK only |
| `dirx-dxi-connector-sdk-test` | `ConnectorTestKit`, `ConnectorContractTests`, `InMemoryConnector` | api, JUnit 5 |
| `dirx-dxi-connector-sdk-rest` | `RestClient` with OAuth2, API key and basic authentication, TLS, retries, JSON and paging helpers | api, Jackson |
| `dirx-dxi-connector-sdk-scim` | Generic, extensible `ScimConnector` (SCIM 2.0) | api, rest |
| `dirx-dxi-connector-sdk-jdbc` | Generic, extensible `JdbcConnector` | api |
| `dirx-dxi-connector-sdk-bom` | Aligns the versions of all modules | – |

## Quick start

1. Copy [`template/`](template) and follow its README.
2. Implement your connector. The [developer guide](docs/developer-guide.md) explains how.
3. Run `gradle build` or `mvn verify`. This runs the contract tests and produces
   `<name>-<version>-connector.zip`.
4. Deploy the zip to DirX Identity and configure a connector of type **SDK Connector**.

A minimal connector:

```java
@ConnectorInfo(name = "hello", version = "1.0.0", objectTypes = "Account")
public class HelloConnector implements Connector, CreateOp {
    @Override public void init(ConnectorContext context) throws ConnectorException { }
    @Override public CreateResult create(CreateRequest request) throws ConnectorException {
        // call your target system here
        return CreateResult.of(request.identifier());
    }
    @Override public void close() { }
}
```

## Documentation

* [Developer guide](docs/developer-guide.md): concepts, operations, configuration, errors, testing, packaging and deployment.
* [API design](docs/api-design.md): the API contract and compatibility rules.
* [Samples](samples/README.md): CSV, REST, SCIM and JDBC connectors.
* [Changelog](CHANGELOG.md)

## Building the SDK

You need JDK 21 or newer. Run these from this directory:

```shell
git clone https://github.com/DE-MUC-CYSP-DIRX/dirx-identity-connector-sdk.git
cd dirx-identity-connector-sdk
./gradlew build            # compile, test, check conventions
./gradlew verifyTemplate   # build the template against the locally published SDK
./gradlew publishAllPublicationsToStagingRepository   # artifacts in build/staging-repo
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.
This is a standalone repository: no DirX Identity checkout or internal build
credentials are required. Product-specific adapters and factory data are
maintained separately and are not part of this Apache-2.0 SDK.

To use a Maven mirror, set `SDK_MAVEN_MIRROR_URL` (and optionally `SDK_MAVEN_MIRROR_USERNAME`
and `SDK_MAVEN_MIRROR_PASSWORD`).

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md). Report vulnerabilities as described in
[SECURITY.md](SECURITY.md). Do not use public issues for them.
