# Commands

> A `Command[A]` describes one command of a command-line application: its name, its [options](options.md), its [arguments](args.md), and, optionally, its subcommands. When the user's input matches the command, the parser produces a value of type `A`. You pass a `Command` to `CliApp.make`, and the value it produces is handed to your program (see [CliApp and Running a CLI](cli-app.md)).

Git is a familiar example. In `git clone https://github.com/zio/zio-cli`, `git` is the top-level command, `clone` is a subcommand, and the URL is an argument of `clone`.

## Creating a Command

`Command.apply` builds a command from a name and, optionally, `Options` and `Args`. It has four overloads:

```scala
object Command {
  def apply(name: String): Command[Unit]
  def apply[OptionsType](name: String, options: Options[OptionsType]): Command[OptionsType]
  def apply[ArgsType](name: String, args: Args[ArgsType]): Command[ArgsType]
  def apply[OptionsType, ArgsType](name: String, options: Options[OptionsType], args: Args[ArgsType]): Command[(OptionsType, ArgsType)]
}
```

The result types above are simplified. Each overload takes an implicit `Reducable` that removes `Unit` from the value: a command without options produces only the arguments' value, a command without arguments produces only the options' value, and a command with both produces the tuple `(options, args)`. `Options.none` and `Args.none` stand for "no options" and "no arguments", so `Command("git", Options.none, Args.none)` is the same as `Command("git")`.

The value type of each command follows from its options and arguments:

```scala
import zio.cli._
import java.nio.file.Path

val statusCmd: Command[Unit]             = Command("status")
val commitCmd: Command[Boolean]          = Command("commit", Options.boolean("quiet"))
val addCmd: Command[Path]                = Command("add", Args.file("path"))
val cloneCmd: Command[(Boolean, String)] = Command("clone", Options.boolean("quiet"), Args.text("repository"))
```

The name is the word the user types. `CliApp` adds the name of the top-level command for you, so users of an app built from `Command("git")` type only what follows `git`.

## Transforming the Value

A command's raw value is often a tuple or `Unit`. These methods turn it into a type that fits your program:

```scala
trait Command[+A] {
  def map[B](f: A => B): Command[B]
  def as[B](b: => B): Command[B]
}
```

### Command#map

`Command#map` applies a function to the parsed value:

```scala
case class Clone(quiet: Boolean, repository: String)

val cloneCommand: Command[Clone] = cloneCmd.map { case (quiet, repository) => Clone(quiet, repository) }
```

### Command#as

`Command#as` replaces the value with a constant, which suits commands that carry no data:

```scala
case object Status

val statusCommand: Command[Status.type] = statusCmd.as(Status)
```

To give every subcommand of an app one shared type, see [How to turn subcommands into one domain type](how-to-map-subcommands.md).

## Adding a Description

`Command#withHelp` sets the command's description:

```scala
trait Command[+A] {
  def withHelp(help: String): Command[A]
  def withHelp(help: HelpDoc): Command[A]
}
```

A `String` is wrapped in a paragraph (`HelpDoc.p`). The description replaces any earlier one. It appears under `DESCRIPTION` in the command's help, and next to the command in its parent's `COMMANDS` list. On a command built with `Command#|`, `Command#withHelp` sets the same description on every alternative. On a command built with `Command#subcommands`, it sets the description of the parent.

The help of a command with a description, an option and an argument looks like this:

```scala
val documentedClone: Command[(Boolean, String)] =
  Command("clone", Options.boolean("quiet").alias("q"), Args.text("repository"))
    .withHelp("Copies a repository.")
```

`Command#helpDoc` returns this help, which we render as plain text:

```scala
println(documentedClone.helpDoc.toPlaintext(color = false))
// 
// DESCRIPTION
// 
//   Copies a repository.
// 
// ARGUMENTS
// 
//   <repository>
//     A user-defined piece of text.
// 
// OPTIONS
// 
//   (-q, --quiet)
//     A true or false value.
// 
//     This setting is optional. Default: 'false'.
// 
//
```

See [Help Documentation](helpdoc.md) for building richer `HelpDoc` values.

## Choosing Between Commands

These methods combine two commands so that the user can type either one:

```scala
trait Command[+A] {
  def |[A1 >: A](that: Command[A1]): Command[A1]
  def orElse[A1 >: A](that: Command[A1]): Command[A1]
  def orElseEither[B](that: Command[B]): Command[Either[A, B]]
}
```

The parser tries the left command first. When the first word is not the left command's name, it tries the right one. If neither name matches, the input is rejected.

### Command#| and Command#orElse

`Command#orElse` is the same as `Command#|`. The value type is the common supertype of both commands:

```scala
val addOrStatus: Command[Any] = addCmd | statusCmd
```

### Command#orElseEither

`Command#orElseEither` keeps track of which command matched by wrapping the value in an `Either`:

```scala
val addOrClone: Command[Either[Path, (Boolean, String)]] = addCmd.orElseEither(cloneCmd)
```

When a top-level command is built with `Command#|`, `CliApp` does not add any name before the input: the user types `add` or `status` first. Most apps instead put the alternatives under a parent command with `Command#subcommands`.

## Subcommands

`Command#subcommands` adds child commands to a parent:

```scala
trait Command[+A] {
  def subcommands[B](that: Command[B])(implicit ev: Reducable[A, B]): Command[ev.Out]
  def subcommands[B](c1: Command[B], c2: Command[B], cs: Command[B]*)(implicit ev: Reducable[A, B]): Command[ev.Out]
}
```

