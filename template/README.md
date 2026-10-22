# Connector template

Starting point for a DirX Identity connector built with the Connector SDK. It contains a
complete, tested connector (`ExampleConnector`) that talks to a stand-in client
(`ExampleClient`). Replace the client with the API of your target system.

## Use the template

1. Copy this directory and rename the project in `settings.gradle.kts` (or `pom.xml`),
   the package `com.example.connector` and the connector class.
2. Update `src/main/resources/META-INF/services/solutions.dirx.identity.connector.sdk.api.Connector`
   to the new class name. DirX Identity finds connectors through this file.
3. Adapt `@ConnectorInfo`, implement the operations against your target system, and
   remove the operation interfaces it does not support.
4. Adapt `ExampleConnectorContractTest`. The SDK contract tests check the behavior DirX
   Identity relies on, for example the error codes for missing or existing objects.

Both a Gradle and a Maven build are included. Keep the one you prefer.

| | Gradle | Maven |
|---|---|---|
| Build and test | `gradle build` | `mvn verify` |
| Connector bundle | `build/connector/<name>-<version>-connector.zip` | `target/<name>-<version>-connector.zip` |
| SDK version | `connectorSdkVersion` in `gradle.properties` | `connector.sdk.version` in `pom.xml` |
| Extra SDK repository | `-PconnectorSdkRepository=<url>` | `-Dconnector.sdk.repository=<url>` |

The template has no Gradle wrapper. Create one with `gradle wrapper` or use an installed
Gradle 9.

## Dependencies

* `dirx-dxi-connector-sdk-api` is provided by DirX Identity. It is a compile-only
  (`provided`) dependency and is never part of the bundle.
* Helper modules (`-rest`, `-scim`, `-jdbc`) and your own libraries are bundled.
* Use the SDK BOM to keep all SDK module versions aligned.

## Deploy

The bundle contains one directory with the connector jar and its runtime dependencies.
Deploy its content as described in the developer guide, and configure a connector of
type "SDK Connector" with the connector name from `@ConnectorInfo` or the class name.

Recommended steps:

1. Unzip the bundle on the DirX Identity server into `<IdS-J install>/connectors/`. This creates
   the directory `connectors/<name>/`.
2. In the connector configuration, set **Connector Bundle Path** (`sdkConnectorPath`) to that
   directory. Relative paths are resolved against the IdS-J working directory.
3. Set **SDK Connector Name** (`sdkConnectorName`) or **SDK Connector Class** (`sdkConnectorClass`).
4. Restart IdS-J after you replace a bundle. Loaded bundles are cached until restart.

The connector is loaded in an isolated, child-first class loader, so your libraries take
precedence over the libraries of the server. Only the JDK and the SDK API are shared.
