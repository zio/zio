# Config PR Assessment and Roadmap

> This document captures the assessment of the current config PR (`feat(config): add config, config-yaml, config-json, config-hocon modules`) and the roadmap discussion for making the config system best-in-class.

# Config PR Assessment and Roadmap

## Context

This document captures the assessment of the current config PR (`feat(config): add config, config-yaml, config-json, config-hocon modules`) and the roadmap discussion for making the config system best-in-class.

The core direction is strong:

- typed configuration decoding from `Schema`
- provenance tracking
- unified config + flags + rollout model
- DI integration via `Config.wire[...]`
- synchronous, zero-dependency core

But the PR is not yet best-in-class. The biggest gaps are documentation coherence, operational/source breadth, secret handling completeness, and introspection UX.

---

## Assessment Summary

### Current strengths

1. **Provenance is a real differentiator**
   - `ConfigSource` and `FlagSource` carry source identity.
   - resolved values retain provenance.
   - composition preserves origin.
   - this is genuinely better than many mainstream config libraries.

2. **DI integration is unusually strong**
   - `Config.wire[A]` and `Config.wire[A](prefix)` keep config decoding inside the dependency graph.
   - this is framework-grade design rather than just “settings loading”.

3. **Config + flags + rollout in one model**
   - this is strategically smart and potentially differentiating.

4. **Format adapters normalize into one model**
   - YAML / JSON / HOCON all end up as `ConfigSource`, which keeps the rest of the system simple.

### Current weaknesses

1. **Public docs and API story are not fully coherent**
   - stale `Option` docs where impl uses `Maybe`
   - stale `Schema.derived` companion examples where Scala 3 `derives` is preferred
   - generated README blocked by unrelated website-plugin failure

2. **Source ecosystem is still narrow**
   - no first-class secrets directory source
   - no `_FILE` support
   - no secret-manager integrations
   - no clear profile/environment model

3. **Secret handling is directionally correct but incomplete**
   - `Secret` as string-only wrapper is good
   - redaction exists, but full reporting/audit safety must be verified everywhere

4. **Diagnostics are structured but not yet elite**
   - errors are accumulated
   - but “why did this value win?” / operator-facing explainability is still immature

5. **Merge / precedence model is still basic**
   - simple fallback composition works
   - richer advanced composition semantics are still missing

6. **Validation and operational governance are still shallow**
   - strong decode
   - weaker config-specific semantic validation, migration, deprecation, profile-aware rules

---

## Ecosystem Comparison

### JVM / Spring Boot

Spring Boot is ahead on:

- property source ecosystem breadth
- metadata generation
- operational introspection (`env`, `configprops`, origin tracking)
- mature profile model

This PR is ahead on:

- conceptual purity
- tighter config/flags/rollout unification
- stronger framework-substrate potential

### Rust / Figment

Figment is the closest conceptual peer.

Figment is ahead on:

- provider abstraction maturity
- profile support
- provider ecosystem
- provenance polish

This PR is ahead on:

- DI integration
- flag/rollout unification

### Go / koanf

koanf is ahead on:

- provider breadth
- composable source ecosystem
- merge flexibility

This PR is ahead on:

- typed decode from schemas
- provenance richness
- DI integration

### Python / pydantic-settings + dynaconf

Pydantic settings is ahead on:

- startup UX
- strong validation ergonomics
- nested env conventions
- secrets dir support

Dynaconf is ahead on:

- layered source breadth
- environment/profile support
- secrets/provider integration

This PR is ahead on:

- provenance quality
- DI alignment
- config/flags/rollout unification

### TypeScript / t3-env / envalid / convict

These are ahead on:

- startup validation ergonomics
- low-friction adoption
- concise developer experience

This PR is ahead on:

- hierarchical typed decode
- provenance
- multi-format config normalization
- DI integration

---

## Roadmap

### 1. Must merge now

These are quality-bar issues for this PR itself.

#### A. Fix public coherence

- update `docs/reference/config.md` to `Maybe` instead of `Option`
- update `Config.scala` Scaladoc examples to Scala 3 `derives Schema, Unscoped`
- ensure all high-level examples use:
  - `Config.wire[...]`
  - `Resource.use(...)`
  - Scala 3 style
- either:
  - fix `generateReadme`, or
  - explicitly mark README generation as blocked by unrelated website-plugin failure in PR notes

#### B. Tighten the config story

Document the canonical golden path:

```scala
Wire(source)
Config.wire[FooConfig]("foo")
Resource.from[App](...)
appResource.use(_.run())
```

#### C. Tighten secret guarantees

Before merge, verify:

- all human-facing config rendering redacts `Secret`
- provenance never leaks secret raw values in default reporting
- error formatting does not accidentally print secrets

---

### 2. Next 3 PRs

#### PR 1 — Source ecosystem + secrets ergonomics

Add:

- `ConfigSource.fromEnv(...)`
- `ConfigSource.fromSystemProperties(...)` as a clearly canonical public path
- `ConfigSource.fromSecretsDir(...)`
- `_FILE` convention support

#### PR 2 — Diagnostics and explainability

Add:

- richer config load reports
- “why did this value win?” APIs
- structured diagnostic output
- provenance rendering designed for operators

#### PR 3 — Profiles / environments / layered config model

Add:

- named environments/profiles
- explicit precedence helpers
- layered composition utilities

---

### 3. 1.0 checklist

For a true 1.0-quality config system:

#### Core

- typed decode from `Schema`
- `Maybe`-based source API
- provenance for every resolved value
- config + flags + rollout unified
- DI-first wiring
- ergonomic `Resource.use(...)`

#### Sources

- map
- env
- system properties
- YAML
- JSON
- HOCON
- secrets dir
- `_FILE`
- custom source extension point

#### Secrets

- string-only `Secret`
- default redaction everywhere
- no accidental secret exposure in reports
- nested secret support
- secret manager adapter strategy

#### Diagnostics

- accumulated errors
- explain/report API
- effective-value provenance
- machine-readable diagnostics
- human-readable startup report

#### Framework readiness

- profile/environment model
- clear precedence semantics
- deprecation/migration support for renamed keys
- test-friendly override model
- future hook points for framework introspection

---

## Strategic Recommendation

The product should be positioned as:

> typed configuration with provenance, rollout, and DI integration

not merely as another config library.

The best path is to make the strongest differentiators impossible to miss:

1. typed config that explains itself
2. config that belongs inside the DI/runtime model
3. one unified system for config, flags, and rollout

---

## Priority Order

### Highest priority

1. docs coherence
2. secrets-dir + `_FILE`
3. diagnostics / explainability

### Medium priority

4. profiles / environments
5. custom source/provider story
6. deprecation / alias migration support

### Later

7. secret manager integrations
8. reload/watch model
9. metadata/schema export
10. framework actuator/introspection layer
