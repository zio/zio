# How to Parse Whitespace and Comments

> Real input is rarely tight. People put spaces around separators, break lines in odd places and leave comments. This guide shows how to accept all of that when parsing, while keeping control of what the printer writes.

The goal is a syntax for a list of numbers, such as `1, 2, 3`, that also accepts `#` comments and extra whitespace anywhere between the items.

## The Problem

A parser that only expects `1,2,3` rejects `1 , 2  # two`, even though a human reads both the same way. Writing the whitespace handling by hand in every place it may appear makes the grammar hard to read and easy to get wrong.

ZIO Parser solves this by describing "skippable text" once and reusing that description. The printer then decides separately how much whitespace to write.

## Prerequisites

The examples use the same imports as the [tutorial](tutorial.md):

```scala
import zio.Chunk
import zio.parser.*
```

## Matching a Comment

A comment starts with `#` and runs to the end of the line. We match the `#` and then every character that is not a newline. The matched text is not needed, so we discard it with `unit`, which takes the value the printer should use for the discarded result:

```scala
val comment: Syntax[String, Char, Char, Unit] =
  (Syntax.char('#') ~> Syntax.notChar('\n').repeat0).unit(Chunk.empty)
```

The code above:

- `Syntax.char('#')` matches the `#` and produces no value
- `~>` drops that result and keeps the result of `Syntax.notChar('\n').repeat0`
- `unit(Chunk.empty)` discards the text, and tells the printer to write nothing for it

## Skipping Whitespace and Comments

We combine a single space and a comment into one syntax, and repeat it to skip any amount of both:

```scala
val skip: Syntax[String, Char, Char, Unit] =
  (Syntax.whitespace.unit(' ') | comment).repeat0.unit(Chunk.empty)
```

The code above:

- `Syntax.whitespace.unit(' ')` matches one whitespace character, and would print a space
- `|` tries the whitespace first, and falls back to a comment
- `repeat0` allows zero or more of either
- `unit(Chunk.empty)` makes the whole skip print nothing

The last point matters when we print. Because `skip` prints nothing, the printer must get its spacing from somewhere else. We come back to this below.

## Parsing Items Between Skippable Text

Each number is surrounded by `skip`, and the items are separated by commas:

```scala
val number: Syntax[String, Char, Char, Int] =
  Syntax.digit.repeat.string.transform(_.toInt, _.toString)

val numbers: Syntax[String, Char, Char, Chunk[Int]] =
  (skip ~> number <~ skip).repeatWithSep0(Syntax.char(','))
```

Now we parse input with spaces and comments in several places:

```scala
numbers.parseString("1 , 2  # two\n, 3")
// res0: Either[StringParserError[String], Chunk[Int]] = Right(
//   IndexedSeq(1, 2, 3)
// )
```

The parser skipped the spaces around each number, skipped the comment `# two` and the newline after it, and read the three numbers.

## Printing with Explicit Spacing

Because `skip` prints nothing, printing the same syntax gives no spaces:

```scala
numbers.printString(Chunk(1, 2, 3))
// res1: Either[String, String] = Right("1,2,3")
```

To print a space after each comma, we build the separator from a comma followed by optional whitespace. The parser accepts any amount of whitespace there, and `asPrinted` makes the printer write exactly one space:

```scala
val spaces: Syntax[String, Char, Char, Unit] =
  Syntax.whitespace.repeat0.asPrinted((), Chunk(' '))

val comma: Syntax[String, Char, Char, Unit] =
  Syntax.char(',') ~> spaces

val spacedNumbers: Syntax[String, Char, Char, Chunk[Int]] =
  (skip ~> number <~ skip).repeatWithSep0(comma)
```

Printing now writes a space after each comma, and the parser still accepts the same input:

```scala
spacedNumbers.printString(Chunk(1, 2, 3))
// res2: Either[String, String] = Right("1, 2, 3")
spacedNumbers.parseString("1 , 2  # two\n, 3")
// res3: Either[StringParserError[String], Chunk[Int]] = Right(
//   IndexedSeq(1, 2, 3)
// )
```

:::tip
To skip whitespace with no comments, `Syntax.unsafeRegexDiscard(Regex.whitespace, Chunk(' '))` skips any amount of whitespace, including line breaks, in one regex step, and prints one space. Regexes match faster than repeated single-character syntaxes. See the [Regex reference](regex.md).
:::

:::caution
Whitespace and comments are both skipped when parsing, so you cannot tell them apart in the result. If the output needs the original comments, parse them into a value of their own instead of discarding them.
:::
