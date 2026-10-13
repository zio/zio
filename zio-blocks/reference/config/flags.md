# Flags

> A flag is a named scalar whose value comes from outside the program. `StaticFlag[A]` resolves once at class-load time and never changes; `DynamicFlag[A]` holds a rollout expression that can be updated while the process runs. Both read through `FlagSource`, parse through `Flag.Reader`, and register themselves in a global registry that `Flag.dump` can print. Supporting types: `Flag.Source`, `FlagException`. The three declarations you write against:

```scala
trait FlagSource {
  def sourceId: String
  def get(name: String): Maybe[SourceValue[String]]
  final def orElse(fallback: FlagSource): FlagSource
}

abstract class StaticFlag[A](default: A)(implicit reader: Flag.Reader[A], displayable: Displayable[A])

abstract class DynamicFlag[A](default: A, defaultExpression: String)(implicit reader: Flag.Reader[A])
```

## Motivation

Configuration and flags answer different questions. Configuration describes a deployment: which database, which port, which region. Flags describe a decision: is this feature on, for whom, and at what percentage. The two have different shapes — configuration is a typed record loaded once, a flag is a single value read at the point of use — and different failure modes.

Flags in this module are Scala objects. That choice buys three things. The name is derived from the object's fully-qualified name, so it cannot drift from the code that reads it. The value is a `val`, so reading it is a field access with no lookup cost. And references are compile-checked: a deleted flag is a compile error at every use site, not a silently-missing key.

The cost is that resolution happens during class initialization, which makes ordering matter. A `StaticFlag` touched before its `FlagSource` is registered resolves from the environment or its default and stays that way.

## FlagSource

`FlagSource` is the scalar half of `ConfigSource`: a name-to-string lookup with provenance, without prefix enumeration. Because `ConfigSource extends FlagSource`, any config source is also a flag source.

### Creating a Source

`FlagSource.fromMap` builds an in-memory source, which is what tests and local overrides use:

```scala
import zio.blocks.config._

val source = FlagSource.fromMap(Map("myapp.poolSize" -> "32"), "local")
```

Lookups return the value with a `Provenance.Resolved` naming the source and the name it answered under:

```scala
source.get("myapp.poolSize")
// res0: Maybe[SourceValue[String]] = SourceValue(
//   value = "32",
//   provenance = Resolved(
//     sourceId = "local",
//     key = "myapp.poolSize",
//     rawValue = "32"
//   )
// )
```

`FlagSource#orElse` layers sources with the receiver taking priority, exactly as on `ConfigSource`.

### The Registry

Flags do not take a source as a constructor argument. They consult `FlagSource.Registry`, a thread-safe global that flags read during initialization:

| Method                          | Effect                                                                 |
| ------------------------------- | ---------------------------------------------------------------------- |
| `Registry.register(source)`     | Adds a source, keyed by `sourceId`; re-registering an id replaces it in place. |
| `Registry.unregister(sourceId)` | Removes a source by id.                                                 |
| `Registry.get(sourceId)`        | Looks up a registered source.                                           |
| `Registry.all`                  | Every source in registration order.                                     |
| `Registry.resolve(name)`        | First source that answers the name wins.                                |
| `Registry.clear()`              | Removes everything; intended for tests.                                 |

Registration order is resolution order, so the first source registered has the highest priority:

```scala
import zio.blocks.config._

FlagSource.Registry.register(FlagSource.fromMap(Map("myapp.poolSize" -> "64"), "remote"))
FlagSource.Registry.register(FlagSource.fromMap(Map("myapp.poolSize" -> "32"), "fallback"))

FlagSource.Registry.resolve("myapp.poolSize")
```

Re-registering an existing `sourceId` swaps the source without changing its position, so refreshing a source's contents does not reshuffle priorities.

:::warning[Register before first touch]
A `StaticFlag` resolves during its object's initialization, which happens the first time anything references it. Sources registered after that point are never consulted for that flag. Register every source at the very top of `main`, before touching any flag.
:::

## StaticFlag

`StaticFlag[A]` is for values that are fixed for the lifetime of the process: pool sizes, buffer limits, endpoints that cannot change without a restart.

