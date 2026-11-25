# Built-in Commands

> Every command built with zio-cli accepts a few options that you do not declare yourself. They show the help, start the interactive wizard, and support shell completion. `CliApp#run` handles them before your `execute` function would run, so `execute` never sees them.

The built-in options are:

| Option                             | Value                  | What it does                                                                           | Result of `CliApp#run`                       |
| ---------------------------------- | ---------------------- | -------------------------------------------------------------------------------------- | -------------------------------------------- |
| `--help`, `-h`                     | none                   | Prints the help of the command.                                                        | `None`                                       |
| `--wizard`                         | none                   | Starts [wizard mode](wizard.md), which asks for each value and then runs the command.  | The result of the command, or `None` on quit |
| `-w`                               | none                   | Starts wizard mode, but only when the rest of the input is invalid.                    | Same as `--wizard`                           |
| `--shell-completion-script <path>` | path of the executable | Prints a Bash completion script that calls `<path>`. Needs `--shell-type`.             | `None`                                       |
| `--shell-completion-index <n>`     | integer                | Prints the completions for word `n` of the line being completed. Needs `--shell-type`. | `None`                                       |
| `--shell-type <type>`              | `sh`, `bash` or `zsh`  | Selects the shell for the two completion options. `sh` is the same as `bash`.          | none on its own                              |

There is no built-in `--version` option, and the help does not list the built-in options.

When `Command#parse` sees a built-in option, it returns `CommandDirective.BuiltIn` holding one of these cases:

```scala
sealed trait BuiltInOption
object BuiltInOption {
  final case class ShowHelp(synopsis: UsageSynopsis, helpDoc: HelpDoc) extends BuiltInOption
  final case class ShowCompletionScript(pathToExecutable: java.nio.file.Path, shellType: ShellType) extends BuiltInOption
  final case class ShowCompletions(index: Int, shellType: ShellType) extends BuiltInOption
  final case class ShowWizard(command: Command[_]) extends BuiltInOption
}
```

`ShellType` is a sealed trait with two cases, `ShellType.Bash` and `ShellType.ZShell`. `ShellType.option` is the `Options[ShellType]` behind `--shell-type`: it maps `sh` and `bash` to `ShellType.Bash`, and `zsh` to `ShellType.ZShell`.

## How Built-in Options Are Matched

zio-cli looks for built-in options after the name of the top-level command and after the name of each subcommand. The following rules decide which one runs:

- The built-in options must be the only words after the command name. `greet --help` shows the help, while `greet --help Ada` is parsed as a normal command, so for a command with a text argument the argument becomes `--help`.
- When several built-in options are given, `--help` wins over `--wizard`, `--wizard` wins over `--shell-completion-script`, and `--shell-completion-script` wins over `--shell-completion-index`.
- When the input does not parse but contains `--help`, `-h`, `--wizard` or `-w` anywhere, zio-cli shows the help or starts the wizard instead of printing the error. [`CliConfig#finalCheckBuiltIn`](cli-config.md#final-built-in-check) turns this fallback off.

## Help

`--help` and `-h` print a banner, a header line with the app's name, version and summary, the usage line, and the help of the command. The examples on this page use a small Git-like app:

```scala
import zio._
import zio.cli._
import zio.cli.HelpDoc.Span.text

val git: Command[Any] =
  Command("git")
    .withHelp("A tiny version control tool.")
    .subcommands(
      Command("add", Args.file("path")).withHelp("Stages a file."),
      Command("clone", Options.boolean("quiet").alias("q"), Args.text("repository")).withHelp("Copies a repository.")
    )

val gitApp: CliApp[Any, java.io.IOException, Unit] =
  CliApp.make("git", "0.1.0", text("A tiny version control tool"), git)(value => Console.printLine(s"Got $value"))

// Prints the help of the app, the same as running it with no subcommand.
val rootHelp = gitApp.run(List("--help"))

// Prints the help of the add subcommand.
val addHelp = gitApp.run(List("add", "-h"))
```

For `rootHelp`, the parent's help lists the subcommands:

```text
          _ __
   ____ _(_) /_
  / __ `/ / __/
 / /_/ / / /_
 \__, /_/\__/
/____/

git 0.1.0 -- A tiny version control tool

USAGE

  $ git <command>

DESCRIPTION

  A tiny version control tool.

COMMANDS

  - add <path>                          Stages a file.
  - clone [(-q, --quiet)] <repository>  Copies a repository.
```

For `addHelp`, the usage line starts with the parent's name, and the help shows the subcommand's own sections:

```text
          _ __
   ____ _(_) /_
  / __ `/ / __/
 / /_/ / / /_
 \__, /_/\__/
/____/

git 0.1.0 -- A tiny version control tool

USAGE

  $ git add <path>

DESCRIPTION

  Stages a file.

ARGUMENTS

  <path>
    A file.
```

:::caution
`--help` and `-h` after any subcommand except the first one show the help of the first subcommand, as described in [Subcommands](commands.md#subcommands). Point users to `git --help`, which lists every subcommand with its usage.
:::

## Wizard

`--wizard` starts an interactive session that asks for the subcommand and for each option and argument, prints the equivalent command line, and runs it. `-w` starts the same session through the fallback described above. [Wizard Mode](wizard.md) describes the session in detail.

## Shell Completion

Two built-in options support Bash completion. Both need `--shell-type`:

- `--shell-completion-script <path> --shell-type bash` prints a Bash script that registers completion for the command's names. `<path>` is the executable that the script calls on each Tab press.
- `--shell-completion-index <n> --shell-type bash` prints the possible completions of word `n`, one per line. It reads the words typed so far from the environment variables `COMP_WORD_0`, `COMP_WORD_1`, and so on. The generated script calls the program this way.

For `gitApp`, `--shell-completion-script /usr/local/bin/git --shell-type bash` prints:

```text
#/usr/bin/env bash
_git()
{
  local CMDLINE
  local IFS=$'\n'
  CMDLINE=(--shell-type bash --shell-completion-index $COMP_CWORD)
  
  INDEX=0
  for arg in ${COMP_WORDS[@]}; do
    export COMP_WORD_$INDEX=${arg}
    (( INDEX++ ))
  done

  COMPREPLY=( $(/usr/local/bin/git "${CMDLINE[@]}") )

  # Unset the environment variables.
  unset $(compgen -v | grep "^COMP_WORD_")
}

complete -F _git git
```

:::caution
`--shell-type zsh` with `--shell-completion-script` is not implemented and stops with a `NotImplementedError`; see [Bash Completion](bash-and-zsh-completion.md). With `--shell-completion-index`, the shell type is ignored, so `zsh` behaves like `bash`.
:::

[Bash Completion](bash-and-zsh-completion.md) explains how zio-cli computes completions, and [How to add Tab completion in bash](how-to-add-shell-completion.md) shows how to install the script.
