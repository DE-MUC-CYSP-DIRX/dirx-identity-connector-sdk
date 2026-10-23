# Developer guide

This guide explains how to write, test, package and deploy a DirX Identity connector with the
Connector SDK. The formal API contract is in [api-design.md](api-design.md).

## 1. How DirX Identity uses your connector

DirX Identity provisions identities, accounts and groups through *connectors*. A Java
real-time workflow uses a connector of type **SDK Connector** to call any connector built
with this SDK:

```
DirX Identity workflow ──► SDK Connector (host adapter) ──► your Connector ──► target system
```

The host adapter translates DirX Identity requests into the SDK model and back.

* An *add*, *modify*, *delete* or *search* request becomes a `CreateRequest`, `UpdateRequest`,
  `DeleteRequest` or `SearchRequest`.
* Your `ConnectorException`s become provisioning errors. Workflows react to them, for example
  by modifying an existing object when a create fails with `ALREADY_EXISTS`.
* Encrypted passwords and other secrets are decrypted before your connector sees them, and they
  are passed as `SecretValue`s.

Your connector depends only on `dirx-dxi-connector-sdk-api`, which DirX Identity provides at
runtime.

## 2. Getting started

1. Copy [`template/`](../template). It builds with Gradle or with Maven.
2. Rename the project, the package and `ExampleConnector`.
3. Update `META-INF/services/solutions.dirx.identity.connector.sdk.api.Connector`.
4. Replace `ExampleClient` with the client of your target system.
5. Run `gradle build` or `mvn verify`.

The [samples](../samples/README.md) show complete connectors for CSV files, a proprietary
REST API, SCIM 2.0 and JDBC.

## 3. Connector lifecycle

```java
@ConnectorInfo(name = "acme", version = "1.0.0", displayName = "ACME HR",
        description = "Provisions ACME HR accounts", objectTypes = {"Account", "Group"})
public class AcmeConnector implements Connector, CreateOp, UpdateOp, DeleteOp, SearchOp, TestOp {
    public AcmeConnector() { }                       // public no-arg constructor
    @Override public void init(ConnectorContext context) throws ConnectorException { /* ... */ }
    @Override public void close() { /* ... */ }      // release resources, never throw
    // operations ...
}
```

* The host calls the constructor, then `init` once, then any number of operations, and
  finally `close`.
* **An instance is never used by two threads at the same time.** The host creates one
  instance per worker thread, so you need no locking inside the instance. You can share
  expensive, thread-safe resources (HTTP clients, connection pools) through static holders,
  keyed by the configuration.
* `init` validates the configuration and connects.
  * Throw `ConnectorException(ErrorCode.CONFIGURATION, ...)` for invalid settings.
  * Throw `RetryableConnectorException` if the target is temporarily unavailable. The host
    then retries later instead of failing the workflow.

### Discovery

DirX Identity finds connectors through `java.util.ServiceLoader`. You need two things:

* The file `META-INF/services/solutions.dirx.identity.connector.sdk.api.Connector` with the fully
  qualified class name. Use one line per connector if a jar contains several.
* The `@ConnectorInfo` annotation. Its `name` is the value an administrator enters as the
  **SDK Connector Name**. Choose a stable, lowercase name, such as `acme-hr`.

## 4. Operations

Implement only the operation interfaces that your target supports. DirX Identity reports
calls to missing operations as "unsupported operation".

| Interface | Implement it to |
|---|---|
| `CreateOp` | create objects. Return the final identifier, for example the generated ID, with `CreateResult.of(id)`. |
| `UpdateOp` | apply attribute modifications. Return `UpdateResult.unchanged()`, or `UpdateResult.renamed(newId)` if the identifier changed. |
| `DeleteOp` | delete objects. |
| `SearchOp` | read and search objects. Stream them to the `ResultHandler`, and return `SearchResult.done()` or `SearchResult.nextPage(cookie)`. |
| `TestOp` | do a cheap connectivity check. |
| `SchemaOp` | describe object types and attributes (optional). |

