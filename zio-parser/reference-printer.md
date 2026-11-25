# Printer Reference

> A `Printer[Err, Out, Value]` takes a `Value` and writes it as a sequence of `Out` elements, or fails with an `Err`. Like `Parser`, it is one half of a `Syntax`, available as `Syntax#asPrinter`. Use a printer on its own when you only need to write values, and never parse them back.

The type has three parameters. `Err` is the error type, which is `String` for most constructors. `Out` is the output element type, which is `Char` for text. `Value` is the type of the value to print. The `Value` parameter is contravariant, so a printer for a supertype can print any subtype.

For combinators that take a `Syntax` as an argument, see the [Syntax reference](syntax.md). A printer method below has the same behavior as the `Syntax` method of the same name, applied to the printer half only.

## Printing a Value

| Method                    | Result                      | Description                                                       |
|---------------------------|-----------------------------|-------------------------------------------------------------------|
| `printString(value)`      | `Either[Err, String]`       | Prints to a `String`. Requires `Out` to be `Char`                 |
| `print(value)`            | `Either[Err, Chunk[Out]]`   | Prints to a `Chunk` of output elements                            |
| `print(value, target)`    | `Either[Err, Unit]`         | Writes the output elements to a `Target`, see [Targets](#targets) |

## Transforming the Value

| Method                              | Description                                                                              |
|-------------------------------------|------------------------------------------------------------------------------------------|
| `contramap(f)`                      | Converts a new value type to the value type of this printer with `f`                    |
| `contramapEither(f)`                | As `contramap`, but `f` returns an `Either`, and a `Left` fails printing                 |
| `contramapTo(from, failure)`        | As `contramap`, but `from` is a `PartialFunction`, and printing fails with `failure` where it is not defined |
| `transformOption(f)`                | As `contramapEither`, with `None` as the failure                                         |
| `filter(condition, failure)`        | Fails printing with `failure` when the value does not satisfy `condition`                |
| `asPrinted(matches, value)`         | Accepts `matches` as the value to print, and prints `value` instead                      |
| `widenWith(narrow, failure)`        | Prints a parent type, using `narrow` to find the subtype to print, and fails with `failure` otherwise |
| `mapError(f)`                       | Transforms the error type                                                                |
| `from[CaseClass]`                   | Prints a case class through a printer of its tuple. Needs a `TupleConversion` for the case class |

## Combining Printers

| Method                              | Description                                                                              |
|-------------------------------------|------------------------------------------------------------------------------------------|
| `<~` / `zipLeft`                    | Prints this value, then the `Unit` printer on the right                                  |
| `~>` / `zipRight`                   | Prints the `Unit` printer on the left, then this value                                   |
| `\|` / `<>` / `orElse`              | Prints with this printer if it accepts the value, otherwise with the other one           |
| `<+>` / `orElseEither`              | As `orElse`, but takes an `Either` as input and prints the side that holds the value     |
| `repeat` / `+`                      | Prints a `Chunk` of values, one after another                                            |
| `repeat0` / `*`                     | As `repeat`, for a possibly empty `Chunk`                                                |
| `repeatWithSep(sep)`                | Prints a `Chunk`, with `sep` printed between the values                                  |
| `repeatWithSep0(sep)`               | As `repeatWithSep`, for a possibly empty `Chunk`                                         |
| `repeatUntil(stop)`                 | Prints a `Chunk` of values, followed by `stop`                                           |
| `optional` / `?`                    | Prints an `Option`: nothing for `None`, and the value for `Some`                         |
| `between(left, right)`              | Prints `left`, the value and `right`                                                     |
| `surroundedBy(other)`               | Prints `other` before and after the value                                                |

## The Companion Object

| Constructor                                       | Description                                                                              |
|---------------------------------------------------|------------------------------------------------------------------------------------------|
| `unit`                                            | Prints nothing for `()`                                                                  |
| `fail(failure)`                                   | Always fails printing with `failure`                                                     |
| `any[T]`                                          | Prints the value unchanged, as an element of type `T`                                    |
| `byValue(f)`                                      | Chooses the printer to use from the value, using `f`                                      |
| `print(value)`                                    | Prints a constant output element                                                         |
| `printString(str)`                                | Prints the constant `str` as `Char` elements                                             |
| `apply(value)`                                    | Prints the constant `value`, for any output type other than `Char`                       |
| `char(c)`                                         | Prints the character `c`, and accepts only the value `()`                                |
| `notChar(c)` / `notChar(c, failure)`              | Prints a character that is not `c`                                                       |
| `exactly(value)` / `exactly(value, failure)`      | Prints `value` only when the value to print equals it                                    |
| `except(value)` / `except(value, failure)`        | Prints any value except `value`                                                          |
| `charIn(chars*)` / `charNotIn(chars*)`            | Prints a character in or outside a set                                                   |
| `anyChar`                                         | Prints any one character                                                                 |
| `anyString`                                       | Prints a `String` as its characters                                                      |
| `string(str, value)`                | Prints `str` when asked to print `value`, the mirror of `Syntax.string`                  |
| `digit`, `letter`, `alphaNumeric`, `whitespace`   | Print one character of that class, and fail for any other value                          |
| `regex(regex, failure)`                           | Prints a `Chunk[Char]` after checking that the regex matches it                          |
| `unsafeRegex(regex)`                              | As `regex`, without a failure value. Use only with regexes that cannot fail              |
| `regexChar(regex, failure)`                       | Prints a `Char` after checking that the regex matches it                                 |
| `unsafeRegexChar(regex)`                          | As `regexChar`, without a failure value                                                  |
| `regexDiscard(regex, value)`                      | Prints `value`, a `Chunk[Char]`, for a `Unit` value                                      |

## Targets

A `Target[Output]` receives output elements one at a time, so a printer can write them without building a `Chunk` first. The printer uses `capture`, `emit` and `drop` to handle alternatives: it captures the output of a branch, then either emits it to the target or drops it when the branch fails.

| Member                 | Description                                                              |
|------------------------|--------------------------------------------------------------------------|
| `write(value)`         | Writes one output element                                                |
| `capture()`            | Starts capturing output for a branch, and returns a `Capture` token      |
| `emit(capture)`        | Writes the output captured by `capture` to the target                    |
| `drop(capture)`        | Discards the output captured by `capture`                                |

The library ships two targets:

- `zio.parser.target.ChunkTarget` collects the output in memory. `result` returns the output as a `Chunk`. `print(value)` uses it internally.
- `zio.parser.target.ZStreamTarget` pushes the output into a queue, and its `stream` field reads the queue as a `ZStream`. See [How to Print to a ZStream](how-to-stream-printing.md).
