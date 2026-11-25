# CLI Configuration

> A `CliConfig` controls how a `CliApp` parses the command line and how its help looks. It covers case sensitivity, "Did you mean" suggestions, the fallback check for `--help` and `--wizard`, and the names and types shown in the usage line.

`CliConfig` is a case class with five fields:

```scala
final case class CliConfig(
  caseSensitive: Boolean,
  autoCorrectLimit: Int,
  finalCheckBuiltIn: Boolean = true,
  showAllNames: Boolean = true,
  showTypes: Boolean = true
)
```

| Field               | Type      | Value in `CliConfig.default` | What it controls                                                                                      |
| ------------------- | --------- | ---------------------------- | ----------------------------------------------------------------------------------------------------- |
| `caseSensitive`     | `Boolean` | `false`                      | Whether upper and lower case letters are different in command names.                                  |
| `autoCorrectLimit`  | `Int`     | `2`                          | The largest number of typing mistakes for which a "Did you mean" hint is shown.                       |
| `finalCheckBuiltIn` | `Boolean` | `true`                       | Whether invalid input that contains `--help` or `--wizard` still shows the help or starts the wizard. |
| `showAllNames`      | `Boolean` | `true`                       | Whether the usage line shows every name of an option or only the long one.                            |
| `showTypes`         | `Boolean` | `true`                       | Whether the usage line shows the value type of each option.                                           |

## Setting the Configuration

`CliApp.make` uses `CliConfig.default` unless you pass the `config` parameter. `CliApp#config` replaces the configuration of an existing app. Both forms are shown here:

```scala
import zio._
import zio.cli._
import zio.cli.HelpDoc.Span.text

val strict: CliConfig = CliConfig(caseSensitive = true, autoCorrectLimit = 1)

val greet: Command[String] = Command("greet", Args.text("name"))

val app: CliApp[Any, java.io.IOException, Unit] = CliApp.make(
  name = "greet",
  version = "1.0.0",
  summary = text("Prints a greeting"),
  command = greet,
  config = strict
)(name => Console.printLine(s"Hello, $name!"))

val relaxedApp = app.config(CliConfig.default)
```

## Default Configuration

`CliConfig.default` is case-insensitive, suggests corrections for up to two mistakes, runs the fallback check for `--help` and `--wizard`, and shows all names and types:

```scala
CliConfig.default
// res1: CliConfig = CliConfig(
//   caseSensitive = false,
//   autoCorrectLimit = 2,
//   finalCheckBuiltIn = true,
//   showAllNames = true,
//   showTypes = true
// )
```

## Case Sensitivity

`caseSensitive` decides whether `GIT CLONE` means the same as `git clone`. With `caseSensitive = false`, upper and lower case letters are equal in these places:

- Command and subcommand names.
- The distance used for "Did you mean" hints.
- Matching words during shell completion.
- The words of a boolean argument, such as `YES` or `Off` for `Args.bool`.

Enumeration values are always case-sensitive.

:::caution
Option names are matched case-sensitively in practice, even with `caseSensitive = false`. For an option declared as `Options.text("name")`, the input `--NAME Ada` is rejected with `Expected to find --name option.` Type option names in the case they are declared.
:::

## Auto-Correction

`autoCorrectLimit` is the largest number of typing mistakes (inserted, deleted or replaced characters) for which zio-cli adds a "Did you mean" hint to a parse error. With the default limit of `2`, a required option `--limit` typed as `--limt` gives:

```text
The flag "--limt" is not recognized. Did you mean --limit?
```

The hint never changes the input: the parse still fails. An option name that is at most `autoCorrectLimit + 1` characters long never gets a hint, and `autoCorrectLimit = 0` turns hints off. [Auto-Correct Suggestions](validation-and-errors.md#auto-correct-suggestions) lists every condition, including when a misspelt option produces no hint at all.

## Final Built-in Check

`finalCheckBuiltIn` controls what happens when the input does not parse but contains `--help`, `-h`, `--wizard` or `-w` somewhere. With `true`, zio-cli shows the help, or starts the wizard, of the command that failed to parse, instead of printing the error. With `false`, it prints the error.

For a command with a required `--limit` option, the input `--limt 3 --help` shows the help with the default configuration and the error `The flag "--limt" is not recognized. Did you mean --limit?` with `finalCheckBuiltIn = false`. `-w` starts the wizard only through this check, so it works only when the rest of the input is invalid. See [Built-in Commands](built-in-commands.md) for how the built-in options are matched.

## Help Appearance

`showAllNames` and `showTypes` change how options appear in the `USAGE` line of the help. They do not change the `OPTIONS` and `COMMANDS` sections. To compare the four combinations, we render the usage line of a command whose option has a short alias:

```scala
val command: Command[String] = Command("command", Options.text("option").alias("o"))

def usageWith(config: CliConfig): String =
  command.synopsis.enumerate(config).map(_.text).mkString("\n")
```

The four lines below use the default configuration, then `showAllNames = false`, then `showTypes = false`, then both set to `false`:

```scala
println(usageWith(CliConfig.default))
// command (-o, --option <text>)
println(usageWith(CliConfig.default.copy(showAllNames = false)))
// command --option <text>
println(usageWith(CliConfig.default.copy(showTypes = false)))
// command (-o, --option)
println(usageWith(CliConfig.default.copy(showAllNames = false, showTypes = false)))
// command --option
```

With `showAllNames = false`, an option with several names shows only its first long name (`--option`). An option with only short names shows all of them.

## Other Members

`CliConfig` also has a few helpers that zio-cli uses while parsing:

```scala
final case class CliConfig(/* ... */) {
  def normalizeCase(text: String): String
  def isLongOption(value: String): Boolean
  def isShortOption(value: String): Boolean
  def isOption(value: String): Boolean
}

object CliConfig {
  val default: CliConfig
  val cliConfig: URIO[CliConfig, CliConfig]
}
```

`CliConfig#normalizeCase` lowercases text when `caseSensitive` is `false` and returns it unchanged otherwise. `CliConfig#isLongOption`, `CliConfig#isShortOption` and `CliConfig#isOption` match only very short words: `--` or two dashes and one character, `-` or one dash and one character. `--name` is not a long option for `CliConfig#isLongOption`, so do not use these helpers to classify user input. `CliConfig.cliConfig` reads a `CliConfig` from the ZIO environment, for code that receives the configuration as a service.
