# Contributing

Vedra is shared as an unfinished prototype. Start with the [project status](docs/PROJECT_STATUS.md) and [roadmap](TODO.md); historical specifications may describe work that is not implemented.

Use JDK 24 and the checked-in Gradle Wrapper. Run `./gradlew check bootJar` before proposing a change. Apply formatting explicitly with `./gradlew ktlintFormat`. Tests should cover behavior and mock external services where appropriate; live acceptance requires a disposable workspace and synthetic data.

Keep changes focused and describe the problem, resulting behavior and verification. Never include `.env`, Slack credentials, real conversation payloads, database dumps or IDE-specific files. Run `gitleaks git --redact .` when available; this scans committed history, so also review staged changes before committing.

The repository currently has no license. Resolve licensing with the owner before assuming reuse or contribution terms.
