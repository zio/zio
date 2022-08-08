---
id: error-refinement
title: "Error Refinement"
---

ZIO has some operators useful for converting defects into failures. So we can take part in non-recoverable errors and convert them into the typed error channel and vice versa.

Note that both `ZIO#refine*` and `ZIO#unrefine*` do not alter the error behavior, but only change the error model. That is to say, if an effect fails or die, then after `ZIO#refine*` or `ZIO#unrefine*`, it will still fail or die; and if an effect succeeds, then after `ZIO#refine*` or `ZIO#unrefine*`, it will still succeed; only the manner in which it signals the error will be altered by these two methods:

1. The `ZIO#refine*` pinches off a piece of _failure_ of type `E`, and converts it into a _defect_.
2. The `ZIO#unrefine*` pinches off a piece of a _defect_, and converts it into a _failure_ of type `E`.

## Refining

### `ZIO#refineToOrDie`

This operator **narrows** down the type of the error channel from `E` to the `E1`. It leaves the rest errors untyped, so everything that doesn't fit is turned into a defect. So it makes the error space **smaller**.

```scala
ZIO[-R, +E, +A] {
  def refineToOrDie[E1 <: E]: ZIO[R, E1, A]
}
```

In the following example, we are going to implement `parseInt` by importing `String#toInt` code from the standard scala library using `ZIO#attempt` and then refining the error channel from `Throwable` to the `NumberFormatException` error type:

```scala mdoc:compile-only
import zio._

def parseInt(input: String): ZIO[Any, NumberFormatException, Int] =
  ZIO.attempt(input.toInt)                 // ZIO[Any, Throwable, Int]
    .refineToOrDie[NumberFormatException]  // ZIO[Any, NumberFormatException, Int]
```

In this example, if the `input.toInt` throws any other exceptions other than `NumberFormatException`, e.g. `IndexOutOfBoundsException`, will be translated to the ZIO defect.

### `ZIO#refineOrDie`

It is the more powerful version of the previous operator. Instead of refining to one specific error type, we can refine to multiple error types using a partial function:

```scala mdoc:compile-only
trait ZIO[-R, +E, +A] {
  def refineOrDie[E1](pf: PartialFunction[E, E1]): ZIO[R, E1, A]
}
```

In the following example, we excluded the `Baz` exception from recoverable errors, so it will be converted to a defect. In another word, we narrowed `DomainError` down to just `Foo` and `Bar` errors:

```scala mdoc:compile-only
import zio._

sealed abstract class DomainError(msg: String)
  extends Exception(msg)
    with Serializable
    with Product
case class Foo(msg: String) extends DomainError(msg)
case class Bar(msg: String) extends DomainError(msg)
case class Baz(msg: String) extends DomainError(msg)

object MainApp extends ZIOAppDefault {
  val effect: ZIO[Any, DomainError, Unit] =
    ZIO.fail(Baz("Oh uh!"))

  val refined: ZIO[Any, DomainError, Unit] =
    effect.refineOrDie {
      case foo: Foo => foo
      case bar: Bar => bar
    }

  def run = refined.catchAll(_ => ZIO.unit).debug
}
```

### `ZIO#refineOrDieWith`

In the two previous refine combinators, we were dealing with exceptional effects whose error channel type was `Throwable` or a subtype of that. The `ZIO#refineOrDieWith` operator is a more powerful version of refining operators. It can work with any exceptional effect whether they are `Throwable` or not. When we narrow down the failure space, some failures become defects. To convert those failures to defects, it takes a function from `E` to `Throwable`:

```scala mdoc:compile-only
trait ZIO[-R, +E, +A] {
  def refineOrDieWith[E1](pf: PartialFunction[E, E1])(f: E => Throwable): ZIO[R, E1, A]
}
```

