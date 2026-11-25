# Parser Reference

> A `Parser[Err, In, Result]` reads input of type `In` and produces a `Result`, or fails with an `Err`. Parsers are one half of a `Syntax`, available as `Syntax#asParser`. You usually build a `Syntax` and use its parser through `parseString`, but a `Parser` can be used on its own when you never need the printer.

The type has three parameters. `Err` is the error type, which is `String` for most constructors. `In` is the input element type, which is `Char` for text. `Result` is the value the parser produces. `Parser` is covariant in `Err` and `Result` and contravariant in `In`, so a parser that needs fewer input types can be used where more are available.

For combinators that take a `Syntax` or `Printer` as an argument, see the [Syntax reference](syntax.md). Every method on `Parser` below has the same behavior as the `Syntax` method of the same name, applied to the parser half only.

## Running a Parser

| Method                                  | Result                                       | Description                                                     |
|-----------------------------------------|----------------------------------------------|-----------------------------------------------------------------|
| `parseString(input)`                    | `Either[StringParserError[Err], Result]`     | Parses a `String`. Requires `In` to be `Char`                   |
| `parseString(input, implementation)`    | `Either[StringParserError[Err], Result]`     | As above, with a `ParserImplementation`                         |
| `parseChars(input)`                     | `Either[StringParserError[Err], Result]`     | Parses a `Chunk[Char]`                                          |
| `parseChars(input, implementation)`     | `Either[StringParserError[Err], Result]`     | As above, with a `ParserImplementation`                         |
| `parseChunk(input)`                     | `Either[ParserError[Err], Result]`           | Parses any `Chunk[In]`. Returns the raw error, without the input |

`StringParserError` holds the input and the `ParserError`, and `pretty` formats it. See [Error Handling](errors.md) for the cases of `ParserError`.

## Transforming Results

| Method                           | Description                                                                              |
|----------------------------------|------------------------------------------------------------------------------------------|
| `map(f)`                         | Transforms the result                                                                    |
| `as(value)`                      | Replaces the result with a constant                                                      |
| `unit`                           | Discards the result                                                                      |
| `flatMap(f)`                     | Runs the parser returned by `f` on the rest of the input. Use it when the next step depends on this result |
| `transformEither(to)`            | Transforms the result. `to` returns an `Either`, and a `Left` fails the parser           |
| `transformOption(to)`            | Transforms the result. `to` returns an `Option`, and `None` fails the parser             |
| `filter(condition, failure)`     | Fails with `failure` when the result does not satisfy `condition`                        |
| `string`                         | Converts a `Chunk[Char]` result into a `String`                                           |
| `to[CaseClass]`                  | Maps a tuple result to a case class. Needs a `TupleConversion` for the case class       |
| `strip`                          | Removes all name information to make parsing faster. Failure messages become less detailed |
| `flatten`                        | Joins a `Chunk[String]` result into one `String`. Available only when the result is a `Chunk[String]` |
| `mapError(f)`                    | Transforms the error type                                                                |

## Sequencing and Alternatives

| Method                              | Result                                          | Description                                                        |
|-------------------------------------|-------------------------------------------------|--------------------------------------------------------------------|
| `~` / `zip`                         | `Parser[..., (A, B)]`                           | Runs this parser, then the other one, and pairs the results        |
| `<~` / `zipLeft`                    | `Parser[..., A]`                                | Runs both and keeps the left result                                |
| `~>` / `zipRight`                   | `Parser[..., B]`             | Runs both and keeps the right result                              |
| `\|` / `<>` / `orElse`              | `Parser[..., A]`                                | Tries this parser, then the other one, if this one fails          |
| `<+>` / `orElseEither`              | `Parser[..., Either[A, B]]`                     | As `orElse`, but keeps which branch matched                       |
| `??` / `named(name)`                | `Parser[..., A]`                                | Adds a name to errors raised inside this parser                   |

## Repetition and Optional Results

| Method                              | Result                       | Description                                              |
|-------------------------------------|------------------------------|----------------------------------------------------------|
| `repeat` / `+`                      | `Chunk[A]`                   | One or more times                                        |
| `repeat0` / `*`                     | `Chunk[A]`                   | Zero or more times                                       |
| `atLeast(n)`                        | `Chunk[A]`                   | At least `n` times                                       |
| `exactly(n)`                        | `Chunk[A]`                   | Exactly `n` times                                        |
| `repeatWithSep(sep)`                | `Chunk[A]`                   | One or more times with `sep` between them                |
| `repeatWithSep0(sep)`               | `Chunk[A]`                   | Zero or more times with `sep` between them               |
| `repeatUntil(stop)`                 | `Chunk[A]`                   | Repeats until `stop` succeeds                            |
| `optional` / `?`                    | `Option[A]`                  | Zero or one time                                         |
| `between(left, right)`              | `Parser[..., A]`             | Runs `left` and `right` around this parser and keeps the result |
| `surroundedBy(other)`               | `Parser[..., A]`             | Runs `other` before and after this parser                |
| `not(failure)`                      | `Parser[..., Unit]`          | Succeeds when this parser fails, and fails with `failure` when this parser succeeds |

## Backtracking

| Method                         | Description                                                                                  |
|--------------------------------|----------------------------------------------------------------------------------------------|
| `backtrack`                    | Rewinds the input to where this parser started if it fails                                   |
| `autoBacktracking`             | Turns automatic backtracking on for this parser                                              |
| `manualBacktracking`           | Turns automatic backtracking off for this parser                                             |
| `setAutoBacktracking(enabled)` | Sets the backtracking mode for this parser                                                   |

The [Syntax reference](syntax.md#backtracking) explains when to use each of them.

## The Companion Object

These constructors are available on `Parser` directly. Most have a `Syntax` counterpart with the same name.

| Constructor                                 | Description                                                             |
|---------------------------------------------|-------------------------------------------------------------------------|
| `succeed(value)`                            | Succeeds with `value` without consuming input                           |
| `fail(failure)`                             | Fails with `failure`                                                    |
| `char(c)` / `char(c, failure)`              | Matches one character                                                   |
| `notChar(c)` / `notChar(c, failure)`        | Matches any character except `c`                                        |
| `charIn(chars*)` / `charNotIn(chars*)`      | Matches a character in or outside a set                                 |
| `anyChar`                                   | Matches any one character                                               |
| `any[T]`                                    | Matches any one element of type `T`                                     |
| `string(str, value)`                        | Matches `str` and produces `value`                                      |
| `anyString`                                 | Matches the rest of the input as a `String`                             |
| `ignoreRest`                                | Consumes the rest of the input without producing a value                |
| `digit`, `letter`, `alphaNumeric`, `whitespace` | Match one character of that class                                   |
| `regex(regex, failure)`                     | Matches a `Regex` and produces the matched `Chunk[Char]`                |
| `regexChar(regex, failure)`                 | Matches a `Regex` and produces its last character                       |
| `regexDiscard(regex, failure)`              | Matches a `Regex` and discards it                                       |
| `unit`                                      | Succeeds with `()`                                                      |
| `index`                                     | Produces the current position in the input                              |
| `end`                                       | Succeeds only at the end of the input                                   |

The `unsafe` variants, such as `unsafeRegex` and `unsafeRegexChar`, have no failure value, so a failure is reported as an `UnknownFailure`. Use them only with regexes that cannot fail.

## Error Cases

`Parser.ParserError` has five cases: `Failure`, `UnknownFailure`, `UnexpectedEndOfInput`, `NotConsumedAll` and `AllBranchesFailed`. Each case, what it means and how `pretty` shows it, is described in [Error Handling](errors.md#error-types).
