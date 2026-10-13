# Configuration Errors

> `ConfigError` is the failure type for every configuration operation. It is a sealed hierarchy divided into four category traits, so code can match on the kind of problem — a missing key, an unparseable value, a source-level failure, a derivation failure — without enumerating every constructor. `ConfigLoadException` wraps a set of errors when a load is allowed to throw. The hierarchy:

```scala
sealed trait ConfigError extends NoStackTrace {
  def message: String
  override def getMessage: String = message
}

sealed trait ConfigParseError      extends ConfigError
sealed trait ConfigValidationError extends ConfigError
sealed trait ConfigSourceError     extends ConfigError
sealed trait ConfigDerivationError extends ConfigError
```

## Motivation

Configuration failures need three properties that a plain exception does not provide.

They need to be **plural**. A config file with four mistakes should report four mistakes, so the person fixing it makes one pass rather than four. That rules out throwing at the first problem, which is why every decoding operation returns `Either[::[ConfigError], A]` — a non-empty list, so a `Left` is guaranteed to say something.

They need to be **cheap**. `ConfigError` extends `NoStackTrace`, so constructing one does not capture a stack trace. Config errors are diagnosed by reading the message, not by reading a trace through the deriver's internals, and a decode that fails on twenty fields should not pay for twenty stack captures.

They need to be **classifiable**. Whether a value was missing, malformed, or rejected by the source changes what the operator should do about it. The four category traits exist so that distinction survives into your error handling.

## Category Traits

Every concrete error extends exactly one category, except `ConfigError.Composite`, which extends `ConfigError` directly because it aggregates errors that may span categories:

| Category                 | Meaning                                        | Constructors                                            |
| ------------------------ | ---------------------------------------------- | ------------------------------------------------------- |
| `ConfigParseError`       | Value present but not convertible               | `InvalidValue`, `ParseError`                             |
| `ConfigValidationError`  | Value parsed but semantically rejected          | *(none built in)*                                        |
| `ConfigSourceError`      | Problem with the source itself                  | `MissingKey`, `DuplicateKey`, `Unauthorized`             |
| `ConfigDerivationError`  | Schema-driven decoding could not proceed        | `UnknownDiscriminator`, `MissingDiscriminatorKey`         |
| *(no category)*          | Aggregate of several errors                     | `Composite`                                              |

:::note[`ConfigValidationError` has no constructors]
The category is declared and documented, but no error in the module currently extends it, and because the trait is sealed it cannot be extended from outside. Matching on it compiles and never matches. Validation failures raised by wrapper types surface as `ConfigError.InvalidValue` instead.
:::

## Error Constructors

Each constructor carries the fields needed to act on the failure without re-reading the config, and renders them into a single-line `message`.

### ConfigError.MissingKey

A required key was not found. Produced by primitive decoding when the key is absent and the field has neither a default nor an `Option` type:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}
```

The error names the dotted path and the source that was searched, which for a composed source is the pipe-joined id of every source in the chain:

```scala
Config.load[Db](ConfigSource.fromMap(Map("port" -> "5432"), "defaults"))
// res0: Either[::[ConfigError], Db] = Left(
//   List(MissingKey(path = "host", source = "defaults"))
// )
```

`MissingKey` is the error that optional fields and defaults absorb. A field of type `Option[A]` becomes `None`, and a field with a declared default takes that default, but only when *every* error from that field is a `MissingKey` — a mix of missing and invalid still fails.

### ConfigError.InvalidValue

A key was present but its value could not be converted to the expected type. Carries the path, the offending string, a human-readable expected type, the source id, and an optional underlying exception:

```scala
Config.load[Db](ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "eight"), "defaults"))
// res1: Either[::[ConfigError], Db] = Left(
//   List(
//     InvalidValue(
//       path = "port",
//       value = "eight",
//       expectedType = "Int",
//       source = "defaults",
//       cause = Some(java.lang.NumberFormatException: For input string: "eight")
//     )
//   )
// )
```

The `expectedType` string is derived from the schema's primitive type name. When a `cause` is present, its message is appended to the rendered message, which is where the underlying `NumberFormatException` text comes from.

`InvalidValue` is also what a failing wrapper type produces: if a newtype's constructor throws while wrapping a successfully-decoded inner value, the thrown exception becomes the `cause`.

### ConfigError.ParseError

The raw value could not be decoded at the format level, as distinct from a type mismatch on a well-formed value. Carries the path, source, expected format, and optional cause. The HOCON adapter produces it when the document itself is malformed, and the JVM file loader produces it for a missing file or a rejected include path.

The difference from `InvalidValue` is which layer failed. `InvalidValue` means "this string is not an `Int`"; `ParseError` means "this document is not HOCON", so there is no string to quote.

### ConfigError.DuplicateKey

The same key appears in multiple conflicting sources. Carries the path and the ids of every source that defined it. Nothing in the module produces it — `ConfigSource#orElse` resolves conflicts by precedence rather than reporting them — so it exists for custom sources that want to treat ambiguity as an error rather than resolving it silently.

### ConfigError.Unauthorized

Access to a key was denied by the source. Carries the path and source id. Like `DuplicateKey`, no built-in source produces it; it is the error a custom source backed by a secrets manager should return when a lookup is rejected rather than merely absent, so that "you may not read this" is distinguishable from "this does not exist".

### ConfigError.MissingDiscriminatorKey

A sealed trait was decoded but the key naming which case to use was absent. Carries the record's path and the discriminator key that was expected:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