### Defining a Flag

Extend `StaticFlag[A]` from a Scala `object`, passing the default:

```scala
import zio.blocks.config._

object poolSize extends StaticFlag[Int](10)

val size: Int = poolSize()
```

The flag's `name` is the object's fully-qualified name with `$` separators rewritten to dots, so an object `Limits.poolSize` in package `com.example` is named `com.example.Limits.poolSize`. Nothing declares that name — it follows from where the object lives, which means moving the object renames the flag.

### Resolution Order

Resolution runs once, in a fixed order, and stops at the first source that produces a value:

```
1. FlagSource.Registry.resolve(name)     ← registration order across all sources
2. System property with the flag's name  ← -Dcom.example.Limits.poolSize=32
3. Environment variable                  ← COM_EXAMPLE_LIMITS_POOLSIZE=32
4. The constructor default
```

The environment variable name is the flag name with dots replaced by underscores, uppercased. That translation is fixed and not configurable.

### Members

Each resolved flag exposes what it resolved to and where from:

| Member         | Type          | Meaning                                                   |
| -------------- | ------------- | --------------------------------------------------------- |
| `name`         | `String`      | Derived fully-qualified flag name.                         |
| `value`        | `A`           | The resolved value.                                        |
| `apply()`      | `A`           | Same as `value`; reads as a call at use sites.              |
| `source`       | `Flag.Source` | Which of the four resolution steps answered.                |
| `provenance`   | `Provenance`  | Source id, key, and raw string.                             |
| `displayValue` | `String`      | Rendered through `Displayable[A]`, used by `Flag.dump`.      |

### Failure at Initialization

An object initializer cannot return an `Either`, so a flag that cannot resolve throws. A value that fails to parse throws `ExceptionInInitializerError` wrapping `FlagException.FlagValueParseException`, and a flag whose defining class is not a Scala object throws `FlagException.FlagNameException`.

Failing at class load is the point: a malformed flag stops the process at startup rather than producing a plausible-looking wrong value that surfaces later.

:::note[Secrets in flags]
`Flag.Reader[Secret]` accepts any string, and the module provides `Displayable[Secret]` in the `zio.blocks.config` package object, so a `StaticFlag[Secret]` renders as `<secret>` in dumps without extra configuration. See [ConfigSource](./config-source.md) for `Secret` itself.
:::

## DynamicFlag

`DynamicFlag[A]` is for decisions that change without a restart: enabling a feature for a subset of users, shifting traffic to a new code path, turning off an expensive query under load.

### Defining a Flag

Extend `DynamicFlag[A]` with both a default value and a default rollout expression:

```scala
import zio.blocks.config._

object newCheckout extends DynamicFlag[Boolean](false, "true@*/50%; false")

val enabled: Boolean = newCheckout("user-1234")
```

The initial expression resolves through the same chain as a static flag's value — registry, then system property, then environment variable, then the constructor default — but what it resolves is the *expression*, not the value. A malformed expression throws `ExceptionInInitializerError` wrapping `FlagException.FlagExpressionParseException`.

### Evaluation

`DynamicFlag#apply` takes a bucketing key and any number of path attributes, evaluates the expression, and tracks a counter for the key. `DynamicFlag#evaluate` does the same without touching counters:

```scala
import zio.blocks.config._

object newCheckout extends DynamicFlag[Boolean](false, "true@*/prod/*/50%; false")

newCheckout("user-1234", "prod", "eu")           // counted
newCheckout.evaluate("user-1234", "prod", "eu") // not counted
```

The key determines the percentage bucket, and it is also the *first* segment of the path a selector matches against: the attributes are appended to it with slashes, so the call above matches against `user-1234/prod/eu`. A selector must have exactly as many segments as that path, which is why the expression starts with a wildcard. Evaluation details are in [Rollout](./rollout.md).

When a matched value cannot be parsed into `A`, evaluation falls back to the flag's default and increments `DynamicFlag#parseErrorCount`. A non-zero count means the expression contains values the reader rejects — a misconfiguration that would otherwise be invisible, since every call still returns something usable.

