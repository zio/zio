# Tutorial: Parse and Print a Config Language

> Welcome to ZIO Parser! This tutorial is for newcomers who know basic Scala and have never used a parser library. You don't need any prior experience with parsing to follow along.

We build a parser and printer for a tiny configuration language, one piece at a time. Each piece is a single `Syntax` value, and we combine them into a bigger one.

## Learning Objectives

By the end of this tutorial, you will understand:

- What a `Syntax` is, and why one value can both parse and print
- How to build small syntaxes from primitives and combine them with operators
- How to turn parsed text into your own case classes
- How to read the error that a failed parse returns

We learn these concepts through:

1. Parsing a Single Key
2. Parsing Values of Three Types
3. Parsing a Setting
4. Parsing a Whole File
5. Reading Errors

We recommend reading from top to bottom, because each section builds on the previous one.

## 1. Parsing a Single Key

A key in our language is one or more letters, such as `timeout`. In ZIO Parser, a `Syntax` describes one piece of text and the value it produces. The type has four parameters: the error type, the input element type, the output element type, and the value type.

For text, the input and output are both `Char`, so a key syntax has the type `Syntax[String, Char, Char, String]`. We start by importing the library:

```scala
import zio.Chunk
import zio.parser.*
```

To describe a key, we take a letter and repeat it. Then `string` turns the repeated characters into a `String`:

```scala
val key: Syntax[String, Char, Char, String] =
  Syntax.letter.repeat.string
```

The code above:

- `Syntax.letter` matches one letter and produces that `Char`
- `repeat` matches one or more of them and produces a `Chunk[Char]`
- `string` converts that chunk into a `String`

Now we run the syntax in both directions. `parseString` reads text into a value, and `printString` writes a value back into text:

```scala
key.parseString("timeout")
// res0: Either[StringParserError[String], String] = Right("timeout")
key.printString("timeout")
// res1: Either[String, String] = Right("timeout")
```

Both calls return an `Either`. `Right` holds the result, and `Left` holds the error. Notice that the same `key` value did both jobs, so we describe the grammar only once.

## 2. Parsing Values of Three Types

A setting value can be an integer, a boolean, or a quoted string. We model these with a small `sealed trait`, so that each kind of value has its own case class:

```scala
sealed trait Value
object Value {
  final case class IntValue(value: Int) extends Value
  final case class BoolValue(value: Boolean) extends Value
  final case class Text(value: String) extends Value
}
```

Each syntax below produces one of these subtypes. An integer is one or more digits. We parse the digits as a `String` and convert them to an `Int`. The conversion can fail, so we use `transformEither`, which returns an error instead of throwing:

```scala
val intValue: Syntax[String, Char, Char, Value.IntValue] =
  Syntax.digit.repeat.string.transformEither[String, Value.IntValue](
    digits => digits.toIntOption.map(Value.IntValue(_)).toRight("number out of range"),
    intValue => Right(intValue.value.toString)
  )
```

The code above:

- The first function turns the digits into a `Value.IntValue`, or into the error `"number out of range"`
- The second function is used when printing, and turns the `Int` back into digits

A boolean is either the text `true` or the text `false`. The `|` operator tries the left alternative first, and falls back to the right one if it fails:

```scala
val boolValue: Syntax[String, Char, Char, Value.BoolValue] =
  (Syntax.string("true", true) | Syntax.string("false", false))
    .transform[Value.BoolValue](Value.BoolValue(_), _.value)
```

A quoted string starts and ends with a double quote, and everything in between is kept. We drop the quotes with `~>` and `<~`, which keep only the value of the side that has one:

```scala
val text: Syntax[String, Char, Char, Value.Text] =
  (Syntax.char('"') ~> Syntax.notChar('"').repeat0.string <~ Syntax.char('"'))
    .transform[Value.Text](Value.Text(_), _.value)
```

The `Syntax.char('"')` parts produce no value, so the quotes disappear from the result. When printing, they come back, because the printer writes them again.

To combine the three alternatives, we widen each one to the common type `Value`. Then `|` can join them:

```scala
val value: Syntax[String, Char, Char, Value] =
  intValue.widen[Value] | boolValue.widen[Value] | text.widen[Value]
```

Now we try all three kinds of value in both directions:

```scala
value.parseString("8080")
// res2: Either[StringParserError[String], Value] = Right(IntValue(8080))
value.parseString("true")
// res3: Either[StringParserError[String], Value] = Right(BoolValue(true))
value.parseString("\"zio-parser\"")
// res4: Either[StringParserError[String], Value] = Right(Text("zio-parser"))
value.printString(Value.Text("zio-parser"))
// res5: Either[String, String] = Right("\"zio-parser\"")
```

:::tip
`widen` checks the type of a value at runtime when printing. It picks the first alternative whose case class matches the value, so the order of the alternatives matters only when two of them could match the same value.
:::

## 3. Parsing a Setting