In the following example, we excluded the `BazError` from recoverable errors, so it will be converted to a defect. In another word, we narrowed the whole space of `String` errors down to just "FooError" and "BarError":

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def effect(i: String): ZIO[Any, String, Nothing] = {
    if (i == "foo") ZIO.fail("FooError")
    else if (i == "bar") ZIO.fail("BarError")
    else ZIO.fail("BazError")
  }

  val refined: ZIO[Any, String, Nothing] =
    effect("baz").refineOrDieWith {
      case "FooError" | "BarError" => "Oh Uh!"
    }(e => new Throwable(e))

  def run = refined.catchAll(_ => ZIO.unit)
}
```

## Unrefining

### `ZIO#unrefineTo[E1 >: E]`

This operator **broadens** the type of the error channel from `E` to the `E1` and embeds some defects into it. So it is going from some fiber failures back to errors and thus making the error type **larger**:

```scala mdoc:compile-only
trait ZIO[-R, +E, +A] {
  def unrefineTo[E1 >: E]: ZIO[R, E1, A]
}
```

In the following example, we are going to implement `parseInt` by importing `String#toInt` code from the standard scala library using `ZIO#succeed` and then unrefining the error channel from `Nothing` to the `NumberFormatException` error type:

```scala mdoc:compile-only
import zio._

def parseInt(input: String): ZIO[Any, NumberFormatException, Int] =
  ZIO.succeed(input.toInt)              // ZIO[Any, Nothing, Int]
    .unrefineTo[NumberFormatException]  // ZIO[Any, NumberFormatException, Int]
```

### `ZIO#unrefine`

It is a more powerful version of the previous operator. It takes a partial function from `Throwable` to `E1` and converts those defects to recoverable errors:

```scala mdoc:compile-only
trait ZIO[-R, +E, +A] {
  def unrefine[E1 >: E](pf: PartialFunction[Throwable, E1]): ZIO[R, E1, A]
}
```

```scala mdoc:invisible:reset

```

```scala mdoc:silent
import zio._

case class Foo(msg: String) extends Throwable(msg)
case class Bar(msg: String) extends Throwable(msg)
case class Baz(msg: String) extends Throwable(msg)

object MainApp extends ZIOAppDefault {
  def unsafeOpThatMayThrows(i: String): String =
    if (i == "foo")
      throw Foo("Oh uh!")
    else if (i == "bar")
      throw Bar("Oh Error!")
    else if (i == "baz")
      throw Baz("Oh no!")
    else i

  def effect(i: String): ZIO[Any, Nothing, String] =
    ZIO.succeed(unsafeOpThatMayThrows(i))

  val unrefined: ZIO[Any, Foo, String] =
    effect("foo").unrefine { case e: Foo => e }

  def run = unrefined.catchAll(_ => ZIO.unit)
}
```

Using `ZIO#unrefine` we can have more control to unrefine a ZIO effect that may die because of some defects, for example in the following example we are going to convert both `Foo` and `Bar` defects to recoverable errors and remain `Baz` unrecoverable:

```scala mdoc:invisible
import MainApp._
```

```scala
val unrefined: ZIO[Any, Throwable, String] =
  effect("foo").unrefine {
    case e: Foo => e
    case e: Bar => e
  }
```

```scala mdoc:invisible:reset

```

### `ZIO#unrefineWith`

This is the most powerful version of unrefine operators. It takes a partial function, as the previous operator, and then tries to broaden the failure space by converting some of the defects to typed recoverable errors. If it doesn't find any defect, it will apply the `f` which is a function from `E` to `E1`, and map all typed errors using this function:

```scala
trait ZIO[-R, +E, +A] {
  def unrefineWith[E1](pf: PartialFunction[Throwable, E1])(f: E => E1): ZIO[R, E1, A]
}
```

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  case class Foo(msg: String) extends Exception(msg)
  case class Bar(msg: String) extends Exception(msg)

  val effect: ZIO[Any, Foo, Nothing] =
    ZIO.ifZIO(Random.nextBoolean)(
      onTrue = ZIO.fail(Foo("Oh uh!")),
      onFalse = ZIO.die(Bar("Boom!"))
    )

  val unrefined: ZIO[Any, String, Nothing] =
    effect
      .unrefineWith {
        case e: Bar => e.getMessage
      }(e => e.getMessage)

  def run = unrefined.cause.debug
}
```
