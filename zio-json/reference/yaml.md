# YAML

> Reference for zio-json-yaml: encoding to and decoding from YAML with JSON codecs, YamlOptions, decoding limits and number rendering.

The `zio-json-yaml` module converts between YAML and any type that has a `JsonEncoder` or `JsonDecoder`. It goes through the [JSON AST](./json-ast.md): values are encoded to a `Json` value and then rendered as YAML, and YAML is parsed into a `Json` value before decoding. Because of this, the same codec that you use for JSON also works for YAML, and there is no separate YAML codec to write.

The module is available for the JVM only, since it depends on [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml).

## Installation

Add the module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-json-yaml" % "1.1.3"
```

## Encoding to YAML

Importing `zio.json.yaml._` adds the `toYaml` extension method to any value that has a `JsonEncoder` in scope. We define a case class with a derived codec first:

```scala
import zio.json._
import zio.json.ast.Json
import zio.json.yaml._

case class Person(name: String, age: Int, email: Option[String])

object Person {
  implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}
```

On a value with an encoder, calling `toYaml` returns an `Either[String, String]`. The `email` field is `None`, so it is absent from the output:

```scala
println(Person("Alice", 30, None).toYaml().getOrElse(""))
// name: Alice
// age: 30
//
```

`toYaml` also works on a `Json` value directly, which is useful when you already have an AST:

```scala
println(Json.Obj("name" -> Json.Str("Alice"), "tags" -> Json.Arr(Json.Str("admin"), Json.Str("ops"))).toYaml().getOrElse(""))
// name: Alice
// tags:
//   - admin
//   - ops
//
```

On a `Json` value, `toYaml` returns `Either[YAMLException, String]`, where `YAMLException` is `org.yaml.snakeyaml.error.YAMLException`. The version for encodable values first converts the value to `Json`, and turns a `YAMLException` into its message.

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
// res3: Either[String, Person] = Right(
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
// res4: Either[String, Person] = Left(
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

YAML scalars that SnakeYAML resolves to a boolean, integer or floating-point number become `Json.Bool` and `Json.Num`, `null` and `~` become `Json.Null`, and every other scalar, such as a date, becomes a `Json.Str` with its text. Mapping keys must be scalars.

## Decoding Limits

`String#fromYaml` parses with `new Yaml()`, so it uses SnakeYAML's default `LoaderOptions`. There is no parameter to pass different options. The defaults set these limits:

| Limit              | Default                      | What happens when it is exceeded                                            |
|--------------------|------------------------------|-----------------------------------------------------------------------------|
| Documents          | One                          | `Left` with `expected a single document in the stream`                      |
| Nesting depth      | 50 levels                    | `Left(Nesting Depth exceeded max 50)`                                       |
| Collection aliases | 50 aliases                   | `Left(Number of aliases for non-scalar nodes exceeds the specified max=50)` |
| Input size         | 3 MB (3,145,728 code points) | `Left(The incoming YAML document exceeds the limit: 3145728 code points.)`  |

The alias limit counts only aliases (`*name`) that point at a mapping or a sequence; aliases of scalars are not limited. Duplicate keys are accepted, and the last value wins:

```scala
"a: 1\na: 2".fromYaml[Json]
// res5: Either[String, Json] = Right(
//   value = Obj(fields = IndexedSeq(("a", Num(value = 2))))
// )
```

A stream with more than one document (separated by `---`) fails, so split such input yourself before decoding each part:

```scala
"a: 1\n---\na: 2\n".fromYaml[Json].isLeft
// res6: Boolean = true
```

An empty input, or one that holds only comments, has no document at all. `fromYaml` then returns a `Left` whose message comes from a `NullPointerException` (`Cannot invoke "org.yaml.snakeyaml.nodes.Node.getClass()" because "node" is null` on current JDKs) rather than a clear "empty document" error. Check for blank input yourself if you need a better message, as [Load a YAML Configuration File](../how-to/load-yaml-config.md) does.

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

println(nested.toYaml(YamlOptions.default.copy(dropNulls = false, indentation = 4)).getOrElse(""))
// server:
//     host: localhost
//     port: 8080
// debug: null
//
```

The settings are:

- `dropNulls` (default `true`): removes object fields whose value is `Json.Null`. A `null` element of an array is still written. Option fields that are `None` are controlled by the codec, not by this setting.
- `indentation` (default `2`): spaces per nesting level.
- `sequenceIndentation` (default `2`): spaces placed before the `-` indicator of sequence entries (SnakeYAML's indicator indent).
- `indentWithIndicator` (default `true`): adds the `-` indicator indent to the general indentation, so sequence item content is indented past the `-` instead of sharing its column. If you set it to `false`, also set `sequenceIndentation = 0`: with the default indicator indent, SnakeYAML writes sequences of mappings that `String#fromYaml` cannot read back.
- `maxScalarWidth` (default `Some(80)`): long string scalars are split across lines. Set it to `None` to keep each scalar on one line.
- `lineBreak`: the line separator. The default follows the platform.
- `flowStyle`, `scalarStyle` and `keyStyle`: functions that choose the SnakeYAML presentation style for each value or key. `flowStyle` receives each object and array, `scalarStyle` each scalar value, and `keyStyle` each key.
- `nonPrintableStyle`: how strings that contain non-printable characters are written. The default escapes them, and `NonPrintableStyle.BINARY` writes them as base64 `!!binary` values.
- `newDumperOptions`: a function that creates the SnakeYAML `DumperOptions` used for rendering.

