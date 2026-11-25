# Tutorial: Build a Git-Like CLI

> Welcome! This tutorial builds a small command-line program that works like a few parts of `git`. You run it from a terminal, the way you run `git add` or `git remote add`. The program only prints what it would do. It does not change any files.

A **command-line interface (CLI)** is a program that reads its input from the words you type after its name. **ZIO CLI** is a Scala library that reads those words for you, checks them, and hands your code clean values. You describe the shape of the input, and **ZIO CLI** does the parsing.

The tutorial is for Scala developers who know sbt and have not used **ZIO CLI** before. If you have not done so yet, read [Getting Started](getting-started.md) first: it shows the smallest possible app.

## Learning Objectives

By the end of this tutorial, you will know how to:

- Describe options, arguments, commands and nested subcommands, and turn their parsed values into your own types
- Run the program and pass it a command line, knowing which words **ZIO CLI** adds for you
- Read an option that takes a value, such as `commit -m <message>`
- Add help text and read the help that **ZIO CLI** generates
- Generate a bash completion script for the program
- Test the command-line parsing with the **ZIO CLI** testkit

The tutorial uses four building blocks:

- An **option** is a named input, such as `-m`. A flag option is either present or absent; other options carry a value.
- An **argument** is a value given by its position, such as the directory `./src`.
- A **command** is a named action, such as `add`. It holds options and arguments.
- A **subcommand** is a command that sits under another command, such as `remote` under `git`.

