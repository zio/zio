# Args

> `Args[A]` describes the positional arguments of a command: the values a user types without a name, such as the repository URL in `git clone https://github.com/zio/zio-cli.git`. An `Args[A]` is a description, not a parsed value. It says which arguments the command accepts and how to build an `A` from them, and a [`Command`](commands.md) uses it to parse the user's input.

Arguments come after the command's [options](options.md). ZIO CLI stops reading options at the first token that is not an option, and a `--` token makes every later token an argument, even one that starts with `-`. [Options Come Before Arguments](parsing.md#options-come-before-arguments) describes the order in detail.

## Constructors

The `Args` companion object has one constructor per value type, plus constructors for enumerations, paths and commands without arguments.

### Value Arguments

Each value type has a named and an unnamed constructor. The named form takes the name shown in the help, and the unnamed form uses the value type's placeholder as its name. `Args.localDate` is the only constructor without an unnamed form.

To declare a named integer argument and an unnamed one:

```scala
import zio.cli._

val count: Args[BigInt]    = Args.integer("count") // shown as <count>
val anyNumber: Args[BigInt] = Args.integer         // shown as <integer>
```

The value types and their constructors are `Args.text`, `Args.integer`, `Args.decimal`, `Args.bool`, `Args.duration`, `Args.instant`, `Args.localDate`, `Args.localDateTime`, `Args.localTime`, `Args.monthDay`, `Args.offsetDateTime`, `Args.offsetTime`, `Args.period`, `Args.year`, `Args.yearMonth`, `Args.zonedDateTime`, `Args.zoneId` and `Args.zoneOffset`. [Value Types](value-types.md) lists the Scala type, the placeholder and the accepted input of each one.

A value that cannot be converted fails with the value type's message, such as `ten is not a integer.`. A missing argument fails with `Missing argument <count>.`.

### Enumeration Arguments

`Args.enumeration` accepts one of a fixed set of words. Each case is a pair of the word the user types and the value it stands for. `Args.enumeration(name)(cases*)` names the argument, and `Args.enumeration(cases*)` uses `choice` as the name.

To accept one of three log levels:

```scala
import zio.cli._

val level: Args[Int] = Args.enumeration("level")(("debug", 0), ("info", 1), ("warn", 2))
```

The word must match exactly, including its case. Any other word fails with `Expected one of the following cases: debug, info, warn`.

### Path Arguments

`Args.file`, `Args.directory` and `Args.path` produce a `java.nio.file.Path`. `Args.file` and `Args.directory` take an `Exists` value that says whether the path must exist, and each has three forms: `(name, exists)`, `(exists)` without a name, and the unnamed value with `Exists.Either`. `Args.path(name)` and `Args.path` accept a file or a directory, existing or not.

To require an existing file and accept any directory:

```scala
import zio.cli._

val source = Args.file("source", exists = Exists.Yes)
val target = Args.directory("target")
```

