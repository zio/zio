# Introduction to ZIO CLI

> Rapidly build powerful command-line applications powered by ZIO

Rapidly build powerful command-line applications powered by ZIO

[![Experimental](https://img.shields.io/badge/Project%20Stage-Experimental-yellowgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-cli/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-cli_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-cli_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-cli_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-cli_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-cli-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-cli-docs_2.13) [![ZIO CLI](https://img.shields.io/github/stars/zio/zio-cli?style=social)](https://github.com/zio/zio-cli)

## Installation

To use **ZIO CLI**, we need to add the following to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-cli" % "0.7.4"
```
## Getting Started
**ZIO CLI** allows to easily construct a CLI application. A CLI or Command-Line Interface is an application that allows the user to give instructions by means of pieces of text called commands. A command has the following structure
```
command param1 param2 ... paramN
```
where `command` is the name of the command and `param1`, `param2`, ..., `paramN` form a list of parameters depending on the command that determines the precise instruction to the CLI application.

Given the case, a command itself might contain subcommands. This allows a better design of the command-line application and a more comfortable user experience.

A command might include arguments and options.
- Arguments are name-less and position-based parameters that the user specifies just by the position in the command. As an example, we can consider the widely used command-line application Git. One subcommand is `clone`. It creates a copy of an existing repository. An argument of `git clone` is `repository`. If the repository name is `https://github.com/zio/zio-cli.git`, we will use it in the following manner:
```
git clone https://github.com/zio/zio-cli.git
```

- Options are named and position-independent parameters that are specified by writing the content after the name. The name is preceded by `--`. An option may have a shorter form called an alias. When the alias is used instead of the full name, only `-` is needed. An option of command `git clone` is `local`. It is a boolean option, so it is not necessary to write true or false after it: it will be true only if it appears. It is used in the following manner:
```
git clone --local
```
It also has an alias `-l`:
```
git clone -l
```

The description of the command `git clone`, taking only into account option `local` and argument `repository` will be
```
git clone [-l] <repository>
```
where `[]` implies that the option is optional and `<>` indicates an argument.

### Difference between Args and Options
Arguments and options are different due to the way the user specifies them. Arguments are not specified using its name, only by the position inside the command. On the other hand, options must be preceded by its name and `--` indicating that it is the name of an option. 

Furthermore, a command-line application will represent them in different ways. Argument's name will be inside `<>` while an option will be preceded by `--`. In case that the option has a short form or alias, this will be preceded by `-`.

### First ZIO CLI example

 This is done by defining `cliApp` value from `ZIOCliDefault` using `CliApp.make` and specifying a `Command` as parameter. A `Command[Model]` is a description of the commands of a CLI application that allows to specify which commands are valid and how to transform the input into an instance of `Model`. Then it is possible to implement the logic of the CLI application in terms of `Model`. As a sample we are going to create a command of Git. We are going to implement only command `git clone` with argument `repository` and option `local`.

```scala

// object of your app must extend ZIOCliDefault
object Sample extends ZIOCliDefault {

  /**
   * First we define the commands of the Cli. To do that we need:
   *    - Create command options
   *    - Create command arguments
   *    - Create help (HelpDoc) 
   */
  val options: Options[Boolean] = Options.boolean("local").alias("l")
  val arguments: Args[String] = Args.text("repository")
  val help: HelpDoc = HelpDoc.p("Creates a copy of an existing repository")
  
  val command: Command[(Boolean, String)] = Command("clone").subcommands(Command("clone", options, arguments).withHelp(help))
  
  // Define val cliApp using CliApp.make
  val cliApp = CliApp.make(
    name = "Sample Git",
    version = "1.1.0",
    summary = text("Sample implementation of git clone"),
    command = command
  ) {
    // Implement logic of CliApp
    case _ => printLine("executing git clone")
  }
}
```
The output will be
```
   _____@       @           @        @   __@     @       @  ______@   _ @  __ @
  / ___/@ ____ _@  ____ ___ @   ____ @  / /@ ___ @       @ / ____/@  (_)@ / /_@
  \__ \ @/ __ `/@ / __ `__ \@  / __ \@ / / @/ _ \@       @/ / __  @ / / @/ __/@
 ___/ / / /_/ / @/ / / / / /@ / /_/ /@/ /  /  __/@       / /_/ /  @/ /  / /_  @
/____/  \__,_/  /_/ /_/ /_/ @/ .___/ /_/   \___/ @       \____/   /_/   \__/  @
        @       @           /_/      @     @     @       @        @     @     @

Sample Git v1.1.0 -- Sample implementation of git clone

USAGE

  $ clone clone [(-l, --local)] <repository>

COMMANDS

  clone [(-l, --local)] <repository>  Creates a copy of an existing repository
```

If there is a `CliApp`, you can run a command using its method `run` and passing parameters in a `List[String]`.

## References

- [10 Minute Command-Line Apps With ZIO CLI](https://www.youtube.com/watch?v=UeR8YUN4Tws) by Aiswarya Prakasan
- [Hacking on ZIO-CLI](https://www.youtube.com/watch?v=HxPCXfnbg3U) by Adam Fraser and Kit Langton
- [Behold! The Happy Path To Captivate Your Users With Stunning CLI Apps!](https://www.youtube.com/watch?v=0c3zbUq4lQo) by Jorge Vasquez
