# ConfigDecoder

> `ConfigDecoder[A]` reads a value of type `A` out of a `ConfigSource`, accumulating every failure instead of stopping at the first one. Instances are derived from `Schema[A]` by `ConfigDecoderDeriver`, which defines how records, variants, sequences, maps, and primitives map onto dot-separated keys. The trait and its derivation entry point:

```scala
trait ConfigDecoder[A] {
  def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], A]
}

object ConfigDecoder {
  def apply[A](implicit decoder: ConfigDecoder[A]): ConfigDecoder[A]
  def derive[A](implicit schema: Schema[A]): ConfigDecoder[A]
}
```

## Motivation

Writing a config decoder by hand means writing the same code once per field: look up a key, parse the string, handle the missing case, collect the error. For a twenty-field config that is twenty near-identical blocks that drift out of sync with the case class the moment someone adds a field.

`Schema[A]` already knows the field names, the types, and the defaults. `ConfigDecoderDeriver` turns that knowledge into the lookup-and-parse loop, so adding a field to a case class adds a key to the config with no other change. The derivation is a runtime walk over the schema, which means it is also introspectable: the same `Deriver` machinery lets you override a single type's decoding without rewriting the rest.

The `prefix` parameter exists so decoders compose. A record decoder calls its field decoders with `s"$prefix.$fieldName"`, and the recursion bottoms out at primitives that look up exactly one key.

## Deriving a Decoder

`ConfigDecoder.derive` builds a decoder from an implicit `Schema[A]` using the default deriver:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}

