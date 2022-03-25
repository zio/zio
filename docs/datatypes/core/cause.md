---
id: cause
title: "Cause"
---

The `ZIO[R, E, A]` effect is polymorphic in values of type `E` and we can work with any error type that we want, but there is a lot of information that is not inside an arbitrary `E` value. So as a result ZIO needs somewhere to store things like **unexpected error or defects**, **stack and execution traces**, **cause of fiber interruptions**, and so forth.

ZIO is very strict about preserving the full information related to a failure. It captures all type of errors into the `Cause` data type. ZIO uses the `Cause[E]` data type to store the full story of failure. So its error model is **lossless**. It doesn't throw information related to the failure result. So we can figure out exactly what happened during the operation of our effects.

It is important to note that `Cause` is the underlying data type for the ZIO data type, and we don't usually deal with it directly. Even though it is not a data type that we deal with very often, anytime we want, we can access the `Cause` data structure, which gives us total access to all parallel and sequential errors in our codebase. 

## Cause Internals

ZIO uses a data structure from functional programming called a _semiring_ for the `Cause` data type. **It allows us to take a base type `E` that represents the error type and then capture the sequential and parallel composition of errors in a fully lossless fashion**.

The following snippet shows how the `Cause` is designed as a semiring data structure:

```scala
sealed abstract class Cause[+E] extends Product with Serializable { self =>
  import Cause._
  def trace: ZTrace = ???

  final def ++[E1 >: E](that: Cause[E1]): Cause[E1] = Then(self, that)
  final def &&[E1 >: E](that: Cause[E1]): Cause[E1] = Both(self, that)
}

object Cause extends Serializable {
  case object Empty extends Cause[Nothing]
  final case class Fail[+E](value: E, override val trace: ZTrace) extends Cause[E]
  final case class Die(value: Throwable, override val trace: ZTrace) extends Cause[Nothing]
  final case class Interrupt(fiberId: FiberId, override val trace: ZTrace) extends Cause[Nothing]
  final case class Stackless[+E](cause: Cause[E], stackless: Boolean) extends Cause[E]
  final case class Then[+E](left: Cause[E], right: Cause[E]) extends Cause[E]
  final case class Both[+E](left: Cause[E], right: Cause[E]) extends Cause[E]
}
```

Using the `Cause` data structure described above, ZIO can capture all errors inside the application.

## Cause Variations

There are several causes for various errors. In this section, we will describe each of these causes. We will see how they can be created manually or how they will be automatically generated as the underlying error management data type of a ZIO application.

### Empty

The `Empty` cause indicates the lack of errors. We use `Cause.empty` constructor to create an `Empty` cause. Using `ZIO.failCause` we can create a ZIO effect that has an empty cause:

```scala mdoc:compile-only
import zio._

ZIO.failCause(Cause.empty).cause.debug
// Empty
```

Also, we can use the `ZIO#cause` to uncover the underlying cause of an effect. For example, we know that the `ZIO.succeed(5)` has no errors. So, let's check that:

```
ZIO.succeed(5).cause.debug
// Empty

ZIO.attempt(5).cause.debug
// Empty
```

### Fail

The `Fail` cause indicates the cause of an expected error of type `E`. We can create one using the `Cause.fail` constructor:

```scala mdoc:compile-only
import zio._

ZIO.failCause(Cause.fail("Oh uh!")).cause.debug
// Fail(Oh uh!,ZTrace(Runtime(2,1646395282),Chunk(<empty>.MainApp.run(MainApp.scala:4))))
```

Let's uncover the cause of some ZIO effects especially when we combine them:

