# Validation

> A `Validation[E, A]` is a data type that is either a `Success` with a value of type `A` or a `Failure` with one or more errors of type `E`.

A `Validation[E, A]` is a data type that is either a `Success` with a value of type `A` or a `Failure` with one or more errors of type `E`.

You can think of a `Validation` as looking something like this:

```scala

sealed trait Validation[+E, +A]

object Validation {
  case class Success[+A](value: A)                 extends Validation[Nothing, A]
  case class Failure[+E](errors: NonEmptyChunk[E]) extends Validation[E, Nothing]
}
```

Thus, a `Validation` looks very much like an `Either` except that it can contain multiple errors instead of just one.

The distinguishing feature of `Validation` is that it can accumulate multiple errors. This makes it extremely convenient for problems like data validation where we want to accumulate all validation errors and report them back instead of failing immediately on the first validation error.

To see this, let's consider a simple example of validating a `Person` data type.

```scala
case class Person(name: String, age: Int)
```

The business requirements are that a valid person must have a name that is not empty and an age that is greater than zero.

We could try to model validating a `Person` using `Either` like this:

```scala
def validateName(name: String): Either[String, String] =
  if (name.isEmpty) Left("Name was empty")
  else Right(name)

def validateAge(age: Int): Either[String, Int] =
  if (age <= 0) Left(s"Age $age was less than zero")
  else Right(age)

def validatePerson(name: String, age: Int): Either[String, Person] =
  for {
    name <- validateName(name)
    age  <- validateAge(age)
  } yield Person(name, age)
```

However, there is a problem with this. If we try to validate a `Person` with both an invalid `name` and an invalid `age` we will just get a single failure indicating that the name was empty.

This is because `Either` embodies "fail fast" semantics where if one part of a computation fails the subsequent parts of the computation will never be performed.

Normally this is what we want because there is no point in doing further work if a computation has already failed. This is similar to how in traditional imperative programs if an exception is thrown on one line then further lines will not be executed.

However, it is not what we want here.

We could imagine that the `name` and `age` were transmitted by a customer. They would probably not be happy if we told them the name was invalid, they corrected that and sent it again, and then we failed again because the age was invalid.

"Why didn't you tell me about all the problems up front?" we can see them saying. `Validation` does just that.

We can fix this example using `Validation` like so:

```scala

case class Person(name: String, age: Int)

def validateName(name: String): Validation[String, String] =
  if (name.isEmpty) Validation.fail("Name was empty")
  else Validation.succeed(name)

def validateAge(age: Int): Validation[String, Int] =
  if (age <= 0) Validation.fail(s"Age $age was less than zero")
  else Validation.succeed(age)

def validatePerson(name: String, age: Int): Validation[String, Person] =
  Validation.validateWith(validateName(name), validateAge(age))(Person)
```

Here we are just using the basic operators on `Validation` of `succeed` and `fail` which construct a `Validation` that succeeds or fails with the specified value. We are then using the `validateWith` operator, which combines the results of multiple `Validation` values, accumulating all errors that occur.

Note that we are using the `apply` method of `Person` to combine the validated name and age into a `Person`. This is often a convenient pattern when we are modeling our data using case classes.

Now if we validate a person where both the `name` and the `age` are invalid we will get a failure containing both errors, giving us exactly what we need for our internal customer, so they can efficiently correct the problem and send us valid data.

## Constructing Validation Values

Now that we understand what `Validation` is, let's look at how to construct `Validation` values.

### From Existing Values

The simplest ways to construct `Validation` values are the `succeed` and `fail` operators we saw above:

```scala
object Validation {
  def fail[E](error: E): Validation[E, Nothing] =
    ???
  def succeed[A](value: A): Validation[Nothing, A] =
    ???
}
```

These just construct validation successes or failures with the specified value. We can use these as the building blocks for creating more complex validation logic as we saw above.

### From Code That May Throw Exceptions

Another useful constructor of `Validation` values is the `apply` method on `Validation`:

```scala
object Validation {
  def apply[A](a: => A): Validation[Throwable, A] =
    ???
}
```

The `apply` operator takes a by name argument and evaluates that argument, converting any non-fatal thrown exceptions into `Validation` failures. This is useful when working with code that might throw exceptions to safely import that code into a `Validation` value.

### From Predicates

The `fromPredicateWith` constructor is useful for constructing `Validation` values from predicates. This is nice for cleaning up code that constructs a validation failure or success based on some condition like the example we wrote above:

```scala
object Validation {
  def fromPredicateWith[E, A](error: => E)(value: A)(f: A => Boolean): Validation[E, A] =
    ???
}
```

The `fromPredicateWith` constructor constructs a `Validation` from a value, a predicate about that value, and an error to provide if the predicate is false.

For example, here is how we could simplify the example we wrote above using the `fromPredicateWith` operator:

```scala
def validateName(name: String): Validation[String, String] =
  Validation.fromPredicateWith("Name was empty")(name)(_.nonEmpty)

def validateAge(age: Int): Validation[String, Int] =
  Validation.fromPredicateWith(s"Age $age was less than zero")(age)(_ >= 0)
```

This is the same as the original example but lets us express our logic at a slightly higher level.

### From Other Data Types

There are also operators for constructing `Validation` values from a variety of other data types in the Scala standard library such as `Either`, `Option`, and `Try`:

```scala

object Validation {
  def fromEither[E, A](value: Either[E, A]): Validation[E, A] =
    ???
  def fromOption[A](value: Option[A]): Validation[Unit, A] =
    ???
  def fromTry[A](value: => Try[A]): Validation[Throwable, A] =
    ???
}
```

Each of these operators just converts another data type that models potential failures into a `Validation`.

