# Security policy

## Supported versions

Security fixes are provided for the latest minor release of each supported major version.

## Reporting a vulnerability

Please do **not** open a public issue. Report vulnerabilities privately through the repository's
"Report a vulnerability" function (GitHub private vulnerability reporting) or to the Atos product
security team (PSIRT). Include the affected version, a description and, if possible, a proof of concept.

We will acknowledge your report, keep you informed about the progress and credit you in the advisory
unless you prefer otherwise.

## Guidance for connector developers

- Treat `SecretValue`s as sensitive: reveal them only where needed and never log them.
- Validate TLS certificates; do not disable host name verification in production.
- Use parameterized queries and encode filter values for the target query language.