The `sequenceIndentation` setting moves the `-` indicator. With `0`, the indicator sits in the same column as the parent key:

```scala
val list: Json = Json.Obj("items" -> Json.Arr(Json.Obj("a" -> Json.Num(1), "b" -> Json.Num(2)), Json.Str("x")))
// list: Json = Obj(
//   fields = IndexedSeq(
//     (
//       "items",
//       Arr(
//         elements = IndexedSeq(
//           Obj(fields = IndexedSeq(("a", Num(value = 1)), ("b", Num(value = 2)))),
//           Str(value = "x")
//         )
//       )
//     )
//   )
// )

println(list.toYaml(YamlOptions.default.copy(sequenceIndentation = 0)).getOrElse(""))
// items:
// - a: 1
//   b: 2
// - x
//
```

The style functions take SnakeYAML types from `org.yaml.snakeyaml.DumperOptions`. To write every object and array in flow style, on one line:

```scala
import org.yaml.snakeyaml.DumperOptions.{ FlowStyle, ScalarStyle }

println(list.toYaml(YamlOptions.default.copy(flowStyle = _ => FlowStyle.FLOW)).getOrElse(""))
// {items: [{a: 1, b: 2}, x]}
//
```

When `scalarStyle` returns `PLAIN` for a string that contains a line break, the string is written as a literal block scalar (`|`) instead. Other styles are kept as chosen:

```scala
val text: Json = Json.Obj("text" -> Json.Str("line1\nline2"))
// text: Json = Obj(
//   fields = IndexedSeq(
//     (
//       "text",
//       Str(
//         value = """line1
// line2"""
//       )
//     )
//   )
// )

println(text.toYaml().getOrElse(""))
// text: |-
//   line1
//   line2
// 

println(text.toYaml(YamlOptions.default.copy(scalarStyle = _ => ScalarStyle.DOUBLE_QUOTED)).getOrElse(""))
// text: "line1\nline2"
//
```

If you need an option that is not exposed by `YamlOptions`, use `newDumperOptions` to supply your own SnakeYAML `DumperOptions`. The settings that `YamlOptions` exposes (indentation, sequence indentation, width, line break, indentWithIndicator) are always overwritten from `YamlOptions`, so set those through `YamlOptions` instead. Other settings take effect, for example an explicit `---` document start:

```scala
import org.yaml.snakeyaml.DumperOptions

val withStart = YamlOptions.default.copy(newDumperOptions = () => {
  val options = new DumperOptions()
  options.setExplicitStart(true)
  options
})
// withStart: YamlOptions = YamlOptions(
//   newDumperOptions = <function0>,
//   dropNulls = true,
//   indentation = 2,
//   sequenceIndentation = 2,
//   maxScalarWidth = Some(value = 80),
//   lineBreak = Line break: UNIX,
//   flowStyle = zio.json.yaml.YamlOptions$$$Lambda$19931/0x00007373271488d8@7770d94,
//   scalarStyle = zio.json.yaml.YamlOptions$$$Lambda$19932/0x0000737327148ca0@bd029cc,
//   keyStyle = zio.json.yaml.YamlOptions$$$Lambda$19933/0x0000737327149068@547e18d6,
//   nonPrintableStyle = ESCAPE,
//   indentWithIndicator = true
// )

println(nested.toYaml(withStart).getOrElse(""))
// ---
// server:
//   host: localhost
//   port: 8080
//
```

## Number Rendering

`Json.Num` values are written as YAML integers when they have no fractional part after `stripTrailingZeros`, and as floats otherwise. A number that was a decimal in JSON can therefore come back as an integer:

```scala
println(Json.Obj("a" -> Json.Num(1.0), "b" -> Json.Num(BigDecimal("2.50")), "c" -> Json.Num(BigDecimal("1E+3"))).toYaml().getOrElse(""))
// a: 1
// b: 2.5
// c: 1000
//
```

:::warning
An integral number outside the `Long` range is not written correctly: only its low 64 bits are kept, so `Json.Num(BigInt("123456789012345678901234567890"))` is written as `-4362896299872285998`. Encode such numbers as strings if they must go through YAML.
:::

## Working with the YAML Node Tree

The `toYamlAST` method returns the SnakeYAML `Node` tree instead of a string. Use it when you need to change the document before it is emitted. On a value with an encoder it returns `Either[String, Node]`, because the encoding to `Json` can fail:

```scala
val node = Person("Carol", 25, None).toYamlAST()
// node: Either[String, org.yaml.snakeyaml.nodes.Node] = Right(
//   value = <org.yaml.snakeyaml.nodes.MappingNode (tag=tag:yaml.org,2002:map, values={ key=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=name)>; value=<NodeTuple keyNode=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=name)>; valueNode=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=Carol)>> }{ key=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=age)>; value=<NodeTuple keyNode=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:str, value=age)>; valueNode=<org.yaml.snakeyaml.nodes.ScalarNode (tag=tag:yaml.org,2002:int, value=25)>> })>
// )
node.map(_.getNodeId)
// res14: Either[String, org.yaml.snakeyaml.nodes.NodeId] = Right(
//   value = mapping
// )
```

On a `Json` value, `toYamlAST` returns the `Node` directly:

```scala
nested.toYamlAST().getNodeId
// res15: org.yaml.snakeyaml.nodes.NodeId = mapping
```

To go from a YAML string to a `Json` value without decoding into a domain type, use `String#fromYaml` with `Json` as the type:

```scala
"""items:
  |  - 1
  |  - true
  |  - null""".stripMargin.fromYaml[Json]
// res16: Either[String, Json] = Right(
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
