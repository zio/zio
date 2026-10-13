# File Formats

> Three optional modules add file-format support: `config-yaml`, `config-json`, and `config-hocon`. Each contributes one constructor to the `ConfigSource` companion, parses its format into an in-memory tree, and flattens that tree into the dot-separated namespace every `ConfigSource` exposes. Supporting types: `YamlConfigSource`, `HoconValue`, `HoconError`. One constructor per format:

```scala
// with `import zio.blocks.config.yaml._`
ConfigSource.fromYaml(yaml: String, sourceId: String = "yaml:string"): Either[ConfigError, ConfigSource]

// with `import zio.blocks.config.json._`
ConfigSource.fromJson(json: String, sourceId: String = "json:string"): Either[ConfigError, ConfigSource]

// with `import zio.blocks.config.hocon._`
ConfigSource.fromHocon(hocon: String, sourceId: String = "hocon:string"): Either[ConfigError, ConfigSource]
```

## Motivation

A decoder that understood YAML, JSON, and HOCON natively would be three decoders with three sets of bugs. Flattening avoids that: each format is parsed by code that knows only that format, and the result is the same flat map of dotted keys the core module already handles.

The consequence is that everything above the source layer is format-blind. `ConfigSource#orElse` layers a YAML file under environment variables. `ConfigDecoder` reads a JSON document with the same rules it uses for a `Map`. Provenance reports `yaml:string` where it would otherwise report `env`. Nothing in the decoding path branches on format.

Keeping the adapters in separate artifacts means a service that only reads environment variables does not pull a YAML parser, and adding HOCON support is a build change rather than a code change.

## Common Flattening Rules

All three adapters agree on how a tree becomes flat keys. Learning the rules once tells you what key any document produces:

| Tree shape                | Flattened form                            | Example                                     |
| ------------------------- | ----------------------------------------- | ------------------------------------------- |
| Nested object or mapping  | Keys joined with `.`                       | `db: { host: x }` → `db.host` = `x`          |
| Array or sequence         | Zero-based numeric segment                 | `hosts: [a, b]` → `hosts.0` = `a`, `hosts.1` = `b` |
| Scalar                    | The value as a string                      | `port: 8080` → `port` = `"8080"`             |
| Null                      | **Omitted entirely**                       | `tag: null` → no `tag` key                   |

Two of these have consequences worth stating plainly.

Indexed sequences are exactly the shape `ConfigDecoder` probes for, so a YAML list decodes into a `List` without any extra configuration. See [Config Decoder](./config-decoder.md).

Null omission means an explicit null is indistinguishable from an absent key. A field of type `Option[A]` becomes `None`, a field with a default takes its default, and a required field reports `ConfigError.MissingKey`. There is no way to express "present but null" through a flattened source.

:::warning[Nulls disappear]
Writing `tag: null` to unset a value from a lower-priority layer does not work. `ConfigSource#orElse` sees no key at all, so the fallback still wins. Override with a real value instead.
:::

## YAML

`config-yaml` reads YAML through the `zio-blocks-schema-yaml` reader:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-config-yaml" % "0.0.56"
```

Importing the adapter package makes the constructor available on the `ConfigSource` companion:

```scala
import zio.blocks.config._
import zio.blocks.config.yaml._

val yamlText =
  """|db:
     |  host: localhost
     |  port: 5432
     |hosts:
     |  - alpha
     |  - beta
     |""".stripMargin
```

Nested mappings become dotted keys and the sequence becomes indexed keys, all as strings:

```scala
ConfigSource.fromYaml(yamlText, "app.yaml")
// res0: Either[ConfigError, ConfigSource] = Right(
//   MapSource(
//     map = Map(
//       "hosts.0" -> "alpha",
//       "hosts.1" -> "beta",
//       "db.port" -> "5432",
//       "db.host" -> "localhost"
//     ),
//     sourceId = "app.yaml"
//   )
// )
```

The resulting source behaves like any other, so decoding a case class needs nothing format-specific:

```scala
import zio.blocks.schema.Schema

case class Db(host: String, port: Int)

object Db {
  implicit val schema: Schema[Db] = Schema.derived[Db]
}
```

Re-rooting with `ConfigSource#prefix` reads the `db` section:

```scala
ConfigSource.fromYaml(yamlText, "app.yaml").map(src => Config.load[Db](src.prefix("db")))
// res1: Either[ConfigError, Either[::[ConfigError], Db]] = Right(
//   Right(Db(host = "localhost", port = 5432))
// )
```

A document that will not parse yields `ConfigError.InvalidValue` whose `value` is the first 100 characters of the input, truncated with an ellipsis, and whose `cause` is the underlying reader exception. `YamlConfigSource.fromString` is the same operation without the syntax import, for code that prefers an explicit call.

Non-scalar mapping keys are skipped rather than rejected, since a dotted key namespace cannot represent them.

## JSON

`config-json` reads JSON through the `zio-blocks-schema-json` parser:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-config-json" % "0.0.56"
```

The import and the constructor mirror the YAML adapter:

```scala
import zio.blocks.config._
import zio.blocks.config.json._

val jsonText =
  """|{
     |  "db": { "host": "localhost", "port": 5432 },
     |  "hosts": ["alpha", "beta"],
     |  "debug": true
     |}
     |""".stripMargin
```

Numbers and booleans are rendered with their `toString`, so every value arrives as a string for the decoder to parse:

```scala
ConfigSource.fromJson(jsonText, "app.json")
// res3: Either[ConfigError, ConfigSource] = Right(
//   MapSource(
//     map = Map(
//       "db.port" -> "5432",
//       "hosts.0" -> "alpha",
//       "hosts.1" -> "beta",
//       "debug" -> "true",
//       "db.host" -> "localhost"
//     ),
//     sourceId = "app.json"
//   )
// )
```

A malformed document yields `ConfigError.InvalidValue` with a 100-character excerpt of the input and the parser's `SchemaError` as the cause.

## HOCON

`config-hocon` has its own parser with no external dependency, and it is the only adapter that supports substitutions and includes:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-config-hocon" % "0.0.56"
```