### Object types and identifiers

SDK packages are `@NullMarked` using JSpecify: values are non-null unless annotated
`@Nullable`. The JSpecify annotations are exposed on the consumer compile API so connectors
can use these contracts in IDEs and static nullness checkers; they add no runtime behavior.

* `request.objectType()` names the kind of object, such as `Account` or `Group`. It is
  `null` if neither the workflow nor the connector configuration (`defaultObjectType`) sets
  one. Reject object types you do not support with `UNSUPPORTED_OPERATION`.
* `Identifier` is the object's key in the target system. Its `IdentifierType` is one of
  `DN`, `GUID`, `URN`, `EMAIL`, `OID` or `GENERIC`. Use whatever is natural for the target,
  and return the same form from `search`.

### Attributes and modifications

* Attribute names compare case-insensitively.
* Values are `String`, `byte[]`, `Boolean`, `Long`, `Integer` or `SecretValue`.
* `Modification.Operation`:
  * `ADD` adds values. If a value already exists, report `ATTRIBUTE_OR_VALUE_EXISTS`.
  * `REPLACE` replaces all values. An empty list removes the attribute.
  * `DELETE` removes the given values. With no values, it removes the whole attribute. Report
    `NO_SUCH_ATTRIBUTE` if the attribute or value does not exist.
* A password change is a `REPLACE` with a `SecretValue`. Call `reveal()` only where you need
  the clear text, and never log it. `SecretValue.toString()` prints `****`.

### Search

* `scope()` is one of these:
  * `OBJECT`: read the single object identified by `base()`. Return nothing if it does not
    exist; this is not an error.
  * `ONE_LEVEL` or `SUBTREE`: search below `base()`, or everything if `base()` is `null`.
    For flat targets the two are the same.
* `filter()` is `null` (everything) or a `SearchFilter` tree. You can handle it in three ways:
  * Push it down to the target. Implement `FilterTranslator<T>` and throw
    `UNSUPPORTED_OPERATION` from the parts the target cannot express.
  * Evaluate it locally with `FilterEvaluator.matches(filter, object)`.
  * Combine both. Push down what you can, and evaluate the full filter locally afterwards.
* `attributesToGet()` is empty for the default set of attributes. Use
  `request.isRequested(name)` to check whether an attribute is needed.
* **Paging.** If `pageSize()` is greater than 0, return at most that many objects. Then
  return `SearchResult.nextPage(cookie)`. The cookie is an opaque string, for example an
  offset or the target's continuation token, and comes back in `pageCookie()`. The REST
  module's `PageCookies` encodes offsets and tokens for you.
* Stop as soon as `ResultHandler.handle(...)` returns `false`.

## 5. Configuration

`ConnectorContext.configuration()` gives you the settings that the administrator entered for
the connector in DirX Identity.

```java
ConnectorConfiguration cfg = context.configuration();
ConnectionSettings conn = cfg.connection();        // url, server, port, user, password, ssl, key/trust store
String url   = conn.urlValue().orElseThrow(() -> new ConnectorException(ErrorCode.CONFIGURATION, "url missing"));
int    size  = cfg.getInt("pageSize", 100);
Duration t   = cfg.getDuration("timeout", Duration.ofSeconds(30));
List<String> groups = cfg.getList("defaultGroups");
Optional<SecretValue> apiKey = cfg.getSecret("apiKey");
```

* **Connection settings** come from the connected directory and the bind profile in DirX
  Identity: server, port, user, password and SSL. The connection `url` is entered directly.
* **Custom properties** are added to the connector configuration as name/value pairs, and
  you read them with `get`, `getInt` and so on. Property names compare case-insensitively.
* **Secrets.** `password`, `keyStorePassword` and `trustStorePassword` are always secrets.
  To mark other properties as secrets, list them in the connector property
  `sdkSecretProperties`. Read them with `getSecret`.
