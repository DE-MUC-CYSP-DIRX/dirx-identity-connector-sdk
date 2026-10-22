# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - unreleased

### Added

- `dirx-dxi-connector-sdk-api`: JDK-only connector API (lifecycle, create/update/delete/search/test/schema
  operations, object model, filters, configuration, secrets, error codes, ServiceLoader discovery).
- `dirx-dxi-connector-sdk-test`: contract tests, test kit and in-memory reference connector.
- `dirx-dxi-connector-sdk-rest`: REST client with Basic, Bearer, API key and OAuth 2.0 client-credentials
  authentication, retries, paging helpers and HTTP error mapping.
- `dirx-dxi-connector-sdk-scim`: generic SCIM 2.0 connector.
- `dirx-dxi-connector-sdk-jdbc`: generic table-based JDBC connector.
- `dirx-dxi-connector-sdk-bom`: bill of materials.
- Samples (CSV, REST, SCIM, JDBC) and a Gradle/Maven project template.
