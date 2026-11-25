# Validator

> A `Validator[A]` packages an arbitrary check on values of type `A` so that a Scala 3 newtype can run it at compile time. It exists for the cases an [`Assertion`](./assertions.md) cannot express, such as checking that a string is a palindrome, while keeping the compile-time validation that smart newtypes provide.

:::note[Scala 3 only]
`Validator`, `NewtypeCustom` and `SubtypeCustom` are defined in the Scala 3 sources of `zio-prelude`. The code on this page is shown in plain `scala` blocks because the documentation build compiles with Scala 2.13. In Scala 2, use `Newtype#assertCustom` instead, as described in [Assertions](./assertions.md).
:::

## Why Validators Exist

In Scala 3 a smart newtype declares an `inline def assertion`, and the compiler reads that assertion as a tree while compiling. This works only for the predefined `Assertion` constructors, because the macro must be able to rebuild the predicate from the syntax tree. An arbitrary function `A => Either[AssertionError, Unit]` cannot be inspected that way.

A `Validator` solves this by moving the function into a separate compiled object. The macro then calls that already-compiled object while compiling the code that uses the newtype. For the same reason, a validator must live in a different file, and be compiled before, the newtype that uses it.

## Defining a Validator

A validator extends the abstract class `Validator[A]` and passes the check to its constructor. The check returns `Right(())` for a valid value and `Left` with an `AssertionError` for an invalid one. Type `A` needs a `scala.quoted.FromExpr` instance, which the standard library provides for literals such as `String`, `Int` and `Boolean`:

```scala
import zio.prelude.{AssertionError, Validator}

object PalindromeValidator
    extends Validator[String](str =>
      if (str.reverse == str) Right(())
      else Left(AssertionError.failure("isPalindrome"))
    )
```

The class offers two members. `Validator#validate` runs the check on a run-time value and returns the `Either`. `Validator#validateInlineImpl` is a macro implementation that reads its argument as a compile-time literal and calls the same check. On failure it aborts compilation with the rendered `AssertionError`.

## Using a Validator in a Newtype

To connect the validator to a newtype, extend `NewtypeCustom` or `SubtypeCustom` and implement the two members that `Newtype` normally implements through its assertion. `validate` is used for run-time constructors such as `make` and `makeAll`, and `validateInline` is an inline method that splices the validator's macro:

```scala
import zio.prelude.SubtypeCustom

type Palindrome = Palindrome.Type
object Palindrome extends SubtypeCustom[String] {
  override protected def validate(value: String) =
    PalindromeValidator.validate(value)

  override protected inline def validateInline(inline value: String) =
    ${ PalindromeValidator.validateInlineImpl('value) }
}
```

The two members must agree. `Newtype` is itself a subclass of `NewtypeCustom` that fills these members in from an `Assertion`, and `Subtype` extends `Newtype`. `SubtypeCustom` is the `NewtypeCustom` variant whose `Type` is a subtype of `A`.

## Constructing Values

With the newtype in place, `Palindrome.apply` checks literal arguments at compile time, and `Palindrome.make` checks run-time values and returns a `Validation`:

```scala
Palindrome("racecar")          // compiles

Palindrome("hello")            // does not compile: hello did not satisfy isPalindrome

val input: String = scala.io.StdIn.readLine()
Palindrome.make(input)         // Validation[String, Palindrome]
```

Passing a value that is not a literal to `Palindrome.apply` does not compile, because the macro uses `Expr#valueOrAbort`. Use `Palindrome.make` for those values, or `Palindrome.wrap` inside the object to skip validation when you can prove validity yourself.

## Reporting Failures

The error text comes from `AssertionError`. `AssertionError.failure("isPalindrome")` produces the message `hello did not satisfy isPalindrome` for the value `hello`. Choose a short condition name that reads well after the words "did not satisfy", as the predefined assertions do. See [Assertion Errors](./assertions.md#assertion-errors) for the full set of operations on errors.