val decoder = ConfigDecoder.derive[Db]
```

Decoding with an empty prefix treats field names as top-level keys:

```scala
decoder.decode(ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432")), "")
// res0: Either[::[ConfigError], Db] = Right(
//   Db(host = "localhost", port = 5432)
// )
```

Passing a non-empty prefix roots the same decoder under a subtree, which is how one decoder serves both a standalone config file and a section of a larger one:

```scala
decoder.decode(ConfigSource.fromMap(Map("db.host" -> "localhost", "db.port" -> "5432")), "db")
// res1: Either[::[ConfigError], Db] = Right(
//   Db(host = "localhost", port = 5432)
// )
```

`Config.load[A]` is a thin wrapper over these two lines. The difference matters for repeated loads: `Config.load` derives a fresh decoder on every call, while a decoder you hold onto is derived once.

:::tip[Derive once for repeated loads]
Derivation walks the entire schema and allocates a decoder per field. For a config reloaded on a timer, or decoded per request, call `ConfigDecoder.derive[A]` at startup and reuse the instance.
:::

## Mapping Rules

The deriver has one rule per schema shape. Together they determine every key your config file needs, so this section is the reference for "what key does field X read?".

### Records

A record's fields become keys under the record's prefix, joined with dots. Nesting composes: a field whose type is itself a record contributes another path segment:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}

case class Http(host: String, port: Int)

object Http {
  implicit val schema: Schema[Http] = Schema.derived[Http]
}

case class App(db: Db, http: Http)

object App {
  implicit val schema: Schema[App] = Schema.derived[App]
}
```

Four keys, two per nested record, all reachable from one top-level decode:

```scala
Config.load[App](
  ConfigSource.fromMap(
    Map(
      "db.host"   -> "dbhost",
      "db.port"   -> "5432",
      "http.host" -> "0.0.0.0",
      "http.port" -> "8080"
    )
  )
)
// res3: Either[::[ConfigError], App] = Right(
//   App(
//     db = Db(host = "dbhost", port = 5432),
//     http = Http(host = "0.0.0.0", port = 8080)
//   )
// )
```

The deriver uses field names verbatim, in the casing the Scala source declares. To read a differently-cased namespace, wrap the source with `ConfigSource#keyFormat` rather than renaming fields — see [ConfigSource](./config-source.md).

### Primitives

A primitive field looks up exactly one key and parses the string. Parsing rules by type:

| Schema type                                              | Accepted form                                  | Example        |
| -------------------------------------------------------- | ---------------------------------------------- | -------------- |
| `Boolean`                                                | `true`/`false`, `1`/`0`, `yes`/`no`, `on`/`off` (any case) | `on`  |
| `Byte`, `Short`, `Int`, `Long`                            | Decimal integer literal                        | `5432`         |
| `Float`, `Double`                                        | Decimal or scientific literal                  | `0.75`         |
| `Char`                                                   | Exactly one character                          | `x`            |
| `String`                                                 | Any string, taken as-is                        | `localhost`    |
| `BigInt`, `BigDecimal`                                    | Arbitrary-precision numeric literal            | `1e40`         |
| `UUID`                                                   | Canonical 36-character form                    | `f81d4fae-…`   |
| `java.time.Duration`                                     | ISO-8601 duration                              | `PT30S`        |
| `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`      | ISO-8601                                       | `2026-08-21`   |
| `OffsetDateTime`, `OffsetTime`, `ZonedDateTime`           | ISO-8601 with offset or zone                   | `2026-08-21T00:00:00Z` |
| `ZoneId`, `ZoneOffset`                                   | Zone name or offset                            | `Europe/Berlin` |
| `Period`, `Year`, `YearMonth`, `MonthDay`                 | ISO-8601                                       | `P1M`          |
| `Month`, `DayOfWeek`                                     | Enum name, uppercased before lookup            | `monday`       |
| `Currency`                                               | ISO 4217 code                                  | `EUR`          |
| `Unit`                                                   | Any value; the key must exist                  | `()`           |

A value that fails to parse produces `ConfigError.InvalidValue` naming the path, the offending string, the expected type, and the source:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}
```

The underlying parse exception is retained as the error's `cause`:

```scala
Config.load[Db](ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "not-a-number"), "bad"))
// res5: Either[::[ConfigError], Db] = Left(
//   List(
//     InvalidValue(
//       path = "port",
//       value = "not-a-number",
//       expectedType = "Int",
//       source = "bad",
//       cause = Some(
//         java.lang.NumberFormatException: For input string: "not-a-number"
//       )
//     )
//   )
// )
```

:::warning[Two different duration formats]
Config decoding parses `java.time.Duration` with `Duration.parse`, so it requires ISO-8601 (`PT30S`). Flag readers parse `scala.concurrent.duration.FiniteDuration` with a suffix grammar instead (`30s`). The same string is not valid in both places — see [Flags](./flags.md).
:::

### Optional Fields

A field of type `Option[A]` decodes the inner `A` at the same key the field would otherwise use — there is no extra path segment for the `Option` itself. A missing key yields `None`:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Service(name: String, tag: Option[String])

object Service {
  implicit val schema: Schema[Service] = Schema.derived[Service]
}
```

Supplying the key produces `Some`, and omitting it produces `None`:

```scala
Config.load[Service](ConfigSource.fromMap(Map("name" -> "api", "tag" -> "v2")))
// res7: Either[::[ConfigError], Service] = Right(
//   Service(name = "api", tag = Some("v2"))
// )
Config.load[Service](ConfigSource.fromMap(Map("name" -> "api")))
// res8: Either[::[ConfigError], Service] = Right(
//   Service(name = "api", tag = None)
// )
```

The distinction that matters is *missing* versus *invalid*. An optional field absorbs missing keys only. A key that is present but unparseable still fails the whole decode, because silently returning `None` for a typo would hide the mistake.

### Default Values

