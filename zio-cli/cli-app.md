# CliApp and Running a CLI

> A `CliApp[R, E, A]` is a complete command-line application. It holds the program's name, version and summary, the [`Command`](commands.md) that describes what the user may type, and the function that runs once the input is parsed. It needs an environment `R`, can fail with an error of type `E`, and produces a value of type `A`.

We build a `CliApp` with `CliApp.make`, run it with `CliApp#run`, and, for a stand-alone program, wrap it in `ZIOCliDefault`:

```scala
sealed trait CliApp[-R, +E, +A] {
  def run(args: List[String]): ZIO[R, CliError[E], Option[A]]
  def config(newConfig: CliConfig): CliApp[R, E, A]
  def map[B](f: A => B): CliApp[R, E, B]
  def flatMap[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): CliApp[R1, E1, B]
  def footer(newFooter: HelpDoc): CliApp[R, E, A]
  def summary(s: HelpDoc.Span): CliApp[R, E, A]
}
```

## Building a CliApp With CliApp.make

`CliApp.make` takes the description of the app in its first parameter list and the function that runs the command in its second:

```scala
object CliApp {
  def make[R, E, Model, A](
    name: String,
    version: String,
    summary: HelpDoc.Span,
    command: Command[Model],
    footer: HelpDoc = HelpDoc.Empty,
    config: CliConfig = CliConfig.default,
    figFont: FigFont = FigFont.Default
  )(execute: Model => ZIO[R, E, A]): CliApp[R, E, A]
}
```

The following app greets the name given with `--name`:

```scala
import zio._
import zio.cli._
import zio.cli.HelpDoc.Span.text

val nameOpt: Options[String] = Options.text("name").withDefault("world") ?? "Who to greet."
val greet: Command[String]   = Command("greet", nameOpt).withHelp("Prints a greeting.")

val greetApp: CliApp[Any, java.io.IOException, Unit] = CliApp.make(
  name = "greet",
  version = "1.0.0",
  summary = text("Prints a greeting"),
  command = greet,
  footer = HelpDoc.p("Example footer text.")
)(name => Console.printLine(s"Hello, $name!"))
```

Each parameter of `CliApp.make` has the following meaning:

| Parameter | Type                    | Default             | What it does                                                                                                            |
| --------- | ----------------------- | ------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `name`    | `String`                | none                | The program name. It is drawn as the banner and starts the header line of the help; the wizard header also contains it. |
| `version` | `String`                | none                | The version string, shown after the name in the header line.                                                            |
| `summary` | `HelpDoc.Span`          | none                | A one-line description, shown after `--` in the header line.                                                            |
| `command` | `Command[Model]`        | none                | The grammar of valid input. The value it parses is the `Model` passed to `execute`. See [Commands](commands.md).        |
| `footer`  | `HelpDoc`               | `HelpDoc.Empty`     | Text printed at the end of the help.                                                                                    |
| `config`  | `CliConfig`             | `CliConfig.default` | Parsing and help settings, such as case sensitivity and auto-correction. See [CLI Configuration](cli-config.md).        |
| `figFont` | `FigFont`               | `FigFont.Default`   | The font used to draw the name as a banner. See [Figlet banners](figlet.md).                                            |
| `execute` | `Model => ZIO[R, E, A]` | none                | The second parameter list. It runs with the parsed value of the command.                                                |

## The Help Screen

`--help` prints the banner, a header line with the name, version and summary, the `USAGE` section, the help of the command and the footer. For `greetApp`, the help is:

```text
                         __
   ____ _________  ___  / /_
  / __ `/ ___/ _ \/ _ \/ __/
 / /_/ / /  /  __/  __/ /_
 \__, /_/   \___/\___/\__/
/____/

greet 1.0.0 -- Prints a greeting

USAGE

  $ greet [--name <text>]

DESCRIPTION

  Prints a greeting.

OPTIONS

  --name <text>
    A user-defined piece of text.

    Who to greet.

    This setting is optional. Default: 'world'.

  Example footer text.
```

The built-in options themselves, such as `--help`, are not listed. See [Built-in Commands](built-in-commands.md) for all of them.

## Running a CliApp With CliApp#run

`CliApp#run` takes the command-line words as a `List[String]`, without the program name, and returns `ZIO[R, CliError[E], Option[A]]`:

```scala
object GreetMain extends ZIOAppDefault {
  def run =
    greetApp.run(List("--name", "Ada"))
}
```

Before parsing, `CliApp#run` puts the name of the top-level command in front of the words, so the user never types it. For a command with subcommands, it adds the parent's name, and the user starts with the subcommand name. For a top-level command built with `Command#|`, it adds nothing, and the user types the name of one alternative first.

The result depends on the input:

| Input                                                     | What `CliApp#run` does                                | Result                                           |
| --------------------------------------------------------- | ----------------------------------------------------- | ------------------------------------------------ |
| A valid command                                           | Runs `execute` with the parsed value.                 | `Some(a)`, or `CliError.Execution(e)` on failure |
| `--help` or `-h`                                          | Prints the help.                                      | `None`                                           |
| A parent command with no subcommand                       | Prints the parent's help.                             | `None`                                           |
| `--shell-completion-script` or `--shell-completion-index` | Prints the completion script or the completions.      | `None`                                           |
| `--wizard`                                                | Asks for the values, then runs the command they form. | The result of that run, or `None` on `quit`      |
| Invalid input                                             | Prints the error message to standard output.          | Fails with `CliError.Parsing`                    |