### Updating and Reloading

`DynamicFlag#update` replaces the expression from code, returning `Left` if the new expression will not parse and leaving the old one in place:

```scala
import zio.blocks.config._

object newCheckout extends DynamicFlag[Boolean](false, "false")

newCheckout.update("true@prod/25%; false")
newCheckout.expression
```

An empty or whitespace-only expression is accepted as a no-op and returns `Right(())` without changing anything.

`DynamicFlag#reload` re-reads the expression from `FlagSource.Registry` and reports what happened as a `Flag.ReloadResult`. See [Rollout](./rollout.md) for the result variants and the update history.

## Flag.Reader

`Flag.Reader[A]` parses a raw string into `A` and supplies a type name for error messages. Instances are resolved implicitly by both flag types:

```scala
trait Reader[A] {
  def parse(flagName: String, raw: String): Either[ConfigError, A]
  def typeName: String
}
```

### Built-in Instances

| Type                | Accepted form                                              | Example  |
| ------------------- | ---------------------------------------------------------- | -------- |
| `Int`, `Long`       | Decimal integer literal                                     | `32`     |
| `Double`            | Decimal or scientific literal                               | `0.5`    |
| `Boolean`           | `true`/`false`, `1`/`0`, `yes`/`no`, `on`/`off` (any case)   | `on`     |
| `String`            | Any string, taken as-is                                      | `eu-west` |
| `Secret`            | Any string, wrapped so it cannot be printed                  | `hunter2` |
| `FiniteDuration`    | Digits followed by a unit suffix                              | `30s`    |
| `Seq[A]`            | Comma-separated values of a scalar `A`, each trimmed          | `a, b, c` |

Boolean parsing accepts the same set of spellings as config decoding:

```scala
Flag.Reader.booleanReader.parse("example", "ON")
// res6: Either[ConfigError, Boolean] = Right(true)
Flag.Reader.booleanReader.parse("example", "maybe")
// res7: Either[ConfigError, Boolean] = Left(
//   InvalidValue(
//     path = "example",
//     value = "maybe",
//     expectedType = "Boolean (true/false/1/0/yes/no/on/off)",
//     source = "flag",
//     cause = None
//   )
// )
```

Duration suffixes are `ms`/`millis`/`milliseconds`, `s`/`sec`/`secs`/`seconds`, `m`/`min`/`mins`/`minutes`, `h`/`hour`/`hours`, and `d`/`day`/`days`:

```scala
Flag.Reader.durationReader.parse("example", "500ms")
// res8: Either[ConfigError, FiniteDuration] = Right(500 milliseconds)
Flag.Reader.durationReader.parse("example", "2hours")
// res9: Either[ConfigError, FiniteDuration] = Right(2 hours)
```

The grammar is digits immediately followed by letters, so a space between the number and the unit is rejected:

```scala
Flag.Reader.durationReader.parse("example", "2 hours")
// res10: Either[ConfigError, FiniteDuration] = Left(
//   InvalidValue(
//     path = "example",
//     value = "2 hours",
//     expectedType = "Duration (e.g., 10s, 5m, 1h, 100ms, 2d)",
//     source = "flag",
//     cause = None
//   )
// )
```

:::warning[Flag durations are not config durations]
`Flag.Reader[FiniteDuration]` parses `30s`. Config decoding parses `java.time.Duration` with `Duration.parse`, which requires ISO-8601 (`PT30S`). Neither accepts the other's format — see [Config Decoder](./config-decoder.md).
:::

### Scalar and Sequence Readers

`Flag.Reader.Scalar[A]` marks a reader whose values contain no commas. `Flag.Reader.seqReader` requires that marker, because it splits on commas — a non-scalar element type would make the split ambiguous:

```scala
Flag.Reader.seqReader[Int].parse("example", "1, 2, 3")
// res11: Either[ConfigError, Seq[Int]] = Right(ArraySeq(1, 2, 3))
```

An empty string parses to an empty sequence rather than failing. If any element fails, its error stands for the whole list.

### Custom Readers

