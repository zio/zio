# Getting Started with ZIO Parser

> This page parses a first value, prints it back and reads an error. Add ZIO Parser to your project first, as described in [Installation](index.md#installation).

## Importing

On Scala 3, import everything from the `zio.parser` package:

```scala
import zio.parser.*
```

On Scala 2, write `import zio.parser._` instead. The rest of the documentation uses the Scala 3 syntax.

## Declaring a Syntax

A `Syntax` is the description of a grammar. `Syntax.digit` matches one digit:

```scala
val digitSyntax: Syntax[String, Char, Char, Char] = Syntax.digit
```

## Parsing a String

`parseString` runs the syntax on a string and returns an `Either`:

```scala
val result: Either[StringParserError[String], Char] = digitSyntax.parseString("1")
// result: Either[StringParserError[String], Char] = Right('1')
```

## Printing a Value

The same syntax prints a value back with `printString`:

```scala
digitSyntax.printString('7')
// res0: Either[String, String] = Right("7")
```

## Reading an Error

When the input does not match, `parseString` returns a `StringParserError`. Its `pretty` method shows the failing position:

```scala
println(digitSyntax.parseString("Hello").left.map(_.pretty).merge)
// Hello
// ^
// error: Failure at position 0: not a digit
//
```

## Next Steps

- [Tutorial: Parse and Print a Config Language](tutorial.md) builds a complete parser and printer step by step.
- [Building Syntaxes](syntax.md) lists the combinators.
- [Printing and Invertible Syntaxes](printing.md) explains why one syntax works in both directions.
