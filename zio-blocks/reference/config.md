# Config

> `zio.blocks.config` provides typed configuration loading, feature flags, provenance tracking, rollout selection, and source adapters for YAML, JSON, and HOCON. The module is synchronous and zero-dependency: configuration is loaded from `ConfigSource`, flags are read through `FlagSource`, and typed decoding is derived from `Schema[A]`.

`zio.blocks.config` provides typed configuration loading, feature flags, provenance tracking, rollout selection, and source adapters for YAML, JSON, and HOCON. The module is synchronous and zero-dependency: configuration is loaded from `ConfigSource`, flags are read through `FlagSource`, and typed decoding is derived from `Schema[A]`.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-config" % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-config-yaml" % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-config-json" % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-config-hocon" % "0.0.51"
```

For Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-config" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x.

## Core Types

At the center of the module are two source abstractions:

```scala
import zio.blocks.maybe.Maybe

trait FlagSource {
  def sourceId: String
  def get(name: String): Maybe[SourceValue[String]]
}

trait ConfigSource extends FlagSource {
  def get(key: String): Maybe[SourceValue[String]]
  def all(prefix: String): Map[String, SourceValue[String]]
}
```

`ConfigSource` extends `FlagSource`, so the same source can power both typed config loading and simple scalar flags.

## Loading Typed Configuration

Use `Config.load[A]` when you want a typed result and explicit errors:

```scala
import zio.blocks.config._
import zio.blocks.scope.Unscoped

final case class AppConfig(host: String, port: Int) derives Schema, Unscoped

val source = ConfigSource.fromMap(
  Map("app.host" -> "localhost", "app.port" -> "8080"),
  "example"
)

val loaded = Config.load[AppConfig](source.prefix("app"))
```

The main entry points are:

```scala
Config.load[A](source)
Config.loadOrThrow[A](source)
Config.loadWithProvenance[A](source)
```

`Config.loadWithProvenance` returns a `ProvenanceMap[A]`, which lets you inspect where resolved values came from.

## Wiring Config into Dependency Graphs

For application wiring, prefer `Config.wire[A]` or `Config.wire[A](prefix)` so decoding stays inside the dependency graph instead of being done manually at startup:

```scala
Config.wire[AppConfig]
Config.wire[AppConfig]("app")
```

That keeps `ConfigSource` as the injected input and the typed config as the derived output.

## Working with Sources

`ConfigSource` supports composition and key transformation:

```scala
val defaults = ConfigSource.fromMap(Map("db.host" -> "localhost"), "defaults")
val env      = ConfigSource.fromMap(Map("db.port" -> "5432"), "env")

val combined = env.orElse(defaults)
val scoped   = combined.prefix("db")
```

Common adapters:

- `ConfigSource.fromMap(...)`
- `ConfigSource.fromYaml(...)`  (requires `config-yaml` dependency)
- `ConfigSource.fromJson(...)`  (requires `config-json` dependency)
- `ConfigSource.fromHocon(...)` (requires `config-hocon` dependency)

## Static and Dynamic Flags

`StaticFlag[A]` resolves once, during object initialization:

```scala
import zio.blocks.config._

object poolSize extends StaticFlag[Int](10)

val size: Int = poolSize()
```

`DynamicFlag[A]` keeps an updatable rollout expression and evaluates it on demand.

`StaticFlag` names are derived from the Scala object's fully qualified name, so custom `FlagSource` registrations must use that exact key.

`FlagSource.Registry` is consulted in registration order, so the first registered source wins when multiple sources provide the same flag.

:::note
Register `FlagSource`s before the first reference to a `StaticFlag` object. Once a static flag object is initialized, later registrations do not retroactively change its resolved value.
:::

## Rollout DSL

`Rollout` selects values based on a path and an optional percentage bucket:

```scala
import zio.blocks.config._

val bucket = Rollout.bucketFor("user-123")
val choice = Rollout.select("true@prod/50%;false", "prod", bucket)
```

The percentage uses slash-separated syntax (`prod/50%`). For the example above, `choice` is `Maybe.present("true")` for roughly half of the `prod` buckets and `Maybe.present("false")` otherwise. Bare values act as catch-all fallbacks and should come last.

## Provenance

Every resolved value carries provenance information through `SourceValue` and `Provenance`:

```scala
val source = ConfigSource.fromMap(Map("db.host" -> "localhost"), "defaults")
val host   = source.get("db.host")
```

`host` contains both the raw value and a `Provenance.Resolved` entry that records the source id and key.

## Format Adapters

The format modules flatten structured documents into dot-separated keys:

- YAML: nested mappings become `a.b.c`
- JSON: arrays are indexed as `items.0`, `items.1`, ...
- HOCON: substitutions are resolved before flattening

Use these adapters when you want the convenience of file formats but still want a single `ConfigSource` API for decoding, composition, and provenance.
