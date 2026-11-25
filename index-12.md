# Introduction to ZIO CLI

> Rapidly build powerful command-line applications powered by ZIO

[![Experimental](https://img.shields.io/badge/Project%20Stage-Experimental-yellowgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-cli/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-cli_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-cli_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-cli_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-cli_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-cli_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-cli_2.13) [![ZIO CLI](https://img.shields.io/github/stars/zio/zio-cli?style=social)](https://github.com/zio/zio-cli)

## Introduction

**ZIO CLI** turns a typed description of a command line into a working program. We describe the commands, options and arguments a program accepts, and ZIO CLI parses the user's input into Scala values, reports errors, and generates the help text from the same description.

Key features of ZIO CLI:

- **Typed options and arguments**: Text, numbers, booleans, dates, paths, enumerations and key-value pairs are parsed into Scala types.
- **Commands and subcommands**: Programs such as `git remote add` are built from small commands that compose.
- **Generated help**: The `--help` output, usage line and error messages come from the command description, so they never drift from the code.
- **Built-in features**: Every app gets `--help`, an interactive `--wizard` mode and bash completion without extra code.
- **Testing support**: The `zio-cli-testkit` module checks parsing and app behaviour inside ZIO Test.
- **Cross-platform**: The library runs on the JVM, Scala.js and Scala Native.

## Installation

ZIO CLI is published for Scala 2.12, 2.13 and 3. To use it on the JVM, add the following to your `build.sbt` file:

```scala
// Commands, options, arguments, generated help, wizard mode and shell completion (JVM, Scala.js, Scala Native)
libraryDependencies += "dev.zio" %% "zio-cli" % "0.8.3"
```

For Scala.js or Scala Native projects, use `%%%` instead:

```scala
// Commands, options, arguments, generated help, wizard mode and shell completion (JVM, Scala.js, Scala Native)
libraryDependencies += "dev.zio" %%% "zio-cli" % "0.8.3"
```

The [Getting Started](getting-started.md) page walks through a first program.

## Modules

Besides the core library, ZIO CLI has a module for tests and an sbt plugin.

### Testing

The testkit adds assertions and generators that check how commands parse their input and how apps behave. To use it on the JVM, add it as a test dependency:

```scala
// Assertions and generators for testing commands and apps with ZIO Test (JVM, Scala.js, Scala Native)
libraryDependencies += "dev.zio" %% "zio-cli-testkit" % "0.8.3" % Test
```

For Scala.js or Scala Native projects, use `%%%` instead:

```scala
// Assertions and generators for testing commands and apps with ZIO Test (JVM, Scala.js, Scala Native)
libraryDependencies += "dev.zio" %%% "zio-cli-testkit" % "0.8.3" % Test
```

The [Testing](testing.md) reference lists the assertions, and [How to test a CLI app](how-to-test-a-cli.md) shows them in use.

### sbt Plugin

The ZIO CLI sbt plugin is not published to Maven Central; the [sbt Plugin](sbt-plugin.md) page explains how to build it from source and what it provides.

## Example

The following program implements a small part of `git clone`, with one boolean option, one argument and help text for both:

```scala
import zio.Console.printLine
import zio.cli._
import zio.cli.HelpDoc.Span.text

object GitClone extends ZIOCliDefault {

  val local: Options[Boolean] =
    Options.boolean("local").alias("l") ?? "Clone from a repository on this machine."

  val repository: Args[String] =
    Args.text("repository") ?? "The repository to clone."

  val cloneCommand: Command[(Boolean, String)] =
    Command("clone", local, repository).withHelp("Creates a copy of an existing repository.")

  val cliApp = CliApp.make(
    name = "Sample Git",
    version = "1.1.0",
    summary = text("Sample implementation of git clone"),
    command = cloneCommand
  ) { case (isLocal, repo) =>
    printLine(s"Cloning $repo (local: $isLocal)")
  }
}
```

Running the program with `-l https://github.com/zio/zio-cli.git` prints `Cloning https://github.com/zio/zio-cli.git (local: true)`. Running it with `--help` prints a banner with the app name, followed by help text generated from the description:

```text
Sample Git 1.1.0 -- Sample implementation of git clone

USAGE

  $ clone [(-l, --local)] <repository>

DESCRIPTION

  Creates a copy of an existing repository.

ARGUMENTS

  <repository>
    A user-defined piece of text.

    The repository to clone.

OPTIONS

  (-l, --local)
    A true or false value.

    Clone from a repository on this machine.

    This setting is optional. Default: 'false'.
```