```scala mdoc:compile-only
import zio._

ZIO.fail("Oh uh!").cause.debug
// Fail(Oh uh!,ZTrace(Runtime(2,1646395627),Chunk(<empty>.MainApp.run(MainApp.scala:3))))

(ZIO.fail("Oh uh!") *> ZIO.dieMessage("Boom!") *> ZIO.interrupt).cause.debug
// Fail(Oh uh!,ZTrace(Runtime(2,1646396370),Chunk(<empty>.MainApp.run(MainApp.scala:6))))

(ZIO.fail("Oh uh!") <*> ZIO.fail("Oh Error!")).cause.debug
// Fail(Oh uh!,ZTrace(Runtime(2,1646396419),Chunk(<empty>.MainApp.run(MainApp.scala:9))))

val myApp: ZIO[Any, String, Int] =
  for {
    i <- ZIO.succeed(5)
    _ <- ZIO.fail("Oh uh!")
    _ <- ZIO.dieMessage("Boom!")
    _ <- ZIO.interrupt
  } yield i 
myApp.cause.debug
// Fail(Oh uh!,ZTrace(Runtime(2,1646397126),Chunk(<empty>.MainApp.myApp(MainApp.scala:13),<empty>.MainApp.run(MainApp.scala:17))))
```

### Die

The `Die` cause indicates a defect or in other words, an unexpected failure of type `Throwable`. Additionally, it contains the stack traces of the occurred defect. We can use the `Cause.die` to create one:

```scala mdoc:compile-only
import zio._

ZIO.failCause(Cause.die(new Throwable("Boom!"))).cause.debug
// Die(java.lang.Throwable: Boom!,ZTrace(Runtime(2,1646479908),Chunk(<empty>.MainApp.run(MainApp.scala:3))))
```

If we have a bug in our code and something throws an unexpected exception, that information would be described inside a `Die`. Let try to investigate some ZIO codes that will die:

```scala mdoc:compile-only
import zio._

ZIO.succeed(5 / 0).cause.debug
// Die(java.lang.ArithmeticException: / by zero,ZTrace(Runtime(2,1646480112),Chunk(zio.internal.FiberContext.runUntil(FiberContext.scala:538),<empty>.MainApp.run(MainApp.scala:3))))

ZIO.dieMessage("Boom!").cause.debug
// Stackless(Die(java.lang.RuntimeException: Boom!,ZTrace(Runtime(2,1646398246),Chunk(<empty>.MainApp.run(MainApp.scala:7)))),true)
```

It is worth noting that the latest example is wrapped by the `Stackless` cause in the previous example. We will discuss the `Stackeless` further, but for now, it is enough to know that the `Stackeless` include fewer stack traces for the `Die` cause.

### Interrupt

The `Interrupt` cause indicates a fiber interruption which contains information of the _fiber id_ of the interrupted fiber and also the corresponding stack strace. Let's try an example of:

```scala mdoc:compile-only
import zio._

ZIO.interrupt.cause.debug
// Interrupt(Runtime(2,1646471715),ZTrace(Runtime(2,1646471715),Chunk(<empty>.MainApp.run(MainApp.scala:3))))

ZIO.never.fork
  .flatMap(f => f.interrupt *> f.join)
  .cause
  .debug
// Interrupt(Runtime(2,1646472025),ZTrace(Runtime(13,1646472025),Chunk(<empty>.MainApp.run(MainApp.scala:7))))
```

### Stackless

The `Stackless` cause is to store stack traces and execution traces. It has a boolean stackless flag which denotes that the ZIO runtime should print the full stack trace of the inner cause or just print the few lines of it.

For example, the `ZIO.dieMessage` uses the `Stackless`:

```scala mdoc:compile-only
import zio._

ZIO.dieMessage("Boom!").cause.debug
// Stackless(Die(java.lang.RuntimeException: Boom!,ZTrace(Runtime(2,1646477970),Chunk(<empty>.MainApp.run(MainApp.scala:3)))),true)
```

So when we run it the following stack traces will be printed:

```scala
timestamp=2022-03-05T11:08:19.530710679Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.RuntimeException: Boom!
at <empty>.MainApp.run(MainApp.scala:3)"
```

While the `ZIO.die` doesn't use `Stackless` cause:

