---
id: assertion
title: "Assertion"
---

Assertions are used to make sure that the assumptions on computations are exactly what we expect them to be. They are _executable checks_ for a property that must be true in our code. Also, they can be seen as a _specification of a program_ and facilitate understanding of programs.

Assume we have a function that concatenates two strings. One simple property of this function would be "the sum of the length of all inputs should be equal to the length of the output". Let's see an example of how we can make an assertion about this property:

```scala mdoc:compile-only
import zio.test._

test("The sum of the lengths of both inputs must equal the length of the output") {
  check(Gen.string, Gen.string) { (a, b) =>
    assert((a + b).length)(Assertion.equalTo(a.length + b.length))
  }
}
```

The syntax of assertion in the above code, is `assert(expression)(assertion)`. The first section is an expression of type `A` which is _result_ of our computation and the second one is the expected assertion of type `Assertion[A]`.

## Using Assertions with ZIO Tests

We have two methods for writing test assertions:

1. **`assert`** and **`assertM`**
2. **`assertTrue`**

The first one is the old way of asserting ordinary values and also ZIO effects. The second method, which is called _smart assertion_, has a unified logic for testing both ordinary values and ZIO effects. We encourage developers to use the smart assertion method, which is much simpler.

### Classic Old-fashioned Assertions


In the following example, we use the `equalTo` assertion, which asserts the equality of two values:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

val result:  TestResult                      = assert(1 + 1)(Assertion.equalTo(2))
val resultM: ZIO[Any, Throwable, TestResult] = assertM(ZIO(1 + 1))(Assertion.equalTo(2))
```

1. This test can be written directly as follows:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("sum") {
  assert(1 + 1)(Assertion.equalTo(2))
}
```

2. If we are testing an effect, we should use the `assertM` function:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("updating ref") {
  val value = for {
    r <- Ref.make(0)
    _ <- r.update(_ + 1)
    v <- r.get
  } yield v
  assertM(value)(Assertion.equalTo(1))
}
```

3. Having this all in mind, probably the most common and also most readable way of structuring tests is to pass a for-comprehension to `test` function and yield a call to `assert` function.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("updating ref") {
  for {
    r <- Ref.make(0)
    _ <- r.update(_ + 1)
    v <- r.get
  } yield assert(v)(Assertion.equalTo(v))
} 
```

### Smart Assertions

The smart assertion is a simpler way to assert both ordinary values and effectful values. It uses the `assertTrue` function, which uses macro under the hood.

1. Testing ordinary values:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("sum"){
  assertTrue(1 + 1 == 2)
}
```

2. Testing effectful values:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("updating ref") {
  for {
    r <- Ref.make(0)
    _ <- r.update(_ + 1)
    v <- r.get
  } yield assertTrue(v == 1)
}
```

## Assertions

To create `Assertion[A]` object one can use functions defined under `zio.test.Assertion`. There are already a number of useful assertions predefined like `equalTo`, `isFalse`, `isTrue`, `contains`, `throws` and more.

What is really useful in assertions is that they behave like boolean values and can be composed with operators known from operating on boolean values like and (`&&`), or (`||`), negation (`negate`):

```scala mdoc:compile-only
import zio.test.Assertion

val assertionForString: Assertion[String] = 
  Assertion.containsString("Foo") && Assertion.endsWithString("Bar")
```

What's more, assertions also compose with each other allowing for doing rich diffs not only simple value to value comparison:

```scala mdoc:compile-only
import zio.test._
import zio.test.Assertion.{isRight, isSome, equalTo, hasField}

test("Check assertions") {
  assert(Right(Some(2)))(isRight(isSome(equalTo(2))))
}
```

Here we're checking deeply nested values inside an `Either` and `Option`. Because `Assertion`s compose this is not a problem. All layers are being peeled off tested for the condition until the final value is reached.

Here the expression `Right(Some(2))` is of type `Either[Any, Option[Int]]` and our assertion `isRight(isSome(equalTo(2)))` is of type `Assertion[Either[Any, Option[Int]]]`

```scala mdoc:reset-object:silent
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

What is nice about those tests is that test reporters will tell you exactly which assertion was broken. Let's say we would change `isGreaterThanEqualTo(18)` to `isGreaterThanEqualTo(40)` which will fail. Print out on the console will be a nice detailed text explaining what exactly went wrong:

```bash
[info]       User(Jonny,26,Address(Denmark,Copenhagen)) did not satisfy (hasField("age", _.age, isGreaterThanEqualTo(45)) && hasField("country", _.country, not(equalTo(USA))))
[info]       26 did not satisfy isGreaterThanEqualTo(45)
```
