# How to turn subcommands into one domain type

> An app with subcommands produces a different raw value for each one: `Unit` for a subcommand without parameters, a `Path` for one with a file argument, a tuple for one with an option and an argument. Combined with `Command#subcommands`, these values lose their types and become `Any`. This guide shows how to give every subcommand one shared domain type, so that the function you pass to `CliApp.make` is a single, exhaustive `match`.

We build a small Git-like app with three subcommands: `add <path>`, `clone [--quiet] <repository>` and `status`.

## Define the Domain Type

Start with a sealed trait that has one case per subcommand. Each case holds the values that subcommand needs:

```scala
import zio._
import zio.cli._
import zio.cli.HelpDoc.Span.text
import java.nio.file.Path

sealed trait GitCommand
object GitCommand {
  case class Add(path: Path)                           extends GitCommand
  case class Clone(quiet: Boolean, repository: String) extends GitCommand
  case object Status                                   extends GitCommand
}
```

## Map Each Subcommand to a Case

Next, map the raw value of each subcommand to its case. Use `Command#map` when the case holds values, and `Command#as` when the subcommand carries no data. Give each command the type `Command[GitCommand]`:

```scala
val add: Command[GitCommand] =
  Command("add", Args.file("path")).withHelp("Stages a file.").map(GitCommand.Add(_))

val cloneRepo: Command[GitCommand] =
  Command("clone", Options.boolean("quiet").alias("q"), Args.text("repository"))
    .withHelp("Copies a repository.")
    .map { case (quiet, repository) => GitCommand.Clone(quiet, repository) }

val status: Command[GitCommand] =
  Command("status").withHelp("Shows the working tree status.").as(GitCommand.Status)
```

The explicit type makes the three commands share one value type. Without it, each command has the type of its own case, such as `Command[GitCommand.Add]`.

## Combine the Subcommands

Then attach the subcommands to a parent. A parent without options or arguments produces exactly the child's value, so the whole command is a `Command[GitCommand]`:

```scala
val git: Command[GitCommand] =
  Command("git").withHelp("A tiny version control tool.").subcommands(add, cloneRepo, status)
```

To check the mapping without running the app, we parse a few command lines. `Command#parse` expects the parent's name as the first word:

```scala
def parse(args: String*): CommandDirective[GitCommand] =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(git.parse(args.toList, CliConfig.default)).getOrThrowFiberFailure()
  }
```

Each command line now produces a `GitCommand` case:

```scala
parse("git", "add", "build.sbt")
// res1: CommandDirective[GitCommand] = UserDefined(
//   leftover = List(),
//   value = Add(path = build.sbt)
// )
parse("git", "clone", "-q", "https://github.com/zio/zio-cli")
// res2: CommandDirective[GitCommand] = UserDefined(
//   leftover = List(),
//   value = Clone(quiet = true, repository = "https://github.com/zio/zio-cli")
// )
parse("git", "status")
// res3: CommandDirective[GitCommand] = UserDefined(
//   leftover = List(),
//   value = Status
// )
```

## Handle Every Case in One Place

Finally, pass the command to `CliApp.make`. The function that runs the command matches on `GitCommand`, and the compiler warns if a case is missing:

```scala
val gitApp: CliApp[Any, java.io.IOException, Unit] =
  CliApp.make(
    name = "git",
    version = "0.1.0",
    summary = text("A tiny version control tool"),
    command = git
  ) {
    case GitCommand.Add(path)                 => Console.printLine(s"Staging $path")
    case GitCommand.Clone(quiet, repository) => Console.printLine(s"Cloning $repository (quiet: $quiet)")
    case GitCommand.Status                    => Console.printLine("Nothing to commit")
  }

object GitMain extends ZIOCliDefault {
  val cliApp = gitApp
}
```

## Parent Options

When the parent has options of its own, such as a global `--verbose` flag, the value becomes a tuple of the parent's value and the subcommand's value. Map the tuple to a type that holds both:

```scala
case class Invocation(verbose: Boolean, command: GitCommand)

val verboseGit: Command[Invocation] =
  Command("git", Options.boolean("verbose"))
    .subcommands(add, cloneRepo, status)
    .map { case (verbose, command) => Invocation(verbose, command) }
```

The parent's options go before the subcommand name on the command line:

```scala
Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(verboseGit.parse(List("git", "--verbose", "status"), CliConfig.default)).getOrThrowFiberFailure()
}
// res5: CommandDirective[Invocation] = UserDefined(
//   leftover = List(),
//   value = Invocation(verbose = true, command = Status)
// )
```

## Commands Combined With orElseEither

`Command#orElseEither` keeps the type of each alternative but nests the values in `Either`. With three commands, the value is an `Either[Either[A, B], C]`. Map the nested `Either` to the domain type once, right after combining:

```scala
val nested: Command[Either[Either[Path, (Boolean, String)], Unit]] =
  Command("add", Args.file("path"))
    .orElseEither(Command("clone", Options.boolean("quiet"), Args.text("repository")))
    .orElseEither(Command("status"))

val flattened: Command[GitCommand] =
  nested.map {
    case Left(Left(path))                 => GitCommand.Add(path)
    case Left(Right((quiet, repository))) => GitCommand.Clone(quiet, repository)
    case Right(())                        => GitCommand.Status
  }
```

Mapping each subcommand before combining, as in the sections above, avoids the nested `Either` altogether.

## Related Pages

- [Commands](commands.md) describes `Command#map`, `Command#as`, `Command#subcommands` and `Command#orElseEither`.
- [CliApp and Running a CLI](cli-app.md) describes `CliApp.make` and `ZIOCliDefault`.