`Flag.Reader.apply` builds a reader from a parse function and a type name, and `Flag.Reader.scalar` does the same for comma-free types so that `Seq` of them works:

```scala
import zio.blocks.config._

final case class Region(code: String)

implicit val regionReader: Flag.Reader.Scalar[Region] =
  Flag.Reader.scalar(
    (flagName, raw) =>
      if (raw.matches("[a-z]{2}-[a-z]+-\\d")) Right(Region(raw))
      else Left(ConfigError.InvalidValue(flagName, raw, "Region (e.g. eu-west-1)", "flag")),
    "Region"
  )
```

Return `ConfigError.InvalidValue` from a failing parse so the resulting message matches every other config failure.

## Flag.Source

`Flag.Source` records which resolution step answers a static flag:

| Variant                            | Meaning                                        |
| ---------------------------------- | ---------------------------------------------- |
| `Flag.Source.FlagSourceValue(id)`  | A registered `FlagSource` answered; `id` names it. |
| `Flag.Source.SystemProperty`       | A JVM system property answered.                 |
| `Flag.Source.EnvironmentVariable`  | An environment variable answered.               |
| `Flag.Source.Default`              | Nothing answered; the constructor default applies. |

Checking for `Flag.Source.Default` at startup is how you detect a flag you meant to configure and did not.

## Diagnostics

Two helpers on `Flag` exist for the two questions that come up when a flag has the wrong value: what did everything resolve to, and did I misspell something?

### Flag.dump

`Flag.dump()` renders every registered flag — static and dynamic — as a table of name, type, value, and source. Static flags show their `displayValue` and provenance source id; dynamic flags show their current expression:

```scala
import zio.blocks.config._

println(Flag.dump())
```

The registry is populated by construction, so a flag appears only after something has touched it. Print the dump after startup has forced every flag, not before.

### Flag.nearMissWarnings

`Flag.nearMissWarnings` looks for names that resemble a flag but do not match it, which catches the common failures: a case mismatch in a system property, a case mismatch in an environment variable, or a registered source holding a nearly-right key:

```scala
import zio.blocks.config._

Flag.nearMissWarnings("com.example.Limits.poolSize").foreach(println)
```

Candidates checked include the name lowercased and uppercased, dots swapped for underscores, and underscores swapped for dots. A warning means a source contains something similar — it does not mean the flag is wrong, only that a typo is plausible.

## FlagException

Flag failures are exceptions rather than values because they occur inside object initializers. `FlagException` extends `NoStackTrace`, so constructing one is cheap:

| Exception                                | Thrown when                                                                 |
| ---------------------------------------- | --------------------------------------------------------------------------- |
| `FlagValueParseException`                | A static flag's raw value fails its `Flag.Reader`.                           |
| `FlagExpressionParseException`            | A dynamic flag's initial rollout expression will not parse.                  |
| `FlagNameException`                       | The defining class is not a Scala object, or is a lambda or anonymous class.  |
| `FlagDuplicateNameException`              | A second flag registers under an existing name.                              |

`FlagValueParseException` and `FlagExpressionParseException` are wrapped in `ExceptionInInitializerError` by the JVM when thrown from an initializer, so catch that and inspect its cause:

```scala
import zio.blocks.config._

try {
  // Touching a flag object forces its initialization.
  Class.forName("com.example.Limits$poolSize$")
} catch {
  case e: ExceptionInInitializerError =>
    e.getCause match {
      case f: FlagException => println(f.getMessage)
      case other           => throw other
    }
}
```

`FlagDuplicateNameException` means two objects derived the same name, which happens when two flags share a fully-qualified name after `$`-to-dot rewriting. Renaming either object resolves it.

## Integration Points

Flags depend on `FlagSource` — and therefore accept any `ConfigSource` — on `Flag.Reader` for parsing, on `Displayable` for rendering, and on `ConfigError` for parse failures. `DynamicFlag` additionally depends on `Rollout` for expression evaluation.

See [Rollout](./rollout.md) for the expression language and reload lifecycle, [ConfigSource](./config-source.md) for sources and `Secret`, and [Errors](./errors.md) for the shared error model.