```scala mdoc:compile-only
import zio._

ZIO.die(new Throwable("Boom!")).cause.debug
// Die(java.lang.Exception: Boom!,ZTrace(Runtime(2,1646479093),Chunk(<empty>.MainApp.run(MainApp.scala:3))))
```

So it prints the full stack trace:

```scala
timestamp=2022-03-05T11:19:12.666418357Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.Exception: Boom!
	at MainApp$.$anonfun$run$1(MainApp.scala:4)
	at zio.ZIO$.$anonfun$die$1(ZIO.scala:3384)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:255)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
	at <empty>.MainApp.run(MainApp.scala:4)"
```

### Both

When we are doing parallel computation, the effect can fail for more than one reason. If we are doing two things at once and both of them fail then we actually have two errors. So, the `Both` cause stores the composition of two parallel causes.

For example, if we run two parallel fibers with `zipPar` and all of them fail, so their causes will be encoded with `Both`:

```scala mdoc:compile-only
import zio._

val myApp: ZIO[Any, String, Unit] =
  for {
    f1 <- ZIO.fail("Oh uh!").fork
    f2 <- ZIO.dieMessage("Boom!").fork
    _ <- (f1 <*> f2).join
  } yield ()
myApp.cause.debug
// Both(Fail(Oh uh!,ZTrace(Runtime(13,1646481219),Chunk(<empty>.MainApp.myApp(MainApp.scala:5)))),Stackless(Die(java.lang.RuntimeException: Boom!,ZTrace(Runtime(14,1646481219),Chunk(<empty>.MainApp.myApp(MainApp.scala:6)))),true))
```

Ir we run the `myApp` in the stack trace we can see two exception traces occurred on two separate fibers:

```scala
timestamp=2022-03-05T12:37:46.831096692Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-13" java.lang.String: Oh uh!
at <empty>.MainApp.myApp(MainApp.scala:5)
  Exception in thread "zio-fiber-14" java.lang.RuntimeException: Boom!
  at <empty>.MainApp.myApp(MainApp.scala:6)"
``` 

Other parallel operators are also the same, for example, ZIO encode the underlying cause of the `(ZIO.fail("Oh uh!") <&> ZIO.dieMessage("Boom!"))` with `Both` cause. 

### Then

ZIO uses `Then` cause to encode sequential errors. For example, if we perform ZIO's analog of `try-finally` (e.g. `ZIO#ensuring`), and both of `try` and `finally` blocks fail, so their causes are encoded with `Then`:

```scala mdoc:compile-only
import zio._

val myApp = 
  ZIO.fail("first")
    .ensuring(ZIO.die(throw new Exception("second")))
    
myApp.cause.debug
// Then(Fail(first,ZTrace(Runtime(2,1646486975),Chunk(<empty>.MainApp.myApp(MainApp.scala:4),<empty>.MainApp.myApp(MainApp.scala:5),<empty>.MainApp.run(MainApp.scala:7)))),Die(java.lang.Exception: second,ZTrace(Runtime(2,1646486975),Chunk(zio.internal.FiberContext.runUntil(FiberContext.scala:538),<empty>.MainApp.myApp(MainApp.scala:5),<empty>.MainApp.run(MainApp.scala:7)))))
```

If we run the `myApp` effect, we can see the following stack trace:

```scala
timestamp=2022-03-05T13:30:17.335173071Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: first
	at <empty>.MainApp.myApp(MainApp.scala:4)
	at <empty>.MainApp.myApp(MainApp.scala:5)
	Suppressed: java.lang.Exception: second
		at MainApp$.$anonfun$myApp$3(MainApp.scala:5)
		at zio.ZIO$.$anonfun$die$1(ZIO.scala:3384)
		at zio.internal.FiberContext.runUntil(FiberContext.scala:255)
		at zio.internal.FiberContext.run(FiberContext.scala:115)
		at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
		at zio.internal.FiberContext.runUntil(FiberContext.scala:538)
		at <empty>.MainApp.myApp(MainApp.scala:5)"
```

As we can see in the above stack trace, the _first_ failure was suppressed by the _second_ defect.
