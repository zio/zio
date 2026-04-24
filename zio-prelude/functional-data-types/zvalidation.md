# ZValidation

> Like many other data types in ZIO, the `Validation` data type we learned about before is actually a type alias for a more general data type called `ZValidation`.

Like many other data types in ZIO, the `Validation` data type we learned about before is actually a type alias for a more general data type called `ZValidation`.

A `ZValidation[W, E, A]` is either a `Success` with a value of type `A` or a `Failure` with one or more errors of type `E`, and in either case contains a log of values of type `W`. A `Validation[E, A]` is just a type alias for `ZValidation[Nothing, E, A]` and since the log type was `Nothing` we were able to ignore it.

```scala
type Validation[+E, +A] = ZValidation[Nothing, E, A]
```

This type `W` can be used for two main purposes.

First, it can be used to maintain a "log" of the things we did in validating some data. In some cases it can be important for auditing or debugging purposes to know not just whether data is valid or why not but the steps we took in validating that data.

Second, it can be used to represent a concept of "warnings".

By definition a `Validation` is either a success with a value or a failure with one or more errors. So if an error occurred the data is invalid and there is no value at all.

However, we can imagine some cases where data might be questionable or noteworthy in some way without being invalid.

For example, we might say that a valid `Person` must have an age that is not negative, so a `Person` with an age of `10` would still be valid data. But depending on the application this data might still be unusual, for example if this `Person` represents a credit card customer.

In cases like this it would be useful to have some way of capturing these warnings as data and propagating them through our validation logic so that we can see all the warnings that occurred and take some further action based on them. This could include logging them, triggering a user to confirm that their input is correct, or triggering other processes based on the content of these warnings.

To model this we can represent a `ZValidation` as follows:

```scala

trait ZValidation[+W, +E, +A]

object ZValidation {
  case class Success[W, A](log: Chunk[W], value: A)                 extends ZValidation[W, Nothing, A]
  case class Failure[W, E](log: Chunk[W], errors: NonEmptyChunk[E]) extends ZValidation[W, E, Nothing]
}
```

Now both validation successes and failures have a log of values of type `W` so we can keep track of values in the log regardless of whether the validation is a success or a failure.

Since a `Validation` is a `ZValidation` all of the operators we learned about before for `Validation` still work. So we will focus here on operators that are specific to working with the `W` type.

## Writing Values To The Log

To write values to the log we can just use the `log` operator on the `ZValidation` trait.

```scala
trait ZValidation[+W, +E, +A] {
  def log[W1 >: W](w: W1): ZValidation[W1, E, A]
}
```

This will just append the specified value to the log without changing the success or failure value of the `ZValidation`.

For example, we could add a warning that the `Person` is a child like this:

```scala

def validateAge(age: Int): ZValidation[String, String, Int] =
  if (age < 0) ZValidation.fail(s"Age $age was less than zero")
  else if (age < 18) ZValidation.succeed(age).log(s"Age $age is less than 18")
  else ZValidation.succeed(age)
```

Notice that we used exactly the same constructors as when we were working with `Validation` except we now use the `log` operator to append to the log.

## Transforming The Log

We can transform the value of the log entries with the `mapLog` operator.

```scala
trait ZValidation[+W, +E, +A] {
  def mapLog[W2](f: W => W2): ZValidation[W2, E, A]
}
```

This can be useful to transform the log values into some common data model, for example mapping `String` logs into some more structured data format.

## Reading Values From The Log

We can get values from the log by using the `getLog` operator, which just returns the current contents of the log.

```scala

trait ZValidation[+W, +E, +A] {
  def getLog: Chunk[W]
}
```

Since a `ZValidation` is just a value we can use a combination of the `getLog` operator and `fold` to extract the log and the errors or success value.
