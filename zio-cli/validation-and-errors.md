# Validation and Errors

> This page describes the errors a `CliApp` can produce, what the user sees for each one, and how the "Did you mean" hints work.

Two types describe failures:

- `ValidationError` describes why the command-line input does not match the command. The parser produces it.
- `CliError[E]` is the error type of `CliApp#run`. It wraps a `ValidationError`, a failure of a built-in option, or an error from your own code.

## ValidationError

`ValidationError` pairs a short code for the kind of failure with the message for the user:

```scala
final case class ValidationError(validationErrorType: ValidationErrorType, error: HelpDoc) extends Throwable {
  def isOptionMissing: Boolean
}
```

`ValidationError` extends `Throwable`, but zio-cli never throws it. `Command#parse` returns it in the error channel of a ZIO effect. `ValidationError#isOptionMissing` is `true` when `validationErrorType` is `MissingValue`.

### Error Types Users See

These `ValidationErrorType` values reach the user. The messages come from a `count` command with a required `--limit` integer option and a `<start>` integer argument, and from a `git` command with the subcommands `add` and `clone`:

| Value             | Raised when                                                                                                | Example message                                              |
| ----------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `MissingValue`    | A required option is absent, or an option is given without its value.                                      | `Expected to find --limit option.`, `Expected some value.`   |
| `InvalidValue`    | An option's value cannot be converted to its type, or a custom check on the option fails.                  | `x is not a integer.`                                        |
| `InvalidArgument` | An argument is missing, cannot be converted to its type, or fails a check from `Args#mapOrFail`.           | `abc is not a integer.`, `Missing argument <start>.`         |
| `CorrectedFlag`   | An option name is misspelt and the parse fails. See [Auto-Correct Suggestions](#auto-correct-suggestions). | `The flag "--limt" is not recognized. Did you mean --limit?` |
| `CommandMismatch` | The first word after a parent command is not one of its subcommands.                                       | `Invalid subcommand for git. Use one of 'add', 'clone'`      |

When several required options are missing, the message lists each of them, one per line.

### Internal Error Types

The parser uses the remaining values to steer its own work and does not show them to users. `MissingFlag` means "the next word is not this option", `UnclusteredFlag` drives the splitting of `-abc` into `-a -b -c`, `KeyValuesDetected` drives key-value options, and `NoBuiltInMatch` means the input holds no built-in option. `MissingSubCommand` exists but is never produced.

### Unknown Options and Extra Words

zio-cli does not reject words it does not recognise. Words left over after a complete command are ignored: for a command without arguments, `status --unknown` runs `status`. For a command with a text argument, an unknown `--option` can become the argument's value. Declare every option your users may type, and validate argument values in `execute` or with `Args#mapOrFail` when a stray option would cause harm.

## CliError

`CliApp#run` returns `ZIO[R, CliError[E], Option[A]]`. `CliError` is a sealed trait with four cases:

```scala
sealed trait CliError[+E] extends Exception
object CliError {
  case class Parsing(e: ValidationError) extends CliError[Nothing]
  case class Execution[+E](e: E)         extends CliError[E]
  case class IO(e: IOException)          extends CliError[Nothing]
  case class BuiltIn(e: Exception)       extends CliError[Nothing]
}
```

| Case           | Raised when                                                         | What the user sees                                                    |
| -------------- | ------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `Parsing(e)`   | The input does not match the command.                               | The text of `e.error`, printed to standard output before the failure. |
| `Execution(e)` | Your `execute` function fails with `e`.                             | Nothing from zio-cli, which passes your error on unchanged.           |
| `IO(e)`        | Writing the help or the completion script to standard output fails. | Nothing from zio-cli.                                                 |
| `BuiltIn(e)`   | Computing shell completions for `--shell-completion-index` fails.   | Nothing from zio-cli.                                                 |

Quitting the [wizard](wizard.md) is not an error: `CliApp#run` succeeds with `None`. To report each case in your own words, match on it:

```scala
import zio._
import zio.cli._

def report(error: CliError[String]): UIO[Unit] =
  error match {
    case CliError.Parsing(e)     => Console.printLineError(s"Input rejected: ${e.validationErrorType}").orDie
    case CliError.Execution(msg) => Console.printLineError(s"Command failed: $msg").orDie
    case CliError.IO(e)          => Console.printLineError(s"Could not write output: ${e.getMessage}").orDie
    case CliError.BuiltIn(e)     => Console.printLineError(s"Completion failed: ${e.getMessage}").orDie
  }
```

`CliError` extends `Exception`, and its `getMessage` is always `"zio-cli app failed"`. Use the case itself, or `getCause`, to find the reason. `getCause` returns the wrapped exception or `ValidationError`, the error of an `Execution` when it is a `Throwable`, and `null` otherwise.

## Exit Codes

`CliApp#run` does not choose an exit code. A program that extends `ZIOCliDefault` exits as follows:

| Outcome                                                   | Exit code | Output                                                   |
| --------------------------------------------------------- | --------- | -------------------------------------------------------- |
| `CliApp#run` succeeds, including after `--help`           | `0`       | Whatever the command or the built-in option prints.      |
| `CliError.Parsing`                                        | `1`       | The validation message, already printed by `CliApp#run`. |
| `CliError.Execution`, `CliError.IO` or `CliError.BuiltIn` | `1`       | ZIO logs the failure at the `ERROR` level.               |

To use different exit codes for different errors, see [How to choose the exit code for each error](how-to-exit-codes.md).

## Auto-Correct Suggestions

When a user misspells an option name, zio-cli can add a hint to the error. With the default configuration, a required option `--limit` typed as `--limt` gives:

```text
The flag "--limt" is not recognized. Did you mean --limit?
```

The hint appears only when all of these are true:

- The distance between the typed word and the option's main name, counted as Levenshtein edits (inserted, deleted or replaced characters), is at most [`CliConfig#autoCorrectLimit`](cli-config.md#auto-correction). The default is `2`. Aliases are not compared.
- The option's main name, without its dashes, is longer than `autoCorrectLimit + 1` characters. With the default, names of four or more characters qualify.
- No other option of the command has exactly the typed word as its name.
- The command as a whole fails to parse, typically because the misspelt option is required.

The hint never rewrites the input. The parse fails with a `CorrectedFlag` error, and the user types the command again. With `caseSensitive = false`, the distance ignores case.

:::caution
A misspelt option gets a hint only when the parse fails. When the option has a default value and the rest of the input still parses, the misspelt word is ignored and the command runs with the default. For example, `--verbos` for a flag `--verbose` runs the command with the flag off, and for a command with a text argument the word `--verbos` can become the argument's value.
:::

## See Also

- [CLI Configuration](cli-config.md) for `CliConfig#autoCorrectLimit` and the other parser settings.
- [Commands](commands.md) for how commands and subcommands are built.
- [Options](options.md) and [Args](args.md) for the values that produce `InvalidValue` and `InvalidArgument` errors.
