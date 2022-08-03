---
id: submerge
title: "Putting Errors Into Success Channel and Submerging Them Back Again"
---

Before taking into `ZIO#either` and `ZIO#absolve`, let's see their signature:

```scala
trait ZIO[-R, +E, +A] {
  def either(implicit ev: CanFail[E]): URIO[R, Either[E, A]]
  def absolve[E1 >: E, B](implicit ev: A IsSubtypeOfOutput Either[E1, B]): ZIO[R, E1, B]
}
```

Before continuing, let's take a look again at the `validate` function we have written earlier:

```scala mdoc:silent
import zio._

sealed trait AgeValidationException extends Exception
case class NegativeAgeException(age: Int) extends AgeValidationException
case class IllegalAgeException(age: Int)  extends AgeValidationException

def validate(age: Int): ZIO[Any, AgeValidationException, Int] =
  if (age < 0)
    ZIO.fail(NegativeAgeException(age))
  else if (age < 18)
    ZIO.fail(IllegalAgeException(age))
  else ZIO.succeed(age)
```

Now we are ready to use `ZIO#either` and `ZIO#absolve` operations:

## `ZIO#either`

The `ZIO#either` convert a `ZIO[R, E, A]` effect to another effect in which its failure (`E`) and success (`A`) channel have been lifted into an `Either[E, A]` data type as success channel of the `ZIO` data type:

```scala mdoc:compile-only
import zio._

val age: Int = ???

val res: URIO[Any, Either[AgeValidationException, Int]] = validate(age).either
```

The resulting effect is an unexceptional effect and cannot fail, because the failure case has been exposed as part of the `Either` success case. The error parameter of the returned `ZIO` is `Nothing`, since it is guaranteed the `ZIO` effect does not model failure.

This method is useful for recovering from `ZIO` effects that may fail:

```scala mdoc:compile-only
import zio._
import java.io.IOException

val myApp: ZIO[Any, IOException, Unit] =
  for {
    _ <- Console.print("Please enter your age: ")
    age <- Console.readLine.map(_.toInt)
    res <- validate(age).either
    _ <- res match {
      case Left(error) => ZIO.debug(s"validation failed: $error")
      case Right(age) => ZIO.debug(s"The $age validated!")
    }
  } yield ()
```

## `ZIO#absolve`/`ZIO.absolve`

The `ZIO#abolve` operator and the `ZIO.absolve` constructor perform the inverse. They submerge the error case of an `Either` into the `ZIO`:

```scala mdoc:compile-only
import zio._

val age: Int = ???
validate(age) // ZIO[Any, AgeValidationException, Int]
  .either     // ZIO[Any, Either[AgeValidationException, Int]]
  .absolve    // ZIO[Any, AgeValidationException, Int]
```

Here is another example:

```scala mdoc:compile-only
import zio._

def sqrt(input: ZIO[Any, Nothing, Double]): ZIO[Any, String, Double] =
  ZIO.absolve(
    input.map { value =>
      if (value < 0.0)
        Left("Value must be >= 0.0")
      else
        Right(Math.sqrt(value))
    }
  )
```
