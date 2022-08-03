---
id: error-accumulation
title: "Error Accumulation"
---

Sequential combinators such as `ZIO#zip` and `ZIO.foreach` stop when they reach the first error and return immediately. So their policy on error management is to fail fast.

In the following example, we can see that the `ZIO#zip` operator will fail as soon as it reaches the first failure. As a result, we only see the first error in the stack trace:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val f1: ZIO[Any, Nothing, Int] = ZIO.succeed(1)
  val f2: ZIO[Any, String, Int]  = ZIO.fail("Oh uh!").as(2)
  val f3: ZIO[Any, Nothing, Int] = ZIO.succeed(3)
  val f4: ZIO[Any, String, Int]  = ZIO.fail("Oh no!").as(4)

  val myApp: ZIO[Any, String, (Int, Int, Int, Int)] =
    f1 zip f2 zip f3 zip f4

  def run = myApp.debug
}

// Output:
// <FAIL> Oh uh!
// timestamp=2022-03-13T09:26:03.447149388Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
// 	at <empty>.MainApp.f2(MainApp.scala:5)
// 	at <empty>.MainApp.myApp(MainApp.scala:10)
//	at <empty>.MainApp.run(MainApp.scala:12)"
```

There is also the `ZIO.foreach` operator that takes a collection and an effectful operation, then tries to apply the transformation to all elements of the collection. This operator also has the same error management behavior. It fails when it encounters the first error:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Any, String, List[Int]] =
    ZIO.foreach(List(1, 2, 3, 4, 5)) { n =>
      if (n < 4)
        ZIO.succeed(n)
      else
        ZIO.fail(s"$n is not less that 4")
    }

  def run = myApp.debug
}

// Output:
// <FAIL> 4 is not less that 4
// timestamp=2022-03-13T08:28:53.865690767Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: 4 is not less that 4
//	at <empty>.MainApp.myApp(MainApp.scala:9)
//	at <empty>.MainApp.run(MainApp.scala:12)"
```

There are some situations when we need to collect all potential errors in a computation rather than failing fast. In this section, we will discuss operators that accumulate errors as well as successes.

## `ZIO#validate`

It is similar to the `ZIO#zip` operator, it sequentially zips two ZIO effects together, if both effects fail, it combines their causes with `Cause.Then`:

```scala
trait ZIO[-R, +E, +A] {
  def validate[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)]
}
```

If any of effecful operations doesn't fail, it results like the `zip` operator. Otherwise, when it reaches the first error it won't stop, instead, it will continue the zip operation until reach the final effect while combining:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val f1 = ZIO.succeed(1).debug
  val f2 = ZIO.succeed(2) *> ZIO.fail("Oh uh!")
  val f3 = ZIO.succeed(3).debug
  val f4 = ZIO.succeed(4) *> ZIO.fail("Oh error!")
  val f5 = ZIO.succeed(5).debug

  val myApp: ZIO[Any, String, (Int, Int, Int)] =
    f1 validate f2 validate f3 validate f4 validate f5

  def run = myApp.cause.debug.uncause
}

// Output:
// 1
// 3
// 5
// Then(Fail(Oh uh!,Trace(None,Chunk())),Fail(Oh error!,Trace(None,Chunk())))
// timestamp=2022-03-14T08:53:42.389942626Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
// 	at <empty>.MainApp.run(MainApp.scala:13)
// 	Suppressed: java.lang.String: Oh error!
//		at <empty>.MainApp.run(MainApp.scala:13)"
```

The `ZIO#validatePar` operator is similar to the `ZIO#validate` operator zips two effects but in parallel. As this operator doesn't fail fast, unlike the `ZIO#zipPar` if it reaches a failure, it won't interrupt another running effect. If both effects fail, it will combine their causes with `Cause.Both`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val f1 = ZIO.succeed(1).debug
  val f2 = ZIO.succeed(2) *> ZIO.fail("Oh uh!")
  val f3 = ZIO.succeed(3).debug
  val f4 = ZIO.succeed(4) *> ZIO.fail("Oh error!")
  val f5 = ZIO.succeed(5).debug

  val myApp: ZIO[Any, String, ((((Int, Int), Int), Int), Int)] =
    f1 validatePar f2 validatePar f3 validatePar f4 validatePar f5

  def run = myApp.cause.map(_.untraced).debug.uncause
}

// One possible output:
// 3
// 1
// 5
// Both(Fail(Oh uh!,Trace(None,Chunk())),Fail(Oh error!,Trace(None,Chunk())))
// timestamp=2022-03-14T09:16:00.670444190Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
//  	at <empty>.MainApp.run(MainApp.scala:13)
// Exception in thread "zio-fiber-2" java.lang.String: Oh error!
//  	at <empty>.MainApp.run(MainApp.scala:13)"
```

In addition, it has a `ZIO#validateWith` variant, which is useful for providing combiner function (`f: (A, B) => C`) to combine pair values.

