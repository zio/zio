# How to add Tab completion in bash

> We want users of our CLI to press Tab in bash and see the subcommands, options and values they can type next. Every `CliApp` already answers completion requests, so the work is to generate a bash completion script once and install it where bash loads it.

Only bash is supported. Zsh has no script generator yet. [Bash Completion](bash-and-zsh-completion.md) describes the built-in options and what gets completed.

You need:

- A CLI built with `CliApp.make`.
- An executable of the CLI installed on the user's `PATH`.
- Bash, and for permanent installation the `bash-completion` package that most Linux distributions and Homebrew provide.

## Steps

The steps below use an application whose command is named `greet`.

### 1. Name the Command After the Executable

The completion script registers itself for the name of the top-level command, not for the file name of the executable. Give the top-level command the name users type to start the program:

```scala
import zio.Console.printLine
import zio.cli._
import zio.cli.HelpDoc.Span.text

object GreetApp extends ZIOCliDefault {
  val greet: Command[String] = Command("greet", Options.text("name"))

  val cliApp = CliApp.make(
    name = "greet",
    version = "1.0.0",
    summary = text("Greets a user"),
    command = greet
  ) { name =>
    printLine(s"Hello, $name!")
  }
}
```

If the executable must have another name, such as a short alias, add one `complete` line for that name to the script that step 3 generates. For an alias `g`, the line is `complete -F _greet g`.

### 2. Build and Install the Executable

Build the executable and install it under the name `greet` on the `PATH`. [How to package a CLI as a native binary](how-to-package-native-binary.md) covers GraalVM native images. The `installer.sh` launcher template in the ZIO CLI repository builds a native image and links it at `$XDG_DATA_HOME/<app>/bin/<app>` (`~/.local/share/<app>/bin/<app>` when `XDG_DATA_HOME` is not set and the user is not root) only when `native-image` is available. Without `native-image` it runs the downloaded JAR and creates no such file, so use it only with a native image, and add that directory to the `PATH`.

The completion script stores the path of the executable and runs it on every Tab press, so install the executable at its final location before generating the script.

### 3. Generate the Completion Script

Run the executable with `--shell-completion-script` and the path to the executable, and with `--shell-type bash`. Both options are required:

```bash
greet --shell-completion-script "$(command -v greet)" --shell-type bash > greet-completion.bash
```

The file contains a bash function named `_greet` and the line `complete -F _greet greet`. [Bash Completion](bash-and-zsh-completion.md) shows the full script.

### 4. Load the Script

To try completion in the current shell session only, load the file with `source`:

```bash
source greet-completion.bash
```

To enable completion in every new session for one user, copy the file to the user's completion directory of `bash-completion`, named after the command:

```bash
mkdir -p ~/.local/share/bash-completion/completions
cp greet-completion.bash ~/.local/share/bash-completion/completions/greet
```

To enable it for all users, install the file in the system directory instead, such as `/etc/bash_completion.d/`. When you ship the CLI as a package, generate the script during the build and let the package install it there.

### 5. Test It

Open a new bash session, type `greet ` with a trailing space, and press Tab. Bash shows `--name`, the only option of the command. [Bash Completion](bash-and-zsh-completion.md#how-a-completion-request-works) explains what the program does on each Tab press.

## Troubleshooting

When Tab shows nothing or shows file names instead of options, check these causes:

- The first word does not match the name of the top-level command. Add a `complete -F _greet <name>` line for the name you type, as described in step 1.
- The executable is no longer at the path stored in the script. Generate the script again with the new path.
- The path to the executable contains spaces. The generated script does not quote the path, so install the executable in a directory without spaces.
- A path option or argument shows no candidates in a Scala.js or Scala Native build. Path completion works only in JVM builds.
- The script request uses `--shell-type zsh`. Zsh scripts are not implemented, and the program stops with an error.
