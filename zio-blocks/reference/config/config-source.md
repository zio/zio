# ConfigSource

> `ConfigSource` is a flat, string-keyed namespace of configuration values with dot-separated paths. It answers single-key lookups, enumerates keys under a prefix, and pairs every value it returns with a `Provenance` recording where that value came from. Supporting types: `SourceValue`, `Provenance`, `ProvenanceMap`, `KeyMapper`, `KeyFormat`, `Secret`. The reading and composition surface:

```scala
trait ConfigSource extends FlagSource {
  def sourceId: String
  def get(key: String): Maybe[SourceValue[String]]
  def all(prefix: String): Map[String, SourceValue[String]]

  final def orElse(fallback: ConfigSource): ConfigSource
  final def prefix(prefix: String): ConfigSource
  final def keyMapper(mapper: KeyMapper, targetFormat: KeyFormat): ConfigSource
  final def keyFormat(format: KeyFormat): ConfigSource
}
```

## Motivation

Configuration arrives as strings, from places that disagree about structure. Environment variables are flat and uppercase. YAML is nested. HOCON has substitutions. A decoder that had to understand all three would be three decoders.

`ConfigSource` is the one shape they all reduce to: a map from dotted path to string. Nested documents flatten into it, environment variables translate into it, and in-memory maps are already it. Because the shape is uniform, everything built above it — decoding, composition, provenance, key renaming — is written once.

The trait extends `FlagSource`, which is the same idea minus prefix enumeration. That inheritance is what lets a single source object serve both typed configuration and feature flags.

## Construction

Sources come from three places in the core module — a map, the environment, and system properties — plus one constructor per file format.

### From a Map

`ConfigSource.fromMap` wraps a `Map[String, String]`, optionally with an identifier that shows up in provenance and error messages:

```scala
import zio.blocks.config._

val source = ConfigSource.fromMap(
  Map("db.host" -> "localhost", "db.port" -> "5432"),
  "defaults"
)
```

Looking up a key returns the value wrapped in a `SourceValue`, which carries the provenance alongside the string:

```scala
source.get("db.host")
// res0: Maybe[SourceValue[String]] = SourceValue(
//   value = "localhost",
//   provenance = Resolved(
//     sourceId = "defaults",
//     key = "db.host",
//     rawValue = "localhost"
//   )
// )
```

An absent key yields `Maybe.absent` rather than throwing or returning `null`:

```scala
source.get("db.user")
// res1: Maybe[SourceValue[String]] = zio.blocks.maybe.Absent$@53f42f8c
```

`ConfigSource.MapSource` is the underlying case class, so you can pattern match on it or construct it directly when you want the concrete type rather than the trait.

### From the Environment

`EnvSource` reads environment variables. It performs its own key translation: a dotted lookup becomes an upper-snake environment variable name, so `get("db.host")` reads `DB_HOST`:

```scala
import zio.blocks.config._

EnvSource.get("db.host")     // reads DB_HOST
EnvSource.all("db")          // every DB_* variable, keys mapped back to dotted form
```

An unset variable is absent. An empty string is present — `FOO=""` resolves to `Some("")`, not a missing key, which matters when you use an empty value to mean "explicitly disabled".

`SysPropSource` reads JVM system properties using the dotted path directly, with no translation.

:::info[Scala.js behavior]
On Scala.js, `EnvSource` reads `process.env` when it is available and returns absent for everything when it is not. `SysPropSource` always returns empty results, since JS has no system properties.
:::

### From a File Format

The YAML, JSON, and HOCON adapters each add a constructor to the `ConfigSource` companion via an implicit class, so importing the adapter package makes `ConfigSource.fromYaml`, `ConfigSource.fromJson`, or `ConfigSource.fromHocon` available. Because parsing can fail, those constructors return `Either[ConfigError, ConfigSource]`. See [File Formats](./formats.md).

## Lookup Operations

Two methods make up the reading surface: one resolves a single key, the other enumerates a subtree.

### ConfigSource#get

`ConfigSource#get` takes a full dotted path and returns `Maybe[SourceValue[String]]`. It never partially matches: `get("db")` on a source containing only `db.host` is absent, because `db` itself has no value.

### ConfigSource#all

`ConfigSource#all` returns every entry whose key equals the prefix or begins with the prefix followed by a dot. Passing an empty prefix returns everything:

