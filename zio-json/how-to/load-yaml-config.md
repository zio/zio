# Load a YAML Configuration File

> Read a YAML configuration file into case classes with zio-json-yaml, handle errors and defaults, write it back, and know the limits.

Goal: read your application's settings from a YAML file into case classes, with default values for the keys the file leaves out and an error that names the bad key when the file is wrong.

The same `JsonDecoder` that reads JSON reads the YAML, so there are no casts and no hand-written checks for missing keys. Steps 1 to 5 build a `loadConfig` function; steps 6 and 7 are optional additions.

## What You Need

Add the module to `build.sbt`. It depends on `zio-json` and on [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml), and it is available on the JVM only, not on Scala.js or Scala Native:

```scala
libraryDependencies += "dev.zio" %% "zio-json-yaml" % "1.1.3"
```

All code on this page uses these imports. `zio.json.yaml._` adds the `String#fromYaml` extension method, and a `toYaml` method to every value that has a `JsonEncoder`:

```scala
import zio.json._
import zio.json.yaml._
```

The guide assumes you know how to derive a codec for a case class (see the [Getting Started](../getting-started.md) tutorial). The [YAML](../reference/yaml.md) reference page documents every option of the module.

## 1. Define the Configuration Type

Model the configuration as case classes with derived codecs. A default value in a case class becomes the value for a key that the file leaves out, and an `Option` field is a key that may be absent. This configuration has a server section, a database section and a list of enabled features:

```scala
final case class ServerConfig(host: String, port: Int)

final case class DatabaseConfig(url: String, user: Option[String], poolSize: Int = 10)

final case class AppConfig(
  server: ServerConfig,
  database: DatabaseConfig,
  features: List[String] = Nil,
  logLevel: String = "info"
)

implicit val serverConfigCodec: JsonCodec[ServerConfig]     = DeriveJsonCodec.gen[ServerConfig]
implicit val databaseConfigCodec: JsonCodec[DatabaseConfig] = DeriveJsonCodec.gen[DatabaseConfig]
implicit val appConfigCodec: JsonCodec[AppConfig]           = DeriveJsonCodec.gen[AppConfig]
```

A `JsonCodec` holds both a `JsonDecoder`, which `String#fromYaml` needs, and a `JsonEncoder`, which `toYaml` (step 7) needs. If you only read the configuration, `DeriveJsonDecoder.gen` is enough. In a project, put each codec in the companion object of its case class, so the compiler finds it without an import.

## 2. Decode YAML Text with `String#fromYaml`

`String#fromYaml[A]` parses the text as YAML, turns it into a [`Json`](../reference/json-ast.md) value, and decodes that with the `JsonDecoder[A]`. It returns `Either[String, A]`. A typical configuration file sets the server address, the database connection and the enabled features:

```scala
val yaml: String =
  """server:
    |  host: 0.0.0.0
    |  port: 8080
    |database:
    |  url: jdbc:postgresql://localhost:5432/shop
    |  user: shop
    |features:
    |  - search
    |  - export
    |""".stripMargin
```

To decode it into `AppConfig`, the type from step 1:

```scala
yaml.fromYaml[AppConfig]
// res1: Either[String, AppConfig] = Right(
//   value = AppConfig(
//     server = ServerConfig(host = "0.0.0.0", port = 8080),
//     database = DatabaseConfig(
//       url = "jdbc:postgresql://localhost:5432/shop",
//       user = Some(value = "shop"),
//       poolSize = 10
//     ),
//     features = List("search", "export"),
//     logLevel = "info"
//   )
// )
```

The file leaves out `database.poolSize` and `logLevel`, so the decoder uses their default values `10` and `"info"`.

## 3. Leave Out Keys That Have Defaults

A key that the file leaves out is decoded as its default value when the field has one, and as `None` when the field is an `Option`. A minimal file for `AppConfig` (step 1) only needs the keys without defaults. YAML flow style (`{ ... }`) works too:

```scala
"""server: { host: localhost, port: 80 }
  |database: { url: "jdbc:h2:mem:test" }
  |""".stripMargin.fromYaml[AppConfig]
// res2: Either[String, AppConfig] = Right(
//   value = AppConfig(
//     server = ServerConfig(host = "localhost", port = 80),
//     database = DatabaseConfig(
//       url = "jdbc:h2:mem:test",
//       user = None,
//       poolSize = 10
//     ),
//     features = List(),
//     logLevel = "info"
//   )
// )
```

