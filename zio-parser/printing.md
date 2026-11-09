# Printing and Invertible Syntaxes

> What sets ZIO Parser apart from most parser libraries is that the description of a grammar is *invertible*. The same `Syntax` value that parses a string can also print a value back to a string, so the two directions cannot drift apart.

```scala
import zio.Chunk
import zio.parser.*
```

## Printing a Value

Every `Syntax` has three printing methods:

| Method                     | Result                    | Description                                      |
|----------------------------|---------------------------|--------------------------------------------------|
| `printString(value)`       | `Either[Err, String]`     | Prints to a `String` (requires `Out` to be `Char`) |
| `print(value)`             | `Either[Err, Chunk[Out]]` | Prints to a `Chunk` of output elements           |
| `print(value, target)`     | `Either[Err, Unit]`       | Writes to a [`Target`](#printing-to-a-target)    |

```scala
val number: Syntax[String, Char, Char, Int] =
  Syntax.digit.repeat.string.transform(_.toInt, _.toString)

val range: Syntax[String, Char, Char, (Int, Int)] =
  number ~ Syntax.char('-') ~ number
```

```scala
range.parseString("10-20")
// res0: Either[StringParserError[String], Tuple2[Int, Int]] = Right((10, 20))
range.printString((10, 20))
// res1: Either[String, String] = Right("10-20")
range.print((1, 2))
// res2: Either[String, Chunk[Char]] = Right(IndexedSeq('1', '-', '2'))
```

## Round Trips

For a well-formed syntax, parsing the printed output gives back the original value:

```scala
range.printString((3, 9)).flatMap(range.parseString(_))
// res3: Either[String | StringParserError[String], Tuple2[Int, Int]] = Right(
//   (3, 9)
// )
```

## Writing Syntaxes That Print

A parser only has to turn text into a value. A printer has to do the opposite, so every step that loses information on the way in needs a hint on how to restore it on the way out.

### Constant results

`as(value)` maps a `Unit` syntax to a constant. When printing, the printer is given that constant and writes the original text again:

```scala
sealed trait Op
case object Plus  extends Op
case object Minus extends Op

val op: Syntax[String, Char, Char, Op] =
  Syntax.char('+').as(Plus: Op) | Syntax.char('-').as(Minus: Op)
```

`Syntax.char('+').as(Plus)` parses `+` into `Plus`, and when asked to print `Plus` it prints `+`. The printer compares the value it is given with `Plus`, so the second alternative is tried for `Minus`:

```scala
op.parseString("-")
// res4: Either[StringParserError[String], Op] = Right(Minus)
op.printString(Minus)
// res5: Either[String, String] = Right("-")
op.printString(Plus)
// res6: Either[String, String] = Right("+")
```

### Discarded results need a value to print

A syntax whose result is thrown away, such as whitespace, has no value for the printer. `asPrinted(result, value)` says what to print instead:

```scala
val spaces: Syntax[String, Char, Char, Unit] =
  Syntax.whitespace.repeat0.asPrinted((), Chunk(' '))

val assignment: Syntax[String, Char, Char, (String, Int)] =
  (Syntax.letter.repeat.string ~ Syntax.char('=').surroundedBy(spaces) ~ number)
```

```scala
assignment.parseString("x   =   5")
// res7: Either[StringParserError[String], Tuple2[String, Int]] = Right(
//   ("x", 5)
// )
assignment.printString(("x", 5))
// res8: Either[String, String] = Right("x = 5")
```

The parser accepts any amount of whitespace around `=`, and the printer produces exactly one space.

### Subtypes need `widen` or `transformTo`

When a syntax produces a parent type but each alternative produces a subtype, the printer needs to find the alternative that fits a value. `widen[T]` does this with a runtime class check, and `transformTo` takes a partial function and an error for the values it does not cover:

```scala
sealed trait Shape
final case class Circle(r: Int)         extends Shape
final case class Square(side: Int)      extends Shape

val circle: Syntax[String, Char, Char, Circle] =
  (Syntax.string("circle ", ()) ~> number).transform(Circle.apply, _.r)

val square: Syntax[String, Char, Char, Square] =
  (Syntax.string("square ", ()) ~> number).transform(Square.apply, _.side)

val shape: Syntax[String, Char, Char, Shape] =
  circle.widen[Shape] | square.widen[Shape]
```

```scala
shape.parseString("square 4")
// res9: Either[StringParserError[String], Shape] = Right(Square(4))
shape.printString(Circle(2))
// res10: Either[String, String] = Right("circle 2")
shape.printString(Square(7))
// res11: Either[String, String] = Right("square 7")
```

### Printing can fail

Printing returns an `Either`. A syntax can reject values it cannot print, for example when a `filter` condition is not met or a `transformTo` partial function is not defined:

```scala
val small: Syntax[String, Char, Char, Int] =
  number.filter[String, Int](_ < 100, "too big")
```

```scala
small.printString(5)
// res12: Either[String, String] = Right("5")
small.printString(500)
// res13: Either[String, String] = Left("too big")
```

## Printers on Their Own

`Syntax` is a pair of a `Parser` and a `Printer`, available as `asParser` and `asPrinter`. A `Printer` can be used independently, for example for pretty printing a data structure that you never parse:

```scala
val printer: Printer[String, Char, Int] = number.asPrinter
```

```scala
printer.printString(42)
// res14: Either[String, String] = Right("42")
```

`Printer.printString(str)`, `Printer.char(c)` and `Printer.byValue` let you build printers by hand. Combine a `Parser` and a `Printer` into a syntax with `parser <=> printer`.

## Printing to a Target

`print(value, target)` writes the output elements to a `Target`. A `Target[Output]` is a small interface with `write`, and `capture`/`emit`/`drop` used by the printer internally for backtracking. ZIO Parser ships two implementations:

- `zio.parser.target.ChunkTarget`, which accumulates the output in a `Chunk` and is used by `print`.
- `zio.parser.target.ZStreamTarget`, which pushes the output into a `ZStream`.

Next: [Error handling](errors.md).
