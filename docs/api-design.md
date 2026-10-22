# DirX Identity Connector SDK – API Design

Status: draft v1 · Java 21 · License: AGPL-3.0-only

## 1. Goals

* Let customers write a provisioning connector for DirX Identity in plain Java,
  **without** depending on any DirX Identity internal library.
* Keep the core API **dependency-free** (JDK only), small and stable.
* Make connectors testable outside DirX Identity (unit tests, contract test kit,
  standalone runner).
* Offer optional helper modules for the most common target types
  (REST/JSON, SCIM 2.0, JDBC).

Non-goals for v1: event/change listening (sync), reconciliation scheduling,
UI/configuration editors, migration of existing DirX Identity connectors.

## 2. Modules and coordinates

| Module (artifactId) | Package | Dependencies |
|---|---|---|
| `dirx-dxi-connector-sdk-api` | `solutions.dirx.identity.connector.sdk.api[.model\|.config\|.spi]` | JDK only |
| `dirx-dxi-connector-sdk-test` | `solutions.dirx.identity.connector.sdk.test` | api, JUnit 5 (API) |
| `dirx-dxi-connector-sdk-rest` | `solutions.dirx.identity.connector.sdk.rest` | api, `java.net.http`, Jackson |
| `dirx-dxi-connector-sdk-scim` | `solutions.dirx.identity.connector.sdk.scim` | api, rest |
| `dirx-dxi-connector-sdk-jdbc` | `solutions.dirx.identity.connector.sdk.jdbc` | api, `java.sql` |
| `dirx-dxi-connector-sdk-bom` | – | BOM aligning all module versions |

groupId: `solutions.dirx.identity`. All public types live below
`solutions.dirx.identity.connector.sdk`. This rule is enforced by a build check.

## 3. Lifecycle and threading

```java
public interface Connector extends AutoCloseable {
    void init(ConnectorContext context) throws ConnectorException;
    @Override void close();
}
```

* The host creates the connector through its **public no-arg constructor**. It then
  calls `init` exactly once, followed by any number of operations, and finally `close`.
* **One instance is used by one thread at a time.** A multi-threaded host,
  DirX Identity included, creates one instance per worker thread. Connectors therefore do
  not need internal synchronization. Expensive resources such as HTTP clients and
  connection pools can be shared through static, thread-safe holders if needed.
* `init` must fail fast on invalid configuration, using `ErrorCode.CONFIGURATION`.
  It must throw `RetryableConnectorException` if the target is only temporarily
  unreachable.

## 4. Operations (capability interfaces)

A connector implements only the operations it supports. The host discovers
capabilities via `instanceof`. Unsupported operations surface as
`ErrorCode.UNSUPPORTED_OPERATION`.

| Interface | Method | Notes |
|---|---|---|
| `CreateOp` | `CreateResult create(CreateRequest r)` | returns the final identifier, plus optional generated attributes |
| `UpdateOp` | `UpdateResult update(UpdateRequest r)` | attribute-level modifications; may return a new identifier (rename) |
| `DeleteOp` | `void delete(DeleteRequest r)` | |
| `SearchOp` | `SearchResult search(SearchRequest r, ResultHandler h)` | streaming via handler, optional paging |
| `SchemaOp` | `Schema schema()` | optional schema discovery |
| `TestOp` | `void test()` | connectivity check used by "test connection" |

The request and result types are immutable records. New optional fields can be added in
minor versions without breaking implementors.

```java
record CreateRequest(String objectType, Identifier identifier,
                     List<Attribute> attributes, OperationOptions options) {}
record UpdateRequest(String objectType, Identifier identifier,
                     List<Modification> modifications, OperationOptions options) {}
record DeleteRequest(String objectType, Identifier identifier, OperationOptions options) {}
record SearchRequest(String objectType, Identifier base, SearchScope scope,
                     SearchFilter filter /* null = all */,
                     Set<String> attributesToGet /* empty = default set */,
                     int pageSize /* 0 = unpaged */, String pageCookie,
                     OperationOptions options) {}
```

`ResultHandler.handle(ConnectorObject)` returns `false` to stop the search early.
`SearchResult` carries an optional `nextPageCookie` (`null` means no more pages).

Password changes are ordinary `REPLACE` modifications of an attribute whose values are
`SecretValue`s. Batch requests are split by the host into single operations, so
connectors need not implement batching.

## 5. Data model (`...api.model`)

* **`Identifier(String value, IdentifierType type)`**: the identifier of an object in
  the target. `IdentifierType` is one of `DN, GUID, URN, EMAIL, OID, GENERIC`.