A setting puts a key, an equals sign and a value together, such as `port = 8080`. Spaces around the equals sign are allowed when parsing, but we want the printer to write exactly one space on each side. The `asPrinted` method does this. It keeps the parsed result as `Unit` and tells the printer which text to write:

```scala
val spaces: Syntax[String, Char, Char, Unit] =
  Syntax.whitespace.repeat0.asPrinted((), Chunk(' '))
```

Next, we combine the key, the equals sign and the value. A `final case class` holds the result of one setting:

```scala
final case class Setting(key: String, value: Value)

val setting: Syntax[String, Char, Char, Setting] =
  (key ~ (Syntax.char('=').surroundedBy(spaces) ~> value)).of[Setting]
```

The code above:

- `key ~ (...)` runs the key first, then the right side, and pairs their results
- `Syntax.char('=').surroundedBy(spaces) ~> value` matches the equals sign with spaces around it, and then the value
- `of[Setting]` turns the pair `(String, Value)` into a `Setting`, and back again when printing

Parsing and printing a setting show the whitespace being normalized:

```scala
setting.parseString("timeout   =   30")
// res6: Either[StringParserError[String], Setting] = Right(
//   Setting(key = "timeout", value = IntValue(30))
// )
setting.printString(Setting("timeout", Value.IntValue(30)))
// res7: Either[String, String] = Right("timeout = 30")
```

The input had three spaces on each side of the equals sign, and the printed output has one.

## 4. Parsing a Whole File

A file is a list of settings, one per line. We need a syntax for the newline character, and a rule that the file ends after the last setting. `repeatWithSep` repeats a syntax with a separator between the repetitions, and `Syntax.end` makes sure nothing is left over:

```scala
val newline: Syntax[String, Char, Char, Unit] = Syntax.char('\n')

val config: Syntax[String, Char, Char, Chunk[Setting]] =
  setting.repeatWithSep(newline) <~ Syntax.end
```

Now we parse a small file with three settings:

```scala
val source = "name = \"zio-parser\"\nport = 8080\ndebug = true"
// source: String = """name = "zio-parser"
// port = 8080
// debug = true"""

config.parseString(source)
// res8: Either[StringParserError[String], Chunk[Setting]] = Right(
//   IndexedSeq(
//     Setting(key = "name", value = Text("zio-parser")),
//     Setting(key = "port", value = IntValue(8080)),
//     Setting(key = "debug", value = BoolValue(true))
//   )
// )
```

Printing works the same way. We give the printer a `Chunk` of settings, and it writes them back one per line:

```scala
config.printString(
  Chunk(
    Setting("name", Value.Text("zio-parser")),
    Setting("port", Value.IntValue(8080))
  )
)
// res9: Either[String, String] = Right(
//   """name = "zio-parser"
// port = 8080"""
// )
```

Because `config` describes both directions, the printed text parses back to the same settings. You can check this yourself by parsing the output of `printString`.

## 5. Reading Errors

A parse that fails returns a `Left` with a `StringParserError`. The `pretty` method turns that error into a message that points at the place in the input where things went wrong.

Suppose a line has an extra word after the value, such as `port = 8080 extra`. The setting matches `port = 8080`, and then `Syntax.end` finds the text ` extra` that it did not expect:

```scala
config.parseString("port = 8080 extra").left.map(_.pretty).merge
// res10: String | Chunk[Setting] = """port = 8080 extra
//            ^
// error: Parser did not consume all input at position 11
// """
```

The message shows the input line and a caret under the first character the parser could not use. It is the position where the parser stopped, so you can read the message and go straight to the problem.

When every alternative of a `|` fails, the error lists the failure of each branch instead. For example, a value such as `port = 99999999999` fails the integer rule with `"number out of range"`, and the text and boolean rules fail too, so the message names all three.

:::note
A syntax returns as soon as it has matched its value. It does not look at the rest of the input, unless you finish it with `Syntax.end`, as `config` does.
:::

## What You've Learned

In this tutorial, you learned:

- What a `Syntax` is, and how its four type parameters describe the input, the output and the value
- How to build a syntax from primitives such as `Syntax.letter` and `Syntax.digit`, and combine them with `~`, `~>`, `<~` and `|`
- How to convert parsed text into your own types with `transform`, `transformEither` and `of`
- How `widen` lets alternatives of different subtypes share one syntax
- How to read an error with `pretty`

You now have a solid foundation for writing parsers and printers with ZIO Parser. The next step is to apply these ideas to common tasks.

## Where to Go Next

- **Ready to use this in practice?** The how-to guide [Whitespace and Comments](how-to-whitespace-and-comments.md) shows how to skip the text you do not need, and [Recursive Syntaxes](how-to-recursive-syntaxes.md) shows how to parse nested data.
- **Want to dive deeper into the API?** Read the [Syntax reference](syntax.md) for every combinator, and the [Regex reference](regex.md) for fast token matching.
- **Interested in a larger example?** [Example: a JSON Syntax](example-json.md) builds a complete parser and printer for JSON.
