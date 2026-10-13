# Configuration & Feature Flags

> `zio.blocks.config` loads typed configuration from string-keyed sources, tracks where every resolved value came from, and evaluates feature flags with percentage-based rollouts. The module is synchronous and zero-dependency. Core types: `ConfigSource`, `ConfigDecoder`, `ConfigError`, `Provenance`, `FlagSource`, `StaticFlag`, `DynamicFlag`, `Rollout`. The shapes at the center of the module:

```scala
trait ConfigSource extends FlagSource {
  def sourceId: String
  def get(key: String): Maybe[SourceValue[String]]
  def all(prefix: String): Map[String, SourceValue[String]]
}

trait ConfigDecoder[A] {
  def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], A]
}

final case class SourceValue[A](value: A, provenance: Provenance)
```

## Introduction

Configuration in this module is a two-layer story. The bottom layer is a flat, string-to-string namespace: a `ConfigSource` answers `get("db.host")` with a raw string plus a record of where that string came from. The top layer turns that namespace into typed values: a `ConfigDecoder[A]`, derived from `Schema[A]`, walks your case class and pulls one key per field.

Everything in between — composing sources, renaming keys to match an environment's conventions, accumulating errors instead of failing on the first one, redacting secrets when you print what was loaded — happens without an effect type. There is no `IO` wrapper: loading configuration is a synchronous function call that returns `Either`.

Feature flags reuse the same source abstraction. `FlagSource` is the scalar half of `ConfigSource`, so one source object can answer both typed config lookups and flag lookups.

## Motivation

Most configuration libraries make you choose between two unpleasant options: a stringly-typed map that compiles but fails at runtime, or a typed API bolted to a specific effect system and a specific file format. This module avoids both.

- **Typed without ceremony.** A `Schema[A]` you already have — or that `Schema.derived` writes for you — is all a decoder needs. There is no separate config-description DSL to learn and keep in sync with the case class.
- **Errors accumulate.** A five-field config with three bad fields reports three errors, not the first one. `ConfigError.Composite` carries them all.
- **Provenance is not an afterthought.** Every value arrives paired with the source that produced it, so "why is this port 8080?" has an answer you can print.
- **Format-agnostic.** YAML, JSON, and HOCON adapters all flatten to the same dot-separated namespace, so decoding, composition, and provenance work identically regardless of where the bytes came from.
- **No dependencies, both platforms.** The core module runs on the JVM and Scala.js. Only file-based HOCON loading is JVM-only.

## Installation

The core module provides sources, decoding, and flags:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-config" % "0.0.56"
```

Each file format is a separate artifact, so you depend only on the ones you use:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-config-yaml"  % "0.0.56"
libraryDependencies += "dev.zio" %% "zio-blocks-config-json"  % "0.0.56"
libraryDependencies += "dev.zio" %% "zio-blocks-config-hocon" % "0.0.56"
```

For Scala.js, use `%%%` instead of `%%`:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-config" % "0.0.56"
```

Supported Scala versions: 2.13.x and 3.x.

## Overview

The module divides into three groups of types: the source layer that produces raw strings, the decoding layer that produces typed values, and the flag layer that resolves runtime switches.

### The Source Layer

`ConfigSource` is a flat key-value namespace with dot-separated paths. `ConfigSource.fromMap` builds one from a `Map`, `EnvSource` reads environment variables, and `SysPropSource` reads system properties. Sources compose with `ConfigSource#orElse` and can be re-rooted with `ConfigSource#prefix`. See [Config Source](./config-source.md).

`SourceValue` pairs a raw value with a `Provenance` describing its origin, and `ProvenanceMap` lets you query and print those origins after a successful load. Also on that page: `KeyMapper` and `KeyFormat`, which translate between your canonical camelCase field names and whatever casing the environment actually uses.

### The Decoding Layer

`ConfigDecoder[A]` turns a source into an `A`, accumulating every failure. `ConfigDecoderDeriver` is the `Deriver` that builds those decoders from `Schema[A]`, and it defines the mapping rules: how nested case classes become dotted paths, how sealed traits pick a case from a discriminator key, how sequences and maps are keyed. See [Config Decoder](./config-decoder.md).

`ConfigError` is the failure type, split into four category traits so you can match on the kind of problem rather than the specific constructor. See [Errors](./errors.md).

