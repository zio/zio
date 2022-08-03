---
id: draft-page
title: "Draft Page"
---

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
