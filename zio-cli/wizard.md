# Wizard Mode

> Wizard mode is an interactive way to build a command. Instead of typing the full command line, the user answers questions in the terminal. The app asks which subcommand to run, then asks for each option and argument. When every answer is in, the app prints the equivalent command line and runs it.

Wizard mode helps users who do not know the options and arguments of an app, or who want a guided way to try a command. Scripts and experienced users type the full command instead.

## Starting Wizard Mode

Wizard mode is a [built-in option](built-in-commands.md), so every app has it. The user adds `--wizard` after the program name. For the app in [Getting Started](getting-started.md), that is:

```bash
sbt "run --wizard"
```

`--wizard` starts the wizard when it is the only word after the program name, or when the rest of the input is invalid. The short form `-w` works only in the second case, through the [final built-in check](cli-config.md#final-built-in-check).

`CliApp#run` returns the result of the command that the wizard runs, wrapped in `Some`, or `None` when the user quits.

## How a Session Runs

A session has three steps:

1. **Choose a subcommand.** For a command with two or more subcommands, the wizard prints a numbered list of them, each with its description, and the user types a name or a number. A chosen subcommand that has subcommands of its own leads to another list.
2. **Answer each option and argument.** For each option and argument of the chosen command, the wizard prints a short description and waits for input. An argument value that is not valid, such as `abc` for an integer, is rejected, and the question is asked again.
3. **Review and run.** The wizard prints the collected values as one command line, prints `Executing command...`, and runs the command with those values.

Before every question, the app prints a header: the banner, a line with `WIZARD of`, the name and the version, and a short explanation. Every question ends with a prompt made of the command's name followed by ` :> `.

The examples on this page use this app, with two subcommands that each take one argument:

```scala
import zio.cli._
import zio.cli.HelpDoc.Span.text
import zio.Console.printLine

val repo: Command[String] = Command("repo").subcommands(
  Command("clone", Args.text("repository")).withHelp("Copies a repository."),
  Command("add", Args.text("path")).withHelp("Stages a path.")
)

val cliApp = CliApp.make(
  name = "repo",
  version = "0.1.0",
  summary = text("Manages repositories"),
  command = repo
)(value => printLine(s"Selected: $value"))

// Starts wizard mode. It reads from the terminal, so the docs build does not run it.
val session = cliApp.run(List("--wizard"))
```

The first screen asks for the subcommand:

```text
   ________  ____  ____
  / ___/ _ \/ __ \/ __ \
 / /  /  __/ /_/ / /_/ /
/_/   \___/ .___/\____/
         /_/

WIZARD of repo0.1.0 -- Manages repositories

Wizard mode assist you in constructing commands for repo0.1.0

WIZARD

  Please, enter the command you would like to execute.

  Write help for more information about this parameter, restart to start again Wizard mode and quit
  to exit.

OPTIONS

  [1]    clone  Copies a repository.

  [2]    add    Stages a path.

repo :>
```

After the user types `1`, the wizard asks for the argument of `clone`:

```text
WIZARD

  Please, specify the following argument.

  Argument <repository>:

  Write help for more information about this parameter, restart to start again Wizard mode and quit
  to exit.

repo :>
```

After the user types `zio/zio-cli`, the wizard prints the command line and runs it:

```text
You may bypass the wizard and execute your command directly with the following options and
arguments:

  clone zio/zio-cli

Executing command...

Selected: zio/zio-cli
```

## Special Input Words

At any question, three words have a special meaning. The wizard compares the input to these words exactly: the match is case-sensitive and does not trim spaces, so `Help`, `QUIT` and ` quit` are ordinary input.

- `help` shows the full help of the current value. The question is asked again, and earlier answers are kept.
- `restart` throws away all answers and starts again from the first question.
- `quit` leaves wizard mode without running the command.

Empty input, which means pressing enter with nothing typed, is never accepted. The wizard prints `No text found.` and asks the same question again.

## Choosing by Number

When the wizard lists subcommands, the user can type either the name or the number shown next to it. A number outside the list, such as `0`, and text that is neither a name nor a number are rejected, and the question is asked again. The list follows the order in which the subcommands are declared for up to four subcommands. With five or more, the order of the list is not guaranteed, so users should check the number they type against the name next to it.

## Which Values the Wizard Asks For

The wizard asks for every option and every argument of the chosen command, in declaration order: first the options, then the arguments. Each kind of value accepts different input:

- For an option with a value, the user types the value, such as `Ada` for `--name`. The wizard accepts any non-empty text here and checks it only when the command runs, so `abc` for an integer option makes the final run fail with `abc is not a integer.`
- For a boolean flag, typing `true`, `1`, `y`, `yes` or `on` adds the flag to the command line. Any other text leaves it out.
- For an option with a default, the user must still type a value, because empty input is rejected.
- For a repeated argument, such as `Args.text("file").*`, the user types all values on one line, separated by spaces.

## The Final Command

The printed line holds the subcommand names, options and arguments in the order the wizard asked for them. It does not include the program's own name, because `CliApp#run` adds that name itself. For the app in [Getting Started](getting-started.md), the answers `yes` for `--loud` and `Ada` for the name print `--loud Ada`. To skip the wizard next time, the user passes the same values after the program name, as in `sbt "run --loud Ada"`.

:::caution
The wizard builds an invalid command line in two cases, and the run fails with `Invalid subcommand for <parent>`. The first is a parent with exactly one subcommand: the wizard does not ask for the subcommand and also leaves its name out of the line. The second is a session started after a subcommand name, as in `repo clone --wizard`: the line again lacks `clone`. Give the parent at least two subcommands, and start the wizard right after the program name.
:::

## Related Pages

- [Built-in Commands](built-in-commands.md) lists `--wizard` with `--help` and the shell completion options.
- [Commands](commands.md) explains how subcommands are built.
- [Args](args.md) and [Options](options.md) describe the values that the wizard asks for.