Keys that the case classes do not declare are ignored. A misspelled optional key, such as `poolsize` instead of `poolSize`, is therefore not an error: the field keeps its default value. To reject unknown keys, annotate the case classes with `@jsonNoExtraFields`, as described in [Optional, Default and Null Fields](optional-and-default-fields.md).

## 4. Read the Error Messages

The `Left` of `String#fromYaml` says what to fix, so pass it on to whoever starts the service. A required key that is missing from a file for `AppConfig` (step 1) is reported with its path:

```scala
"""server:
  |  host: localhost
  |database:
  |  url: jdbc:h2:mem:test
  |""".stripMargin.fromYaml[AppConfig]
// res3: Either[String, AppConfig] = Left(value = ".server.port(missing)")
```

A value of the wrong type is reported the same way:

```scala
"""server:
  |  host: localhost
  |  port: eighty
  |database:
  |  url: jdbc:h2:mem:test
  |""".stripMargin.fromYaml[AppConfig]
// res4: Either[String, AppConfig] = Left(
//   value = ".server.port(expected a Int)"
// )
```

Invalid YAML returns SnakeYAML's message, which spans several lines and points at the line and column of the problem:

```scala
"server: [localhost".fromYaml[AppConfig]
// res5: Either[String, AppConfig] = Left(
//   value = """while parsing a flow sequence
//  in 'reader', line 1, column 9:
//     server: [localhost
//             ^
// expected ',' or ']', but got <stream end>
//  in 'reader', line 1, column 19:
//     server: [localhost
//                       ^
// """
// )
```

The paths in these messages use the same format as JSON decoding errors, described in [Errors](../reference/errors.md).

:::tip
SnakeYAML reads unquoted `yes`, `no`, `on`, `off`, `true` and `false` as booleans, so `host: on` fails with `.server.host(expected string)`. Quote such values (`host: "on"`) when the field is a `String`.
:::

## 5. Load the Configuration from a File

`String#fromYaml` takes the text, not a path. Read the file first and turn a read failure into a `Left`, so both kinds of error reach the caller in the same way. `loadConfig` decodes into `AppConfig`, the type from step 1:

```scala
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import scala.util.Try

def loadConfig(path: Path): Either[String, AppConfig] =
  Try(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).toEither.left
    .map(error => s"Cannot read ${path.getFileName}: ${error.getClass.getSimpleName}")
    .flatMap(_.fromYaml[AppConfig])
```

To try `loadConfig`, write `yaml` (the sample file from step 2) into a temporary file, load it, load a file that does not exist, and delete the file and its directory afterwards:

```scala
{
  val dir  = Files.createTempDirectory("config-demo")
  val file = dir.resolve("app.yaml")

  try {
    Files.write(file, yaml.getBytes(StandardCharsets.UTF_8))
    println(loadConfig(file))
    println(loadConfig(dir.resolve("missing.yaml")))
  } finally {
    Files.deleteIfExists(file)
    Files.deleteIfExists(dir)
  }
}
// Right(AppConfig(ServerConfig(0.0.0.0,8080),DatabaseConfig(jdbc:postgresql://localhost:5432/shop,Some(shop),10),List(search, export),info))
// Left(Cannot read missing.yaml: NoSuchFileException)
```

At startup, turn the `Left` into a failure, for example with `loadConfig(path).fold(error => sys.error(s"Invalid configuration: $error"), identity)`, or with `ZIO.fromEither` in a ZIO application.

## 6. Reject an Empty File with a Clear Message

An empty file, or a file that holds only comments, does not produce a helpful message. The parser finds no document, and the conversion to `Json` fails with a `NullPointerException`, whose message `String#fromYaml` returns:

```scala
println("".fromYaml[AppConfig])
// Left(Cannot invoke "org.yaml.snakeyaml.nodes.Node.getClass()" because "node" is null)

println("# all settings commented out\n".fromYaml[AppConfig])
// Left(Cannot invoke "org.yaml.snakeyaml.nodes.Node.getClass()" because "node" is null)
```

The text comes from the JVM, so it can differ between JDK versions. To give a better message, check for an empty file before decoding:

