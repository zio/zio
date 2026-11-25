# How to Get Readable Error Messages

> A parse error is only useful if it says what was expected and where. This guide shows how to name the parts of a grammar so that errors describe the failing part, and how to show the position of an error in a multi-line input.

The goal is a parser for points written as `x,y`, where the separator between the coordinates is named. A point written with `;` instead of `,` then produces a message such as `Not ',' at 1 in comma -> point`.

## The Problem

By default, an error only says what the failing rule was, such as `Not ','`. In a larger grammar, the same rule appears in many places, so the message alone does not tell you which field was wrong. A line number and a column help, but a bare index into the input is hard to map back to a line of a multi-line file.

ZIO Parser records a name stack for each error. Naming a syntax adds its name to that stack, and the error reports the chain of names that led to the failure.

## Prerequisites

```scala
import zio.Chunk
import zio.parser.*
import zio.parser.Parser.ParserError
```

## Naming the Parts

We parse a number, and then give the number a name for each coordinate. We also name the separator and the whole point. The `??` operator attaches a name to a syntax:

```scala
val int: Syntax[String, Char, Char, Int] =
  Syntax.digit.repeat.string.transform(_.toInt, _.toString)

val x: Syntax[String, Char, Char, Int] = int ?? "x coordinate"
val y: Syntax[String, Char, Char, Int] = int ?? "y coordinate"

val point: Syntax[String, Char, Char, (Int, Int)] =
  (x ~ (Syntax.char(',') ?? "comma") ~ y) ?? "point"
```

The code above:

- `int ?? "x coordinate"` is the same number syntax, with a name that appears in errors inside it
- `Syntax.char(',') ?? "comma"` names the separator
- `(...) ?? "point"` names the whole point, so errors inside it also mention `point`

Now we parse a point whose separator is a `;`:

```scala
point.parseString("3;4").left.map(_.pretty).merge
// res0: String | Tuple2[Int, Int] = """3;4
//  ^
// error: Failure at position 1: not ',' in comma -> point
// """
```

The message names the separator and the point, and the caret under the `;` shows the position.

## Reading the Position Directly

When your code needs the position rather than the text, match on the error. `StringParserError` holds the input and a `ParserError`, and the failure cases carry the position:

```scala
point.parseString("3;4") match {
  case Left(StringParserError(_, ParserError.Failure(names, position, message))) =>
    s"$message at $position in ${names.mkString(" -> ")}"
  case Left(other)  => other.toString
  case Right(value) => value.toString
}
// res1: String = "not ',' at 1 in comma -> point"
```

The name stack lists the innermost name first, and then the names around it. In this message, `comma` is inside `point`, so the stack is `comma -> point`.

## Errors in Multi-Line Input

For a file with one point per line, each line is a point followed by a newline. A repetition stops at the first line it cannot read, so `Syntax.end` is what reports the failure. Its position is where reading stopped. The `pretty` message then shows the lines around that position, not the whole file:

```scala
val points: Syntax[String, Char, Char, Chunk[(Int, Int)]] =
  (point <~ Syntax.char('\n')).repeat0 <~ Syntax.end
```

```scala
points.parseString("1,2\n3,4\n5;6\n7,8\n").left.map(_.pretty).merge
// res2: String | Chunk[Tuple2[Int, Int]] = """1,2
// 3,4
// 5;6
// ^
// error: Parser did not consume all input at position 8
// 7,8
// """
```

The output shows up to two lines before and after the position, marks `...` where lines were left out, and puts a caret under the position.

:::tip
Name each syntax that a user writes, such as a field, a value or a section. A single character, such as a separator, is often clear enough without a name, but a name makes the message more precise.
:::
