# Testing CLI Applications

> The `zio-cli-testkit` module provides [ZIO Test](https://zio.dev/reference/test/) assertions and generators for **ZIO CLI** [commands](commands.md) and [apps](cli-app.md). It checks the usage synopsis and help document of a command, how a command parses an argument list, what a `CliApp` returns, and what it prints. For a step-by-step walkthrough that builds a complete test spec, see [How to test a CLI app](how-to-test-a-cli.md).

## Overview

The testkit is published as a separate artifact for the JVM, Scala.js and Scala Native, for Scala 2.12, 2.13 and 3. Every API on this page is available on all three platforms. [Modules](index.md#modules) gives the dependency line; add it to the `Test` configuration. The testkit brings in `zio-test` and `zio-test-sbt` (and, on the JVM and Scala.js, `zio-test-magnolia`).

All public types live in the `zio.cli.testkit` package:

| Type                  | Kind   | Purpose                                                                 |
| --------------------- | ------ | ----------------------------------------------------------------------- |
| `CliAssertion`        | object | Assertions on a `Command` or a `CliApp`                                 |
| `GenerationAssertion` | object | Assertions on a function that builds a `Command` or a `CliApp`          |
| `CliRepr`             | class  | Pairs an argument list with the expected result                         |
| `TestReturn`          | trait  | Parse result that also represents built-in options such as `--help`     |
| `Tree`                | class  | Builds generators of nested values, such as expressions                 |

Most assertions take a `Gen` of `CliRepr` values and run a ZIO Test `check` over it, so every generated input is checked. A `Gen` can be a fixed list (`Gen.const`, `Gen.fromIterable`) or a random generator for property-based testing.

## Imports and the Implicit CliConfig

Several assertion methods take an implicit [`CliConfig`](cli-config.md). `CliAssertion` and `GenerationAssertion` each define one, named `cliConfig` and equal to `CliConfig.default`. A wildcard import of the object brings it into scope:

```scala
import zio.cli.testkit._
import zio.cli.testkit.CliAssertion._
```

Without the import, the compiler reports a missing implicit value for `cliConfig`. Do not import both `CliAssertion._` and `GenerationAssertion._` with wildcards in the same scope: both objects define `cliConfig`, the two names clash, and the compiler again reports that it cannot find the implicit. Import one object with a wildcard and call the other with its prefix, for example `GenerationAssertion.commandCreation`.

Each method uses the implicit differently:

| Method                                                                                             | Uses the implicit `CliConfig` for                                                    |
| -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `CliAssertion.assertSynopsis` with a `String` or `List[String]`                                    | Rendering the command's synopsis                                                     |
| `CliAssertion.assertSynopsis` with a `UsageSynopsis`                                               | Nothing: both sides are rendered with `CliConfig.default`                            |
| `CliAssertion.assertCommand`, `CliAssertion.assertCommandSuccess`, `CliAssertion.assertCommandAll` | Parsing the argument list                                                            |
| `CliAssertion.assertCliApp`, `CliAssertion.assertCliAppValues`                                     | Nothing: the app parses with its own config, set by `CliApp.make` or `CliApp#config` |
| `GenerationAssertion.commandCreation`                                                              | Parsing, through `CliAssertion.assertCommand`                                        |

To check a command against a different `CliConfig`, pass it explicitly as the second argument list instead of declaring a second implicit, which would be ambiguous with the imported one. To test an app with a different config, build the app with that config.

## Argument Lists for Commands and Apps

A `Command` is parsed directly, so its argument list starts with the command name. `CliApp#run` prepends the command name itself, so an app's argument list does not:

| Input under test  | Argument list                       | Example                          |
| ----------------- | ----------------------------------- | -------------------------------- |
| `Command[A]`      | First element is the command name   | `List("greet", "--name", "Ada")` |
| `CliApp[R, E, A]` | No command name                     | `List("--name", "Ada")`          |

The one exception is an app whose top-level command is built with `Command#orElse` (or `|`). `CliApp#run` prepends nothing for it, so the argument list starts with the name of the chosen command. See [Commands](commands.md) for how commands are combined.

## Example Definitions

The examples on this page share one command and one app. The command has two required options, and the app returns a greeting:

```scala
import zio._
import zio.cli._
import zio.cli.testkit._
import zio.cli.testkit.CliAssertion._
import zio.test._

val greet: Command[(String, BigInt)] =
  Command("greet", Options.text("name") ++ Options.integer("times"))

val app: CliApp[Any, Nothing, String] =
  CliApp.make("greet", "1.0.0", HelpDoc.Span.text("Greets a person."), greet) { case (name, times) =>
    ZIO.succeed(s"Hello, $name x$times")
  }
```

## CliAssertion

`CliAssertion` holds the assertions on a single `Command` or `CliApp`. They fall into five groups: access methods, synopsis and help assertions, parsing assertions, app assertions, and the console output assertion.

### Access Methods

Access methods read one part of a `CliApp` and run an `Assertion` you write on it. They return a `TestResult` directly, with no effect and no generator:

```scala
object CliAssertion {
  def accessCommand[R, E, A](cliApp: CliApp[R, E, A], assert: Assertion[Command[_]]): TestResult
  def accessHelpDoc[R, E, A](cliApp: CliApp[R, E, A], assert: Assertion[HelpDoc]): TestResult
  def accessSynopsis[R, E, A](cliApp: CliApp[R, E, A], assert: Assertion[UsageSynopsis]): TestResult
}
```

`CliAssertion.accessCommand` passes the app's `Command`, `CliAssertion.accessHelpDoc` passes that command's `HelpDoc`, and `CliAssertion.accessSynopsis` passes its unrendered `UsageSynopsis`. Use them when no other assertion covers your check. To render a `UsageSynopsis` inside the assertion, call `UsageSynopsis#enumerate` with a `CliConfig`.

Each access method takes the app and an assertion on the extracted part:

```scala
val isNamedGreet: TestResult =
  accessCommand(app, Assertion.assertion[Command[_]]("is named greet")(_.names.contains("greet")))

val hasGreetHelp: TestResult =
  accessHelpDoc(app, Assertion.equalTo(greet.helpDoc))

val hasOneUsageLine: TestResult =
  accessSynopsis(app, Assertion.assertion[UsageSynopsis]("has one usage line")(_.enumerate(CliConfig.default).size == 1))
```

### Synopsis and Help Assertions

These assertions compare the usage synopsis or the help document of a command, or of the command inside an app:

```scala
object CliAssertion {
  def assertSynopsis[A](command: Command[A], synopsis: String)(implicit cliConfig: CliConfig): TestResult
  def assertSynopsis[A](command: Command[A], synopsis: List[String])(implicit cliConfig: CliConfig): TestResult
  def assertSynopsis[A](command: Command[A], synopsis: UsageSynopsis): TestResult
  def assertSynopsis[R, E, A](cliApp: CliApp[R, E, A], synopsis: String)(implicit cliConfig: CliConfig): TestResult
  def assertSynopsis[R, E, A](cliApp: CliApp[R, E, A], synopsis: List[String])(implicit cliConfig: CliConfig): TestResult
  def assertSynopsis[R, E, A](cliApp: CliApp[R, E, A], synopsis: UsageSynopsis): TestResult

  def assertHelpDoc[A](command: Command[A], helpDoc: HelpDoc): TestResult
  def assertHelpDoc[R, E, A](cliApp: CliApp[R, E, A], helpDoc: HelpDoc): TestResult
}
```

`CliAssertion.assertSynopsis` renders the synopsis and compares it with the expected text. The `String` form expects exactly one usage line. The `List[String]` form expects every line in order; a command built with `Command#orElse` has the single line `<command>`, and an `Options#orElse` whose alternatives are several words has one line per alternative. The `UsageSynopsis` form renders both sides with `CliConfig.default`. For an app, the synopsis is the one of the app's command, rendered with the implicit config, not the app's own config.

`CliAssertion.assertHelpDoc` compares the HTML rendering (`HelpDoc#toHTML`) of the command's help document with the expected one. For an app, it checks the command's help document only. The help screen that `CliApp#run` prints for `--help` also contains the app name, version, summary, a usage section and the footer; use `CliAssertion.outputContains` to check those.

Each form takes the expected value directly:

```scala
val synopsisFromText: TestResult =
  assertSynopsis(greet, "greet --name <text> --times <integer>")

val synopsisFromLines: TestResult =
  assertSynopsis(app, List("greet --name <text> --times <integer>"))

val synopsisFromValue: TestResult =
  assertSynopsis(greet, greet.synopsis)

val helpDocCheck: TestResult =
  assertHelpDoc(app, greet.helpDoc)
```

To render the synopsis with a non-default config, pass the config as the second argument list. With `showTypes = false` the value placeholders disappear:

```scala
val synopsisWithoutTypes: TestResult =
  assertSynopsis(greet, "greet --name --times")(CliConfig.default.copy(showTypes = false))
```

### Parsing Assertions

Parsing assertions run `Command#parse` on every argument list that the generator produces. They return a `ZIO` effect that a ZIO Test `test` can return directly:

```scala
object CliAssertion {
  def assertCommand[A, R](
    command: Command[A],
    pairs: Gen[R, CliRepr[List[String], A]]
  )(implicit cliConfig: CliConfig): ZIO[R, Throwable, TestResult]

  def assertCommandSuccess[A, R](
    command: Command[A],
    pairs: Gen[R, CliRepr[List[String], Assertion[Either[ValidationError, A]]]]
  )(implicit cliConfig: CliConfig): ZIO[R, Throwable, TestResult]

  def assertCommandAll[A, R](
    command: Command[A],
    pairs: Gen[R, CliRepr[List[String], Assertion[Either[ValidationError, TestReturn[A]]]]]
  )(implicit cliConfig: CliConfig): ZIO[R, Throwable, TestResult]
}
```

The three methods differ in what the expected result is:

- `CliAssertion.assertCommand` expects the parsed value to equal the value in the `CliRepr`. A `ValidationError` fails the assertion.
- `CliAssertion.assertCommandSuccess` runs your `Assertion` on the whole `Either[ValidationError, A]`. Despite its name, it is the method to use when an input must be rejected.
- `CliAssertion.assertCommandAll` runs your `Assertion` on `Either[ValidationError, TestReturn[A]]`. Use it for built-in options such as `--help`, which do not produce a value of type `A`. See [TestReturn](#testreturn).

Each method takes the command and a generator of inputs:

```scala
val parsesNameAndTimes: ZIO[Any, Throwable, TestResult] =
  assertCommand(greet, Gen.const(CliRepr(List("greet", "--name", "Ada", "--times", "2"), ("Ada", BigInt(2)))))

val rejectsMissingTimes: ZIO[Any, Throwable, TestResult] =
  assertCommandSuccess(greet, Gen.const(CliRepr(List("greet", "--name", "Ada"), Assertion.isLeft)))

val helpIsBuiltIn: ZIO[Any, Throwable, TestResult] =
  assertCommandAll(
    greet,
    Gen.const(CliRepr(List("greet", "--help"), Assertion.isRight(Assertion.isSubtype[TestReturn.Help](Assertion.anything))))
  )
```

:::caution
`CliAssertion.assertCommand` and `CliAssertion.assertCommandSuccess` cannot handle built-in options. An input such as `--help` or `--wizard` makes the whole check fail with a `java.lang.Exception` that has no message, instead of an assertion failure. Use `CliAssertion.assertCommandAll` for those inputs. The app assertions below behave the same way for any input that makes `CliApp#run` return `None`.
:::

### App Assertions

App assertions run `CliApp#run` on every argument list that the generator produces:

```scala
object CliAssertion {
  def assertCliApp[R, E, A, RGen](
    cliApp: CliApp[R, E, A],
    pairs: Gen[RGen, CliRepr[List[String], Assertion[Either[CliError[E], A]]]]
  )(implicit cliConfig: CliConfig): ZIO[RGen with R, Throwable, TestResult]

  def assertCliAppValues[R, E, A, RGen](
    cliApp: CliApp[R, E, A],
    pairs: Gen[RGen, CliRepr[List[String], A]]
  )(implicit cliConfig: CliConfig): ZIO[RGen with R, Throwable, TestResult]

  def assertCliAppZIO[R, E, A, RGen](
    cliApp: CliApp[R, E, A],
    pairs: Gen[RGen, CliRepr[List[String], ZIO[R, E, A]]]
  ): ZIO[RGen with R, Throwable, TestResult]
}
```

The three methods differ in how they treat errors:

- `CliAssertion.assertCliApp` runs your `Assertion` on the whole `Either[CliError[E], A]`. Every [`CliError`](validation-and-errors.md) case reaches the assertion: `CliError.Parsing` for an argument list that does not parse, `CliError.Execution` for an error from the app's own code, and `CliError.IO` or `CliError.BuiltIn` from built-in options.
- `CliAssertion.assertCliAppValues` expects the app to succeed with the value in the `CliRepr`.
- `CliAssertion.assertCliAppZIO` runs the effect in the `CliRepr` as the expected result and compares it with the app's result using `==`. A failure `e` of the effect is compared as `CliError.Execution(e)`. Any other `CliError` from the app, such as a parse error, fails the whole check with that error instead of an assertion result.

`CliAssertion.assertCliApp` and `CliAssertion.assertCliAppValues` accept an implicit `CliConfig` but do not use it; the app parses with its own config. Each method takes the app and a generator of inputs without the command name:

```scala
val returnsGreeting: ZIO[Any, Throwable, TestResult] =
  assertCliAppValues(app, Gen.const(CliRepr(List("--name", "Ada", "--times", "2"), "Hello, Ada x2")))

val reportsParseError: ZIO[Any, Throwable, TestResult] =
  assertCliApp(app, Gen.const(CliRepr(List("--name", "Ada"), Assertion.isLeft(Assertion.isSubtype[CliError.Parsing](Assertion.anything)))))

val matchesEffect: ZIO[Any, Throwable, TestResult] =
  assertCliAppZIO(app, Gen.const(CliRepr(List("--name", "Ada", "--times", "2"), ZIO.succeed("Hello, Ada x2"))))
```

### Console Output Assertion

`CliAssertion.outputContains` runs the app and checks the console output that ZIO Test captures with `TestConsole`:

```scala
object CliAssertion {
  def outputContains[R, E, A, RGen](
    cliApp: CliApp[R, E, A],
    pairs: Gen[RGen, CliRepr[List[String], Seq[String]]]
  ): ZIO[RGen with R, Throwable, TestResult]
}
```

`TestConsole.output` holds one entry per `print` or `printLine` call. The check passes when every expected string is a substring of at least one entry. `CliApp#run` prints the whole help screen with a single `printLine`, so for `--help` all of the help text is one entry. The app must not fail: if `CliApp#run` fails, for example with a parse error, the whole check fails with that `CliError`, so the error message printed on a parse error cannot be checked this way.

To check that the help screen lists both options:

```scala
val helpListsOptions: ZIO[Any, Throwable, TestResult] =
  outputContains(app, Gen.const(CliRepr(List("--help"), Seq("--name", "--times"))))
```

:::caution
`CliAssertion.outputContains` does not clear the captured output before each run. Output from earlier samples of the same generator, and anything printed earlier in the same test, still counts. A later sample can therefore pass on text that only an earlier sample printed. Keep one sample per `CliAssertion.outputContains` call when the samples print different text.
:::

## GenerationAssertion

`GenerationAssertion` tests a function that builds a `Command` or a `CliApp` from a seed value, such as a name. Each method takes a two-level generator: the outer generator produces `(seed, inputs)` pairs, and `inputs` is the generator of `CliRepr` values that the command or app built from `seed` must satisfy:

```scala
object GenerationAssertion {
  def commandCreation[A, B, R1, R2](
    f: A => Command[B],
    response: Gen[R1, (A, Gen[R2, CliRepr[List[String], B]])]
  )(implicit cliConfig: CliConfig): ZIO[R1 with R2, Throwable, TestResult]

  def commandCreationZIO[A, B, R, E <: Throwable, RGen1, RGen2](
    f: A => ZIO[R, E, Command[B]],
    response: Gen[RGen1, (A, Gen[RGen2, CliRepr[List[String], B]])]
  ): ZIO[R with RGen1 with RGen2, Throwable, TestResult]

  def cliAppCreation[R, E, A, B, RGen](
    f: A => CliApp[R, E, B],
    response: Gen[RGen, (A, Gen[RGen, CliRepr[List[String], B]])]
  ): ZIO[R with RGen, Throwable, TestResult]

  def cliAppCreationZIO[R, E, A, B, RGen, RC, EC <: Throwable](
    f: A => ZIO[RC, EC, CliApp[R, E, B]],
    response: Gen[RGen, (A, Gen[RGen, CliRepr[List[String], B]])]
  ): ZIO[R with RC with RGen, Throwable, TestResult]
}
```

`GenerationAssertion.commandCreation` and `GenerationAssertion.commandCreationZIO` check each built command with `CliAssertion.assertCommand`, so their argument lists start with the command name. `GenerationAssertion.cliAppCreation` and `GenerationAssertion.cliAppCreationZIO` check each built app with `CliAssertion.assertCliAppValues`, so their argument lists do not. The `ZIO` variants build the command or app in an effect whose error type must be a `Throwable`. In the two app methods the outer and inner generators share one environment type `RGen`.

The following factories build a command and an app from a greeting word, and the generators pair each word with the input it must accept:

```scala
def greetingCommand(word: String): Command[String] =
  Command(word, Options.text("name")).map(name => s"$word, $name")

def greetingApp(word: String): CliApp[Any, Nothing, String] =
  CliApp.make(word, "1.0.0", HelpDoc.Span.text("Greets a person."), greetingCommand(word))(ZIO.succeed(_))

val words: Gen[Any, String] = Gen.fromIterable(List("hello", "bye"))

val commandSeeds: Gen[Any, (String, Gen[Any, CliRepr[List[String], String]])] =
  words.map(word => (word, Gen.const(CliRepr(List(word, "--name", "Ada"), s"$word, Ada"))))

val appSeeds: Gen[Any, (String, Gen[Any, CliRepr[List[String], String]])] =
  words.map(word => (word, Gen.const(CliRepr(List("--name", "Ada"), s"$word, Ada"))))
```

Each method takes the factory and the matching generator:

```scala
val commandsCheck: ZIO[Any, Throwable, TestResult] =
  GenerationAssertion.commandCreation(greetingCommand, commandSeeds)

val commandsInEffectCheck: ZIO[Any, Throwable, TestResult] =
  GenerationAssertion.commandCreationZIO((word: String) => ZIO.succeed(greetingCommand(word)), commandSeeds)

val appsCheck: ZIO[Any, Throwable, TestResult] =
  GenerationAssertion.cliAppCreation(greetingApp, appSeeds)

val appsInEffectCheck: ZIO[Any, Throwable, TestResult] =
  GenerationAssertion.cliAppCreationZIO((word: String) => ZIO.succeed(greetingApp(word)), appSeeds)
```

:::note
Each `GenerationAssertion` method runs a `check` over the inner generator inside a `check` over the outer one, so the sample counts multiply. With random generators and the default of 200 samples, one call runs up to 40,000 parses. Use finite generators or lower the count with `TestAspect.samples`.
:::

## TestReturn

`TestReturn` is the parse result that `CliAssertion.assertCommandAll` passes to your assertion. Unlike `CommandDirective`, it is a plain value that is easy to compare:

```scala
sealed trait TestReturn[+A]

object TestReturn {
  def convert[A](res: CommandDirective[A]): TestReturn[A]

  sealed trait BuiltIn extends TestReturn[Nothing]
  case class Help()   extends BuiltIn
  case class Wizard() extends BuiltIn
  case class SH()     extends BuiltIn
  case class SHH()    extends BuiltIn
  case class Value[A](a: A) extends TestReturn[A]
}
```

`TestReturn.convert` turns a user-defined result into `Value(a)` and every built-in directive into `Help()`. This includes `--wizard` and the shell-completion options: `TestReturn.convert` never returns `Wizard`, `SH` or `SHH`, so match on `Help()` or on `TestReturn.BuiltIn` for any built-in option.

Converting a user-defined directive gives a `Value`:

```scala
TestReturn.convert(CommandDirective.userDefined(Nil, 5))
// res2: TestReturn[Int] = Value(a = 5)
```

## CliRepr

`CliRepr` pairs an input with its expected result:

```scala
final case class CliRepr[+A, +R](value: A, repr: R) {
  def map[B](f: A => B): CliRepr[B, R]
  def map2[B](f: R => B): CliRepr[A, B]
}
```

`value` is the input, usually the argument list. `repr` is the expected result: a value, a `ZIO` effect, or an `Assertion`, depending on the method. `CliRepr#map` changes `value`, and `CliRepr#map2` changes `repr`. Use `CliRepr#map2` to turn plain expected values into assertions or effects:

```scala
val input = CliRepr(List("--times", "2"), 2)
// input: CliRepr[List[String], Int] = CliRepr(
//   value = List("--times", "2"),
//   repr = 2
// )

input.map(_.size)
// res3: CliRepr[Int, Int] = CliRepr(value = 2, repr = 2)

input.map2(times => times * 10)
// res4: CliRepr[List[String], Int] = CliRepr(
//   value = List("--times", "2"),
//   repr = 20
// )
```

## Tree

`Tree` builds a generator of nested values, such as arithmetic expressions. Atoms are the leaves, operators combine two sub-trees, and mappers combine one sub-tree with a value drawn from a second generator:

```scala
final case class Tree[A](
  atoms: List[Gen[Any, A]],
  mappers: List[Tree.Mapper[A, Any]],
  operators: List[((A, A) => A)],
  depth: Int
) {
  def gen: Gen[Any, A]
  lazy val anyAtom: Gen[Any, A]
  def anyTree(depth: Int): Gen[Any, A]
}

object Tree {
  final case class Compare[A, Repr](a: A, repr: Repr)
  final case class Mapper[A, B](f: (A, B) => A, anyB: Gen[Any, B])
}
```

The members behave as follows:

- `Tree#anyAtom` picks one of the atom generators.
- `Tree#anyTree` builds values of exactly the nesting depth you pass; depth 0 is an atom.
- `Tree#gen` picks a depth between 0 and 2 and calls `Tree#anyTree`. It does not read the `depth` field.
- `Tree.Mapper` is invariant in its second type parameter, and the `mappers` field holds `Mapper[A, Any]`, so write every mapper as `Tree.Mapper[A, Any]`.
- `Tree.Compare` is a helper type that the testkit itself does not use.

With no mappers and no operators, every depth above 0 produces no values at all. A tree of sums, products and increments over the atoms 1, 2 and 3:

```scala
val arithmetic: Tree[Int] = Tree[Int](
  atoms = List(Gen.const(1), Gen.const(2), Gen.const(3)),
  mappers = List(Tree.Mapper[Int, Any]((a: Int, _: Any) => a + 1, Gen.const[Any](()))),
  operators = List((a: Int, b: Int) => a + b, (a: Int, b: Int) => a * b),
  depth = 2
)

val anyExpression: Gen[Any, Int] = arithmetic.gen

val nestedOnce: Gen[Any, Int] = arithmetic.anyTree(1)
```

## See Also

- [How to test a CLI app](how-to-test-a-cli.md) builds a complete test spec step by step.
- [Commands](commands.md) and [Options](options.md) describe the values under test.
- [HelpDoc](helpdoc.md) describes the help document that `CliAssertion.assertHelpDoc` compares.
- [CLI config](cli-config.md) describes `CliConfig`.
- [Validation and Errors](validation-and-errors.md) describes `ValidationError` and `CliError`.