A field with a default in the case class falls back to that default when its key is missing, exactly as an `Option` falls back to `None`:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int = 5432)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}
```

Omitting `port` uses the declared default rather than reporting a missing key:

```scala
Config.load[Db](ConfigSource.fromMap(Map("host" -> "localhost")))
// res10: Either[::[ConfigError], Db] = Right(
//   Db(host = "localhost", port = 5432)
// )
```

As with optional fields, the fallback applies only to missing keys. A present-but-invalid value is an error, not a reason to use the default.

### Sequences

A sequence is read in one of two shapes. The indexed shape uses one key per element, numbered from zero, and the deriver probes upward until it finds a gap:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Hosts(items: List[String])

object Hosts {
  implicit val schema: Schema[Hosts] = Schema.derived[Hosts]
}
```

Indexed keys are what the YAML and JSON adapters produce when they flatten an array:

```scala
Config.load[Hosts](
  ConfigSource.fromMap(Map("items.0" -> "alpha", "items.1" -> "beta", "items.2" -> "gamma"))
)
// res12: Either[::[ConfigError], Hosts] = Right(
//   Hosts(List("alpha", "beta", "gamma"))
// )
```

When no indexed key exists, the deriver falls back to reading the prefix itself as a single comma-separated string, which is what an environment variable can realistically hold:

```scala
Config.load[Hosts](ConfigSource.fromMap(Map("items" -> "alpha, beta, gamma")))
// res13: Either[::[ConfigError], Hosts] = Right(
//   Hosts(List("alpha", "beta", "gamma"))
// )
```

Elements are trimmed after splitting, so `"alpha, beta"` and `"alpha,beta"` are equivalent. An element containing a comma cannot be expressed in the flat form; use indexed keys for those.

Probing stops at the first index that has neither a value nor any nested keys beneath it, so `items.0` and `items.2` without `items.1` yields a one-element list rather than an error.

### Maps

A map's keys are discovered by enumeration: the deriver calls `ConfigSource#all` on the prefix and takes the first path segment after it as a map key. Values decode at `prefix.key`:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Limits(counts: Map[String, Int])

object Limits {
  implicit val schema: Schema[Limits] = Schema.derived[Limits]
}
```

Each distinct segment under `counts` becomes one entry:

```scala
Config.load[Limits](ConfigSource.fromMap(Map("counts.read" -> "100", "counts.write" -> "20")))
// res15: Either[::[ConfigError], Limits] = Right(
//   Limits(Map("read" -> 100, "write" -> 20))
// )
```

Because discovery goes through `ConfigSource#all`, a map cannot be decoded from a source that does not enumerate. Map keys themselves are decoded through the key type's decoder, so a `Map[Int, String]` requires segments that parse as integers.

### Sealed Traits

A sealed trait is decoded by reading a discriminator key that names which case to use. The default key is `type`, read at `prefix.type`:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

sealed trait Backend

object Backend {
  case class Postgres(host: String, port: Int) extends Backend
  case class Sqlite(path: String)              extends Backend

  implicit val schema: Schema[Backend] = Schema.derived[Backend]
}

case class Store(backend: Backend)