## `ZIO.validate`

This operator is very similar to the `ZIO.foreach` operator. It transforms all elements of a collection using the provided effectful operation, but it collects all errors in the error channel, as well as the success values in the success channel.

It is similar to the `ZIO.partition` but it is an exceptional operator which means it collects errors in the error channel and success in the success channel:

```scala
object ZIO {
  def validate[R, E, A, B](in: Collection[A])(
    f: A => ZIO[R, E, B]
  ): ZIO[R, ::[E], Collection[B]]

  def validate[R, E, A, B](in: NonEmptyChunk[A])(
    f: A => ZIO[R, E, B]
  ): ZIO[R, ::[E], NonEmptyChunk[B]]
}
```

Another difference is that this operator is lossy, which means if there are errors all successes will be lost.

In the lossy scenario, it will collect all errors in the error channel, which cause the failure:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val res: ZIO[Any, ::[String], List[Int]] =
    ZIO.validate(List.range(1, 7)){ n =>
      if (n < 5)
        ZIO.succeed(n)
      else
        ZIO.fail(s"$n is not less that 5")
    }
  def run = res.debug
}

// Output:
// <FAIL> List(5 is not less that 5, 6 is not less that 5)
// timestamp=2022-03-12T07:34:36.510227783Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" scala.collection.immutable.$colon$colon: List(5 is not less that 5, 6 is not less that 5)
//	at <empty>.MainApp.res(MainApp.scala:5)
//	at <empty>.MainApp.run(MainApp.scala:11)"
```

In the success scenario when we have no errors at all, all the successes will be collected in the success channel:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val res: ZIO[Any, ::[String], List[Int]] =
    ZIO.validate(List.range(1, 4)){ n =>
      if (n < 5)
        ZIO.succeed(n)
      else
        ZIO.fail(s"$n is not less that 5")
    }
  def run = res.debug
}

// Ouput:
// List(1, 2, 3)
```

Two more notes:

1. The `ZIO.validate` operator is sequential, so we can use the `ZIO.validatePar` version to do the computation in parallel.
2. The `ZIO.validateDiscard` and `ZIO.validateParDiscard` operators are mostly similar to their non-discard versions, except they discard the successes. So the type of the success channel will be `Unit`.

## `ZIO.validateFirst`

Like the `ZIO.validate` in the success scenario, it will collect all errors in the error channel except in the success scenario it will return only the first success:

```scala
object ZIO {
  def validateFirst[R, E, A, B](in: Collection[A])(
    f: A => ZIO[R, E, B]
  ): ZIO[R, Collection[E], B]
}
```

In the failure scenario, it will collect all errors in the failure channel, and it causes the failure:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val res: ZIO[Any, List[String], Int] =
    ZIO.validateFirst(List.range(5, 10)) { n =>
      if (n < 5)
        ZIO.succeed(n)
      else
        ZIO.fail(s"$n is not less that 5")
    }
  def run = res.debug
}
// Output:
// <FAIL> List(5 is not less that 5, 6 is not less that 5, 7 is not less that 5, 8 is not less that 5, 9 is not less that 5)
// timestamp=2022-03-12T07:50:15.632883494Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" scala.collection.immutable.$colon$colon: List(5 is not less that 5, 6 is not less that 5, 7 is not less that 5, 8 is not less that 5, 9 is not less that 5)
// 	at <empty>.MainApp.res(MainApp.scala:5)
//	at <empty>.MainApp.run(MainApp.scala:11)"
```

In the success scenario it will return the first success value:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val res: ZIO[Any, List[String], Int] =
    ZIO.validateFirst(List.range(1, 4)) { n =>
      if (n < 5)
        ZIO.succeed(n)
      else
        ZIO.fail(s"$n is not less that 5")
    }
  def run = res.debug
}

// Output:
// 1
```

## `ZIO.partition`

The partition operator takes an iterable and effectful function that transforms each value of the iterable and finally creates a tuple of both failures and successes in the success channel.

```scala
object ZIO {
  def partition[R, E, A, B](in: => Iterable[A])(
    f: A => ZIO[R, E, B]
  ): ZIO[R, Nothing, (Iterable[E], Iterable[B])]
}
```

Note that this operator is an unexceptional effect, which means the type of the error channel is `Nothing`. So using this operator, if we reach a failure case, the whole effect doesn't fail. This is similar to the `List#partition` in the standard library:

Let's try an example of collecting even numbers from the range of 0 to 7:

```scala mdoc:compile-only
import zio._

val res: ZIO[Any, Nothing, (Iterable[String], Iterable[Int])] =
  ZIO.partition(List.range(0, 7)){ n =>
    if (n % 2 == 0)
      ZIO.succeed(n)
    else
      ZIO.fail(s"$n is not even")
  }
res.debug

// Output:
// (List(1 is not even, 3 is not even, 5 is not even),List(0, 2, 4, 6))
```