### Parsing a String

The package object mixes in both the shared and platform-specific syntax, so one import brings in every constructor available on the current platform:

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

val hoconText =
  """|db {
     |  host = "localhost"
     |  port = 5432
     |}
     |""".stripMargin
```

Braces nest into dotted keys, and an integral number flattens without a decimal point — `5432`, not `5432.0`:

```scala
ConfigSource.fromHocon(hoconText, "app.conf")
// res5: Either[ConfigError, ConfigSource] = Right(
//   MapSource(
//     map = Map("db.host" -> "localhost", "db.port" -> "5432"),
//     sourceId = "app.conf"
//   )
// )
```

That integral-number handling matters because a value rendered as `5432.0` would not parse as an `Int`. Non-integral and infinite values keep their `Double` rendering.

### Substitutions

Parsing runs in two passes: the text is read into a tree that may contain unresolved `${...}` references, then those references are resolved. Substitution therefore happens **before** flattening, so a substituted value reaches the source layer already expanded:

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

val withSubstitution =
  """|base = "/srv/app"
     |paths {
     |  data = ${base}/data
     |  logs = ${base}/logs
     |}
     |""".stripMargin
```

The flattened keys hold expanded strings, and nothing downstream needs to know a substitution was involved:

```scala
ConfigSource.fromHocon(withSubstitution, "app.conf")
// res7: Either[ConfigError, ConfigSource] = Right(
//   MapSource(
//     map = Map(
//       "base" -> "/srv/app",
//       "paths.data" -> "/srv/app/data",
//       "paths.logs" -> "/srv/app/logs"
//     ),
//     sourceId = "app.conf"
//   )
// )
```

The resolution pass detects circular references and reports them as a parse failure rather than looping.

### Includes

`include "file"` directives are resolved through a callback, because the core parser does not assume a filesystem. `HoconParser.parse` accepts a `String => Option[String]` that returns the contents of a named resource, which lets includes come from a classpath, an archive, or a test fixture.

On the JVM, `ConfigSource.fromFile` wires that callback to the filesystem for you.

### Loading from a File

`ConfigSource.fromFile` is JVM-only and resolves includes relative to the including file's directory:

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

ConfigSource.fromFile("conf/application.conf")
```

Two parameters guard against a hostile or accidentally-recursive configuration tree:

| Parameter          | Default | Effect                                                                           |
| ------------------ | ------- | -------------------------------------------------------------------------------- |
| `allowedBase`      | `None`  | When set, both the file and every include must resolve inside this directory.      |
| `maxIncludeDepth`  | `10`    | Maximum nesting depth for includes; exceeding it is a parse failure.               |

Pass `allowedBase` whenever the path comes from outside the program. Paths are canonicalized before the check, so `../` sequences cannot escape:

```scala
import java.io.File
import zio.blocks.config._
import zio.blocks.config.hocon._

ConfigSource.fromFile(
  path = "conf/application.conf",
  allowedBase = Some(new File("conf"))
)
```

Failures are reported as `ConfigError.ParseError`: a missing file expects `"existing file"`, a path outside `allowedBase` expects `"path inside <base>"`, and a malformed document carries the `HoconError` as its cause. The `sourceId` of a file-loaded source is `hocon:` followed by the file's name.

### HoconValue

`HoconValue` is the parsed tree, exposed for code that needs the structure rather than a flat source:

```scala
sealed trait HoconValue

object HoconValue {
  final case class Obj(fields: Map[String, HoconValue]) extends HoconValue
  final case class Arr(elements: Seq[HoconValue])       extends HoconValue
  final case class Str(value: String)                   extends HoconValue
  final case class Num(value: Double)                   extends HoconValue
  final case class Bool(value: Boolean)                 extends HoconValue
  case object Null                                      extends HoconValue
}
```

`HoconValue.flatten` performs the flattening the adapter uses, and `HoconValue.deepMerge` merges two trees recursively with the right side winning on conflict — the operation a layered HOCON setup needs before flattening, as opposed to `ConfigSource#orElse`, which layers after.

`HoconError` carries a message with the line and column where parsing failed, and extends `Exception` so it can be thrown or attached as a cause:

```scala
final case class HoconError(message: String, line: Int, column: Int)
```

## Choosing a Format

The adapters differ in capability, not in how their output is consumed:

| Capability                    | YAML | JSON | HOCON |
| ----------------------------- | ---- | ---- | ----- |
| Nested objects and arrays      | Yes  | Yes  | Yes   |
| Comments                       | Yes  | No   | Yes   |
| Substitutions (`${...}`)       | No   | No   | Yes   |
| Includes                       | No   | No   | Yes   |
| Load from a file               | No   | No   | Yes (JVM) |
| Scala.js                       | Yes  | Yes  | Yes (string only) |

For everything else, prefer the format your deployment already uses. Because all three flatten identically, switching later changes one constructor call.

## Integration Points

Each adapter depends on the core `config` module for `ConfigSource` and `ConfigError`. YAML and JSON additionally depend on `zio-blocks-schema-yaml` and `zio-blocks-schema-json` for parsing; HOCON has no dependency beyond the core module.

Adapters produce nothing but a `ConfigSource`, so see [ConfigSource](./config-source.md) for composition and provenance, and [Config Decoder](./config-decoder.md) for how the flattened keys become typed values.
