# Options

> `Options[A]` describes the named parameters of a command, such as `--quiet` or `--message "Fix typo"`. An `Options[A]` is a description, not a parsed value. It says which options the command accepts and how to build an `A` from them, and a [`Command`](commands.md) uses it to parse the user's input.

An option is written as `--name` when its name is longer than one character, and as `-n` when it is one character long. The same rule applies to aliases. ZIO CLI accepts these forms on the command line:

- A value after the name, as in `--name Ada`, or joined with `=`, as in `--name=Ada`.
- Clustered one-character flags, as in `-abc` for `-a -b -c`.
- Options in any order among themselves, but before the command's positional [arguments](args.md). Reading of options stops at the first token that is not an option, and a `--` token makes every later token an argument.

A misspelled option name close to a real one, such as `--firstnme`, fails with `The flag "--firstnme" is not recognized. Did you mean --firstname?`. [How to Write Options](parsing.md#how-to-write-options) describes these rules in detail.

## Constructors

The `Options` companion object has one constructor per value type, plus constructors for flags, key-value pairs and commands without options. Every constructor takes the option's name; there are no unnamed options.

### Value Options

A value option reads the token after its name and converts it to its value type. The constructors are `Options.text`, `Options.integer`, `Options.decimal`, `Options.duration`, `Options.instant`, `Options.localDate`, `Options.localDateTime`, `Options.localTime`, `Options.monthDay`, `Options.offsetDateTime`, `Options.offsetTime`, `Options.period`, `Options.year`, `Options.yearMonth`, `Options.zonedDateTime`, `Options.zoneId` and `Options.zoneOffset`. [Value Types](value-types.md) lists the Scala type, the placeholder and the accepted input of each one.

To declare a required integer option:

```scala
import zio.cli._

val count: Options[BigInt] = Options.integer("count")
```

A value option is required. Without it, parsing fails with `Expected to find --count option.`, and a value that cannot be converted fails with `ten is not a integer.`. [Defaults](#defaults) shows how to make an option optional.

### Boolean Options

`Options.boolean(name, ifPresent = true)` creates a flag. The flag alone gives `ifPresent`, and a missing flag gives the opposite value, so every boolean option already has a default.

To declare a flag that is `true` when present, and one that is `false` when present:

```scala
import zio.cli._

val quiet: Options[Boolean]    = Options.boolean("quiet")
val progress: Options[Boolean] = Options.boolean("progress", ifPresent = false)
```

The user can also give the value explicitly, as `--quiet true`, `--quiet false` or `--quiet=false`.

:::caution
A flag reads the next token as its value only when it is exactly `true` or `false`. Other boolean words such as `no`, `0` or `FALSE` are not read: `--quiet=no` and `--quiet no` give `true`, and `no` is left over as a positional argument. The words listed in [Boolean](value-types.md#boolean) work for `Args.bool`, not for flags. [Boolean Flags](parsing.md#boolean-flags) shows examples.
:::

### Boolean Options With a Negation

`Options.boolean(name, ifPresent, negationName, negationNames*)` adds a second flag that gives the opposite value. With `ifPresent` set to `true`, `--color` gives `true`, `--no-color` gives `false`, and the value is `false` when neither flag is given. Extra `negationNames` are aliases of the negation flag. Giving both flags fails with `Options collision detected. You can only specify either --color or --no-color.`.

To declare a color flag with its negation:

```scala
import zio.cli._

val color: Options[Boolean] = Options.boolean("color", true, "no-color")
```

`Options.boolean(name, alias, ifPresent, negationName, negationNames*)` also gives the main flag an alias. To declare `-v`/`--verbose` with the negation `-q`/`--quiet`:

```scala
val verbose: Options[Boolean] = Options.boolean("verbose", "v", true, "quiet", "q")
```

:::caution
Do not call `Options#alias` on a boolean option with a negation. The alias is added to both flags, so using it always fails with `Options collision detected`. Pass the alias to `Options.boolean` instead, as in the example above.
:::

### Enumeration Options

`Options.enumeration(name)(cases*)` accepts one of a fixed set of words. Each case is a pair of the word the user types and the value it stands for. The word must match exactly, including its case.

To accept one of three commit modes:

```scala
import zio.cli._

sealed trait Mode
object Mode {
  case object All         extends Mode
  case object Interactive extends Mode
  case object Patch       extends Mode
}

val mode: Options[Mode] =
  Options.enumeration[Mode]("mode")(("all", Mode.All), ("interactive", Mode.Interactive), ("patch", Mode.Patch))
```

The help lists the accepted words:

```scala
println(mode.helpDoc.toPlaintext(color = false))
// --mode all | interactive | patch
//   One of the following cases: all, interactive, patch.
// 
//
```

### Path Options

`Options.file(name, exists)` and `Options.directory(name, exists)` produce a `java.nio.file.Path`. The `exists` parameter defaults to `Exists.Either`. An existing path must also be of the right kind, so `Options.file` rejects an existing directory.

To require an existing configuration file:

```scala
import zio.cli._

val config = Options.file("config", exists = Exists.Yes)
```

`Options.directory` is not available on Scala.js, and `Options.file` there rejects every value. There is no `Options.path`; an option that accepts a file or a directory is built from a `PrimType`. [Paths](value-types.md#paths), [Custom Parameters From a PrimType](value-types.md#custom-parameters-from-a-primtype) and [Platform Support](value-types.md#platform-support) give the details.

### Key-Value Options

`Options.keyValueMap(name)` collects `key=value` pairs into a `Map[String, String]`. The user repeats the option for each pair, as in `-d a=1 -d b=2`, or lists several pairs after one option, as in `-d a=1 b=2`. Reading stops at the first token without `=` or with an empty value, and the rest is left for the next options or arguments. `Options.keyValueMap(option)` takes an `Options.Single[String]` instead of a name.

To collect definitions under `--define` and `-d`:

```scala
import zio.cli._

val defines: Options[Map[String, String]] = Options.keyValueMap("define").alias("d")
```

:::caution
A key-value option does not accept the `--define=a=1` form, and with the default case-insensitive [`CliConfig`](cli-config.md) it does not recognize a name or alias that contains capital letters, such as `-D`. Use lower-case names.
:::

### No Options

`Options.none` is an `Options[Unit]` that consumes no input. A command built without options uses it, and it disappears from the command's value.

To declare a command that takes no options:

```scala
import zio.cli._

val status = Command("status", Options.none, Args.none)
```

### Other Constructors

`Options.oauth2(provider, scope)` adds OAuth2 authorization and is available only on the JVM; see [Authorization with OAuth2](auth.md). `Options.Single(name, aliases, primType, description, pseudoName)` wraps any `PrimType` directly, as shown in [Custom Parameters From a PrimType](value-types.md#custom-parameters-from-a-primtype).

## Methods

The `Options` trait has the following public methods. `Options#as` and `Options#fold` have overloads for two to six values, and only the smallest is shown:

```scala
sealed trait Options[+A] {
  def ++[A1 >: A, B](that: Options[B])(implicit zippable: Zippable[A, B]): Options[zippable.Out]
  def |[A1 >: A](that: Options[A1]): Options[A1]
  def orElse[A1 >: A](that: Options[A1]): Options[A1]
  def orElseEither[B](that: Options[B]): Options[Either[A, B]]

  def optional: Options[Option[A]]
  def withDefault[A1 >: A](value: A1): Options[A1]

  def map[B](f: A => B): Options[B]
  def mapOrFail[B](f: A => Either[ValidationError, B]): Options[B]
  def collect[B](message: String)(f: PartialFunction[A, B]): Options[B]
  def as[B, C, Z](f: (B, C) => Z)(implicit ev: A <:< (B, C)): Options[Z]
  def fold[B, C, Z](f1: B => Z, f2: C => Z)(implicit ev: A <:< Either[B, C]): Options[Z]

  def ??(that: String): Options[A]
  def alias(name: String, names: String*): Options[A]
  def withPseudoName(name: String): Options[A]

  def helpDoc: HelpDoc
  def synopsis: UsageSynopsis
  def isBool: Boolean
  def flatten: List[Options[_] with Input]
  def validate(args: Map[String, List[String]], conf: CliConfig): IO[ValidationError, A]
  def uid: Option[String]
}
```

### Combining Options With ++

`Options#++` requires both options. The result is a flat tuple: `a ++ b ++ c` is an `Options[(A, B, C)]`, not a nested pair, and `Unit` values such as `Options.none` disappear from it.

To combine a flag and a text option:

```scala
import zio.cli._

val commitOptions: Options[(Boolean, String)] = Options.boolean("all") ++ Options.text("message")
```

The usage line of a command with these options is `[--all] --message <text>`. A flag is in square brackets because it has a default.

### Choosing Between Options

`Options#|` (and its alias `Options#orElse`) accepts exactly one of two options of a common type. `Options#orElseEither` does the same for options of different types and wraps the result in an `Either`, with `Left` for the first option. If both options are given, parsing fails with `Options collision detected. You can only specify either --name or --id.`. If neither is given, parsing fails with both missing-option messages, unless the whole alternative has a default.

To accept a user by name or by ID, with a default when neither is given:

```scala
import zio.cli._

val user: Options[Either[String, BigInt]] =
  Options.text("name") orElseEither Options.integer("id")

val userOrMe: Options[Either[String, BigInt]] = user.withDefault(Left("me"))
```

The usage line shows the alternatives separated by `|`, as `--name <text>|--id <integer>`.

:::caution
Do not combine options that already have a default with `Options#|`, `Options#orElse` or `Options#orElseEither`. Such an option always succeeds, so the alternative always reports a collision, even when the user gives no option at all. This includes every `Options.boolean` and every option built with `Options#optional` or `Options#withDefault`. Put the default on the whole alternative instead, and use an enumeration for mutually exclusive modes, as in [Enumeration Options](#enumeration-options).
:::

When the choice is between flags, separate flags with a check also work. To reject `--all` together with `--patch`:

```scala
val mode: Options[String] =
  (Options.boolean("all") ++ Options.boolean("patch")).mapOrFail {
    case (true, true) =>
      Left(ValidationError(ValidationErrorType.InvalidValue, HelpDoc.p("Use either --all or --patch.")))
    case (true, false) => Right("all")
    case (false, true) => Right("patch")
    case (false, false) => Right("default")
  }
```

## Defaults

An option without a default is required. These methods make it optional.

### Options#optional

`Options#optional` wraps the value in `Some`, and gives `None` when the option is missing. To make a port optional:

```scala
import zio.cli._

val port: Options[Option[BigInt]] = Options.integer("port").optional
```

The help adds `This setting is optional.`, and the usage line shows the option in square brackets, as `[--port <integer>]`.

### Options#withDefault

`Options#withDefault(value)` uses `value` when the option is missing. To default the output directory to `target`:

```scala
val output = Options.directory("output").withDefault(java.nio.file.Paths.get("target"))
```

The help shows the default:

```scala
println(output.helpDoc.toPlaintext(color = false))
// --output <directory>
//   A directory.
// 
//   This setting is optional. Default: 'target'.
// 
//
```

The default is used only when the option is missing. A value the user gives that is not valid is still an error. The default itself is not validated, so a default path is not checked against `Exists`. The default must have the option's type or a supertype of it: `Options.integer("retries").withDefault(3)` compiles as an `Options[Any]` unless the expected type is `Options[BigInt]`. [How to make an option optional or give it a default](how-to-optional-option-with-default.md) walks through the choices.

## Transforming Values

These methods convert the parsed value. Each one keeps the option's name, help and position on the command line.

### Options#map

`Options#map` converts the value with a function that cannot fail. To convert an integer option to an `Int`:

```scala
import zio.cli._

val retries: Options[Int] = Options.integer("retries").map(_.toInt)
```

### Options#mapOrFail

`Options#mapOrFail` converts the value with a function that may reject it. The function returns `Right` with the new value, or `Left` with a `ValidationError` whose text is shown to the user. To accept only valid port numbers:

```scala
val port: Options[Int] =
  Options.integer("port").mapOrFail { (value: BigInt) =>
    if (value >= 1 && value <= 65535) Right(value.toInt)
    else Left(ValidationError(ValidationErrorType.InvalidValue, HelpDoc.p("Port must be between 1 and 65535.")))
  }
```

### Options#collect

`Options#collect` keeps the values that a partial function matches, and rejects the others with the given message. To accept two colors:

```scala
val colorCode: Options[Int] =
  Options.text("color").collect("color must be red or blue") {
    case "red"  => 1
    case "blue" => 2
  }
```

### Options#as

`Options#as` turns a tuple into a value of a case class, or into the result of any function of two to six arguments. It is a shorthand for `Options#map` that spreads the tuple into the function's arguments. To build a `Bounds` from two options:

```scala
case class Bounds(from: BigInt, to: BigInt)

val bounds: Options[Bounds] = (Options.integer("from") ++ Options.integer("to")).as(Bounds.apply _)
```

### Options#fold

`Options#fold` handles each side of an `Either`, such as the result of `Options#orElseEither`. It takes one function per side, and versions for two to six sides exist. With three or more sides, the nested `Either` is the one that chained `Options#orElseEither` calls produce, nested to the left. To turn a name or an ID into a description:

```scala
val nameOrId: Options[String] =
  (Options.text("name") orElseEither Options.integer("id"))
    .fold((name: String) => s"name $name", (id: BigInt) => s"id $id")
```

## Names and Help

These methods change how an option is named and described. On a combined option, such as `a ++ b` or `a | b`, they apply to every option inside it, so call them on each option before combining.

### Options#alias

`Options#alias(name, names*)` adds one or more alternative names. A one-character alias is written with `-`, and a longer one with `--`. To let `-o` and `--out` stand for `--output`:

```scala
import zio.cli._

val output = Options.text("output").alias("o", "out")
```

The help lists every name:

```scala
println(output.helpDoc.toPlaintext(color = false))
// (-o, --output, --out <text>)
//   A user-defined piece of text.
// 
//
```

### Options#??

`Options#??` appends a paragraph to the help of an option. On an option with a default, the description comes before the sentence about the default. To describe the `--quiet` flag:

```scala
val quiet = Options.boolean("quiet") ?? "Suppress feedback messages."
```

```scala
println(quiet.helpDoc.toPlaintext(color = false))
// --quiet
//   A true or false value.
// 
//   Suppress feedback messages.
// 
//   This setting is optional. Default: 'false'.
// 
//
```

### Options#withPseudoName

`Options#withPseudoName` replaces the placeholder that the help shows after the option name, such as `<text>`. It changes only the help, not what the option accepts. To show `<NAME>` instead of `<text>`:

```scala
val lastname = Options.text("lastname").withDefault("xyz").withPseudoName("NAME")
```

```scala
println(lastname.helpDoc.toPlaintext(color = false))
// --lastname <NAME>
//   A user-defined piece of text.
// 
//   This setting is optional. Default: 'xyz'.
// 
//
```

## Inspecting and Validating Options

`Options#helpDoc` returns the help as a [`HelpDoc`](helpdoc.md), and `Options#synopsis` returns the usage line. `Options#isBool` tells whether the option is a flag, and `Options#uid` returns its names, such as `--name, --id`.

`Options.validate(options, args, conf)` parses a list of tokens without a command, which is useful in tests. It returns a tuple of an optional parse error that is kept for the arguments to report, the tokens left for the arguments, and the value. To parse two tokens with the default configuration:

```scala
import zio.cli._

val parsed = Options.validate(Options.text("name"), List("--name", "Ada"), CliConfig.default)
```

`parsed` is a ZIO effect that succeeds with `(None, List(), "Ada")`.

## How Options Are Represented

`Options` is a sealed trait. Its cases are `Options.Empty` (the result of `Options.none`), `Options.Single` (one named option), `Options.WithDefault` (the result of `Options#optional` and `Options#withDefault`), `Options.Both` (the result of `Options#++`), `Options.OrElse` (the result of `Options#|`), `Options.Map` (the result of `Options#map` and the methods built on it), `Options.KeyValueMap` and `Options.OAuth2Options`. They are public, but you build options with the constructors and methods above instead of constructing the cases yourself.