```scala
source.all("db")
// res3: Map[String, SourceValue[String]] = Map(
//   "db.host" -> SourceValue(
//     value = "localhost",
//     provenance = Resolved(
//       sourceId = "defaults",
//       key = "db.host",
//       rawValue = "localhost"
//     )
//   ),
//   "db.port" -> SourceValue(
//     value = "5432",
//     provenance = Resolved(
//       sourceId = "defaults",
//       key = "db.port",
//       rawValue = "5432"
//     )
//   )
// )
```

The decoder uses `ConfigSource#all` to discover the shape of collections — how many elements a sequence has, which keys a map contains — so a source that cannot enumerate cannot decode those types.

## Composition

Real deployments layer configuration: defaults from a file, overrides from the environment, and a subtree per component. Two operations cover both needs, and both are `final` so every source gets them.

### ConfigSource#orElse

`ConfigSource#orElse` consults the receiver first and falls back to the argument. Provenance records whichever source actually answered, so layering does not obscure the origin:

```scala
import zio.blocks.config._

val defaults     = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "defaults")
val envOverrides = ConfigSource.fromMap(Map("host" -> "db.prod.internal"), "env")

val layered = defaults.orElse(envOverrides)
```

The receiver wins on conflict, which means the *first* source listed has priority — write the highest-priority source on the left:

```scala
layered.get("host")
// res5: Maybe[SourceValue[String]] = SourceValue(
//   value = "localhost",
//   provenance = Resolved(
//     sourceId = "defaults",
//     key = "host",
//     rawValue = "localhost"
//   )
// )
```

Keys only the fallback provides still resolve, and their provenance names the fallback:

```scala
layered.get("port")
// res6: Maybe[SourceValue[String]] = SourceValue(
//   value = "5432",
//   provenance = Resolved(sourceId = "defaults", key = "port", rawValue = "5432")
// )
```

The composed source's `sourceId` is the two ids joined with a pipe, which is what you see in error messages when a key is missing from both:

```scala
layered.sourceId
// res7: String = "defaults|env"
```

`ConfigSource#all` on a composed source merges both key sets, with the receiver's entries overwriting the fallback's on collision. That merge is why enumeration-driven decoding — sequences and maps — behaves the same on a layered source as on a single one.

### ConfigSource#prefix

`ConfigSource#prefix` re-roots a source so that lookups are relative to a subtree. A source prefixed with `db` turns `get("host")` into `get("db.host")` against the underlying source:

```scala
import zio.blocks.config._

val full = ConfigSource.fromMap(
  Map("db.host" -> "localhost", "db.port" -> "5432", "http.port" -> "8080"),
  "app"
)

val db = full.prefix("db")
```

Relative lookups resolve against the composed key, so the caller never spells the prefix again:

```scala
db.get("host")
// res9: Maybe[SourceValue[String]] = SourceValue(
//   value = "localhost",
//   provenance = Resolved(
//     sourceId = "app",
//     key = "db.host",
//     rawValue = "localhost"
//   )
// )
```

Enumeration strips the prefix back off the returned keys, which keeps the view consistent — what `ConfigSource#get` resolves is what `ConfigSource#all` reports:

```scala
db.all("")
// res10: Map[String, SourceValue[String]] = Map(
//   "host" -> SourceValue(
//     value = "localhost",
//     provenance = Resolved(
//       sourceId = "app",
//       key = "db.host",
//       rawValue = "localhost"
//     )
//   ),
//   "port" -> SourceValue(
//     value = "5432",
//     provenance = Resolved(sourceId = "app", key = "db.port", rawValue = "5432")
//   )
// )
```

Prefixing preserves `sourceId`, since re-rooting does not change where values come from. This is the mechanism behind `Config.wire[A](prefix)`: one injected source, several independently-rooted config sections.

## Key Mapping

Field names in Scala are camelCase. Environment variables are `UPPER_SNAKE_CASE`. Some YAML files use `kebab-case`. Rather than renaming fields or duplicating keys, a source can translate between the canonical form the decoder asks for and whatever form the source actually uses.

### KeyFormat

`KeyFormat` enumerates the four supported spellings of a key:

| Variant                   | Example        |
| ------------------------- | -------------- |
| `KeyFormat.CamelCase`     | `databaseUrl`  |
| `KeyFormat.SnakeCase`     | `database_url` |
| `KeyFormat.KebabCase`     | `database-url` |
| `KeyFormat.UpperSnakeCase`| `DATABASE_URL` |

### KeyMapper

