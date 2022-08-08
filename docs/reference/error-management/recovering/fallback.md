---
id: fallback
title: "Fallback"
sidebar_label: "2. Fallback"
---


## `ZIO#orElse`

We can try one effect, or if it fails, try another effect with the `orElse` combinator:

```scala
trait ZIO[-R, +E, +A] {
  def orElse[R1 <: R, E2, A1 >: A](that: => ZIO[R1, E2, A1]): ZIO[R1, E2, A1]
}
```

Let's try an example:

```scala mdoc:invisible
import zio._
import java.io.{ FileNotFoundException, IOException }

def readFile(s: String): ZIO[Any, IOException, Array[Byte]] =
  ZIO.attempt(???).refineToOrDie[IOException]
```

```scala mdoc:compile-only
import zio._
import java.io.IOException

val primaryOrBackupData: ZIO[Any, IOException, Array[Byte]] =
  readFile("primary.data").orElse(readFile("backup.data"))
```

## `ZIO#orElseEither`

If the original effect fails, this operator tries another effect, and as a result, returns either:

```scala
trait ZIO[-R, +E, +A] {
  def orElseEither[R1 <: R, E2, B](that: => ZIO[R1, E2, B]): ZIO[R1, E2, Either[A, B]]
}
```

This operator is useful when the fallback effect has a different result type than the original effect. So this will unify both in the `Either[A, B]` data type. Here is an example usage of this operator:

```scala mdoc:compile-only
import zio._

trait LocalConfig
trait RemoteConfig

def readLocalConfig: ZIO[Any, Throwable, LocalConfig] = ???
def readRemoteConfig: ZIO[Any, Throwable, RemoteConfig] = ???

val result: ZIO[Any, Throwable, Either[LocalConfig, RemoteConfig]] =
  readLocalConfig.orElseEither(readRemoteConfig)
```

## `ZIO#orElseSucceed`/`ZIO#orElseFail`

These two operators convert the original failure with constant succeed or failure values:

```scala
trait ZIO[-R, +R, +E] {
  def orElseFail[E1](e1: => E1): ZIO[R, E1, A]

  def orElseSucceed[A1 >: A](a1: => A1): ZIO[R, Nothing, A1]
}
```

The `ZIO#orElseFail` will always replace the original failure with the new one, so `E1` does not have to be a supertype of `E`. It is useful when we have `Unit` as an error, and we want to unify that with something else:

```scala mdoc:silent
import zio._

sealed trait AgeValidationException extends Exception
case class NegativeAgeException(age: Int) extends AgeValidationException
case class IllegalAgeException(age: Int)  extends AgeValidationException

def validate(age: Int): ZIO[Any, AgeValidationException, Int] = {
  if (age < 0)
    ZIO.fail(NegativeAgeException(age))
  else if (age < 18)
    ZIO.fail(IllegalAgeException(age))
  else ZIO.succeed(age)
}

val result: ZIO[Any, String, Int] =
  validate(3).orElseFail("invalid age")
```

The `ZIO#orElseSucceed` will always replace the original failure with a success value so the resulting effect cannot fail. It is useful when we have a constant value that will work in case the effect fails:

```scala mdoc:compile-only
val result: ZIO[Any, Nothing, Int] =
  validate(3).orElseSucceed(0)
```

## `ZIO#orElseOptional`

When dealing with optional failure types, we might need to fall back to another effect when the failure value is `None`. This operator helps to do so:

```scala
trait ZIO[-R, +E, +A] {
  def orElseOptional[R1 <: R, E1, A1 >: A](
      that: => ZIO[R1, Option[E1], A1]
    )(implicit ev: E IsSubtypeOfError Option[E1]): ZIO[R1, Option[E1], A1] =
}
```

In the following example, the `parseInt(" ")` fails with `None`, so then the fallback effect results in a zero:

```scala mdoc:compile-only
import zio._

def parseInt(input: String): ZIO[Any, Option[String], Int] =
  input.toIntOption match {
    case Some(value) => ZIO.succeed(value)
    case None =>
      if (input.trim.isEmpty)
        ZIO.fail(None)
      else
        ZIO.fail(Some(s"invalid non-integer input: $input"))
  }

val result = parseInt("  ").orElseOptional(ZIO.succeed(0)).debug
```

## `ZIO.firstSuccessOf`/`ZIO#firstSuccessOf`

These two operators make it easy for a user to run an effect, and in case it fails, it will run a series of ZIO effects until one succeeds:

```scala
object ZIO {
  def firstSuccessOf[R, R1 <: R, E, A](
    zio: => ZIO[R, E, A],
    rest: => Iterable[ZIO[R1, E, A]]
  ): ZIO[R1, E, A] =
}

trait ZIO[-R, +E, +A] {
  final def firstSuccessOf[R1 <: R, E1 >: E, A1 >: A](
    rest: => Iterable[ZIO[R1, E1, A1]]
  ): ZIO[R1, E1, A1]
}
```

These methods use `orElse` to reduce the non-empty iterable of effects into a single effect.

In the following example, we are trying to get the config from the master node, and if it fails, we will try successively to retrieve the config from the next available node:

```scala mdoc:compile-only
import zio._

trait Config

def remoteConfig(name: String): ZIO[Any, Throwable, Config] =
  ZIO.attempt(???)

val masterConfig: ZIO[Any, Throwable, Config] =
  remoteConfig("master")

val nodeConfigs: Seq[ZIO[Any, Throwable, Config]] =
  List("node1", "node2", "node3", "node4").map(remoteConfig)

val config: ZIO[Any, Throwable, Config] =
  ZIO.firstSuccessOf(masterConfig, nodeConfigs)
```
