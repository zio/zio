---
id: converting-optional-values-to-optional-errors-and-vice-versa
title: "Converting Optional Values to Optional Errors and Vice Versa"
---

Assume we have the following effect:

```scala mdoc:silent
import zio._

val nextRandomEven: ZIO[Any, String, Option[Int]] =
  Random.nextInt
    .reject {
      case n if n < 0 => s"$n is negative!"
    }
    .map{
      case n if n % 2 == 0 => Some(n)
      case _               => None
    }
```

Now we can convert this effect which is optional on the success channel to an effect that is optional on the error channel using the `ZIO#some` operator and also the `ZIO#unsome` to reverse this conversion.

```scala mdoc:compile-only
nextRandomEven // ZIO[Any, String, Option[Int]]
  .some        // ZIO[Any, Option[String], Int]
  .unsome      // ZIO[Any, String, Option[Int]]
```

```scala mdoc:invisible:reset

```

Sometimes instead of converting optional values to optional errors, we can perform one of the following operations:

## `ZIO#someOrElse`

Extract the optional value if it is not empty or return the given default:

```scala mdoc:compile-only
import zio._

val getEnv: ZIO[Any, Nothing, Option[String]] = ???

val result: ZIO[Any, Nothing, String] =
  getEnv.someOrElse("development")
```

## `ZIO#someOrElseZIO`

Like the `ZIO#someOrElse` but the effectful version:

```scala mdoc:compile-only
import zio._

trait Config

val list: List[Config] = ???

val getCurrentConfig: ZIO[Any, Nothing, Option[Config]] = ZIO.succeed(list.headOption)
val getRemoteConfig : ZIO[Any, Throwable, Config]       = ZIO.attempt(new Config {})

val config: ZIO[Any, Throwable, Config] =
  getCurrentConfig.someOrElseZIO(getRemoteConfig)
```

## `ZIO#someOrFail`

It converts the ZIO effect of an optional value to an exceptional effect:

```scala mdoc:compile-only
import zio._

def head(list: List[Int]): ZIO[Any, NoSuchElementException, Int] =
  ZIO
    .succeed(list.headOption)
    .someOrFail(new NoSuchElementException("empty list"))
```

In the above example, we can also use the `ZIO#someOrFailException` which will directly convert the unexceptional effect to the exceptional effect with the error type of `NoSuchElementException`.