* `MapConnectorConfiguration.builder()` creates configurations for tests and standalone use.

Document every property your connector supports, for example in its README.

## 6. Errors

Throw `ConnectorException` with the `ErrorCode` that matches the situation. DirX Identity
workflows depend on these codes.

| Situation | `ErrorCode` |
|---|---|
| The object to create already exists | `ALREADY_EXISTS` |
| The object to update, delete or read does not exist | `NOT_FOUND` |
| A value to add already exists | `ATTRIBUTE_OR_VALUE_EXISTS` |
| An attribute or value to delete does not exist | `NO_SUCH_ATTRIBUTE` |
| The target rejects the data (missing or invalid attribute) | `SCHEMA_VIOLATION` |
| The request makes no sense, for example a missing identifier or an invalid cookie | `INVALID_REQUEST` |
| Operation, object type or filter not supported | `UNSUPPORTED_OPERATION` |
| Authentication failed | `INVALID_CREDENTIALS` |
| Not authorized | `PERMISSION_DENIED` |
| Target unreachable | `CONNECTION_FAILED` (consider `RetryableConnectorException`) |
| Timeout | `TIMEOUT` |
| Invalid connector configuration | `CONFIGURATION` |
| Anything else | `OTHER` |

* Use `RetryableConnectorException` for temporary problems, such as HTTP 429/503 or a lost
  connection. Make sure retrying the operation is safe.
* Unexpected runtime exceptions are reported as `OTHER`, and their stack trace is logged.
* The helper modules map errors for you: `HttpErrorMapper`, `ScimErrorMapper` and
  `SqlErrorMapper`.
* Never include secrets in error messages.

## 7. Logging

Use `context.logger(MyConnector.class)`, which returns a `java.lang.System.Logger`. DirX
Identity routes these messages to its server log. Log at `DEBUG` or `TRACE` for request
details, and never log `SecretValue.reveal()`.

## 8. Helper modules

### REST (`dirx-dxi-connector-sdk-rest`)

`RestClient.fromConfiguration(...)` builds a client for JSON APIs. It is based on
`java.net.http` and Jackson, and provides the following:

* Authentication through `authType`: basic (user/password from the connection), bearer, API
  key (`apiKeyHeader`) or OAuth 2.0 client credentials (`tokenUrl`, `scope`,
  `clientCredentialsInBody`).
* TLS with the key store and trust store from the connection settings.
* Timeouts (`connectTimeout`, `requestTimeout`) and retries (`maxRetries`) for 429/503
  responses and connection errors.
* `Json` helpers, `PageCookies` for offset and token paging, and `HttpErrorMapper`.

See the `rest` sample.

### SCIM 2.0 (`dirx-dxi-connector-sdk-scim`)

`ScimConnector` is a complete SCIM 2.0 connector for Users and Groups. You can use it
directly or extend it through its protected hooks: endpoint and object type mapping,
attribute conversion, and request customization.

| Property | Meaning |
|---|---|
| `scim.endpoints` | Object type to endpoint mapping, for example `Account=/Users,Group=/Groups` |
| `scim.multiValuedAttributes` | Attributes that are always sent as arrays |
| `scim.nonStringAttributes` | Attributes sent as JSON booleans or numbers |
| `scim.createIdentifierAttribute` | Attribute that receives the identifier on create |
| `scim.updateMethod` | `PATCH` (default) or `PUT` |
| `scim.pageSize` | Default page size |
| `scim.testPath` | Path used by `test()` |

The REST properties apply as well. See the `scim` sample.

### JDBC (`dirx-dxi-connector-sdk-jdbc`)

`JdbcConnector` maps object types to tables. It translates filters to parameterized SQL and
pages with `OFFSET`/`FETCH`.