`KeyMapper` converts in both directions: `KeyMapper#toCanonical` normalizes a source-facing key into lower camelCase, and `KeyMapper#fromCanonical` renders a canonical key into a requested `KeyFormat`:

```scala
import zio.blocks.config._

val mapper = KeyMapper.default
```

The default mapper treats snake_case and kebab-case as equivalent inputs, collapsing both to camelCase:

```scala
mapper.toCanonical("database_url")
// res12: String = "databaseUrl"
mapper.toCanonical("database-url")
// res13: String = "databaseUrl"
```

Rendering goes the other way, one output per format:

```scala
mapper.fromCanonical("databaseUrl", KeyFormat.UpperSnakeCase)
// res14: String = "DATABASE_URL"
mapper.fromCanonical("databaseUrl", KeyFormat.KebabCase)
// res15: String = "database-url"
```

A key containing neither separator passes through `KeyMapper#toCanonical` unchanged, so already-canonical keys cost nothing.

### Applying a Mapper to a Source

`ConfigSource#keyFormat` wraps a source so that every lookup is rendered into the given format before it reaches the underlying source. Ask for `databaseUrl`, and the wrapped source looks up `DATABASE_URL`:

```scala
import zio.blocks.config._

val upperSnake = ConfigSource.fromMap(Map("DATABASE_URL" -> "postgres://localhost"), "env-style")
val canonical  = upperSnake.keyFormat(KeyFormat.UpperSnakeCase)
```

The decoder — which only ever asks for camelCase field names — now resolves against an upper-snake namespace:

```scala
canonical.get("databaseUrl")
// res17: Maybe[SourceValue[String]] = SourceValue(
//   value = "postgres://localhost",
//   provenance = Resolved(
//     sourceId = "env-style",
//     key = "DATABASE_URL",
//     rawValue = "postgres://localhost"
//   )
// )
```

`ConfigSource#keyMapper` is the general form, taking an explicit `KeyMapper` as well as the target format, for sources whose naming convention the default mapper does not cover. Both operations compose with `ConfigSource#orElse` and `ConfigSource#prefix`.

:::note[EnvSource already maps keys]
`EnvSource` performs dot-to-underscore uppercasing internally, so it does not need `ConfigSource#keyFormat`. Use `ConfigSource#keyFormat` for sources that hold environment-style keys without being the environment — a `Map` scraped from an env file, for instance.
:::

## Provenance

Every value a source returns is wrapped with a record of its origin. That record survives composition, prefixing, and key mapping, which is what makes "where did this value come from?" answerable after the fact rather than only at the point of lookup.

### SourceValue and Provenance

`SourceValue` is the pair, and `Provenance` is the origin:

```scala
final case class SourceValue[A](value: A, provenance: Provenance)

sealed trait Provenance {
  def sourceId: String
}

object Provenance {
  final case class Resolved(sourceId: String, key: String, rawValue: Maybe[String]) extends Provenance
  case object Default extends Provenance
}
```

`Provenance.Resolved` names the source that answered, the source-facing key it answered under, and the raw string. The key matters when mapping is involved: a lookup of `databaseUrl` against an upper-snake source records `DATABASE_URL`, telling you the actual variable to change.

`Provenance.Default` marks a value that came from a schema default rather than any source. Its `sourceId` is the constant `"schema-default"`.

### ProvenanceMap

`ProvenanceMap[A]` pairs a decoded value with the source it was decoded from, which allows per-key queries after the load has already succeeded:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}

val source   = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "startup")
val loaded   = Config.loadWithProvenance[Db](source).toOption.get
```

The decoded value is available directly, so a `ProvenanceMap` can be passed around in place of the raw config:

```scala
loaded.value
// res19: Db = Db(host = "localhost", port = 5432)
```

`ProvenanceMap#provenanceOf` looks up a single dotted path and returns absent for keys the source does not have:

```scala
loaded.provenanceOf("port")
// res20: Maybe[Provenance] = Resolved(
//   sourceId = "startup",
//   key = "port",
//   rawValue = "5432"
// )
loaded.provenanceOf("nonexistent")
// res21: Maybe[Provenance] = zio.blocks.maybe.Absent$@53f42f8c
```

### Dumping Configuration

`ProvenanceMap#dump` renders every key visible under a prefix as a box-drawn table of key, value, and source. It is meant for a single startup log line that makes a misconfigured deployment obvious:

