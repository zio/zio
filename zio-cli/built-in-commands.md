# Built-in commands

> **ZIO CLI** automatically constructs some common commands:
- `--help` or `-h` shows a description of the CLI's commands.
- `--wizard` executes wizard mode.
- `--shell-completion-index`, `--shell-completion-script` and `--shell-type` are used for Bash and Zsh Completion.

**ZIO CLI** automatically constructs some common commands:
- `--help` or `-h` shows a description of the CLI's commands.
- `--wizard` executes wizard mode.
- `--shell-completion-index`, `--shell-completion-script` and `--shell-type` are used for Bash and Zsh Completion.

These commands are automatically generated also for all subcommands of a command.
```scala

val cliApp = CliApp.make(
    name = "SampleCliApp",
    version = "0.0.1",
    summary = HelpDoc.Span.text("Sample CLI application"),
    command = Command("command")
) {
    case _ => ???
}

// Prints help of the cliApp command
cliApp.run(List("-h"))

// Prints help of subcommand
cliApp.run(List("subcommand", "-h"))
```

## Examples
```scala

// Construction of basic commands and HelpDoc
val helpGit = "This is command git."
val helpGitAdd = "Stages changes in the working repository."
val helpGitClone = "Creates a copy of an existing repository."

val git: Command[Unit] = Command("git").withHelp(helpGit)
val gitAdd: Command[Unit] = Command("add").withHelp(helpGitAdd)
val gitClone: Command[Path] = Command("clone", Options.directory("directory")).withHelp(helpGitClone)

// Adds subcommands add and clone to command git
val finalCommand: Command[Any] = git.subcommands(gitAdd, gitClone)

```
### Help
The output produced by command `--help` or `-h` is
```
USAGE

  $ git <command>

DESCRIPTION

  This is command git.

COMMANDS

  - add                          Stages changes in the working repository.
  - clone --directory directory  Creates a copy of an existing repository.
```
If we desire to consult the help of a subcommand, like `clone`, we would get the following.
```
USAGE

  $ git clone --directory directory

DESCRIPTION

  Creates a copy of an existing repository.

OPTIONS

  --directory directory
    A directory.
```

### Wizard
Wizard mode allows to construct a command by choosing between parameters offered by the application. The output would be the following.
```
Command(add|clone): 
```
If we input `clone`, we will get:
```
--directory (directory): 
```
