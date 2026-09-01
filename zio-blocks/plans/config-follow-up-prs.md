# Config Follow-up PR Plan

> This document turns the config assessment roadmap into concrete follow-up PR/issue-sized work items.

---

## PR 1 — Config Source Ecosystem and Secret File Ergonomics

### Working title

`feat(config): add env, secrets-dir, and _FILE config sources`

### Goal

Expand the source ecosystem so the config module is usable in real deployment environments without custom glue.

### Scope

- Add `ConfigSource.fromEnv(...)`
- Make the system-properties source a clearly documented canonical public entrypoint
- Add `ConfigSource.fromSecretsDir(...)`
- Add `_FILE` convention support for file-backed secret indirection
- Preserve provenance through all of the above
- Keep default secret reporting redacted

### Acceptance criteria

- environment variables can be loaded as a first-class `ConfigSource`
- a secrets directory can be loaded as a first-class `ConfigSource`
- `_FILE` indirection works for secret-like values
- precedence and composition are documented with examples
- path traversal / invalid file cases are tested
- provenance identifies real source keys without leaking secret values

### Non-goals

- Vault / cloud secret manager integration
- hot reload
- profiles / environments

### Why it matters

This closes the largest practical gap against Pydantic Settings, Spring Boot, Dynaconf, and koanf.

---

## PR 2 — Config Diagnostics and Explainability

### Working title

`feat(config): add explain/report APIs for resolved config and failures`

### Goal

Turn provenance into a true operator-facing differentiator.

### Scope

- Add a config explanation/report API
- Improve human-readable load failure reports
- Add machine-readable structured diagnostics
- Explain why a value won when multiple sources are composed
- Ensure secrets stay redacted throughout reports

### Acceptance criteria

- there is a first-class API for explaining resolved config
- there is a first-class API for explaining load failure details
- reports include source/key precedence information
- reports are safe by default for secrets
- docs include examples of using diagnostics in startup and tests

### Non-goals

- HTTP/Actuator endpoints
- framework runtime integration

### Why it matters

This is the path to leapfrogging most libraries on debuggability, especially outside Spring and Figment.

---

## PR 3 — Profiles, Environments, and Layered Composition

### Working title

`feat(config): add profile-aware layered config composition`

### Goal

Support real deployment models (local/dev/staging/prod/test) without custom source wiring in every application.

### Scope

- Add a profile/environment concept
- Add layered source helpers
- Document deterministic precedence rules
- Support test/dev/prod examples in docs

### Acceptance criteria

- applications can express a layered source model declaratively
- precedence is deterministic and documented
- examples cover local, CI, and production usage
- tests cover cross-source override behavior

### Non-goals

- remote config systems
- secret managers
- live reload

### Why it matters

This closes one of the biggest operational gaps against Spring Boot, Dynaconf, and Figment.

---

## PR 4 — Secret Manager Integrations

### Working title

`feat(config): add secret manager source adapters`

### Goal

Support real production secret backends without forcing application-specific adapters.

### Candidate integrations

- Vault
- AWS Secrets Manager
- GCP Secret Manager
- Azure Key Vault

### Acceptance criteria

- each integration composes as a `ConfigSource`
- provenance identifies manager and secret key/version
- secret values stay redacted by default
- failure modes are explicit and tested

### Why it matters

This is where the library becomes truly production-native.

---

## PR 5 — Deprecation, Alias, and Migration Support

### Working title

`feat(config): support deprecated keys, aliases, and migration warnings`

### Goal

Make configuration evolution safe and user-friendly over time.

### Scope

- key aliases
- deprecated key warnings
- renamed key migration helpers
- optional strict mode for rejecting deprecated keys

### Acceptance criteria

- old keys can be mapped to new keys
- warnings are surfaced clearly
- docs include migration examples
- diagnostics explain alias/deprecation behavior

### Why it matters

This is a major usability feature for real framework adoption.

---

## Release sequencing recommendation

1. PR 1 — source ecosystem + secret file ergonomics
2. PR 2 — diagnostics and explainability
3. PR 3 — profiles / environments / layered composition
4. PR 5 — deprecation / alias support
5. PR 4 — secret manager integrations

This order maximizes immediate practical value while strengthening the long-term framework story.
