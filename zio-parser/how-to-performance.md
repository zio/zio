# How to Tune Parser Performance

> A parser can be fast enough for most inputs without any tuning. When it is not, a few choices in how you write the syntax make a large difference. This guide covers the choices that matter most, and how to check their effect with a measurement.

The goal is to replace a character-by-character token with one regex step, and to pick the parser implementation that fits your input.

## The Problem

A syntax such as `Syntax.digit.repeat` runs one parsing step per character. Each step has overhead, and a long token pays that overhead for every character. Parsing a large file of numbers can then spend most of its time on that overhead.

ZIO Parser compiles a `Regex` into one matcher, so a whole token is checked in a single step.

## Prerequisites

```scala
import zio.Chunk
import zio.parser.*
```

## Matching Tokens with a Regex

We write the same token two ways. The first repeats a character syntax. The second matches the whole token with `Regex.digits`:

```scala
val charByChar: Syntax[String, Char, Char, String] =
  Syntax.digit.repeat.string

val wholeToken: Syntax[String, Char, Char, String] =
  Syntax.regex(Regex.digits, "not digits").string
```

Both produce the same result for the same input:

```scala
charByChar.parseString("2024")
// res0: Either[StringParserError[String], String] = Right("2024")
wholeToken.parseString("2024")
// res1: Either[StringParserError[String], String] = Right("2024")
```

The difference shows up in speed, not in the output. The regex version is preferred for tokens such as numbers, identifiers and whitespace, and combinators are the right tool for the structure that joins them. The [Regex reference](regex.md) lists the available regexes.

:::caution
Do not claim a speed-up from this change without measuring it on your own input. The repository has a `benchmarks` module with JMH benchmarks for the parser, and you can add your own benchmark with your real data.
:::

## Choosing the Parser Implementation

Parsing defaults to `ParserImplementation.Recursive`, which is the faster option. `ParserImplementation.StackSafe` is slower, but it does not overflow the stack on deeply nested input. Keep the default unless your input can be nested deeply enough to overflow the stack. The [Syntax reference](syntax.md#choosing-the-parser-implementation) describes both options in detail.

## Limiting Backtracking

By default, a failing branch of `|` rewinds the input, so the next branch starts at the same position. Rewinding costs work, and you can avoid it in grammars where the first character already tells you which branch to take. The [Syntax reference](syntax.md#backtracking) describes `manualBacktracking` and `backtrack`.

Turning backtracking off changes which inputs parse. A branch that consumed input and then failed will no longer hand the input back to the next branch. Make this change only where the grammar guarantees the choice from the first character, and test the change with inputs that take each branch.

## Measuring a Change

A single timing run on a laptop is not a reliable measure. Use a benchmark that runs the parser many times on realistic input, warms up the JVM first, and reports the spread of results. The `benchmarks` module in the repository shows how JMH benchmarks are set up for this library.