```scala
println(loaded.dump())
// ┌──────┬───────────┬─────────┐
// │ Key  │ Value     │ Source  │
// ├──────┼───────────┼─────────┤
// │ host │ localhost │ startup │
// │ port │ 5432      │ startup │
// └──────┴───────────┴─────────┘
```

Values are redacted when the key name looks sensitive. The check lowercases the path, normalizes hyphens to underscores, and looks for any of `secret`, `password`, `passwd`, `token`, `apikey`, `api_key`, `accesskey`, `access_key`, `privatekey`, `private_key`, `credential`, or `credentials` as a substring:

```scala
import zio.blocks.config._

val withSecret = ConfigSource.fromMap(
  Map("db.host" -> "localhost", "db.password" -> "hunter2"),
  "startup"
)
```

The key is still listed — you can see that it was set — but the value is replaced:

```scala
println(ProvenanceMap((), withSecret).dump())
// ┌─────────────┬───────────┬─────────┐
// │ Key         │ Value     │ Source  │
// ├─────────────┼───────────┼─────────┤
// │ db.host     │ localhost │ startup │
// │ db.password │ <secret>  │ startup │
// └─────────────┴───────────┴─────────┘
```

:::warning[Redaction is name-based only]
`ProvenanceMap#dump` redacts by key name, not by type. A secret stored under a key like `db.credentials_blob` is redacted; the same secret under `db.blob` is printed in full. For values that must never be printed regardless of key, use `Secret`.
:::

## Secrets

`Secret` is a wrapper whose `toString` is always `<secret>`, so a value inside one cannot leak through string interpolation, logging, or a case class `toString`:

```scala
import zio.blocks.config._

val token = Secret("s3cr3t-token")
```

Rendering the wrapper reveals nothing, even inside a larger string:

```scala
token.toString
// res26: String = "<secret>"
s"token = $token"
// res27: String = "token = <secret>"
```

`Secret#equals` and `Secret#hashCode` compare the underlying value, so secrets remain usable as map keys and in equality checks:

```scala
token == Secret("s3cr3t-token")
// res28: Boolean = true
```

Reading the value back requires the explicit `Secret.unwrap`, which makes every access point visible in a code search:

```scala
Secret.unwrap(token)
// res29: String = "s3cr3t-token"
```

### Displayable

`Displayable[A]` is the type class behind rendered flag values, with instances for `String`, `Int`, `Long`, `Double`, and `Boolean`, plus a low-priority fallback that calls `toString`. The module provides `Displayable[Secret]` as an implicit in the `zio.blocks.config` package object, so a `StaticFlag[Secret]` renders as `<secret>` in `Flag.dump` output without any per-flag configuration.

To control how a custom type appears in flag dumps, provide your own instance with `Displayable.instance`:

```scala
import zio.blocks.config._

final case class Port(value: Int)

implicit val portDisplayable: Displayable[Port] = Displayable.instance(p => s"port ${p.value}")
```

## Writing a Custom Source

Implementing `ConfigSource` requires three members: an id, a single-key lookup, and prefix enumeration. Constructing `Provenance.Resolved` yourself is what makes the new source participate in provenance tracking:

```scala
import zio.blocks.config._
import zio.blocks.maybe.Maybe

final class UppercaseSource(entries: Map[String, String]) extends ConfigSource {
  val sourceId: String = "uppercase"

  def get(key: String): Maybe[SourceValue[String]] =
    Maybe.fromOption(
      entries.get(key).map(v => SourceValue(v.toUpperCase, Provenance.Resolved(sourceId, key, Maybe.present(v))))
    )

  def all(prefix: String): Map[String, SourceValue[String]] = {
    val dotted = if (prefix.isEmpty) "" else s"$prefix."
    entries.collect {
      case (k, v) if prefix.isEmpty || k == prefix || k.startsWith(dotted) =>
        k -> SourceValue(v.toUpperCase, Provenance.Resolved(sourceId, k, Maybe.present(v)))
    }
  }
}
```

Keep `rawValue` as the original string even when `value` is transformed. Provenance is meant to explain what the source held, not what the source returned.

## Integration Points

`ConfigSource` is the input to every other part of the module: `ConfigDecoder#decode` takes one, `Config.wire` injects one, and `FlagSource.Registry` accepts one because `ConfigSource` extends `FlagSource`. It depends only on `Maybe` from `zio-blocks-maybe`.

See [Config Decoder](./config-decoder.md) for how a source becomes a typed value, [Errors](./errors.md) for what a failed lookup produces, and [File Formats](./formats.md) for the YAML, JSON, and HOCON constructors.
