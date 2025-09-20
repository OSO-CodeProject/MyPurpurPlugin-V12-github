# MyPurpurPlugin

[![CI](https://github.com/OWNER/MyPurpurPlugin-V12-github/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/MyPurpurPlugin-V12-github/actions/workflows/ci.yml)

A Purpur plugin project.

## Tests

Unit tests run with the standard `./gradlew test` command. A heavier integration test that
launches a real Purpur server is tagged with `realServer` and is skipped unless explicitly
enabled. To execute it locally:

1. Download the desired Purpur server jar manually.
2. Set the `PURPUR_SERVER_JAR` environment variable (or the `purpur.serverJar` system property)
   to point at the downloaded jar.
3. Run `./gradlew test -PenableRealServerTests=true`.

The GitHub Actions workflow only runs the lightweight unit tests, preventing timeouts during
routine validation.
