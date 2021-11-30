---
id: test-effects
title:  "How to Test effects?"
---

## How zio-test was designed

`zio-test` is designed around the idea of _making tests first-class objects_. What it means is that tests (and other accompanying concepts like assertions) become ordinary values that can be passed around, transformed, and composed together.

This approach allows for greater flexibility compared to some other testing frameworks where tests and additional logic around tests had to be put into callbacks so that framework could make use of them.

This approach also fits better with other `ZIO` concepts like `ZManaged` which can only be used within a scoped block of code. This also created a mismatch between `BeforeAll`, `AfterAll` callback-like methods when there were resources that should be opened and closed during test suite execution.

Another thing worth pointing out is that tests being values are also effects. Implications of this design are far-reaching.

- First, the well-known problem of testing asynchronous value is gone. Whereas in other frameworks you have to somehow "run" your effects and at best wrap them in `scala.util.Future` because blocking would eliminate running on ScalaJS, `zio-test` expects you to create `ZIO` objects. There is no need for indirect transformations from one wrapping object to another.

- Second, because our tests are ordinary `ZIO` values we don't need to turn to a testing framework for things like retries, timeouts, and resource management. We can solve all those problems with the full richness of functions that `ZIO` exposes.

## Constructing tests

All below code assumes that you have imported `zio.test._`

The backbone of `zio-test` is the `Spec[L, T]` class. Every spec is labeled with `L`, and can be a suite that contains other specs, or a test of type `T`.

The most common and easy way to create suites is to use the `suite` function. For testing of pure functions and for effectful testing there is `test`:

```scala mdoc
import zio.test._
import zio.Clock.nanoTime
import Assertion.isGreaterThan

val clockSuite = suite("clock") (
  test("time is non-zero") {
    assertM(Live.live(nanoTime))(isGreaterThan(0L))
  }
)
```

As you can see the whole suite was assigned to `clockSuite` val. As it was said suites can contain other suites, so we can aggregate them as much as needed.

For example, we can have multiple suites that test external HTTP APIs and one big suite that will aggregate them all:


```scala mdoc
import zio.test._
import Assertion._

val paymentProviderABCSuite  =
  suite("ABC payment provider tests") {
    test("Your test")(
      assert("Your value")(Assertion.isNonEmptyString)
    )
  }
val paymentProviderXYZSuite  =
  suite("XYZ payment provider tests") {
    test("Your other test")(
      assert("Your other value")(Assertion.isNonEmptyString)
    )
  }
val allPaymentProvidersTests =
  suite("All payment providers tests")(
    paymentProviderABCSuite,
    paymentProviderXYZSuite
  )
```

Real tests that run some logic and return testing result are created mostly with `test` function. It expects two arguments, first one will be the label of test which will be used for visual reporting back to the user, and an assertion of type `ZIO[R, E, TestResult]`.

This means writing test in `zio-test` mostly gets down to creating a `ZIO` object that will produce `TestResult`.

There is another variant of function for creating a test that are pure called simply `test`. It expects a thunk of code that will just return a `TestResult` without packing it into `ZIO`.

### Assertions - creating TestResults

As it was already mentioned, tests should return `TestResult`. The most common way to produce a `TestResult` is to resort to `assert` or its effectful counterpart `assertM`. Both of them accept a value of type `A` (effectful version wrapped in a `ZIO`) and an `Assertion[A]`.

To create `Assertion[A]` object one can use functions defined under `zio.test.Assertion`. There are already a number of useful assertions predefined like `equalTo`, `isFalse`, `isTrue`, `contains`, `throws` and more.

What is really useful in assertions is that they behave like boolean values and can be composed with operators
known from operating on boolean values like and (`&&`), or (`||`), negation (`negate`):

```scala mdoc
import zio.test.Assertion

val assertionForString: Assertion[String] = 
  Assertion.containsString("Foo") && Assertion.endsWithString("Bar")
```

What's more, assertions also compose with each other allowing for doing rich diffs not only simple value to value comparison:

```scala mdoc
import zio.test.Assertion.{isRight, isSome,equalTo, hasField}

test("Check assertions") {
  assert(Right(Some(2)))(isRight(isSome(equalTo(2))))
}
```

Here we're checking deeply nested values inside an `Either` and `Option`. Because `Assertion`s compose this is not a problem. All layers are being peeled off tested for the condition until the final value is reached.

Here the expression `Right(Some(2))` is of type `Either[Any, Option[Int]]`and our assertion `isRight(isSome(equalTo(2)))` is of type `Assertion[Either[Any, Option[Int]]]`

```scala mdoc:reset-object
import zio.test._
import zio.test.Assertion.{isRight, isSome,equalTo, isGreaterThanEqualTo, not, hasField}

final case class Address(country:String, city:String)
final case class User(name:String, age:Int, address: Address)

test("Rich checking") {
  assert(
    User("Jonny", 26, Address("Denmark", "Copenhagen"))
  )(
    hasField("age", (u:User) => u.age, isGreaterThanEqualTo(18)) &&
    hasField("country", (u:User) => u.address.country, not(equalTo("USA")))
  )
}
```

There is also an easy way to test an object's data for certain assertions with `hasField` which accepts besides a name, a mapping function from object to its tested property, and `Assertion` object which will validate this property. Here our test checks if a person has at least 18 years and is not from the USA.

What is nice about those tests is that test reporters will tell you exactly which assertion was broken. Let's say we would change `isGreaterThanEqualTo(18)` to `isGreaterThanEqualTo(40)` which will fail. Printout
on the console will be a nice detailed text explaining what exactly went wrong:

```bash
[info]       User(Jonny,26,Address(Denmark,Copenhagen)) did not satisfy (hasField("age", _.age, isGreaterThanEqualTo(45)) && hasField("country", _.country, not(equalTo(USA))))
[info]       26 did not satisfy isGreaterThanEqualTo(45)
```

Having this all in mind, probably the most common and also most readable way of structuring tests is to pass a for-comprehension to `test` function and yield a call to `assert` function.

```scala mdoc
import zio.{test => _, _}
import zio.test._
import Assertion._

test("Semaphore should expose an available number of permits") {
  for {
    s         <- Semaphore.make(1L)
    permits   <- s.available.commit
  } yield assert(permits)(equalTo(1L))
}
```
