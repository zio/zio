---
id: examples
title: "Examples"
---

## Example 1: Equality Assertion

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

## Example 2: Field-level Assertion

There is also an easy way to test an object's data for certain assertions with `hasField` which accepts besides a name, a mapping function from object to its tested property, and `Assertion` object which will validate this property. Here our test checks if a person has at least 18 years and is not from the USA.

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

What is nice about those tests is that test reporters will tell you exactly which assertion was broken. Let's say we would change `isGreaterThanEqualTo(18)` to `isGreaterThanEqualTo(40)` which will fail. Print out on the console will be a nice detailed text explaining what exactly went wrong:

```bash
[info]       User(Jonny,26,Address(Denmark,Copenhagen)) did not satisfy (hasField("age", _.age, isGreaterThanEqualTo(45)) && hasField("country", _.country, not(equalTo(USA))))
[info]       26 did not satisfy isGreaterThanEqualTo(45)
```

## Example 3: Test if a ZIO Effect Fails With a Particular Error Type

The following example shows how to test if a ZIO effect fails with a particular error type. To test if a ZIO effect fails with a particular error type, we can use the `ZIO#exit` to determine the exit type of that effect. 

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }
import zio.test.Assertion._

case class MyError(msg: String) extends Exception

val effect: ZIO[Any, MyError, Unit] = ZIO.fail(MyError("my error msg"))

test("test if a ZIO effect fails with a particular error type") {
  for {
    exit <- effect.exit
  } yield assertTrue(exit == Exit.fail(MyError("my error msg")))
}
```

The exit method on a ZIO effect returns an `Exit` value, which represents the outcome of the effect. The `Exit` value can be either `Exit.succeed` or `Exit.fail`. If the effect succeeded, the `Exit.succeed` value will contain the result of the effect. If the effect failed, the `Exit.fail` value will contain the error that caused the failure.

## Example 4: Test if a ZIO Effect Fails With a Subtype of a Particular Error Type

To test if a ZIO effect fails with a `subtype` of a particular error type, we can use the `assertZIO` function and the two `fails`, and `isSubtype` assertions from the zio-test library. The `assertZIO` function takes a ZIO effect and an assertion. The assertion is called with the result of the ZIO effect. If the assertion returns true, then the `assertZIO` will succeed, otherwise it will fail.

Assume we have these error types:

```scala mdoc:silent
sealed trait MyError extends Exception
case class E1(msg: String) extends MyError
case class E2(msg: String) extends MyError
```

To assert if an error type is a subtype of a particular error type, we need to combine the `fails` and `isSubtype` assertions together:


```scala mdoc:compile-only
import zio.test.Assertion._

Assertion.fails(isSubtype[MyError](anything))
```

Now let's look at an example:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }
import zio.test.Assertion._

val effect = ZIO.fail(E1("my error msg"))

test("Test if a ZIO effect fails with a MyError") {
  assertZIO(effect.exit)(fails(isSubtype[MyError](anything)))
}
```
