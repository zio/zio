# How to Accumulate Validation Errors

> Validate a form and report every problem at once using Validation, instead of stopping at the first error.

In this guide, we validate a sign-up form and report every invalid field in a single pass. By the end, you will have a `signUp` function that returns all errors together, a way to mix dependent checks into it, and a way to turn the result into an `Either` for the rest of your program.

We start with one validation per field, combine them, handle a field whose check has two dependent steps, and finally validate a list of values.

## The Problem

Validating with `Either` stops at the first failure. Each step of a `for` comprehension depends on the success of the previous one, so later fields are never checked once an earlier one fails:

```scala
final case class SignUp(name: String, email: String, age: Int)

def checkName(name: String): Either[String, String] =
  Either.cond(name.trim.nonEmpty, name, "name is blank")

def checkEmail(email: String): Either[String, String] =
  Either.cond(email.contains("@"), email, "email is missing @")

def checkAge(age: Int): Either[String, Int] =
  Either.cond(age >= 18, age, "age is under 18")

def signUp(name: String, email: String, age: Int): Either[String, SignUp] =
  for {
    n <- checkName(name)
    e <- checkEmail(email)
    a <- checkAge(age)
  } yield SignUp(n, e, a)
```

A user who enters a blank name, an invalid email and an age of 12 sees "name is blank", fixes it, submits again, and only then sees "email is missing @". The three checks do not depend on each other, so there is no reason to hide the later errors.

In this guide, we replace `Either` with `Validation`, which keeps the errors of every independent check.

## Prerequisites

We need the `zio-prelude` dependency:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC49"
```

We import the package and define the types for the form and for its errors. Modeling the errors as a sealed trait keeps them structured, so the caller can react to them instead of parsing text:

```scala
import zio.prelude._

final case class SignUp(name: String, email: String, age: Int)

sealed trait FormError

object FormError {
  final case class Blank(field: String)                   extends FormError
  final case class Invalid(field: String, reason: String) extends FormError
}

import FormError._
```

## Validating Each Field

A `Validation[E, A]` is either a success with an `A` or a failure with one or more `E` errors. `Validation.fromPredicateWith` builds one from a value and a predicate, with the error to use when the predicate is false. We write one function per field:

```scala
def validateName(name: String): Validation[FormError, String] =
  Validation.fromPredicateWith[FormError, String](Blank("name"))(name.trim)(_.nonEmpty)

def validateEmail(email: String): Validation[FormError, String] =
  Validation.fromPredicateWith[FormError, String](Invalid("email", "missing @"))(email)(_.contains("@"))
```

The first parameter list takes the error, the second takes the value, and the third takes the predicate. Each function returns a success with the (cleaned) value, or a failure with its own error.

## Combining Independent Validations

`Validation.validateWith` takes several validations and a function that builds the result. It runs all of them. If more than one fails, the failure holds the errors of all of them in order, and the function is called only when everything succeeded. We add the `age` validation, and then the `signUp` function that combines the three:

```scala
def validateAge(age: Int): Validation[FormError, Int] =
  Validation.fromPredicateWith[FormError, Int](Invalid("age", "under 18"))(age)(_ >= 18)

def signUp(name: String, email: String, age: Int): Validation[FormError, SignUp] =
  Validation.validateWith(
    validateName(name),
    validateEmail(email),
    validateAge(age)
  )(SignUp.apply)
```

With valid input, we get the success with the `SignUp` value. With three bad fields, we get all three errors in one failure:

```scala
signUp("Ada", "ada@example.org", 36)
// res2: Validation[FormError, SignUp] = Success(
//   log = IndexedSeq(),
//   value = SignUp(name = "Ada", email = "ada@example.org", age = 36)
// )

signUp(" ", "ada", 12)
// res3: Validation[FormError, SignUp] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(Blank(name), Invalid(email,missing @), Invalid(age,under 18))
// )
```

When we need the values as a tuple instead of a function result, `Validation.validate` returns `Validation[E, (A, B, C)]`. For two validations, the `Validation#zipPar` operator (symbolic alias `<&>`) does the same.

## Handling a Check with Dependent Steps

Some fields need two steps where the second only makes sense after the first, such as parsing text to a number and then checking the range. `Validation#flatMap` expresses this, and it short-circuits: if the parse fails, the range check does not run. The two steps of one field are dependent, while the fields remain independent:

```scala
def parseAge(text: String): Validation[FormError, Int] =
  Validation
    .fromOptionWith[FormError, Int](Invalid("age", "not a number"))(text.toIntOption)
    .flatMap(validateAge)
```

We use `parseAge` in a variant of `signUp` that takes the raw text of the age field. A bad age text still adds exactly one age error, while the other fields are checked as before:

```scala
Validation.validateWith(validateName(" "), validateEmail("ada"), parseAge("x"))(SignUp.apply)
// res4: ZValidation[Nothing, FormError, SignUp] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(Blank(name), Invalid(email,missing @), Invalid(age,not a number))
// )
```

:::warning
Do not use a `for` comprehension to combine independent validations. A `for` comprehension calls `Validation#flatMap`, so it stops at the first failure and reports one error, just like `Either`. Use `Validation.validateWith`, `Validation.validate` or `Validation#zipPar` for independent checks.
:::

## Validating a Collection

To validate every element of a collection, `Validation.validateAll` takes a collection of validations and returns one validation of the collection. On failure it holds the errors of all invalid elements:

```scala
Validation.validateAll(List(parseAge("20"), parseAge("x"), parseAge("5")))
// res5: ZValidation[Nothing, FormError, List[Int]] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(Invalid(age,not a number), Invalid(age,under 18))
// )
```

Use `ForEach#forEach` instead when each element still needs to be turned into a validation first, as [How to Traverse Collections with ForEach](./traverse-with-foreach.md) shows.

## Using the Result

A `Validation` is a value, so we decide at the edge of the program what to do with it. To handle both outcomes in one place, use `Validation#fold`, which receives all errors as a `NonEmptyChunk` or the success value. To translate the errors to another type, for example to messages for the user, use `Validation#mapError`. To pass the result to code that expects an `Either`, use `Validation#toEither`:

```scala
signUp(" ", "ada", 12).fold(errors => s"${errors.size} problems", form => s"welcome ${form.name}")
// res6: String = "3 problems"

signUp(" ", "ada", 12).mapError {
  case Blank(field)           => s"$field is blank"
  case Invalid(field, reason) => s"$field: $reason"
}
// res7: ZValidation[Nothing, String, SignUp] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(name is blank, email: missing @, age: under 18)
// )

signUp(" ", "ada", 12).toEither
// res8: Either[zio.NonEmptyChunk[FormError], SignUp] = Left(
//   value = NonEmptyChunk(Blank(name), Invalid(email,missing @), Invalid(age,under 18))
// )
```

Existing results of other types convert in: `Validation.fromEither` takes an `Either` with a single error, `Validation.fromOptionWith` takes an `Option` and the error for `None`, and `Validation.fromTry` takes a `Try`.

## Going Further

- Smart newtypes return a `Validation` from `make`, so they plug into `Validation.validateWith` directly. See [How to Create Smart Newtypes](./create-smart-newtypes.md).
- `ZValidation` adds a log of warnings next to the errors. See [ZValidation](../functional-data-types/zvalidation.md).
- Read [Validation](../functional-data-types/validation.md) for the full set of operators.
