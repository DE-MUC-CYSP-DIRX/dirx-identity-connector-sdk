# Contributing

Thank you for your interest in the DirX Identity Connector SDK.

## Reporting issues

Use the issue tracker of the public repository. Please include the SDK version, the Java version, a
description of the expected and the actual behavior and, if possible, a minimal reproducer.
Report security vulnerabilities **privately** as described in [SECURITY.md](SECURITY.md).

## Pull requests

1. Discuss larger changes in an issue first.
2. Fork the repository and create a topic branch.
3. Follow the conventions below; `./gradlew build` must pass (it runs the tests, the javadoc build and
   the convention checks).
4. Add tests and a [CHANGELOG](CHANGELOG.md) entry under *Unreleased*.
5. Sign off your commits (`git commit -s`) to certify the
   [Developer Certificate of Origin](https://developercertificate.org/).

By contributing you agree that your contribution is licensed under the Apache License 2.0.

## Conventions

- Java 21, no compiler warnings (`-Xlint:all -Werror`).
- Every Java source file starts with the Apache-2.0 license header used in the existing files.
- Packages live below `solutions.dirx.identity.connector.sdk`.
- `dirx-dxi-connector-sdk-api` depends on the JDK only. Helper modules keep dependencies minimal and
  Apache-2.0 compatible.
- Logging uses `java.lang.System.Logger`.
- Public API changes must be backward compatible within a major version (see
  [docs/api-design.md](docs/api-design.md)); document every public type and method with Javadoc.