An existing path must also be of the right kind: `Args.file` rejects an existing directory, and `Args.directory` rejects an existing file. These constructors are not available on Scala.js. [Paths](value-types.md#paths) and [Platform Support](value-types.md#platform-support) give the full rules.

### No Arguments

`Args.none` is an `Args[Unit]` that consumes no input. A command built without arguments uses it, and it disappears from the command's value.

To declare a command that takes no arguments:

```scala
import zio.cli._

val status: Command[Boolean] = Command("status", Options.boolean("short"), Args.none)
```

### Arguments From a PrimType

`Args.Single(pseudoName, primType, description)` wraps any `PrimType` directly. You rarely need it; it is for a value type that no constructor offers, such as a path that must exist but may be a file or a directory. [Custom Parameters From a PrimType](value-types.md#custom-parameters-from-a-primtype) shows an example.

## Methods

The `Args` trait has the following public methods:

```scala
sealed trait Args[+A] {
  def ++[B](that: Args[B])(implicit zippable: Zippable[A, B]): Args[zippable.Out]

  def * : Args[List[A]]
  def +[A1 >: A]: Args[::[A1]]
  def repeat: Args[List[A]]
  def repeat1[A1 >: A]: Args[::[A1]]
  def atLeast(min: Int): Args[List[A]]
  def atMost(max: Int): Args[List[A]]
  def between(min: Int, max: Int): Args[List[A]]

  def map[B](f: A => B): Args[B]
  def mapOrFail[B](f: A => Either[HelpDoc, B]): Args[B]
  def mapTry[B](f: A => B): Args[B]

  def ??(that: String): Args[A]

  def helpDoc: HelpDoc
  def synopsis: UsageSynopsis
  def minSize: Int
  def maxSize: Int
  def uid: Option[String]
  def validate(args: List[String], conf: CliConfig): IO[ValidationError, (List[String], A)]
}
```

`Args` has no `optional` or `withDefault`. To make an argument optional, accept at most one value with `Args#atMost` and take the first one with `Args#map`, as in `Args.text("name").atMost(1).map(_.headOption)`.

### Combining Arguments With ++

A command takes a single `Args` value, so we combine several arguments into one with `Args#++`. The arguments are read in the order they are combined. The result is a flat tuple: `a ++ b ++ c` is an `Args[(A, B, C)]`, not a nested pair, and `Unit` values such as `Args.none` disappear from it.

To combine the two arguments of `git clone`:

```scala
import zio.cli._

val cloneArgs: Args[(String, String)] = Args.text("repository") ++ Args.text("directory")
```

The usage line of a command with these arguments shows them in order, as `<repository> <directory>`.

### Repeating Arguments

These methods turn an argument into a list of arguments of the same type:

- `Args#*` (and its alias `Args#repeat`) accepts zero or more values.
- `Args#+` (and its alias `Args#repeat1`) accepts one or more values and returns a non-empty list, `::[A]`.
- `Args#atLeast(min)` accepts `min` or more values.
- `Args#atMost(max)` accepts up to `max` values.
- `Args#between(min, max)` accepts from `min` to `max` values.

To accept one or more file names, and between two and five numbers:

```scala
import zio.cli._

val files: Args[::[String]]       = Args.text("file").+
val numbers: Args[List[BigInt]]   = Args.integer("n").between(2, 5)
```

The help names the repetition rule, and the usage line shows the argument followed by `...`:

```scala
println(numbers.helpDoc.toPlaintext(color = false))
// <n> 2 - 5
//   An integer.
// 
//   This argument must be repeated at least 2 times and may be repeated up to 5 times.
// 
//
```

`Args#*`, `Args#+` and `Args#atLeast` are greedy: they take every remaining token. An argument combined after them never receives a value, so a required one fails with `Missing argument <name>.`. If a remaining token is not a valid value, the command fails with that value's error; the token is not left for a later argument. `Args#atMost` and `Args#between` stop at their maximum, so another argument can follow them once that many values are given. With fewer values, a remaining token that is not valid for the repeated argument fails the command instead of going to the next argument.

:::caution
Tokens left over after the last argument is filled are ignored, without an error. With `Args.text("name")`, the input `alice bob` gives `"alice"`, and `bob` is dropped; see [Leftover Words](parsing.md#leftover-words).
:::

### Transforming Values

`Args#map` converts the parsed value with a function that cannot fail. To turn a text argument into an upper-case name:

```scala
import zio.cli._

val shout: Args[String] = Args.text("name").map(_.toUpperCase)
```

`Args#mapOrFail` converts the value with a function that may reject it. The function returns `Right` with the new value, or `Left` with a `HelpDoc` that is shown to the user. To accept only positive counts:

```scala
val positive: Args[Int] =
  Args.integer("count").mapOrFail { (n: BigInt) =>
    if (n > 0) Right(n.toInt) else Left(HelpDoc.p("The count must be positive."))
  }
```

`Args#mapTry` converts the value with a function that may throw. An exception becomes a validation error whose text is the exception's message. To parse a URI:

```scala
val uri: Args[java.net.URI] = Args.text("uri").mapTry(java.net.URI.create)
```

With the input `not a uri`, the user sees `Illegal character in path at index 3: not a uri`. [How to validate and convert a parameter's value](how-to-validate-input.md) compares these methods with their `Options` counterparts.

### Adding Help With ??

`Args#??` appends a paragraph to the help of an argument. To describe the repository argument:

```scala
import zio.cli._

val repository = Args.text("repository") ?? "The repository to clone."
```

The help shows the description after the sentence for the value type:

```scala
println(repository.helpDoc.toPlaintext(color = false))
// <repository>
//   A user-defined piece of text.
// 
//   The repository to clone.
// 
//
```

On arguments combined with `Args#++`, `Args#??` appends the same paragraph to every argument, so call it on each argument before combining them.

### Inspecting an Args

`Args#helpDoc` returns the help as a [`HelpDoc`](helpdoc.md), and `Args#synopsis` returns the usage line. `Args#minSize` and `Args#maxSize` give the number of tokens the arguments accept, and `Args#uid` returns their names, such as `<repository>, <directory>`. `Args#validate` parses a list of tokens and returns the leftover tokens with the value; tests use it to check an `Args` without building a command.

## How Args Are Represented

`Args` is a sealed trait. Its cases are `Args.Empty` (the result of `Args.none`), `Args.Single` (one argument), `Args.Both` (the result of `Args#++`), `Args.Variadic` (the result of `Args#*`, `Args#+`, `Args#atLeast`, `Args#atMost` and `Args#between`) and `Args.Map` (the result of `Args#map` and the methods built on it). They are public, but you build arguments with the constructors and methods above instead of constructing the cases yourself.