```scala
def parseConfig(raw: String): Either[String, AppConfig] =
  if (raw.linesIterator.forall(line => line.trim.isEmpty || line.trim.startsWith("#")))
    Left("The configuration file is empty")
  else raw.fromYaml[AppConfig]
```

An empty file now gets a message that says what is wrong:

```scala
parseConfig("")
// res9: Either[String, AppConfig] = Left(
//   value = "The configuration file is empty"
// )
```

To use the check when loading from a file, call `parseConfig` instead of `String#fromYaml` in the last line of `loadConfig` from step 5.

## 7. Write the Configuration Back as YAML

`toYaml` encodes a value with its `JsonEncoder` and renders the result as YAML. It returns `Either[String, String]`. Use it to print the effective configuration at startup or to generate a template file. Start from a loaded `AppConfig`, here decoded from `yaml`, the sample file from step 2:

```scala
val config: AppConfig = yaml.fromYaml[AppConfig].fold(sys.error, identity)
```

Default values are written out, and fields that are `None` are left out:

```scala
println(config.toYaml().getOrElse(""))
// server:
//   host: 0.0.0.0
//   port: 8080
// database:
//   url: jdbc:postgresql://localhost:5432/shop
//   user: shop
//   poolSize: 10
// features:
//   - search
//   - export
// logLevel: info
//
```

`YamlOptions` controls the layout. To indent with four spaces and put the `-` of list items in the same column as their key:

```scala
println(config.toYaml(YamlOptions.default.copy(indentation = 4, sequenceIndentation = 0)).getOrElse(""))
// server:
//     host: 0.0.0.0
//     port: 8080
// database:
//     url: jdbc:postgresql://localhost:5432/shop
//     user: shop
//     poolSize: 10
// features:
// - search
// - export
// logLevel: info
//
```

The other settings, such as `maxScalarWidth`, `dropNulls` and the style functions, are described in [Options](../reference/yaml.md#options).

## Limitations

`String#fromYaml` parses with SnakeYAML's default settings, and there is no parameter to change them:

- A file must hold one YAML document. A second document after `---` fails with `expected a single document in the stream`.
- Nesting deeper than 50 levels fails with `Nesting Depth exceeded max 50`.
- Input larger than 3,145,728 code points (3 MB) fails with `The incoming YAML document exceeds the limit: 3145728 code points.`
- More than 50 aliases (`*name`) to mappings or sequences fail.

The exact limits and their messages are listed in [Decoding Limits](../reference/yaml.md#decoding-limits). For example, a stream with two documents is rejected:

```scala
"""server: { host: a, port: 1 }
  |database: { url: x }
  |---
  |server: { host: b, port: 2 }
  |database: { url: y }
  |""".stripMargin.fromYaml[AppConfig].isLeft
// res12: Boolean = true
```

When writing YAML, `toYaml` does not write integers outside the `Long` range correctly. Take a setting that holds a large integer:

```scala
final case class Limits(maxUploadBytes: BigInt)

implicit val limitsCodec: JsonCodec[Limits] = DeriveJsonCodec.gen[Limits]
```

`toYaml` keeps only the low 64 bits of the number, and reading the YAML back gives the wrong value without an error:

```scala
val limits = Limits(BigInt("123456789012345678901234567890"))
// limits: Limits = Limits(maxUploadBytes = 123456789012345678901234567890)

limits.toYaml()
// res13: Either[String, String] = Right(
//   value = """maxUploadBytes: -4362896299872285998
// """
// )

limits.toYaml().flatMap(_.fromYaml[Limits])
// res14: Either[String, Limits] = Right(
//   value = Limits(maxUploadBytes = -4362896299872285998)
// )
```

Store such numbers as strings in the case class if they must go through YAML. Numbers with a fractional part are not affected; see [Number Rendering](../reference/yaml.md#number-rendering).

## See Also

- [YAML](../reference/yaml.md) for every `YamlOptions` setting, how YAML scalars map to `Json` values, and the SnakeYAML node tree returned by `toYamlAST`.
- [Read and Edit JSON of Unknown Shape](edit-json-with-cursors.md) to change a configuration document without a case class, for example to apply overrides before decoding: parse it with `fromYaml[Json]` and edit it with cursors.
- [Optional, Default and Null Fields](optional-and-default-fields.md) for fields that may be missing, `null` or empty.