In the case of `fromEither` the `Validation` will fail with the same error type as the `Either`.

In the case of `fromTry` the error type is always `Throwable`, since a `Try` can fail with any `Throwable`. The `fromTry` operator will also catch any exception thrown in the construction of the `Try` value and convert it to a `Validation` failure.

A `Validation` constructed using `fromOption` has `Unit` for the error type since an `Option` contains no useful information about why a failure has occurred. We will see shortly when we learn about transforming `Validation` values how we can add additional information here about why a validation failure occurred.

## Transforming Validation Values

Now that we know about creating `Validation` values, the next thing we need to know is how to transform them.

We can transform the value type of the `Validation` using the `map` operator:

```scala
trait Validation[+E, +A] {
  def map[B](f: A => B): Validation[E, B]
}
```

We can transform the error type of the `Validation` using the `mapError` operator:

```scala
trait Validation[+E, +A] {
  def mapError[E2](f: E => E2): Validation[E2, A]
}
```

This can be useful to transform the error type to our own internal data model. It can also be useful to add additional information about why a validation failure occurred, for example adding a descriptive error message if an optional value did not exist.

## Combining Validation Values

We can also combine `Validation` values.

### Accumulating Errors

The most common way to do this is with the `validateWith` operator we saw above:

```scala
object Validation {
  def validateWith[E, A, B, C](
    left: Validation[E, A],
    right: Validation[E, B]
  )(f: (A, B) => C): Validation[E, C] =
    ???
}
```

This will check each of the original `Validation` values and if they are both successes combine their values with the function `f` to produce a new successful `Validation` value. If one or more of the original `Validation` values are failures then the result will be a failure with all of the failures of both original `Validation` values.

There are variants of `validateWith` for combining up to twenty two different `Validation` values so even if you are working with large cases classes with many fields you can still use `validateWith` to combine them.

There is also a `validate` variant that combines multiple `Validation` values but does not take a combining function. In this case the returned `Validation` just contains a tuple with all the original values, and you can combine them yourself later using `map`.

If you have a whole collection of values you want to validate you can use the `validateAll` operator to validate them all at the same time.

### Chaining Validations

The second way we might want to combine `Validation` values is by returning another `Validation` value based on the result of the first validation value. For example, we might first try to parse a string into an integer and then validate that the integer is within a reasonable range.

```scala

def validateInt(s: String): Validation[String, Int] =
  Validation(s.toInt).mapError(_.getMessage)

def validateAge(age: Int): Validation[String, Int] =
  Validation.fromPredicateWith(s"Age $age was less than zero")(age)(_ >= 0)

def validateAgeString(s: String): Validation[String, Int] =
  for {
    n <- validateInt(s)
    _ <- validateAge(n)
  } yield n
```

Here the second validation depended on the first one because if we could not parse the string into an integer then we would not even have an integer to validate. Just like with other data types we can express this with the `flatMap` operator and use a for comprehension to chain multiple validations together.

Note that when we chain validations like this we only do the second validation if the first one is successful so we will never see both errors here. If all we are doing is chaining then we don't actually need `Validation` and could just use `Either`.

However, frequently we want to do some validations with error accumulating semantics and chain others. Having `flatMap` on `Validation` makes it very easy for us to do this.

To see this, let's expand on our example of validating a `Person` data type from above. Now we will validate the person based on two `String` inputs using the logic we implemented above.

```scala
def validatePerson(name: String, age: String): Validation[String, Person] =
  Validation.validateWith(validateName(name), validateAgeString(age))(Person)
```

Now we are combining accumulating errors and chaining validations.

Validating the age will proceed by validating that the age can be parsed into an `Int` and then that the age is not negative. This will return either a validated age or a validation error if either of these steps failed.

We are also validating that the name is not empty. The final result will be either a validated person or a validation error containing both any errors that occurred in validating the name and age.

By combining operators like `validateWith` and `flatMap` you can build complex validation logic that handles any of your problems in this or related domains.

## Extracting Validation Values

The final thing we want to be able to do with `Validation` values is extract them to access the success value or errors and do something with them. This also includes transforming `Validation` values into other data types that we may be interested in.

The most basic way to do this is with the `fold` operator, which lets us provide functions specifying what we want to do with the success or failure.

```scala
trait Validation[+E, +A] {
  def fold[B](failure: E => B, success: A => B): B
}
```

Using `fold`, we can get out the success value of the `Validation` by providing a function that says what we want to do if the `Validation` is a failure. For example, we might provide a default value or refail in the context of some other data type that can model failure such as `ZIO`.

There are a variety of helpful operators implemented in terms of `fold` for transforming `Validation` values into other data types we might be interested in. The most common of these are:

```scala

trait Validation[+E, +A] {
  def toEither: Either[NonEmptyChunk[E], A]
  def toEitherWith[E2](f: NonEmptyChunk[E] => E2): Either[E2, A]
  def toZIO: IO[E, A]
}
```

The `toEither` operator converts the `Validation` into an `Either` where the `Left` side of the other contains a `NonEmptyChunk` of all the errors that occurred if the `Validation` was a failure. The `toEitherWith` operator is similar to this but lets us provide our own function for converting the collection of errors that occurred into our own error type.

The `toZIO` operator is particularly useful because frequently we will do some data validation which we will model with `Validation` but then want to do some further work with the result that needs a `ZIO` effect such as writing it to a file or sending it over the wire.

With `toZIO` we can easily do our validation in the context of `Validation` and then "lift" it into the context of `ZIO` to compose with our other `ZIO` effects. One nice feature of this operator is that it will automatically preserve all validation errors that occurred in ZIO's `Cause` data type.
