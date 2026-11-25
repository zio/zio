# Bash Completion

> Every `CliApp` supports Tab completion in bash without extra code. The application gets three built-in options that generate a completion script and answer completion requests from the shell. The design follows the Haskell library [optparse-applicative](https://github.com/pcapriotti/optparse-applicative#bash-zsh-and-fish-completions).

Only bash is supported. The `zsh` value of `--shell-type` exists, but no zsh script generator is implemented. [How to add Tab completion in bash](how-to-add-shell-completion.md) walks through installing the script for an application.

## Built-in Options

The three options are recognized by the parser of every `CliApp`, next to `--help` and `--wizard`, which [Built-in Commands](built-in-commands.md) describes. They are not listed in the help output:

| Option                      | Value                  | Purpose                                                              |
| --------------------------- | ---------------------- | -------------------------------------------------------------------- |
| `--shell-completion-script` | Path to the executable | Asks for the completion script. The path is written into the script. |
| `--shell-type`              | `bash`, `sh` or `zsh`  | Selects the shell. `sh` is another name for `bash`.                  |
| `--shell-completion-index`  | Integer                | Asks for the completions of the word at this position.               |

The options work in two combinations. `--shell-completion-script <path> --shell-type bash` prints the completion script. `--shell-type bash --shell-completion-index <n>` prints the completions for one Tab press, one per line. Any other combination is not a built-in request, so the input is parsed as a normal command line.

:::caution
`--shell-completion-script <path> --shell-type zsh` is not implemented. The program stops with a `scala.NotImplementedError` instead of printing a script. Use `--shell-type bash`.
:::

## The Completion Script

For an application whose top-level command is named `greet`, the following command line prints the script:

```bash
greet --shell-completion-script /usr/local/bin/greet --shell-type bash
```

The output is:

```bash
#/usr/bin/env bash
_greet()
{
  local CMDLINE
  local IFS=$'\n'
  CMDLINE=(--shell-type bash --shell-completion-index $COMP_CWORD)
  
  INDEX=0
  for arg in ${COMP_WORDS[@]}; do
    export COMP_WORD_$INDEX=${arg}
    (( INDEX++ ))
  done

  COMPREPLY=( $(/usr/local/bin/greet "${CMDLINE[@]}") )

  # Unset the environment variables.
  unset $(compgen -v | grep "^COMP_WORD_")
}

complete -F _greet greet
```

The script defines a function named after the first top-level command name and registers it with `complete -F` for every top-level command name. The `name` of the `CliApp` is used only when the command has no name of its own. The name of the executable file plays no part in the registration: bash calls the function only when the first word on the line is a command name. The path passed to `--shell-completion-script` is the program that the function runs, so pass the path where users run the installed executable. A path that contains spaces breaks the generated script, because the script does not quote it.

The first line of the script is a comment, not a shebang. Load the script with `source` or install it in a bash completion directory instead of running it.

## How a Completion Request Works

Each time the user presses Tab after a registered command name, the function in the script runs the executable again. It passes the words typed so far in the environment variables `COMP_WORD_0`, `COMP_WORD_1` and so on, and the position of the cursor word with `--shell-completion-index`.

When the user types `greet foo bar baz`, moves the cursor over `foo` and presses Tab, the function runs the executable with these variables and arguments:

```bash
COMP_WORD_0=greet          \
COMP_WORD_1=foo            \
COMP_WORD_2=bar            \
COMP_WORD_3=baz            \
/usr/local/bin/greet       \
--shell-type bash          \
--shell-completion-index 1
```

The application reads the `COMP_WORD_` variables, matches the words before the cursor against its `Command`, and prints every possible next word that starts with the word under the cursor. Bash reads these lines and shows them as candidates.

The `COMP_WORD_` names come from the `COMP_WORDS` array of bash's [programmable completion](https://www.gnu.org/software/bash/manual/html_node/Programmable-Completion.html). An array cannot be passed as an environment variable, so each word gets its own variable.

## What Gets Completed

Completion follows the same grammar that the parser uses. The first word is not checked, so a completion request works under any program name. The other words complete as follows:

| Position                    | Candidates                                                          |
| --------------------------- | ------------------------------------------------------------------- |
| Subcommand name             | The names of the subcommands that can follow                        |
| Option name                 | The names and aliases of options not given yet                      |
| Value of an enumeration     | The case names given to `Options.enumeration` or `Args.enumeration` |
| Value of a boolean argument | `true` and `false`                                                  |
| Value of a `ZoneId`         | Time-zone IDs that start with the typed text                        |
| Path to a file              | File and directory names, on the JVM only                           |
| Path to a directory         | Directory names, on the JVM only                                    |
| Any other value             | Nothing                                                             |

A finished word ends with a space, so the cursor moves on to the next word. A directory name ends with `/` and no space, so Tab can be pressed again to go deeper. The built-in options themselves are not offered.

Path completion runs bash's `compgen` command in a child process. Scala.js and Scala Native builds do not run it, so path values get no candidates there. A GraalVM native image is built from the JVM artifact and uses the JVM implementation.

## Testing Completions

`Completion.complete` in the `zio.cli.completion` package computes completions without a shell. It takes the words of the line, the index of the word to complete, the command and the configuration:

```scala
object Completion {
  def complete(
    words: List[String],
    index: Int,
    command: Command[Any],
    cliConfig: CliConfig,
    compgen: Compgen = Compgen.live
  ): UIO[List[String]]
}
```

We define a command with a flag, an enumeration and an integer, and a helper that runs `Completion.complete`:

```scala
import zio._
import zio.cli._
import zio.cli.completion.Completion

val files: Command[(Boolean, String, Option[BigInt])] =
  Command(
    "files",
    Options.boolean("verbose").alias("v") ++
      Options.enumeration("format")("json" -> "json", "text" -> "text") ++
      Options.integer("depth").optional
  )

def complete(words: List[String], index: Int): List[String] =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(Completion.complete(words, index, files, CliConfig.default)).getOrThrowFiberFailure()
  }
```

After the command name, every option is a candidate. After `--format`, the cases of the enumeration are. After `--depth`, an integer, nothing is:

```scala
complete(List("files", ""), 1)
// res1: List[String] = List("--depth ", "--format ", "--verbose ", "-v ")
complete(List("files", "--f"), 1)
// res2: List[String] = List("--format ")
complete(List("files", "--format", ""), 2)
// res3: List[String] = List("json ", "text ")
complete(List("files", "--depth", ""), 2)
// res4: List[String] = List()
complete(List("files", "-v", ""), 2)
// res5: List[String] = List("--depth ", "--format ")
```

`Compgen` is the service that lists file and directory names. On the JVM, `Compgen.live` runs `compgen` in the current directory, and `Compgen.test(workingDirectory)` runs it in the given directory, which keeps a test independent of where it runs. On Scala.js and Scala Native, `Compgen` returns no names.

## Platform Support

Completion of commands, options and values works on every platform. Only path completion needs the JVM:

| Feature                                  | JVM | Scala.js | Scala Native |
| ---------------------------------------- | --- | -------- | ------------ |
| Completion script and requests           | Yes | Yes      | Yes          |
| Commands, options, enumerations, ZoneIds | Yes | Yes      | Yes          |
| File and directory paths                 | Yes | No       | No           |

The sbt plugin declares `zioCliGenerateBashCompletion` and `zioCliGenerateZshCompletion` tasks, which only print `TODO: Not Implemented!`. See [sbt plugin](sbt-plugin.md). Generate the script with the built-in options instead.