### The Flag Layer

`StaticFlag[A]` resolves once at class-load time and never changes. `DynamicFlag[A]` holds a rollout expression that can be updated or reloaded while the process runs. Both derive their names from the enclosing Scala object and register themselves globally. See [Flags](./flags.md).

`Rollout` is the expression language behind dynamic flags: a semicolon-separated list of choices, each optionally targeted at a path pattern and a percentage bucket. See [Rollout](./rollout.md).

### The Format Adapters

`config-yaml`, `config-json`, and `config-hocon` each add one `ConfigSource` constructor. They differ in what they support — only HOCON has substitutions and includes — but they agree on the flattening rules. See [File Formats](./formats.md).

## How They Work Together

A typed load moves through four stages. The source produces raw strings, the decoder walks the schema requesting one key per field, errors accumulate rather than short-circuit, and the result is either a fully-built value or every problem found along the way:

```
1. Build a source            ConfigSource.fromMap / fromYaml / EnvSource
2. Compose and re-root       source.orElse(fallback).prefix("app")
3. Derive a decoder          ConfigDecoder.derive[A]  (from Schema[A])
4. Decode                    decoder.decode(source, "")
                                 │
                                 ├─ Right(a)      all fields resolved
                                 └─ Left(errors)  every failure, accumulated
```

The type relationships behind those stages:

```
FlagSource ─────────────────┐  get(name): Maybe[SourceValue[String]]
     ▲                      │
     │ extends              │
ConfigSource ───────────────┤  + all(prefix): Map[String, SourceValue[String]]
     │                      │
     ├─ MapSource           │
     ├─ EnvSource           │  DB_HOST  ← db.host
     ├─ SysPropSource       │
     └─ (yaml/json/hocon)   │  flattened to dotted keys
                            │
                            └─> SourceValue ──> Provenance
                                                  ├─ Resolved(sourceId, key, rawValue)
                                                  └─ Default

Schema[A] ──deriving──> ConfigDecoderDeriver ──> ConfigDecoder[A]
                                                      │
                                    decode(source, prefix)
                                                      │
                                    ┌─────────────────┴──────────────────┐
                                    ▼                                    ▼
                              Right(A)                        Left(::[ConfigError])
                                                                   ├─ MissingKey
                                                                   ├─ InvalidValue
                                                                   ├─ ParseError
                                                                   └─ Composite(all)

StaticFlag[A]   ──resolves once──> Registry → sysprop → env → default
DynamicFlag[A]  ──evaluates──>   Rollout.Choices x bucket → value
```

## Common Patterns

Four shapes cover most usage: a straight typed load, a layered load where environment variables win over file defaults, wiring config into a dependency graph, and loading with provenance so you can explain what happened.

### Loading a Typed Value

`Config.load` derives a decoder and runs it, returning every error it found:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}

val source = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "example")
```

Decoding returns `Either[::[ConfigError], Db]`, where `::` is Scala's non-empty list, so a `Left` always carries at least one error:

```scala
Config.load[Db](source)
// res0: Either[::[ConfigError], Db] = Right(
//   Db(host = "localhost", port = 5432)
// )
```

When a key is missing, the error names both the path and the source that was searched:

```scala
Config.load[Db](ConfigSource.fromMap(Map("host" -> "localhost"), "example"))
// res1: Either[::[ConfigError], Db] = Left(
//   List(MissingKey(path = "port", source = "example"))
// )
```

### Layering Sources

Production configuration usually comes from more than one place: a file provides defaults and the environment overrides them. `ConfigSource#orElse` consults the receiver first and falls back to the argument, and provenance still records which one actually answered:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}

val defaults = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "defaults")
val envOverrides = ConfigSource.fromMap(Map("host" -> "db.prod.internal"), "env")
```

The override wins for `host`, and `port` falls through to the defaults:

```scala
Config.load[Db](envOverrides.orElse(defaults))
// res3: Either[::[ConfigError], Db] = Right(
//   Db(host = "db.prod.internal", port = 5432)
// )
```

### Wiring Config into a Dependency Graph

Rather than decoding at startup and threading the result through constructors, `Config.wire` produces a `Wire.Shared[ConfigSource, A]` so decoding becomes a node in the graph. The `ConfigSource` is the injected input; the typed config is the derived output:

```scala
import zio.blocks.config._
import zio.blocks.context.Context
import zio.blocks.schema.Schema
import zio.blocks.scope.{Resource, Scope, Unscoped, Wire}

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db]     = Schema.derived[Db]
  implicit val unscoped: Unscoped[Db] = Unscoped.derived[Db]
}