sealed trait Backend

object Backend {
  case class Postgres(host: String, port: Int) extends Backend
  case class Sqlite(path: String)              extends Backend

  implicit val schema: Schema[Backend] = Schema.derived[Backend]
}
```

The message states both, so the fix is unambiguous:

```scala
Config.load[Backend](ConfigSource.fromMap(Map("host" -> "localhost"), "conf"))
// res3: Either[::[ConfigError], Backend] = Left(
//   List(MissingDiscriminatorKey(path = "", key = "type"))
// )
```

### ConfigError.UnknownDiscriminator

The discriminator key was present but named a case that does not exist. Carries the full key path, the value found, and every accepted value in sorted order:

```scala
Config.load[Backend](ConfigSource.fromMap(Map("type" -> "MySql"), "conf"))
// res4: Either[::[ConfigError], Backend] = Left(
//   List(
//     UnknownDiscriminator(
//       path = "type",
//       found = "MySql",
//       expected = List("Postgres", "Sqlite")
//     )
//   )
// )
```

Listing the expected values in the message is deliberate: a typo in a discriminator is one of the few config mistakes where the error can name the correct answer.

### ConfigError.Composite

Two or more errors accumulated while decoding one record. Carries them as a `::[ConfigError]`, and renders as the child messages joined with newlines:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}

val failed = Config.load[Db](ConfigSource.fromMap(Map.empty[String, String], "empty"))
```

A record with a single failing field returns that error directly, so `Composite` appears only when there is genuinely more than one:

```scala
failed.left.map(_.head.getClass.getSimpleName)
// res6: Either[String, Db] = Left("Composite")
```

Composites nest. Wrapping happens once per record, so a nested config whose inner and outer records both have multiple failures produces a `Composite` containing a `Composite`. Flatten before reporting if you want one list:

```scala
import zio.blocks.config._

def flatten(error: ConfigError): List[ConfigError] = error match {
  case ConfigError.Composite(errors) => errors.toList.flatMap(flatten)
  case other                         => List(other)
}
```

## Handling Errors

How you consume a `Left` depends on whether the response varies by kind of failure. Matching on categories keeps the handler stable as new constructors are added; matching on constructors gets you the fields.

### Matching by Category

Categories answer "whose fault is this?", which is usually what determines the response — a missing key is an operator problem, a derivation error is a schema problem:

```scala
import zio.blocks.config._

def describe(error: ConfigError): String = error match {
  case _: ConfigSourceError     => "configuration is incomplete or unreadable"
  case _: ConfigParseError      => "a configured value has the wrong format"
  case _: ConfigDerivationError => "the config shape does not match the schema"
  case _                        => "multiple problems"
}
```

### Matching by Constructor

Matching the concrete type gives access to the fields, which is what you need to build a targeted message or a machine-readable report:

```scala
import zio.blocks.config._

def remedy(error: ConfigError): String = error match {
  case ConfigError.MissingKey(path, source) =>
    s"set $path in $source"
  case ConfigError.InvalidValue(path, value, expected, _, _) =>
    s"$path is '$value' but must be a $expected"
  case ConfigError.UnknownDiscriminator(path, found, expected) =>
    s"$path is '$found'; use one of ${expected.mkString(", ")}"
  case other =>
    other.message
}
```

Because `ConfigError` extends `NoStackTrace`, and therefore `Throwable`, an individual error can also be thrown or wrapped directly when integrating with code that expects exceptions.

## ConfigLoadException

`Config.loadOrThrow` throws `ConfigLoadException` rather than returning a `Left`. The exception carries both a formatted report for humans and the original error list for programs:

```scala
final class ConfigLoadException(val report: String, val errors: ::[ConfigError])
  extends RuntimeException(report)
```

`report` is a multi-line summary: a count line followed by one indented bullet per top-level error. Catching the exception and reading `errors` recovers the structured form, so throwing does not lose information:

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}

try Config.loadOrThrow[Db](ConfigSource.fromMap(Map.empty[String, String], "empty"))
catch {
  case e: ConfigLoadException =>
    println(e.report)
    e.errors.toList.foreach(err => println(err.message))
}
```

Use `Config.loadOrThrow` at the top of `main`, where an unrecoverable config problem should stop startup with a readable message. Prefer `Config.load` anywhere the failure is recoverable — a reload, a per-request decode, or a validation pass that reports rather than aborts.

:::warning[`Config.wire` throws]
`Config.wire` decodes with `Config.loadOrThrow`, so a bad config surfaces as a `ConfigLoadException` while the dependency graph is being allocated rather than as a typed failure. Validate with `Config.load` first if you need to report config problems before touching the graph.
:::

## Flag Exceptions

Flag resolution has a separate hierarchy, `FlagException`, because flags fail at class-load time and cannot return an `Either` from an object initializer. `ConfigError` still appears inside those exceptions as the underlying cause — a flag whose value will not parse carries the same `ConfigError.InvalidValue` a config field would. See [Flags](./flags.md).

## Integration Points

`ConfigError` is produced by `ConfigDecoder`, by every `ConfigSource` constructor that can fail, and by `Rollout` when an expression will not parse. It is consumed by `Config`, which either returns it or formats it into a `ConfigLoadException`, and by `Flag.ReloadResult.Failed`, which carries it out of a failed dynamic-flag reload.

See [Config Decoder](./config-decoder.md) for which rule produces which error, and [Rollout](./rollout.md) for expression-level failures.