Words left over after a complete command are ignored, so `greet --name Ada extra` still greets `Ada`. The error type `CliError[E]` has four cases, `Parsing`, `Execution`, `IO` and `BuiltIn`, described in [Validation and Errors](validation-and-errors.md#clierror).

## Running a Stand-alone Program With ZIOCliDefault

`ZIOCli` is a `ZIOApp` whose `ZIOApp#run` reads the program arguments and passes them to `cliApp.run`. `ZIOCliDefault` combines it with `ZIOAppDefault` and is the usual way to write a command-line program:

```scala
trait ZIOCli extends ZIOApp {
  def cliApp: CliApp[Environment with ZIOAppArgs with Scope, Any, Any]
}

trait ZIOCliDefault extends ZIOCli with ZIOAppDefault
```

With `ZIOAppDefault`, `Environment` is `Any`, so `cliApp` may require at most `ZIOAppArgs` and `Scope`. Provide other services inside `execute`. The program becomes an object that names its `CliApp`:

```scala
object Greet extends ZIOCliDefault {
  val cliApp = greetApp
}
```

The program exits with code `0` when `cliApp.run` succeeds and with code `1` otherwise; [Exit Codes](validation-and-errors.md#exit-codes) lists each outcome. To choose other exit codes, see [How to choose the exit code for each error](how-to-exit-codes.md).

Use `ZIOCli` directly when your program extends another `ZIOApp` subtype.

## Changing a CliApp

These methods return a new `CliApp` and leave the original unchanged. Each one changes a single aspect of the app.

### CliApp#map

`CliApp#map` transforms the value that `execute` returns. Built-in actions that return `None`, such as `--help`, are not affected:

```scala
val greetingApp: CliApp[Any, Nothing, String] = CliApp.make(
  name = "greet",
  version = "1.0.0",
  summary = text("Builds a greeting"),
  command = greet
)(name => ZIO.succeed(s"Hello, $name!"))

val lengthApp: CliApp[Any, Nothing, Int] = greetingApp.map(_.length)
```

### CliApp#flatMap

`CliApp#flatMap` runs another effect after `execute`, with the value that `execute` returns. The environment and error types widen to include those of the new effect:

```scala
val loggedApp: CliApp[Any, java.io.IOException, Unit] =
  greetApp.flatMap(_ => Console.printLine("Greeting sent."))
```

### CliApp#footer

`CliApp#footer` appends a `HelpDoc` to the footer. Calling it more than once keeps all the text, in call order:

```scala
val withFooter = greetApp.footer(HelpDoc.p("Report problems to the issue tracker."))
```

### CliApp#summary

`CliApp#summary` appends a `HelpDoc.Span` to the summary in the header line. No space is added, so include one in the span:

```scala
val withSummary = greetApp.summary(text(" (beta)"))
```

### CliApp#config

`CliApp#config` replaces the whole `CliConfig` of the app:

```scala
val caseSensitiveApp = greetApp.config(CliConfig.default.copy(caseSensitive = true))
```

## The Usage Line

The `USAGE` section of the help has one line per shape of input the command accepts. Each line starts with `$ `. The line comes from a `UsageSynopsis` value, which every `Command`, `Options` and `Args` has. The synopsis is built as follows:

- A command starts with its name, followed by the synopsis of its options and then of its arguments.
- An option is written as its names and, unless it is a flag, its value type, such as `--name <text>`. An enumeration option shows its choices, such as `--mode fast | slow`.
- An option with several names lists them in parentheses, such as `(-q, --quiet)`.
- Options with a default value, options made with `Options#optional`, and flags are wrapped in square brackets, such as `[--verbose]`.
- Two options combined with `Options#|` are joined by `|` with no spaces, such as `[-a]|[-b]`.
- A repeated argument, made with `Args#*` or `Args#+`, ends with `...`.
- A command with two or more subcommands shows `<command>` in their place. A command with exactly one subcommand shows that subcommand's name, options and arguments.
- Empty parts are removed, so a command with no options and no arguments shows only its name.

To print the usage line of a few commands, we render each synopsis with the default configuration:

```scala
def usage(command: Command[_]): String =
  command.synopsis.enumerate(CliConfig.default).map(_.text).mkString("\n")

val tail       = Command("tail", Options.integer("lines").alias("n").withDefault(BigInt(10)), Args.file("file").+)
val repo       = Command("repo").subcommands(Command("clone", Args.text("repository")), Command("add", Args.text("path")))
val singleRepo = Command("repo").subcommands(Command("clone", Args.text("repository")))
```

The three commands render as follows:

```scala
println(usage(tail))
// tail [(-n, --lines <integer>)] <file>...
println(usage(repo))
// repo <command>
println(usage(singleRepo))
// repo clone <repository>
```

`CliConfig#showAllNames` and `CliConfig#showTypes` change how names and types appear in the `USAGE` lines (see [Help Appearance](cli-config.md#help-appearance)). They do not affect the `OPTIONS` and `COMMANDS` sections, which always show every name and type.

`UsageSynopsis` has these public members:

```scala
sealed trait UsageSynopsis {
  def enumerate(config: CliConfig): List[HelpDoc.Span]
  def helpDoc: HelpDoc
  def +(that: UsageSynopsis): UsageSynopsis
  def optional: UsageSynopsis
}
```

`UsageSynopsis#enumerate` returns the lines for a given configuration. `UsageSynopsis#helpDoc` returns the same lines as a `HelpDoc`, always rendered with `CliConfig.default`. `UsageSynopsis#+` places two synopses one after the other, and `UsageSynopsis#optional` wraps a synopsis in square brackets. The cases that make up a synopsis (`UsageSynopsis.Named`, `Optional`, `Repeated`, `Sequence`, `Alternation`, `Mixed` and `None`) are public, but the constructors of `Command`, `Options` and `Args` create them for you.
