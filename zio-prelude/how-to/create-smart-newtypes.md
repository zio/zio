# How to Create Smart Newtypes

> Replace raw String and Int parameters with smart newtypes that validate at compile time and at run time.

In this guide, we replace the raw `String` and `Int` parameters of a sign-up function with smart newtypes. By the end, you will have a `Username`, an `Age` and a `Plan` type that reject invalid values, accept literals checked by the compiler, and validate run-time input with an error message.

We build one type at a time, then construct values in the four ways a smart newtype offers.

## The Problem

A sign-up function that takes primitive types accepts any combination of values, including nonsense. The compiler cannot tell the arguments apart, so swapping two `String` arguments is silent:

```scala
def signUp(username: String, plan: String, age: Int): String =
  s"$username ($age) signed up for $plan"

val welcome = signUp("pro", "ada_l", 17)
```

This call compiles even though the plan and the username are swapped and the age is below the minimum. Every function that receives these values has to check them again, or trust that someone did.

In this guide, we move the checks into types, so a value that exists is a value that is valid.

## Prerequisites

We need the `zio-prelude` dependency:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC49"
```

We import the package and the assertions that describe valid values:

```scala
import zio.prelude._
import zio.prelude.Assertion._
```

## Choosing Between Newtype and Subtype

A newtype wraps an existing type and has no run-time cost. We extend `Newtype[A]` when the new type must be fully independent of `A`: a `Username` should not be usable as a `String` without an explicit conversion. We extend `Subtype[A]` when the new type should still be usable as an `A`: an `Age` can be added to or compared with other `Int` values without unwrapping. In both cases we define an object and a type alias to its `Type`.

## Defining a Smart Newtype with Compound Rules

An `assertion` makes a newtype smart. Assertions combine with `&&`, `||` and `!`. In Scala 2, the assertion is a `def` without a type annotation, wrapped in `assert`. A username must be 3 to 16 characters long and contain only lowercase letters, digits and underscores:

```scala
object Username extends Newtype[String] {
  override def assertion = assert {
    hasLength(between(3, 16)) && matches("[a-z0-9_]+")
  }
}
type Username = Username.Type
```

The code above:

- `hasLength(between(3, 16))` — requires the string length to be between 3 and 16 inclusive
- `matches("[a-z0-9_]+")` — requires the whole string to match the regular expression
- `&&` — requires both assertions to hold

The age rule is a single range, and we want to keep using the type as an `Int`, so the next type is a `Subtype`. In Scala 3 the macro API differs, so the definition is shown in tabs:

  

**Scala 2**

```scala
object Age extends Subtype[Int] {
  override def assertion = assert { between(18, 120) }
}
type Age = Age.Type
```

  
  

**Scala 3**

```scala
object Age extends Subtype[Int] {
  override inline def assertion: Assertion[Int] =
    between(18, 120)
}
type Age = Age.Type
```

  

A value that must be one of a few choices uses `||`:

```scala
object Plan extends Newtype[String] {
  override def assertion = assert { equalTo("free") || equalTo("pro") }
}
type Plan = Plan.Type
```

## Constructing Values from Literals

When we pass a literal to `apply`, the compiler checks the assertion while compiling. A valid literal produces the new type directly, with no `Validation` to handle. A literal that breaks the assertion, such as `Age(17)`, is a compilation error that names the assertion that failed:

```scala
val age: Age = Age(30)
// age: Age = 30

val plan: Plan = Plan("pro")
// plan: Plan = "pro"
```

To create several values at once, pass them all to `apply`. The result is a `NonEmptyChunk`:

```scala
Age(20, 30, 40)
// res2: zio.NonEmptyChunk[Age.Type] = NonEmptyChunk(20, 30, 40)
```

## Constructing Values from Run-Time Input

Input from a form or a file is not a literal, so the compiler cannot check it. In Scala 2, passing such a value to `apply` is a compilation error. We use `make`, which checks the assertion while the program runs and returns a `Validation`:

```scala
Username.make("ada_l")
// res3: ZValidation[Nothing, String, Username.Type] = Success(
//   log = IndexedSeq(),
//   value = "ada_l"
// )

Username.make("ab")
// res4: ZValidation[Nothing, String, Username.Type] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(ab did not satisfy hasLength(between(3, 16)))
// )

Username.make("Ada Lovelace")
// res5: ZValidation[Nothing, String, Username.Type] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(Ada Lovelace did not satisfy matches([a-z0-9_]+))
// )
```

Notice that `&&` keeps the failures of both sides when both fail. Here `"ab"` fails only the length check and `"Ada Lovelace"` fails only the character check, so each result holds one message. Two failing fields of one form are both reported when we combine several `make` results, which [How to Accumulate Validation Errors](./accumulate-validation-errors.md) explains.

For a collection of input values, `makeAll` validates every element and accumulates the errors of all invalid elements:

```scala
val allValid   = Age.makeAll(List(20, 30))
val someFailed = Age.makeAll(List(20, 17, 200))
```

The first call succeeds with all the values. The second fails, and its error describes every element that broke the assertion, not only the first.

`makeAll` is the bulk constructor that checks every element at run time. On Scala 2, the `wrapAll` method is rejected at compile time for smart newtypes, because it would skip the check. On Scala 3, `wrapAll` compiles but skips the check, so do not use it on values that need validation.

## Using the New Types

We use the types as parameters, and the earlier mistake no longer compiles. The function now takes a `Username`, a `Plan` and an `Age`:

```scala
def signUp(username: Username, plan: Plan, age: Age): String =
  s"${Username.unwrap(username)} ($age) signed up for ${Plan.unwrap(plan)}"

signUp(Username("ada_l"), Plan("pro"), Age(30))
// res6: String = "ada_l (30) signed up for pro"
```

Swapping the first two arguments is now a type error, because a `Plan` is not a `Username`. To get the underlying value out of a `Newtype`, we use `unwrap`. An `Age` is a `Subtype`, so `age` was interpolated as an `Int` without conversion, and `Age(30) > Age(20)` works directly.

## Providing Instances

A `Subtype` is an `Int`, so the instances of `Int` apply to it. A `Newtype` is unrelated to its underlying type, so it has no instances until we provide them. Inside the object, the protected `derive` method reuses an instance of the underlying type. Here we add `Equal` and `Debug` to `Username`:

```scala
import zio.prelude._
import zio.prelude.Assertion._

object Username extends Newtype[String] {
  override def assertion = assert {
    hasLength(between(3, 16)) && matches("[a-z0-9_]+")
  }

  implicit val equal: Equal[Username] = derive
  implicit val debug: Debug[Username] = derive
}
type Username = Username.Type
```

With the instances in scope, `===` and `render` work on usernames:

```scala
Username("ada_l") === Username("ada_l")
// res8: Boolean = true

Username("ada_l").render
// res9: String = "\"ada_l\""
```

## Going Further

- Read [Smart Newtypes](../newtypes/smart-newtypes.md) for how to bypass the check inside the object with `wrap`, and [Operators and Instances](../newtypes/operators-and-instances.md) for more on instances.
- See [How to Accumulate Validation Errors](./accumulate-validation-errors.md) to combine the `make` results of a whole form.