object Store {
  implicit val schema: Schema[Store] = Schema.derived[Store]
}
```

The discriminator selects the case, and the remaining keys are read at the same prefix as the case's own fields:

```scala
Config.load[Store](
  ConfigSource.fromMap(
    Map("backend.type" -> "Postgres", "backend.host" -> "localhost", "backend.port" -> "5432")
  )
)
// res17: Either[::[ConfigError], Store] = Right(
//   Store(Postgres(host = "localhost", port = 5432))
// )
```

A missing discriminator produces `ConfigError.MissingDiscriminatorKey`, which names the path and the key it expected:

```scala
Config.load[Store](ConfigSource.fromMap(Map("backend.host" -> "localhost")))
// res18: Either[::[ConfigError], Store] = Left(
//   List(MissingDiscriminatorKey(path = "backend", key = "type"))
// )
```

An unrecognized discriminator produces `ConfigError.UnknownDiscriminator`, which lists every accepted value in sorted order — the error tells the reader what to write instead:

```scala
Config.load[Store](ConfigSource.fromMap(Map("backend.type" -> "MySql")))
// res19: Either[::[ConfigError], Store] = Left(
//   List(
//     UnknownDiscriminator(
//       path = "backend.type",
//       found = "MySql",
//       expected = List("Postgres", "Sqlite")
//     )
//   )
// )
```

Case names are matched exactly as the schema reports them, which for a plain sealed trait is the simple class name.

### Wrappers

A wrapper type — one whose schema is a `Binding.Wrapper`, such as a newtype over `String` — decodes its underlying representation and then applies the wrapping function. If wrapping throws, the failure is reported as `ConfigError.InvalidValue` at the wrapper's path, with the thrown exception as the cause. This is how validating newtypes surface their validation failures as ordinary config errors.

### Dynamic Values

A `DynamicValue` field is not parsed at all. A present key becomes a `DynamicValue.Primitive` holding the raw string, and an absent key becomes `DynamicValue.Null` rather than an error. Use it for config sections whose shape is not known at compile time.

## Customizing Derivation

`ConfigDecoderDeriver` is an ordinary `Deriver[ConfigDecoder]`, so the schema layer's override mechanisms apply. Two customizations are specific to config decoding.

### Choosing a Discriminator Key

`ConfigDecoderDeriver#discriminator` returns a new deriver that reads a different discriminator key. Pass the deriver to `Config.load` to use it:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

sealed trait Backend

object Backend {
  case class Postgres(host: String, port: Int) extends Backend
  case class Sqlite(path: String)              extends Backend

  implicit val schema: Schema[Backend] = Schema.derived[Backend]
}

case class Store(backend: Backend)

object Store {
  implicit val schema: Schema[Store] = Schema.derived[Store]
}

val kindDeriver = ConfigDecoderDeriver.discriminator("kind")
```

The same config now selects its case from `backend.kind`:

```scala
Config.load[Store](
  ConfigSource.fromMap(Map("backend.kind" -> "Sqlite", "backend.path" -> "/tmp/db.sqlite")),
  kindDeriver
)
// res21: Either[::[ConfigError], Store] = Right(
//   Store(Sqlite("/tmp/db.sqlite"))
// )
```

`ConfigDecoderDeriver` — the object — is the default instance, equivalent to `new ConfigDecoderDeriver("type")`.

### Overriding a Single Type

To change how one type decodes while leaving everything else alone, build the decoder through the schema's `deriving` API and supply an instance override. This is the standard `Deriver` workflow described in the schema module's derivation documentation; the only config-specific part is that the type class being overridden is `ConfigDecoder`.

## Error Accumulation

Decoding does not stop at the first failure. A record decoder attempts every field, collects the errors, and only then decides the outcome. The exact shape of the `Left` depends on how many errors it finds: a single error is returned on its own, and two or more are wrapped in one `ConfigError.Composite`.

That wrapping happens per record, so a nested config produces nested composites — one per record that had more than one failing field:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}
```

An empty source fails both fields, so both errors arrive together:

```scala
Config.load[Db](ConfigSource.fromMap(Map.empty[String, String], "empty"))
// res23: Either[::[ConfigError], Db] = Left(
//   List(
//     Composite(
//       List(
//         MissingKey(path = "host", source = "empty"),
//         MissingKey(path = "port", source = "empty")
//       )
//     )
//   )
// )
```

`ConfigError.Composite#message` joins the child messages with newlines, and `Config.loadOrThrow` formats them into a numbered report. See [Errors](./errors.md) for the full error model.

## Integration Points

`ConfigDecoder` sits between the schema module and the source layer. It depends on `Schema[A]` and the `Deriver` machinery from `zio-blocks-schema`, reads through `ConfigSource`, and reports failures as `ConfigError`. `Config` is the user-facing wrapper, and `Config.wire` places a derived decoder inside a `zio-blocks-scope` dependency graph.

See [ConfigSource](./config-source.md) for building the sources a decoder reads from, and [Errors](./errors.md) for handling what it returns.
