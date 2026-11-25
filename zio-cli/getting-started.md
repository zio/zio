# Getting Started

> This page builds a small command-line program with **ZIO CLI** from an empty project. It assumes you know Scala and sbt, but have not used ZIO CLI before. At the end you will have one command, one option and one argument, and you will see the help text that **ZIO CLI** generates from them.

The steps are:

1. Add the library to your build.
2. Write an app that describes one command.
3. Run the app and read its help output.

## 1. Add the Dependency

Add `zio-cli` to `build.sbt` as shown in [Installation](index.md#installation). This page uses the JVM and `sbt run`.

## 2. Write the App

Create a file such as `src/main/scala/Greet.scala`. The app is an `object` that extends `ZIOCliDefault`. That trait makes the object a runnable program, and it asks you to define one value, `cliApp`.

Building a CLI app takes three pieces:

- An **option**, a named parameter such as `--loud`. See [Options](options.md).
- An **argument**, a position-based parameter such as the name in `greet Ada`. See [Args](args.md).
- A **command**, the named instruction that owns the option and the argument. See [Commands](commands.md).

The complete app defines all three pieces and ties them to the code that prints the greeting:

```scala
import zio.Console.printLine
import zio.cli._
import zio.cli.HelpDoc.Span.text

object Greet extends ZIOCliDefault {

  // An option: "--loud" or "-l". It is a boolean, so it is true only when the flag is present.
  val loud: Options[Boolean] = Options.boolean("loud").alias("l")

  // An argument: a text value found by its position, after the options.
  val who: Args[String] = Args.text("name")

  // The command "greet" takes the option and the argument, and carries a help text.
  val greet: Command[(Boolean, String)] =
    Command("greet", loud, who).withHelp("Prints a greeting for the given name")

  // CliApp.make ties the command to the code that runs after the user input is parsed.
  // The function receives the parsed values as a tuple: (loud, who).
  val cliApp = CliApp.make(
    name = "greet",
    version = "0.1.0",
    summary = text("Prints a greeting"),
    command = greet
  ) { case (isLoud, name) =>
    val message = s"Hello, $name!"
    printLine(if (isLoud) message.toUpperCase else message)
  }
}
```

Some details in this code:

- `Options.boolean("loud")` creates the option. `Options#alias` adds a short name, so `-l` works too.
- `Args.text("name")` creates the argument. The `"name"` string is only a label for the help text. It is not a flag.
- `Command("greet", loud, who)` combines the option and the argument. The result has the type `Command[(Boolean, String)]`, which is the type of the value the app receives.
- `CliApp.make` takes a display name, a version, a one-line summary and the command. Its last parameter list is the function that runs the app logic. The function receives the parsed values, so you do not parse text yourself.

## 3. Run the App

From the project root, run:

```shell
sbt "run --loud Ada"
```

The program parses `--loud` as true and `Ada` as the name, then prints `HELLO, ADA!` in upper case. Notice that you do not type `greet` at the start. `CliApp` adds the command name before it parses your input, so you only type the options and arguments. Options come before arguments: ZIO CLI stops reading options at the first argument, as [How ZIO CLI reads a command line](parsing.md) explains.

Now ask for help:

```shell
sbt "run --help"
```

The help text comes from the code you wrote, so you do not write it by hand. Below a banner that spells the app name in large letters, it prints:

```text
greet 0.1.0 -- Prints a greeting

USAGE

  $ greet [(-l, --loud)] <name>

DESCRIPTION

  Prints a greeting for the given name

ARGUMENTS

  <name>
    A user-defined piece of text.

OPTIONS

  (-l, --loud)
    A true or false value.

    This setting is optional. Default: 'false'.
```

The usage line shows the option in parentheses inside brackets, because it is optional, and the argument in angle brackets. If you change `Command#withHelp`, the `summary`, or the alias, rerun `--help` to see the change. The built-in `--help` flag is covered in [Built-in Commands](built-in-commands.md).

## Where to Go Next

Each of these pages takes one part further:

- [Commands](commands.md) explains how to build several commands and nest subcommands.
- [Options](options.md) lists the option types, such as text, integer and enumeration.
- [Args](args.md) explains position-based arguments, including repeated arguments.
- [Built-in Commands](built-in-commands.md) covers `--help`, `--wizard` and shell completion, which every app gets for free.
- [Wizard Mode](wizard.md) shows how a user can build a command interactively, without knowing the options in advance.
- [How ZIO CLI reads a command line](parsing.md) explains the order in which the input is read.
- [Tutorial: Build a Git-Like CLI](examples.md) builds a larger program with subcommands, step by step.
