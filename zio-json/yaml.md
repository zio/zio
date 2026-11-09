# YAML

> The `zio-json-yaml` module converts between YAML and any type that has a `JsonEncoder` or `JsonDecoder`.
> It goes through the [JSON AST](./json-ast.md): values are encoded to a `Json` value and then rendered as YAML, and YAML is parsed into a `Json` value before decoding.
> Because of this, the same codec that you use for JSON also works for YAML, and there is no separate YAML codec to write.

The module is available for the JVM only, since it depends on [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml).

## Installation

Add the module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-json-yaml" % "1.1.2"
```

## Encoding to YAML

Importing `zio.json.yaml._` adds the `toYaml` extension method to any value that has a `JsonEncoder` in scope.
We define a case class with a derived codec first:

```scala
import zio.json._
import zio.json.ast.Json
import zio.json.yaml._

case class Person(name: String, age: Int, email: Option[String])

object Person {
  implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}
```

Calling `toYaml` returns an `Either[String, String]`. The `email` field is `None`, so it is absent from the output:

```scala
Person("Alice", 30, None).toYaml().getOrElse("")
// res0: String = """name: Alice
// age: 30
// """
```

`toYaml` also accepts a `Json` value directly, which is useful when you already have an AST:

```scala
Json.Obj("name" -> Json.Str("Alice"), "tags" -> Json.Arr(Json.Str("admin"), Json.Str("ops"))).toYaml().getOrElse("")
// res1: String = """name: Alice
// tags:
//   - admin
//   - ops
// """
```

## Decoding from YAML

The `String#fromYaml` extension method parses YAML and decodes the result with a `JsonDecoder`:

```scala
val yaml: String =
  """name: Bob
    |age: 41
    |email: bob@example.com""".stripMargin
// yaml: String = """name: Bob
// age: 41
// email: bob@example.com"""

yaml.fromYaml[Person]
// res2: Either[String, Person] = Right(
//   value = Person(
//     name = "Bob",
//     age = 41,
//     email = Some(value = "bob@example.com")
//   )
// )
```

If the input is not valid YAML, or the resulting structure does not match the decoder, `String#fromYaml` returns a `Left` with an error message:

```scala
"name: [unclosed".fromYaml[Person]
// res3: Either[String, Person] = Left(
//   value = """while parsing a flow sequence
//  in 'reader', line 1, column 7:
//     name: [unclosed
//           ^
// expected ',' or ']', but got <stream end>
//  in 'reader', line 1, column 16:
//     name: [unclosed
//                    ^
// """
// )
```

## Options

`YamlOptions` controls how YAML is rendered. `YamlOptions.default` is used when no options are given, and you can change individual settings with `copy`:

```scala
val nested: Json = Json.Obj(
  "server" -> Json.Obj("host" -> Json.Str("localhost"), "port" -> Json.Num(8080)),
  "debug"  -> Json.Null
)
// nested: Json = Obj(
//   fields = IndexedSeq(
//     (
//       "server",
//       Obj(
//         fields = IndexedSeq(
//           ("host", Str(value = "localhost")),
//           ("port", Num(value = 8080))
//         )
//       )
//     ),
//     ("debug", null)
//   )
// )

nested.toYaml(YamlOptions.default.copy(dropNulls = false, indentation = 4)).getOrElse("")
// res4: String = """server:
//     host: localhost
//     port: 8080
// debug: null
// """
```

The most commonly used settings are:

- `dropNulls` (default `true`): removes object fields whose value is `Json.Null`. Option fields that are `None` are controlled by the codec, not by this setting.
- `indentation` (default `2`) and `sequenceIndentation` (default `2`): the number of spaces used for nested mappings and sequences.
- `maxScalarWidth` (default `Some(80)`): long string scalars are split across lines. Set it to `None` to keep each scalar on one line.
- `lineBreak`: the line separator. The default follows the platform.
- `flowStyle`, `scalarStyle` and `keyStyle`: functions that choose the YAML presentation style for each value or key. They let you, for example, force flow style (`{a: 1}`) for small objects.
- `nonPrintableStyle`: how strings that contain non-printable characters are written. The default escapes them, and `NonPrintableStyle.BINARY` writes them as base64 `!!binary` values.

If you need an option that is not exposed by `YamlOptions`, use `newDumperOptions` to supply your own SnakeYAML `DumperOptions`.

Strings that contain line breaks are written as literal block scalars (`|`) automatically, even when the `scalarStyle` is `PLAIN`.

## Working with the YAML Node Tree

The `toYamlAST` method returns the SnakeYAML `Node` tree instead of a string. Use it when you need to change the document before it is emitted:

```scala
val node = Person("Carol", 25, None).toYamlAST()
// node: Either[String, org.yaml.snakeyaml.nodes.Node] = Right(
//   value = <org.yaml.snakeyaml.nodes.MappingNode (tag=tag:yaml.org,2002:map, values={ key=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=name)>; value=<NodeTuple keyNode=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=name)>; valueNode=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=Carol)>> }{ key=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=age)>; value=<NodeTuple keyNode=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=age)>; valueNode=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:int, value=25)>> })>
// )
node.isRight
// res5: Boolean = true
```

To go from a YAML string to a `Json` value without decoding into a domain type, use `String#fromYaml` with `Json` as the type:

```scala
"""items:
  |  - 1
  |  - true
  |  - null""".stripMargin.fromYaml[Json]
// res6: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(
//       (
//         "items",
//         Arr(elements = IndexedSeq(Num(value = 1), Bool(value = true), null))
//       )
//     )
//   )
// )
```
