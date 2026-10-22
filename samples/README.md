# Connector SDK samples

Runnable sample connectors that show the typical ways of building a connector with the SDK.
Every sample has a contract test based on `ConnectorContractTests` and runs without external
systems (temporary files, an in-process HTTP server or an in-memory HSQLDB database).

| Sample | Connector name | Shows |
|---|---|---|
| [`csv`](csv) | `sample-csv` | Implementing all operations from scratch on top of the core API only: schema, filtering with `FilterEvaluator`, paging cookies, rename, error mapping, locking and atomic file updates. |
| [`rest`](rest) | `sample-rest` | A connector for a proprietary JSON API using the REST helpers (`RestClient`, `Json`, `PageCookies`): filter push-down with client-side evaluation, read-modify-write updates, multi-valued attributes. |
| [`scim`](scim) | `sample-scim-enterprise` | Customizing the generic `ScimConnector` through its protected hooks: object type mapping, the SCIM enterprise extension, derived attributes. |
| [`jdbc`](jdbc) | `sample-jdbc-softdelete` | Customizing the generic `JdbcConnector`: soft delete with a status column, extra search conditions, custom SQL via `execute`/`prepare`. |

Build and test all samples from the `sdk` directory:

```shell
./gradlew :dirx-dxi-connector-sdk-sample-csv:build :dirx-dxi-connector-sdk-sample-rest:build \
          :dirx-dxi-connector-sdk-sample-scim:build :dirx-dxi-connector-sdk-sample-jdbc:build
```

Each connector is registered for `java.util.ServiceLoader` in
`src/main/resources/META-INF/services/solutions.dirx.identity.connector.sdk.api.Connector` and is
annotated with `@ConnectorInfo`; this is all that is needed for DirX Identity to discover it.

The samples are not published as Maven artifacts.
