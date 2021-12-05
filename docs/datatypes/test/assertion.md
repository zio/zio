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


## How it works?

> **_Note:_**
>
> In this section we are going to learn about the internals of the `Assertion` data type. So feel free to skip this section if you are not interested.

### The `test` Function

In order to understand the `Assertion` data type, let's first look at the `test` function:

```scala
def test[In](label: String)(assertion: => In)(implicit testConstructor: TestConstructor[Nothing, In]): testConstructor.Out
```

Its signature is a bit complicated and uses _path dependent types_, but it doesn't matter. We can think of a `test` as a function from `TestResult` (or its effectful versions such as `ZIO[R, E, TestResult]`, `ZManaged[R, E, TestResult]` or `ZSTM[R, E, TestResult]`) to the `ZSpec[R, E]` data type:

```scala
def test(label: String)(assertion: => TestResult): ZSpec[Any, Nothing]
def test(label: String)(assertion: => ZIO[R, E, TestResult]): ZSpec[R, E]
```

Therefore, the function `test` needs a `TestResult`. The most common way to produce a `TestResult` is to resort to `assert` or its effectful counterpart `assertM`. The former one is for creating ordinary `TestResult` values and the latter one is for producing effectful `TestResult` values. Both of them accept a value of type `A` (effectful version wrapped in a `ZIO`) and an `Assertion[A]`.

### The `assert` Function

Let's look at the `assert` function:

```scala
type TestResult = BoolAlgebra[AssertionResult]

def assert[A](expr: => A)(assertion: Assertion[A]): TestResult
``` 

It takes an expression of type `A` and an `Assertion[A]` and returns the `TestResult` which is the boolean algebra of the `AssertionResult`. Furthermore, we have an `Assertion[A]` which is capable of producing _assertion results_ on any value of type `A`. So the `assert` function can apply the expression to the assertion and produce the `TestResult`.

### The `Assertion` data type

#### Definition

We can think of an `Assertion[A]` as a function of type `A` to the `AssertResult`:

```scala
type AssertResult  = BoolAlgebra[AssertionValue]

class Assertion[A] {
  def apply(a: => A): AssertResult
}
```

So we can apply any expression of type `A` to any assertion of type `A`:

```scala mdoc
import zio.test._

val isTrue: Assertion[Boolean] = Assertion.isTrue

val r1: AssertResult = isTrue(false)
val r2: AssertResult = isTrue(true)
```

In case of failure, the `AssertResult` contains all details about the cause of failure. It's useful when an assertion failed, and the ZIO Test Runner can produce a proper report about the test failure.

#### Logical Operations

As a proposition, assertions compose using logical conjunction and disjunction and can be negated:

```scala mdoc:silent
import zio.test._

val greaterThanZero: Assertion[Int] = Assertion.isPositive
val lessThanFive   : Assertion[Int] = Assertion.isLessThan(5)
val equalTo10      : Assertion[Int] = Assertion.equalTo[Int, Int](10)

val assertion: Assertion[Int] = greaterThanZero && lessThanFive || equalTo10.negate
```

After composing them, we can render the result and also run it on any expression:

```scala mdoc
import zio._

assertion.render

val result: AssertResult = assertion.run(10)
```

```scala mdoc:invisible:reset
```