You need an sbt project with the `zio-cli` dependency, as shown in [Installation](index.md#installation). Each step below adds one idea to the same program. We recommend reading from top to bottom, because each step builds on the previous one. The complete program is at the end of the page.

## Step 1: Define One Option and One Argument

An option and an argument are the two kinds of input your program reads. Each one has a Scala type that describes the value it produces. Let's define one of each:

```scala
import zio.cli._

import java.nio.file.{Path => JPath}

val modified: Options[Boolean] = Options.boolean("m")

val directory: Args[JPath] = Args.directory("directory")
```

The two values work like this:

- `Options.boolean("m")` reads the `-m` flag. It produces a `Boolean`: `true` when `-m` is present and `false` when it is not.
- `Args.directory("directory")` reads one positional value that names a directory. It produces a `java.nio.file.Path`. By default the directory does not have to exist.

The name you pass in (`"m"`, `"directory"`) is what appears on the command line and in help text. It is not the Scala variable name.

## Step 2: Build a Command

A command gives a name to a set of options and arguments. `Command("add", modified, directory)` means: the word `add`, then optionally `-m`, then one directory:

```scala
import zio.cli._

import java.nio.file.{Path => JPath}

val modified: Options[Boolean] = Options.boolean("m")
val directory: Args[JPath]     = Args.directory("directory")

val add: Command[(Boolean, JPath)] =
  Command("add", modified, directory).withHelp("Stage the files in a directory")
```

The type of the result is `Command[(Boolean, JPath)]`. **ZIO CLI** puts the option values and the argument values in a tuple, in the order you listed them. `Command#withHelp` attaches the description that appears in the help output.

## Step 3: Add Subcommands

A program usually has more than one action. The usual pattern is a root command, here `git`, with subcommands under it. Each subcommand is one action.

Two ideas are new in this step. `Command#map` changes a command's result type: the tuple from Step 2 becomes a value of a type we define, so the program can use a single `match` to decide what to do. `Command#subcommands` attaches child commands to a parent: `Command("git").subcommands(add, remote)` accepts `git add ...` and `git remote ...`:

```scala
import zio.cli._

import java.nio.file.{Path => JPath}

sealed trait Subcommand extends Product with Serializable
object Subcommand {
  case class Add(modified: Boolean, directory: JPath) extends Subcommand
  case object Remote                                  extends Subcommand
}

val modified: Options[Boolean] = Options.boolean("m")
val directory: Args[JPath]     = Args.directory("directory")

val add: Command[Subcommand] =
  Command("add", modified, directory)
    .withHelp("Stage the files in a directory")
    .map { case (modified, directory) => Subcommand.Add(modified, directory) }

val remote: Command[Subcommand] =
  Command("remote").withHelp("Manage the set of remotes").as(Subcommand.Remote)

val git: Command[Subcommand] =
  Command("git").subcommands(add, remote)
```

`Subcommand` is a `sealed trait`. The compiler then knows every possible case, so a `match` on it can be checked for completeness. `Command#as` replaces the result of `remote`, which has no options or arguments, with the fixed value `Subcommand.Remote`.

## Step 4: Add a Nested Subcommand

A subcommand can have its own subcommands. `git remote add <name> <url>` has three levels: the program `git`, the subcommand `remote`, and the sub-subcommand `add`.

In this step, `remote` stops being a plain command and becomes a parent with the children `add` and `remove`. The two children need their own result type, so we put them in a nested `Remote` group inside `Subcommand`:

```scala
import zio.cli._

import java.nio.file.{Path => JPath}

sealed trait Subcommand extends Product with Serializable
object Subcommand {
  case class Add(modified: Boolean, directory: JPath) extends Subcommand

  sealed trait Remote extends Subcommand
  object Remote {
    case class Add(name: String, url: String) extends Remote
    case class Remove(name: String)           extends Remote
  }
}

val modified: Options[Boolean] = Options.boolean("m")
val directory: Args[JPath]     = Args.directory("directory")

val add: Command[Subcommand] =
  Command("add", modified, directory)
    .withHelp("Stage the files in a directory")
    .map { case (modified, directory) => Subcommand.Add(modified, directory) }

val remoteAdd: Command[Subcommand.Remote] =
  Command("add", Args.text("name") ++ Args.text("url"))
    .withHelp("Add a remote with a name and a URL")
    .map { case (name, url) => Subcommand.Remote.Add(name, url) }

val remoteRemove: Command[Subcommand.Remote] =
  Command("remove", Args.text("name"))
    .withHelp("Remove the remote with this name")
    .map(name => Subcommand.Remote.Remove(name))

val remote: Command[Subcommand] =
  Command("remote")
    .withHelp("Manage the set of remotes")
    .subcommands(remoteAdd, remoteRemove)

val git: Command[Subcommand] =
  Command("git").subcommands(add, remote)
```

Two new constructors appear here. `Args.text` reads one text value, and `Args.text("name") ++ Args.text("url")` reads two text values in order and produces them as a tuple. The parts fit together like this:

```text
git
├── add [-m] <directory>
└── remote
    ├── add <name> <url>
    └── remove <name>
```

## Step 5: Run the App

The program needs one more piece: a function that runs for each parsed command. `CliApp.make` takes a name, a version, a one-line summary and the root command, plus that function:

```scala
import zio.Console.printLine
import zio.cli.HelpDoc.Span.text

val cliApp = CliApp.make(
  name = "git",
  version = "0.9.2",
  summary = text("a client for the git dvcs protocol"),
  command = git
) {
  case Subcommand.Add(modified, directory) =>
    printLine(s"Executing `git add $directory` with modified flag set to $modified")
  case Subcommand.Remote.Add(name, url) =>
    printLine(s"Executing `git remote add $name $url`")
  case Subcommand.Remote.Remove(name) =>
    printLine(s"Executing `git remote remove $name`")
}
```

Create `src/main/scala/GitTutorial.scala`. Put the imports from Step 4 and from this step at the top of the file. Put the definitions from Step 4 and this `cliApp` inside an `object GitTutorial extends ZIOCliDefault`, with `Subcommand` and its cases inside the object too. `ZIOCliDefault` turns the object into a runnable program that hands the words from the command line to `cliApp`. Step 6 shows the finished file.

The text after `run` is what your program receives. Notice that it does not start with `git`. When you start an installed program as `git add ./src`, the operating system passes only `add ./src` to it, so `CliApp` adds the root command name `git` in front before it parses the words. You type only what comes after the program name:

```shell
sbt "run add -m ./src"
```

The program prints:

```text
Executing `git add ./src` with modified flag set to true
```

Without `-m`, the flag is `false`:

```shell
sbt "run add ./src"
```

The program prints:

```text
Executing `git add ./src` with modified flag set to false
```

The nested subcommands work the same way:

```shell
sbt "run remote add origin https://example.com/repo.git"
```

The program prints:

```text
Executing `git remote add origin https://example.com/repo.git`
```

If you type `git` yourself, as in `sbt "run git add ./src"`, the program sees `git git add ./src` and stops with an "Invalid subcommand for git" error.

Try one more line, with the flag after the directory:

```shell
sbt "run add ./src -m"
```

The program prints:

```text
Executing `git add ./src` with modified flag set to false
```

The flag is `false`, and there is no error. **ZIO CLI** reads a command's options before its arguments, and stops reading options at the first word that is not one. Once `./src` has been read as the argument, `-m` is a leftover word, and `CliApp` ignores leftover words. Always put options before arguments. [How ZIO CLI reads a command line](parsing.md) explains these rules in detail.

## Step 6: Read an Option That Takes a Value

The `-m` flag of `add` is either present or absent. Many options carry a value instead, like the message of `git commit -m "Fix the typo"`. Let's add a `commit` subcommand with a `--message` option, short name `-m`.

`Options.text("message")` reads the word after `--message` as a `String`. `Options#alias` gives the option a second name, `-m`. An option without a default is required: `commit` fails when the message is missing. Here is the whole program so far, as it goes into `GitTutorial.scala`, with the new `Commit` case, the `message` option, the `commit` command and its case in `cliApp`:

```scala
import zio.Console.printLine
import zio.cli.HelpDoc.Span.text
import zio.cli._

import java.nio.file.{Path => JPath}

object GitTutorial extends ZIOCliDefault {

  sealed trait Subcommand extends Product with Serializable
  object Subcommand {
    case class Add(modified: Boolean, directory: JPath) extends Subcommand
    case class Commit(message: String)                  extends Subcommand

    sealed trait Remote extends Subcommand
    object Remote {
      case class Add(name: String, url: String) extends Remote
      case class Remove(name: String)           extends Remote
    }
  }

  val modified: Options[Boolean] = Options.boolean("m")
  val directory: Args[JPath]     = Args.directory("directory")
  val message: Options[String]   = Options.text("message").alias("m")

  val add: Command[Subcommand] =
    Command("add", modified, directory)
      .withHelp("Stage the files in a directory")
      .map { case (modified, directory) => Subcommand.Add(modified, directory) }

  val commit: Command[Subcommand] =
    Command("commit", message)
      .withHelp("Record the staged files with a message")
      .map(message => Subcommand.Commit(message))

  val remoteAdd: Command[Subcommand.Remote] =
    Command("add", Args.text("name") ++ Args.text("url"))
      .withHelp("Add a remote with a name and a URL")
      .map { case (name, url) => Subcommand.Remote.Add(name, url) }

  val remoteRemove: Command[Subcommand.Remote] =
    Command("remove", Args.text("name"))
      .withHelp("Remove the remote with this name")
      .map(name => Subcommand.Remote.Remove(name))

  val remote: Command[Subcommand] =
    Command("remote")
      .withHelp("Manage the set of remotes")
      .subcommands(remoteAdd, remoteRemove)

  val git: Command[Subcommand] =
    Command("git").subcommands(add, commit, remote)

  val cliApp = CliApp.make(
    name = "git",
    version = "0.9.2",
    summary = text("a client for the git dvcs protocol"),
    command = git
  ) {
    case Subcommand.Add(modified, directory) =>
      printLine(s"Executing `git add $directory` with modified flag set to $modified")
    case Subcommand.Commit(message) =>
      printLine(s"Executing `git commit` with message: $message")
    case Subcommand.Remote.Add(name, url) =>
      printLine(s"Executing `git remote add $name $url`")
    case Subcommand.Remote.Remove(name) =>
      printLine(s"Executing `git remote remove $name`")
  }
}
```

The changes from Step 5 are:

- `Subcommand.Commit(message: String)` is the new case for the parsed `commit` line.
- `Options.text("message").alias("m")` defines the option. Its type is `Options[String]`, because it carries a text value.
- `Command("commit", message)` takes only options, so its value is the message itself, and `Command#map` wraps it in `Subcommand.Commit`.
- `Command("git").subcommands(add, commit, remote)` adds `commit` to the root command.
- `cliApp` has a new case that prints the message.

Run `commit` with a message. The quotes keep the message together as one word:

```shell
sbt 'run commit -m "Fix the typo"'
```

The program prints:

```text
Executing `git commit` with message: Fix the typo
```

The long name works too, and so does the `--message=<text>` form. Without a message, parsing fails and the program prints the error:

```shell
sbt "run commit"
```

The program prints the error and exits with a failure code:

```text
Expected to find --message option.
```

## Step 7: Add Help Text

**ZIO CLI** generates help from the commands, options and arguments you define. `Command#withHelp` already gives each command a description. Options and arguments get one with the `??` operator, which adds a sentence to the help of an option or argument. In `GitTutorial`, we give each option and argument a description, and we pull the two remote arguments into vals so both remote commands share them:

```scala
val modified: Options[Boolean] = Options.boolean("m") ?? "Stage only the files that were modified."
val directory: Args[JPath]     = Args.directory("directory") ?? "The directory to stage."
val message: Options[String]   = Options.text("message").alias("m") ?? "The commit message."
val remoteName: Args[String]   = Args.text("name") ?? "The name of the remote."
val remoteUrl: Args[String]    = Args.text("url") ?? "The URL of the remote."
```

The remote commands then use the new vals: `Command("add", remoteName ++ remoteUrl)` and `Command("remove", remoteName)`. The complete program at the end of the page has every change.

Every app understands the built-in `--help` flag. Ask for the help of the whole program:

```shell
sbt "run --help"
```

The output starts with the app name drawn as a banner, then the name, version and summary, a usage line, and one line per subcommand with its description:

```text
          _ __ 
   ____ _(_) /_
  / __ `/ / __/
 / /_/ / / /_  
 \__, /_/\__/  
/____/         

git 0.9.2 -- a client for the git dvcs protocol

USAGE

  $ git <command>

COMMANDS

  - add [-m] <directory>           Stage the files in a directory
  - commit (-m, --message <text>)  Record the staged files with a message
  - remote                         Manage the set of remotes
  - remote add <name> <url>        Add a remote with a name and a URL
  - remote remove <name>           Remove the remote with this name
```

Notice the notation in the usage lines: `[-m]` is optional, `(-m, --message <text>)` lists two names of one required option that takes a text value, and `<directory>` is an argument. Running the program with no words at all prints the same help.

`--help` after a subcommand name shows the help of that subcommand:

```shell
sbt "run add --help"
```

The `??` texts appear under the arguments and options, below a sentence that **ZIO CLI** writes for each type:

```text
          _ __ 
   ____ _(_) /_
  / __ `/ / __/
 / /_/ / / /_  
 \__, /_/\__/  
/____/         

git 0.9.2 -- a client for the git dvcs protocol

USAGE

  $ git add [-m] <directory>

DESCRIPTION

  Stage the files in a directory

ARGUMENTS

  <directory>
    A directory.

    The directory to stage.

OPTIONS

  -m
    A true or false value.

    Stage only the files that were modified.

    This setting is optional. Default: 'false'.
```

:::caution
`--help` after any subcommand other than the first one in `subcommands(add, commit, remote)` shows the help of the first one, `add`. Running `sbt "run commit --help"` prints the help above. Use `sbt "run --help"` to see the usage line and description of every subcommand. See [Built-in Commands](built-in-commands.md#help) for the rule.
:::

[Help Documentation](helpdoc.md) covers `HelpDoc`, the type behind help text, and [Built-in Commands](built-in-commands.md) lists every built-in flag.

## Step 8: Generate a Shell Completion Script

Shell completion lets a user press Tab to complete subcommand and option names. Every **ZIO CLI** app can print a bash script that sets this up. The built-in `--shell-completion-script` flag takes the path of the installed program, and `--shell-type bash` picks the shell:

```shell
sbt "run --shell-completion-script /usr/local/bin/git --shell-type bash"
```

The program prints the script:

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

The script defines a bash function, `_git`, and its last line registers that function for the word `git`, the name of the root command. On each Tab press, the function calls the program at the path given to `--shell-completion-script`, with the built-in `--shell-completion-index` flag, and the program prints the possible completions.

Because our root command is called `git`, loading this script would replace the completion of the real `git` in that shell, so in this tutorial we only generate it and read it. To install completion for a program of your own, follow [How to add Tab completion in bash](how-to-add-shell-completion.md).

## Step 9: Test the Parsing

The testkit module, `zio-cli-testkit`, checks a command without a terminal. Add it to your build as a test dependency, as listed in [Modules](index.md#modules), together with ZIO Test. sbt 1.5 and later run ZIO Test specs without extra setup; on older sbt versions, add `testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")` to `build.sbt`.

`CliAssertion.assertCommand` parses each command line in a ZIO Test generator and checks that it produces the expected value. Each `CliRepr` pairs a command line with that value. Like `Command#parse`, it expects the full line, so each line starts with the root command name `git`, which `CliApp` adds when the program runs. `CliAssertion.assertCommandSuccess` takes an assertion on the result instead, which lets us check that a line fails. The spec goes into `src/test/scala/GitTutorialSpec.scala`:

```scala
import zio.cli.testkit.CliAssertion._
import zio.cli.testkit.CliRepr
import zio.test._

import java.nio.file.Paths

import GitTutorial._

object GitTutorialSpec extends ZIOSpecDefault {
  def spec = suite("git")(
    test("parses each subcommand") {
      assertCommand(
        git,
        Gen.fromIterable(
          List(
            CliRepr(List("git", "add", "-m", "src"), Subcommand.Add(true, Paths.get("src"))),
            CliRepr(List("git", "commit", "-m", "Fix the typo"), Subcommand.Commit("Fix the typo")),
            CliRepr(List("git", "remote", "remove", "origin"), Subcommand.Remote.Remove("origin"))
          )
        )
      )
    },
    test("commit without a message fails") {
      assertCommandSuccess(git, Gen.const(CliRepr(List("git", "commit"), Assertion.isLeft)))
    }
  )
}
```

The spec works like this:

- `import zio.cli.testkit.CliAssertion._` brings in the assertions and the `CliConfig` they use.
- `import GitTutorial._` gives access to `git` and `Subcommand`.
- `assertCommand(git, ...)` parses every line in the generator and compares the result with the expected `Subcommand` value.
- `assertCommandSuccess(git, ...)` runs `Assertion.isLeft` on the parse result, so the test passes only when `git commit` without a message is an error.

Run the spec with `sbt test`. Both tests pass. [Testing](testing.md) lists every testkit assertion, and [How to test a CLI app](how-to-test-a-cli.md) also tests help output and errors from the app's own code.

## What You've Learned

In this tutorial, you learned how to:

- Describe options, arguments, commands and nested subcommands, and map their values into a `sealed trait`
- Run the program, typing only the words after the program name, with options before arguments
- Read an option that takes a value with `Options.text` and give it a short name with `Options#alias`
- Describe options and arguments with `??` and read the help that `--help` prints
- Generate a bash completion script with `--shell-completion-script`
- Test the parsing with `CliAssertion.assertCommand` and `CliAssertion.assertCommandSuccess` from the testkit

## Where to Go Next

Each of these pages covers one part of the library in more detail:

- [How ZIO CLI reads a command line](parsing.md) explains the parsing rules used in this tutorial.
- [Commands](commands.md) covers command trees, `Command#map` and `Command#subcommands` in full.
- [Options](options.md) lists the option types, aliases and boolean negation.
- [Args](args.md) lists the argument types and how to repeat them.
- [Help Documentation](helpdoc.md) explains how to write help text with `HelpDoc`.
- [How to add Tab completion in bash](how-to-add-shell-completion.md) installs the completion script for a real program.
- [How to test a CLI app](how-to-test-a-cli.md) tests a whole app, including its output.

Some small changes to try on this program:

1. Give `-m` of `add` a long name with `Options.boolean("modified").alias("m")`, so `--modified` works too.
2. Add a `remote list` subcommand that prints a line such as "Executing `git remote list`": a `Command("list")` under `remote`, a new case in the `Subcommand.Remote` group and a new case in `cliApp`.
3. Add a `--depth` option to a new `clone` subcommand with `Options.integer("depth").optional`, and compare the help output.

## Complete Program

This is the whole program in one file, `src/main/scala/GitTutorial.scala`. It is the program from Step 6 with the help texts from Step 7:

```scala
import zio.Console.printLine
import zio.cli.HelpDoc.Span.text
import zio.cli._

import java.nio.file.{Path => JPath}

object GitTutorial extends ZIOCliDefault {

  sealed trait Subcommand extends Product with Serializable
  object Subcommand {
    case class Add(modified: Boolean, directory: JPath) extends Subcommand
    case class Commit(message: String)                  extends Subcommand

    sealed trait Remote extends Subcommand
    object Remote {
      case class Add(name: String, url: String) extends Remote
      case class Remove(name: String)           extends Remote
    }
  }

  val modified: Options[Boolean] = Options.boolean("m") ?? "Stage only the files that were modified."
  val directory: Args[JPath]     = Args.directory("directory") ?? "The directory to stage."
  val message: Options[String]   = Options.text("message").alias("m") ?? "The commit message."
  val remoteName: Args[String]   = Args.text("name") ?? "The name of the remote."
  val remoteUrl: Args[String]    = Args.text("url") ?? "The URL of the remote."

  val add: Command[Subcommand] =
    Command("add", modified, directory)
      .withHelp("Stage the files in a directory")
      .map { case (modified, directory) => Subcommand.Add(modified, directory) }

  val commit: Command[Subcommand] =
    Command("commit", message)
      .withHelp("Record the staged files with a message")
      .map(message => Subcommand.Commit(message))

  val remoteAdd: Command[Subcommand.Remote] =
    Command("add", remoteName ++ remoteUrl)
      .withHelp("Add a remote with a name and a URL")
      .map { case (name, url) => Subcommand.Remote.Add(name, url) }

  val remoteRemove: Command[Subcommand.Remote] =
    Command("remove", remoteName)
      .withHelp("Remove the remote with this name")
      .map(name => Subcommand.Remote.Remove(name))

  val remote: Command[Subcommand] =
    Command("remote")
      .withHelp("Manage the set of remotes")
      .subcommands(remoteAdd, remoteRemove)

  val git: Command[Subcommand] =
    Command("git").subcommands(add, commit, remote)

  val cliApp = CliApp.make(
    name = "git",
    version = "0.9.2",
    summary = text("a client for the git dvcs protocol"),
    command = git
  ) {
    case Subcommand.Add(modified, directory) =>
      printLine(s"Executing `git add $directory` with modified flag set to $modified")
    case Subcommand.Commit(message) =>
      printLine(s"Executing `git commit` with message: $message")
    case Subcommand.Remote.Add(name, url) =>
      printLine(s"Executing `git remote add $name $url`")
    case Subcommand.Remote.Remove(name) =>
      printLine(s"Executing `git remote remove $name`")
  }
}
```
