---
id: test-effects
title:  "How to Test effects?"
---

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