| Property | Meaning |
|---|---|
| `jdbc.tables` | Object type to table mapping |
| `jdbc.keyColumns` | Key column per table |
| `jdbc.driver` | Optional JDBC driver class |
| `jdbc.queryTimeout` | Query timeout |
| `jdbc.testQuery` | Query used by `test()` |
| `jdbc.paging` | Paging mode |
| `jdbc.property.*` | Passed to the JDBC driver |

The connection URL is the JDBC URL. Bundle the JDBC driver with your connector. See the
`jdbc` sample for extension points such as soft delete and custom SQL.

## 9. Testing

Add `dirx-dxi-connector-sdk-test` as a test dependency.

* **Contract tests.** Extend `ConnectorContractTests` and implement `newConnector()`,
  `configuration()`, `objectType()` and `newObject(suffix)`. Override `sampleModification()`,
  `filterMatching(...)` and `pageSize()` as needed. The inherited tests check the behavior
  DirX Identity relies on: create/read/update/delete, error codes for missing and existing
  objects, filters, paging and early termination. Run them against a test instance of your
  target, or against a local fake.
* **Test kit.** `ConnectorTestKit.open(connector, configuration)` gives you a convenient,
  synchronous API (`create`, `update`, `read`, `search`, `searchAll`, `delete`) for your own
  tests. `TestConnectorContext` captures log output.
* **In-memory connector.** `InMemoryConnector` is a reference implementation of the contract.
  It is useful when you test code that consumes connectors.

## 10. Packaging and deployment

The template builds a **connector bundle**, `<name>-<version>-connector.zip`. It contains a
directory `<name>/` with your jar and all its runtime dependencies (helper modules,
libraries, JDBC drivers). It does not contain the SDK API, which DirX Identity provides.

To deploy it:

1. Unzip the bundle on the DirX Identity server into `<IdS-J install>/connectors/`. This
   creates the directory `connectors/<name>/`.
2. In the DirX Identity Manager, create a Java real-time workflow whose connector is of type
   **SDK Connector**, and configure it:

   | Property | Value |
   |---|---|
   | SDK Connector Name | the `@ConnectorInfo` name (or set **SDK Connector Class** instead) |
   | Connector Bundle Path | `connectors/<name>` (relative to the IdS-J working directory, or absolute) |
   | Default Object Type | the object type used when the workflow does not send one |
   | Secret Properties | comma-separated names of custom properties that contain secrets |
   | Secret Attributes | attributes besides `userPassword` whose values are secrets |
   | Connection | URL, or server/port from the connected directory and user/password from the bind profile |

   Add your custom properties to the connector configuration.
3. Restart the Java-based Identity Server (IdS-J) after you install or replace a bundle.

The bundle is loaded in an **isolated, child-first class loader**. Your libraries take
precedence over those of the server, and only the JDK and the SDK API are shared. Several
connector configurations that point to the same bundle share one class loader. The thread
context class loader is set to the bundle loader during every call, so libraries that use
`ServiceLoader` (JDBC drivers, JSON providers) work as expected.

## 11. Compatibility and versioning

* The SDK follows semantic versioning. Connectors built against `1.x` run on every DirX
  Identity release that ships SDK API `1.y` with `y >= x`.
* New capabilities are added as new interfaces. Request records get new optional components
  without breaking existing connectors.
* Build against the lowest SDK version that has the features you need. Use the BOM to align
  module versions.

## 12. Checklist

- [ ] Public no-arg constructor, `@ConnectorInfo` and the `META-INF/services` entry
- [ ] `init` fails fast with `CONFIGURATION` and uses `RetryableConnectorException` for outages
- [ ] Only the supported operation interfaces are implemented
- [ ] `ALREADY_EXISTS` and `NOT_FOUND` are reported correctly; other errors are mapped
- [ ] Search honors scope, filter, `attributesToGet`, paging and early termination
- [ ] Secrets are never logged or included in messages
- [ ] Contract tests pass
- [ ] Supported properties are documented
