# Commands

> A command is a piece of text representing a directive for a CLI application. This allows the user to communicate easily which task must perform the application. A popular CLI app is the Version Control System called Git. Commonly used commands of Git are among the following.
```
git clone // Creates a copy of a repository
git add   // Adds modified or new files that will be committed after using git commit
```
**ZIO CLI** uses class `Command[Model]` as a description of a CLI command. When using `CliApp.make`, it is needed to specify a `Command[Model]` instance. This parameter is parsed by `CliApp` to produce a collection of possible commands and transform a valid input from a user into an instance of type `Model`. Then you can implement the functionality of the CLI App using pattern matching on instances of `Model`.

A command is a piece of text representing a directive for a CLI application. This allows the user to communicate easily which task must perform the application. A popular CLI app is the Version Control System called Git. Commonly used commands of Git are among the following.
```
git clone // Creates a copy of a repository
git add   // Adds modified or new files that will be committed after using git commit
```
**ZIO CLI** uses class `Command[Model]` as a description of a CLI command. When using `CliApp.make`, it is needed to specify a `Command[Model]` instance. This parameter is parsed by `CliApp` to produce a collection of possible commands and transform a valid input from a user into an instance of type `Model`. Then you can implement the functionality of the CLI App using pattern matching on instances of `Model`.

### Subcommands
A subcommand is a command that belongs to a larger command. Thus, it is possible for a command to have different subcommands. Subcommands can be used to better organize and design the functionality of a CLI app. Different subcommands can represent different types of task that a CLI can perform and give them a descriptive name. In the Git example above, `git` is the name of the command while `clone` or `add` are subcommands of `git`. As the functionality of `git clone` and `git add` are very different, it is better to use different subcommands. In the same way, it would be possible for a subcommand to have other subcommands.

### Basic construction

It's possible to create a `Command` using the `apply` method. You must always specify the name of the command that will be used
in the CLI, but you can also specify options and/or arguments:

```scala

object Command {
  def apply(name: String) = ???
  def apply[ArgsType](name: String, args: Args[ArgsType]) = ???
  def apply[OptionsType](name: String, options: Options[OptionsType]) = ???
  def apply[OptionsType, ArgsType](name: String, options: Options[OptionsType], args: Args[ArgsType]) = ???
}
```
## Combining commands and transformations
**ZIO CLI** also allows to combine different commands in a functional manner. This can be used for a cleaner design of the commands of a CLI app.

```scala
trait Command[+A] {
  def |[A1 >: A](that: Command[A1]): Command[A1]
  def orElse[A1 >: A](that: Command[A1]): Command[A1]
  def orElseEither[B](that: Command[B]): Command[Either[A, B]]
  def withHelp(help: HelpDoc): Command[A]
  def withHelp(help: String): Command[A]
  def subcommands[B](that: Command[B])
  def subcommands[B](c1: Command[B], c2: Command[B], cs: Command[B]*)
  def map[B](f: A => B): Command[B]
}
```

### Choosing between commands
If we have more than one command, we can create a new `Command` that allows to choose between two commands using methods `|`, `orElse` and `orElseEither` as with `Options`. The difference between these three methods lies in the type parameter of the new `Command`. The user will be able to enter only the command that he wants, triggering the desired functionality.
- Method `orElse`

`|` is just an alias for `orElse`. In the Git CLI, it is possible to choose between different commands. This can be realized using `orElse`.
```scala

val gitAdd: Command[Unit] = Command("add")
val gitClone: Command[Path] = Command("clone", Options.directory("directory"))

val newCommand: Command[Any] = gitAdd orElse gitClone 
```

- Method `orElseEither`

This method wraps the types in an `Either` class.

```scala

val gitAdd: Command[Unit] = Command("add")
val gitClone: Command[Path] = Command("clone", Options.directory("directory"))

val newCommand: Command[Either[Unit, Path]] = gitAdd orElseEither gitClone 
```

### Adding Help Documentation
Method `withHelp` returns a new command with the parameter `HelpDoc` or `String` as the `helpDoc` of the command.

```scala

val helpGit = "This is command git."
val helpGitAdd = "Stages changes in the working repository."
val helpGitClone = "Creates a copy of an existing repository."

// Adding HelpDoc to Commands
val gitCommand: Command[Unit] = Command("git").withHelp(helpGit)
val gitAdd: Command[Unit] = Command("add").withHelp(helpGitAdd)
val gitClone: Command[Path] = Command("clone", Options.directory("directory")).withHelp(helpGitClone)
```
The output that this HelpDoc produces is the following.
```
DESCRIPTION

  This is command git.

COMMANDS

  - add                          Stages changes in the working repository.
  - clone --directory directory  Creates a copy of an existing repository.
```

### Adding subcommands
Method `subcommands` returns a new command with a new set of child commands.

```scala
// Adds subcommands add and clone to command git
val git: Command[Any] = gitCommand.subcommands(gitAdd, gitClone)
```

### Changing the type parameter
`Command[_]` is a generic class. We can apply `map` to construct commands returning any custom type that helps us implement business logic. In this way, we can avoid using types as `Unit`, `Path`, `Any` or `Either` that are not very informative. For example, we can modify the type parameter of `git` to the trait `Git`. 
```scala
sealed trait Git
case class Add() extends Git
case class Clone(directory: Path) extends Git

val customCommand: Command[Git] =
  git.map {
    case () => Add()
    case directory: Path => Clone(directory)
  }
```

This makes possible to implement the business logic of our CLI app using directly a custom type. This can be of great use if we have employed various (`orElseEither`). We are going to implement another subcommand of Git using `orElseEither`.
```scala
// new Git subcommand
val gitBranch = Command("branch", Args.text("branch-name"))

// new Git subclass
case class Branch(branchName: String) extends Git

val eitherCommand: Command[Either[Either[Unit, Path], String]] = gitAdd orElseEither gitClone orElseEither gitBranch
val customEitherCommand: Command[Git] =
  eitherCommand.map {
    case Left(Left(_)) => Add()
    case Left(Right(directory)) => Clone(directory)
    case Right(branchName) => Branch(branchName)
  }
```
In this example, we have transformed a rather cumbersome `Command[Either[Either[Unit, Path], String]]` into a `Command[Git]`.
