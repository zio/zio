# How to test a CLI app

> This guide writes a [ZIO Test](https://zio.dev/reference/test/) spec for a **ZIO CLI** app with the `zio-cli-testkit` module. By the end, the spec checks that:

1. The usage line and the help document describe the command correctly.
2. Valid arguments, both hand-picked and randomly generated, produce the right value, and invalid arguments are rejected.
3. `--help` is recognized and prints the option names.
4. The app returns the right result, and parse errors and errors from the app's own code are reported as different `CliError` cases.

A **spec** is a ZIO Test object that holds tests, a **suite** groups tests under a name, and each **test** runs one check. Every assertion used here is described in the [testkit reference](testing.md).

## Prerequisites

Before starting, make sure you have:

- The `zio-cli-testkit` dependency in the `Test` configuration of your build. The dependency line is in [Modules](index.md#modules). sbt 1.5 and later run ZIO Test specs without extra setup; on older sbt versions, add `testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")` to `build.sbt`.
- A test source file, for example `src/test/scala/GreetCliSpec.scala`.
- A `Command` and a `CliApp` to test. This guide uses a small greeting app.

## Step 1: Define the Command and the App

We start with the code under test. The command `GreetCli.greet` has two required options, `--name` (text) and `--times` (integer). The app `GreetCli.app` returns a greeting, and a second app, `GreetCli.failingApp`, always fails so we can test the execution error path:

```scala
import zio.ZIO
import zio.cli._
import zio.cli.testkit._
import zio.cli.testkit.CliAssertion._
import zio.test._

object GreetCli {

  val greet: Command[(String, BigInt)] =
    Command("greet", Options.text("name") ++ Options.integer("times"))

  val app: CliApp[Any, Nothing, String] =
    CliApp.make("greet", "1.0.0", HelpDoc.Span.text("Greets a person."), greet) { case (name, times) =>
      ZIO.succeed(s"Hello, $name x$times")
    }

  val failingApp: CliApp[Any, String, String] =
    CliApp.make[Any, String, (String, BigInt), String](
      "greet",
      "1.0.0",
      HelpDoc.Span.text("Greets a person."),
      greet
    ) { case (_, _) =>
      ZIO.fail("boom")
    }
}
```

Two details matter in every later step. First, the `import zio.cli.testkit.CliAssertion._` line brings the implicit `CliConfig` into scope, which the parsing assertions need; see [Imports and the Implicit CliConfig](testing.md#imports-and-the-implicit-cliconfig). Second, an argument list passed to a `Command` starts with the command name, while an argument list passed to a `CliApp` does not, because `CliApp#run` adds the name itself. That is why the command tests below start with `"greet"` and the app tests do not.

## Step 2: Check the Usage Line and the Help Document

`CliAssertion.assertSynopsis` compares the rendered usage line, and `CliAssertion.assertHelpDoc` compares the help document of the command. The expected usage line uses the placeholders that ZIO CLI prints for each value type, here `<text>` and `<integer>`:

```scala
val documentationTests =
  suite("documentation")(
    test("the usage line lists both options") {
      assertSynopsis(GreetCli.greet, "greet --name <text> --times <integer>")
    },
    test("the app uses the greet command's help document") {
      assertHelpDoc(GreetCli.app, GreetCli.greet.helpDoc)
    }
  )
```

These assertions return a `TestResult` directly, so no effect is involved. See [Synopsis and Help Assertions](testing.md#synopsis-and-help-assertions) for the other forms.

## Step 3: Test Parsing with Fixed Inputs

Next, we check how the command parses hand-picked argument lists. `CliAssertion.assertCommand` takes `CliRepr` pairs of arguments and the expected value. `CliAssertion.assertCommandSuccess` takes pairs of arguments and an `Assertion` on the whole `Either[ValidationError, A]`, which makes it the method for inputs that must be rejected:

```scala
val validInputs: Gen[Any, CliRepr[List[String], (String, BigInt)]] =
  Gen.fromIterable(
    List(
      CliRepr(List("greet", "--name", "Ada", "--times", "2"), ("Ada", BigInt(2))),
      CliRepr(List("greet", "--times", "1", "--name", "Lin"), ("Lin", BigInt(1)))
    )
  )

val invalidInputs: Gen[Any, CliRepr[List[String], Assertion[Either[ValidationError, (String, BigInt)]]]] =
  Gen.fromIterable(
    List(
      // --times is required and missing
      CliRepr(List("greet", "--name", "Ada"), Assertion.isLeft),
      // --times is not an integer
      CliRepr(List("greet", "--name", "Ada", "--times", "two"), Assertion.isLeft)
    )
  )

val parsingTests =
  suite("parsing")(
    test("valid arguments produce the parsed values") {
      assertCommand(GreetCli.greet, validInputs)
    },
    test("invalid arguments produce a validation error") {
      assertCommandSuccess(GreetCli.greet, invalidInputs)
    }
  )
```

Both methods return a ZIO effect, and returning that effect from `test` is enough. The check runs every input from the generator and fails if any of them does not match. See [Parsing Assertions](testing.md#parsing-assertions).

## Step 4: Test Parsing with Generated Inputs

Fixed inputs cover the cases we think of. A random generator covers many more: here every sample is a random alphanumeric name and a random count, and the expected value is built from the same random parts:

```scala
val randomInputs: Gen[Any, CliRepr[List[String], (String, BigInt)]] =
  (Gen.alphaNumericStringBounded(1, 10) <*> Gen.int(1, 100)).map { case (name, times) =>
    CliRepr(List("greet", "--name", name, "--times", times.toString), (name, BigInt(times)))
  }

val propertyTests =
  suite("property-based parsing")(
    test("any alphanumeric name and positive count parse") {
      assertCommand(GreetCli.greet, randomInputs)
    }
  )
```

ZIO Test draws 200 samples by default; change the count with `TestAspect.samples`. When ZIO Test finds a failing sample, it shrinks it to a smaller failing input before reporting it.

## Step 5: Test Help Output

There are two ways to check `--help`. `CliAssertion.assertCommandAll` checks that the command recognizes the built-in option, and `CliAssertion.outputContains` checks the text the app prints.

`--help` does not produce a value of the command's type, so `CliAssertion.assertCommand` cannot check it: it fails the test with a `java.lang.Exception` that has no message. `CliAssertion.assertCommandAll` passes the result as a [`TestReturn`](testing.md#testreturn), where every built-in option appears as `TestReturn.Help()`:

```scala
val helpInput: CliRepr[List[String], Assertion[Either[ValidationError, TestReturn[(String, BigInt)]]]] =
  CliRepr(List("greet", "--help"), Assertion.isRight(Assertion.equalTo(TestReturn.Help())))

val helpTests =
  suite("help")(
    test("--help returns the built-in help directive") {
      assertCommandAll(GreetCli.greet, Gen.const(helpInput))
    },
    test("--help prints the option names") {
      outputContains(GreetCli.app, Gen.const(CliRepr(List("--help"), Seq("--name", "--times"))))
    }
  )
```

For `--help`, the app prints this help screen as a single console entry:

```text
                         __
   ____ _________  ___  / /_
  / __ `/ ___/ _ \/ _ \/ __/
 / /_/ / /  /  __/  __/ /_
 \__, /_/   \___/\___/\__/
/____/

greet 1.0.0 -- Greets a person.

USAGE

  $ greet --name <text> --times <integer>

OPTIONS

  --name <text>
    A user-defined piece of text.

  --times <integer>
    An integer.
```

`CliAssertion.outputContains` passes when each expected string is a substring of one captured console entry, so `--name` and `--times` both match. The captured output is not cleared between samples, so keep one input per `CliAssertion.outputContains` call when the inputs print different text. See [Console Output Assertion](testing.md#console-output-assertion).

## Step 6: Test Results and Failures

`CliAssertion.assertCliAppValues` checks that the app succeeds with an expected value. `CliAssertion.assertCliApp` passes the full `Either[CliError[E], A]` from `CliApp#run` to an `Assertion`, which lets us tell the two kinds of failure apart:

- `CliError.Parsing` means the arguments did not parse. The app prints only the error message, for example `Expected to find --times option.`, and stops before it runs its own code.
- `CliError.Execution` means the app's own code failed. It holds the error the app returned.

The following tests cover a successful run and both failure kinds:

```scala
val parseFailure: Gen[Any, CliRepr[List[String], Assertion[Either[CliError[Nothing], String]]]] =
  Gen.const(
    CliRepr(
      List("--name", "Ada"),
      Assertion.assertion[Either[CliError[Nothing], String]]("is a parsing error") {
        case Left(CliError.Parsing(_)) => true
        case _                         => false
      }
    )
  )

val executionFailure: Gen[Any, CliRepr[List[String], Assertion[Either[CliError[String], String]]]] =
  Gen.const(CliRepr(List("--name", "Ada", "--times", "2"), Assertion.isLeft(Assertion.equalTo(CliError.Execution("boom")))))

val appTests =
  suite("app")(
    test("valid arguments return the greeting") {
      assertCliAppValues(GreetCli.app, Gen.const(CliRepr(List("--name", "Ada", "--times", "2"), "Hello, Ada x2")))
    },
    test("missing --times is a parsing error") {
      assertCliApp(GreetCli.app, parseFailure)
    },
    test("an error from the app is an execution error") {
      assertCliApp(GreetCli.failingApp, executionFailure)
    }
  )
```

Do not use `CliAssertion.assertCliAppZIO` for parse errors: it reports only `CliError.Execution` to the comparison and fails the whole check on any other error. Do not pass `--help` to the app assertions either; `CliApp#run` returns `None` for it, and they fail with a message-less `java.lang.Exception`. See [App Assertions](testing.md#app-assertions).

## Step 7: Assemble and Run the Spec

Finally, we put the suites in one spec object named after the app:

```scala
object GreetCliSpec extends ZIOSpecDefault {
  def spec =
    suite("GreetCli")(documentationTests, parsingTests, propertyTests, helpTests, appTests)
}
```

Run it with your build's test task, for example `sbt test`, or `sbt "testOnly GreetCliSpec"` for this spec alone.

## Troubleshooting

When a test fails for a reason unrelated to your app, check these common causes:

- `could not find implicit value for parameter cliConfig`: the file does not import `zio.cli.testkit.CliAssertion._`, or it imports both `CliAssertion._` and `GenerationAssertion._` with wildcards.
- `java.lang.Exception: null`: a built-in option such as `--help` reached `CliAssertion.assertCommand`, `CliAssertion.assertCommandSuccess` or one of the app assertions. Use `CliAssertion.assertCommandAll` or `CliAssertion.outputContains`.
- A valid input fails as a parsing error: a command test is missing the command name as its first argument, or an app test includes it.

## See Also

- [Testing CLI Applications](testing.md) lists every testkit method with its exact behaviour.
- [Commands](commands.md) and [Options](options.md) describe the values under test.
- [Built-in Commands](built-in-commands.md) describes `--help`, shell completion and the wizard.
- [Validation and Errors](validation-and-errors.md) describes `ValidationError` and `CliError`.