final class DbService(val config: Db)

val source   = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "graph")
val resource = Resource.from[DbService](Wire(source), Config.wire[Db])

Scope.global.scoped { scope =>
  import scope._
  val service = allocate(resource)
  $(service)(_.config)
}
```

`Config.wire[A](prefix)` re-roots the injected source before decoding, which is how you keep several config sections in one graph without giving each its own source.

### Loading with Provenance

`Config.loadWithProvenance` returns a `ProvenanceMap[A]` alongside the value, which answers per-key origin questions and renders a table for startup logs:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}

val source = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "startup")
val loaded = Config.loadWithProvenance[Db](source)
```

Querying a single key reports the source id, the source-facing key, and the raw string:

```scala
loaded.map(_.provenanceOf("host"))
// res6: Either[::[ConfigError], Maybe[Provenance]] = Right(
//   Resolved(sourceId = "startup", key = "host", rawValue = "localhost")
// )
```

`ProvenanceMap#dump` renders every visible key as a table, redacting values whose key names look sensitive:

```scala
loaded.map(_.dump()).foreach(println)
// ┌──────┬───────────┬─────────┐
// │ Key  │ Value     │ Source  │
// ├──────┼───────────┼─────────┤
// │ host │ localhost │ startup │
// │ port │ 5432      │ startup │
// └──────┴───────────┴─────────┘
```

## Entry Points

`Config` is the front door for typed loading. Which method you want depends on how you handle failure and whether you need provenance:

| Method                        | Returns                              | Use when                                                        |
| ----------------------------- | ------------------------------------ | --------------------------------------------------------------- |
| `Config.load[A]`              | `Either[::[ConfigError], A]`          | You want to inspect or report errors yourself.                   |
| `Config.loadOrThrow[A]`       | `A`                                  | Failure should abort startup; throws `ConfigLoadException`.       |
| `Config.loadWithProvenance[A]`| `Either[::[ConfigError], ProvenanceMap[A]]` | You need to explain where values came from.               |
| `Config.wire[A]`              | `Wire.Shared[ConfigSource, A]`       | Decoding belongs inside a dependency graph.                      |

`Config.load` derives a fresh decoder on every call. When you load the same type repeatedly — during reloads, or per request — derive once with `ConfigDecoder.derive[A]` and reuse the result.

:::warning[Derivation is not free]
`Schema.derived` runs at compile time, but turning a `Schema[A]` into a `ConfigDecoder[A]` happens at runtime and walks the whole schema. Calling `Config.load[A]` in a hot path re-does that walk every time.
:::

## Integration Points

The module sits on top of three other blocks and hands its output to a fourth:

- **`zio-blocks-schema`** supplies `Schema[A]`, and `ConfigDecoderDeriver` is an ordinary `Deriver[ConfigDecoder]`. Anything the schema layer can describe — records, variants, sequences, maps, wrappers, dynamic values — has a decoding rule.
- **`zio-blocks-maybe`** supplies `Maybe`, the allocation-free option type returned by every source lookup.
- **`zio-blocks-scope`** supplies `Wire` and `Resource`, which is what `Config.wire` produces so config can participate in dependency graphs.
- **`zio-blocks-schema-yaml`** and **`zio-blocks-schema-json`** back the YAML and JSON adapters; the HOCON adapter has its own parser and no external dependency.

Within the module, the dependency direction is one-way: sources know nothing about decoders, decoders know nothing about flags, and flags reuse `FlagSource` without reaching into `ConfigDecoder`.

## Next Steps

Start with [Config Source](./config-source.md) to build and compose sources, then [Config Decoder](./config-decoder.md) for the mapping rules from case classes to keys. [Errors](./errors.md) covers the failure model, [Flags](./flags.md) and [Rollout](./rollout.md) cover runtime switches, and [File Formats](./formats.md) covers YAML, JSON, and HOCON.