* **`Attribute(String name, List<Object> values)`**
  * Allowed value types are `String`, `byte[]`, `Boolean`, `Long`, `Integer` and
    `SecretValue`.
  * Name comparison is case-insensitive.
  * Helpers: `Attribute.of(name, values...)`, `firstString()`, `strings()`, `isEmpty()`.
* **`Modification(Operation op, String attributeName, List<Object> values)`**
  * `Operation` is one of `ADD`, `REPLACE`, `DELETE`.
  * `DELETE` with no values removes the whole attribute.
* **`ConnectorObject(String objectType, Identifier identifier, List<Attribute> attributes)`**
  * Provides case-insensitive `attribute(name)` lookup and a builder.
* **`SearchFilter`**: a sealed AST.
  * Node types: `And`, `Or`, `Not`, `Equals`, `Present`, `Substring(initial, any[], final)`,
    `GreaterOrEqual`, `LessOrEqual`, `Approx`.
  * The `Filters` factory builds filters, and `SearchFilter.toLdapString()` renders them
    for debugging.
  * Connectors translate filters with Java 21 pattern-matching `switch`, or with
    `FilterTranslator<T>` for reuse.
* **`SearchScope`**: one of `OBJECT`, `ONE_LEVEL`, `SUBTREE`.
* **`OperationOptions`**: a case-insensitive, read-only multi-value map of per-request
  options supplied by the host, such as `pagesize`, `sortattribute` and `sortorder`.
* **`Schema` / `ObjectClassInfo` / `AttributeInfo`**: the result of schema discovery.
  `AttributeInfo` carries name, type, multi-valued, required, creatable, updatable and
  readable flags.

## 6. Configuration and secrets (`...api.config`)

`ConnectorContext` provides:

```java
String instanceName();
ConnectorConfiguration configuration();
System.Logger logger(Class<?> c);     // routed by the host to its logging system
```

`ConnectorConfiguration` provides:

* Typed access to properties:
  * `Optional<String> get(name)`, `String require(name)`
  * `int getInt(name, def)`, `boolean getBoolean(name, def)`, `Duration getDuration(name, def)`
  * `List<String> getList(name)` for multi-value properties
  * `Optional<SecretValue> getSecret(name)`
  * `Set<String> names()`
* `ConnectionSettings connection()`: the standard endpoint settings. These are `url`,
  `server`, `port`, `user`, `password` (a `SecretValue`), `ssl`, `keyStore*` and `trustStore*`.

`SecretValue` (package `...api.model`, because secrets also appear as attribute values):

* `toString()` always returns `****`.
* `reveal()` returns the clear text, and `clear()` wipes it.
* The host decrypts secrets **before** handing them to the connector. A connector never
  sees host-specific encryption formats.
* Secrets in arbitrary properties are resolved by the host through the
  `spi.SecretResolver` hook.

`MapConnectorConfiguration` is a simple map-backed implementation for tests and
standalone use.

## 7. Errors

* `ConnectorException(ErrorCode code, String message, Throwable cause)` is a checked
  exception.
* `RetryableConnectorException extends ConnectorException` marks a temporary failure.
  The host may retry the operation later.

`ErrorCode` values:

* `ALREADY_EXISTS`, `NOT_FOUND`
* `ATTRIBUTE_OR_VALUE_EXISTS`, `NO_SUCH_ATTRIBUTE`, `SCHEMA_VIOLATION`
* `INVALID_REQUEST`, `UNSUPPORTED_OPERATION`
* `INVALID_CREDENTIALS`, `PERMISSION_DENIED`
* `CONNECTION_FAILED`, `TIMEOUT`, `CONFIGURATION`, `OTHER`

## 8. Discovery and metadata (`...api.spi`)

* Connectors are registered through `ServiceLoader`, in
  `META-INF/services/solutions.dirx.identity.connector.sdk.api.Connector`.
* Each connector is annotated with
  `@ConnectorInfo(name, version, displayName, description, objectTypes)`.
* `ConnectorRegistry` lists the available connectors and finds one by `name` or class
  name, without instantiating anything. It returns `ConnectorRegistry.Descriptor` records;
  `Descriptor.newInstance()` creates the connector.
* `SecretResolver` is a host-side SPI for resolving encrypted property values.

## 9. Compatibility rules

* The SDK uses semantic versioning. The `api` module keeps binary compatibility within a
  major version.
* Capability interfaces are never changed incompatibly. New capabilities are added as
  new interfaces.
* Records only receive new components together with a compatible secondary constructor.