The second overload joins its arguments with `Command#|`, so the user picks exactly one child. The value combines the parent's value and the child's value with the same `Reducable` rule as `Command.apply`. When the parent has no options or arguments (a `Command[Unit]`), the value is the child's value. Otherwise it is the tuple `(parentValue, childValue)`.

A parent without options gives the child's value directly:

```scala
val git: Command[Any] =
  Command("git")
    .withHelp("A tiny version control tool.")
    .subcommands(Command("add", Args.file("path")).withHelp("Stages a file."), documentedClone)
```

A parent with options gives a tuple, and the parent's options go before the subcommand name, as in `git --verbose add file.txt`:

```scala
val verboseGit: Command[(Boolean, Any)] =
  Command("git", Options.boolean("verbose")).subcommands(addCmd, cloneCmd)
```

A child can have subcommands of its own, which gives nested commands such as `git remote add`. The parent's help lists every subcommand with its usage and description:

```scala
println(git.helpDoc.toPlaintext(color = false))
// 
// DESCRIPTION
// 
//   A tiny version control tool.
// 
// COMMANDS
// 
//   - add <path>                          Stages a file.
//   - clone [(-q, --quiet)] <repository>  Copies a repository.
//
```

Parsing a command with subcommands follows these rules:

- When the input names no subcommand, the result is the parent's help, as if the user had typed `--help`.
- When the first word after the parent is not a subcommand, the input is rejected with `Invalid subcommand for git. Use one of 'add', 'clone'`.
- When the input continues after a complete subcommand, the extra words are ignored.

`--help` after a subcommand other than the first shows the first subcommand's help; see [Built-in Commands](built-in-commands.md#help).

## Inspecting a Command

These members describe a command without running it. `CliApp` uses them to print help and to parse input, and tests use them to check a command:

```scala
trait Command[+A] {
  def helpDoc: HelpDoc
  def synopsis: UsageSynopsis
  def names: Set[String]
  def getSubcommands: Map[String, Command[_]]
  def parse(args: List[String], conf: CliConfig): IO[ValidationError, CommandDirective[A]]
}
```

`Command#helpDoc` holds the sections that `--help` prints below the usage line: `DESCRIPTION`, `ARGUMENTS`, `OPTIONS` and, for a parent, `COMMANDS`. `Command#synopsis` is the usage line, described in [The Usage Line](cli-app.md#the-usage-line). `Command#names` returns the name of the command (for a command built with `Command#|`, the names of both alternatives). `Command#getSubcommands` maps each subcommand name to its command.

### Command#parse

`Command#parse` parses a list of words. Unlike `CliApp#run`, the list must start with the command's own name. The result is a `CommandDirective`:

```scala
sealed trait CommandDirective[+A] {
  def map[B](f: A => B): CommandDirective[B]
  def mapBuiltIn(f: BuiltInOption => BuiltInOption): CommandDirective[A]
}
object CommandDirective {
  final case class BuiltIn(option: BuiltInOption)                   extends CommandDirective[Nothing]
  final case class UserDefined[A](leftover: List[String], value: A) extends CommandDirective[A]

  def builtIn(option: BuiltInOption): CommandDirective[Nothing]
  def userDefined[A](leftover: List[String], value: A): CommandDirective[A]
}
```

`UserDefined` carries the parsed value and any words the parser does not use. `BuiltIn` means the input asks for a [built-in command](built-in-commands.md) such as `--help`. A failure is a [`ValidationError`](validation-and-errors.md). `CommandDirective#map` changes the value of a `UserDefined` and leaves a `BuiltIn` unchanged, `CommandDirective#mapBuiltIn` changes the option of a `BuiltIn` and leaves a `UserDefined` unchanged, and `CommandDirective.builtIn` and `CommandDirective.userDefined` build the two cases with the wider type `CommandDirective`.

To run the parser outside a ZIO program, we use a small helper:

```scala
import zio._

def parse[A](command: Command[A], args: String*): CommandDirective[A] =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(command.parse(args.toList, CliConfig.default)).getOrThrowFiberFailure()
  }
```

With the helper, we can parse a `clone` command line and an `add` command line:

```scala
parse(git, "git", "clone", "-q", "https://github.com/zio/zio-cli")
// res3: CommandDirective[Any] = UserDefined(
//   leftover = List(),
//   value = (true, "https://github.com/zio/zio-cli")
// )
parse(verboseGit, "git", "--verbose", "add", "build.sbt")
// res4: CommandDirective[(Boolean, Any)] = UserDefined(
//   leftover = List(),
//   value = (true, build.sbt)
// )
```

See [Testing CLI Applications](testing.md) for assertions built on `Command#parse`.

### The -- Separator

A `--` word ends the options. Every word after it goes to the arguments, even a word that looks like an option:

```scala
val echo: Command[(Boolean, List[String])] = Command("echo", Options.boolean("n"), Args.text("word").*)
```

Parsing the words after the separator shows that `-n` stays an argument:

```scala
parse(echo, "echo", "--", "-n", "hello")
// res5: CommandDirective[(Boolean, List[String])] = UserDefined(
//   leftover = List(),
//   value = (false, List("-n", "hello"))
// )
```

## How Commands Are Represented

`Command` is a sealed trait. Its cases are `Command.Single` (a name, a description, options and arguments), `Command.Map` (the result of `Command#map`), `Command.OrElse` (the result of `Command#|`) and `Command.Subcommands` (the result of `Command#subcommands`). They are public, but you build commands with `Command.apply` and the methods above instead of constructing the cases yourself.
