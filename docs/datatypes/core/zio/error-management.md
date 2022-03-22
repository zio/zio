---
id: error-management
title: "Error Management"
---

As well as providing first-class support for typed errors, ZIO has a variety of facilities for catching, propagating, and transforming errors in a typesafe manner. In this section, we will learn about different types of errors in ZIO and how we can manage them.

## Three Types of Errors in ZIO

We should consider three types of errors when writing ZIO applications:

1. **Failures** are expected errors. We use `ZIO.fail` to model a failure. As they are expected, we know how to handle them. So we should handle these errors and prevent them from propagating throughout the call stack.

2. **Defects** are unexpected errors. We use `ZIO.die` to model a defect. As they are not expected, we need to propagate them through the application stack, until in the upper layers one of the following situations happens:
    - In one of the upper layers, it makes sense to expect these errors. So we will convert them to failure, and then they can be handled.
    - None of the upper layers won't catch these errors, so it will finally crash the whole application.

3. **Fatals** are catastrophic unexpected errors. When they occur we should kill the application immediately without propagating the error furthermore. At most, we might need to log the error and print its call stack.

### 1. Failures

When writing ZIO application, we can model the failure, using the `ZIO.fail` constructor:

```scala
trait ZIO {
  def fail[E](error: => E): ZIO[Any, E, Nothing]
}
```

Let's try to model some failures using this constructor:

```scala mdoc:silent
import zio._

val f1: ZIO[Any, String, Nothing] = ZIO.fail("Oh uh!")
val f2: ZIO[Any, String, Int]     = ZIO.succeed(5) *> ZIO.fail("Oh uh!")
```

Let's try to run a failing effect and see what happens:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.succeed(5) *> ZIO.fail("Oh uh!")
}
```

This will crash the application and print the following stack trace:

```scala
timestamp=2022-03-08T17:55:50.002161369Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
	at <empty>.MainApp.run(MainApp.scala:4)"
```

We can also model the failure using `Exception`:

```  
val f2: ZIO[Any, Exception, Nothing] = 
  ZIO.fail(new Exception("Oh uh!"))
```

Or we can model our failures using user-defined failure types (domain errors):

```
case class NegativeNumberException(msg: String) extends Exception(msg)

val validateNonNegaive(input: Int): ZIO[Any, NegativeNumberException, Int] =
  if (input < 0)
    ZIO.fail(NegativeNumberException(s"entered negative number: $input"))
  else
    ZIO.succeed(input)
```

In the above examples, we can see that the type of the `validateNonNegaive` function is `ZIO[Any, NegativeNumberException, Int]`. It means this is an exceptional effect, which may fail with the type of `NegativeNumberException`.

The `ZIO.fail` constructor is somehow the moral equivalent of `throw` for pure codes. We will discuss this [further](#imperative-vs-functional-error-handling).

### 2. Defects

By providing a `Throwable` value to the `ZIO.die` constructor, we can describe a dying effect:

```scala
object ZIO {
  def die(t: => Throwable): ZIO[Any, Nothing, Nothing]
}
```

Here is an example of such effect, which will die because of encountering _divide by zero_ defect:

```scala mdoc:compile-only
import zio._

val dyingEffect: ZIO[Any, Nothing, Nothing] = 
  ZIO.die(new ArithmeticException("divide by zero"))
```

The result is the creation of a ZIO effect whose error channel and success channel are both 'Nothing'. In other words, this effect cannot fail and does not produce anything. Instead, it is an effect describing a _defect_ or an _unexpected error_.

Let's see what happens if we run this effect:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.die(new ArithmeticException("divide by zero"))
}
```

If we run this effect, the ZIO runtime will print the stack trace that belongs to this defect. So, here is the output:

```scala
timestamp=2022-02-16T13:02:44.057191215Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.ArithmeticException: divide by zero
	at MainApp$.$anonfun$run$1(MainApp.scala:4)
	at zio.ZIO$.$anonfun$die$1(ZIO.scala:3384)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:255)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
	at <empty>.MainApp.run(MainApp.scala:4)"
```

The `ZIO.die` constructor is used to manually describe a dying effect because of a defect inside the code.

For example, assume we want to write a `divide` function that takes two numbers and divides the first number by the second. We know that the `divide` function is not defined for zero dominators. Therefore, we should signal an error if division by zero occurs.

We have two choices to implement this function using the ZIO effect:

1. We can divide the first number by the second, and if the second number was zero, we can fail the effect using `ZIO.fail` with the `ArithmeticException` failure value:

```scala mdoc:compile-only
import zio._

def divide(a: Int, b: Int): ZIO[Any, ArithmeticException, Int] =
  if (b == 0)
    ZIO.fail(new ArithmeticException("divide by zero"))
  else
    ZIO.succeed(a / b)
```

2. We can divide the first number by the second. In the case of zero for the second number, we use `ZIO.die` to kill the effect by sending a signal of `ArithmeticException` as the defect signal:

```scala mdoc:compile-only
import zio._

def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] =
  if (b == 0)
    ZIO.die(new ArithmeticException("divide by zero")) // Unexpected error
  else
    ZIO.succeed(a / b)
```

So what is the difference between these two approaches? Let's compare the function signature:

```scala
def divide(a: Int, b: Int): ZIO[Any, ArithmeticException, Int]   // using ZIO.fail
def divide(a: Int, b: Int): ZIO[Any, Nothing,             Int]   // using ZIO.die
```

1. The first approach, models the _divide by zero_ error by _failing_ the effect. We call these failures _expected errors_or _typed error_.
2. While the second approach models the _divide by zero_ error by _dying_ the effect. We call these kinds of errors _unexpected errors_, _defects_ or _untyped errors_.

We use the first method when we are handling errors as we expect them, and thus we know how to handle them. In contrast, the second method is used when we aren't expecting those errors in our domain, and we don't know how to handle them. Therefore, we use the _let it crash_ philosophy.

In the second approach, we can see that the `divide` function indicates that it cannot fail. But, it doesn't mean that this function hasn't any defects. ZIO defects are not typed, so they cannot be seen in type parameters.

Note that to create an effect that will die, we shouldn't throw an exception inside the `ZIO.die` constructor, although it works. Instead, the idiomatic way of creating a dying effect is to provide a `Throwable` value into the `ZIO.die` constructor:

```scala mdoc:compile-only
import zio._

val defect1 = ZIO.die(new ArithmeticException("divide by zero"))       // recommended
val defect2 = ZIO.die(throw new ArithmeticException("divide by zero")) // not recommended 
```

Also, if we import a code that may throw an exception, all the exceptions will be translated to the ZIO defect:

```scala mdoc:compile-only
import zio._

val defect3 = ZIO.succeed(throw new Exception("boom!"))
```

Therefore, in the second approach of the `divide` function, we do not require to die the effect in case of the _dividing by zero_ because the JVM itself throws an `ArithmeticException` when the denominator is zero. When we import any code into the `ZIO` effect if any exception is thrown inside that code, will be translated to _ZIO defects_ by default. So the following program is the same as the previous example:

```scala mdoc:compile-only
import zio._

def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] = 
  ZIO.succeed(a / b)
```

Another important note is that if we `map`/`flatMap` a ZIO effect and then accidentally throw an exception inside the map operation, that exception will be translated to the ZIO defect:

```scala mdoc:compile-only
import zio._

val defect4 = ZIO.succeed(???).map(_ => throw new Exception("Boom!"))
val defect5 = ZIO.attempt(???).map(_ => throw new Exception("Boom!"))
```

### 3. Fatal Errors

In ZIO, the `VirtualMachineError` and all its subtypes are the only errors considered fatal by the ZIO runtime. So if during the running application, the JVM throws any of these errors like `StackOverflowError`, the ZIO runtime considers it as a catastrophic fatal error. So it will interrupt the whole application immediately without safe resource interruption. None of the `ZIO#catchAll` and `ZIO#catchAllDefects` can catch this fatal error. At most, if the `RuntimeConfig.reportFatal` is enabled, the application will log the stack trace before interrupting the whole application.

Here is an example of manually creating a fatal error. Although we are ignoring all expected and unexpected errors, the fatal error interrupts the whole application.

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    ZIO
      .attempt(
        throw new StackOverflowError(
          "The call stack pointer exceeds the stack bound."
        )
      )
      .catchAll(_ => ZIO.unit)       // ignoring all expected errors
      .catchAllDefect(_ => ZIO.unit) // ignoring all unexpected errors
}
```

The output will be something like this:

```scala
java.lang.StackOverflowError: The call stack pointer exceeds the stack bound.
	at MainApp$.$anonfun$run$1(MainApp.scala:8)
	at zio.ZIO$.$anonfun$attempt$1(ZIO.scala:2946)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:247)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
**** WARNING ****
Catastrophic error encountered. Application not safely interrupted. Resources may be leaked. Check the logs for more details and consider overriding `RuntimeConfig.reportFatal` to capture context.
```

Note that, to change the default fatal error we can use the `Runtime#mapRuntimeConfig` and change the `RuntimeConfig#fatal` function. Using this map operation we can also change the `RuntimeConfig#reportFatal` to change the behavior of the `reportFatal`'s runtime hook function.

## Imperative vs. Functional Error Handling

When practicing imperative programming in Scala, we have the `try`/`catch` language construct for handling errors. Using them, we can wrap regions that may throw exceptions, and handle them in the catch block.

Let's try an example. In the following code we have an age validation function that may throw two exceptions:

```scala mdoc:silent
sealed trait AgeValidationException extends Exception
case class NegativeAgeException(age: Int) extends AgeValidationException
case class IllegalAgeException(age: Int)  extends AgeValidationException

def validate(age: Int): Int = {
  if (age < 0)
    throw NegativeAgeException(age)
  else if (age < 18) 
    throw IllegalAgeException(age)
  else age
}
```

Using `try`/`catch` we can handle exceptions:

```scala
try {
  validate(17)
} catch {
  case NegativeAgeException(age) => ???
  case IllegalAgeException(age) =>  ???
}
```

There are some issues with error handling using exceptions and `try`/`catch`/`finally` statement:

1. **It lacks type safety on errors** — There is no way to know what errors can be thrown by looking the function signature. The only way to find out in which circumstance a method may throw an exception is to read and investigate its implementation. So the compiler cannot prevent us from type errors. It is also hard for a developer to read the documentation event through reading the documentation is not suffice as it may be obsolete, or it may don't reflect the exact exceptions.

```scala mdoc:invisible:reset

```

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

We can handle errors using `catchAll`/`catchSome` methods:

```scala mdoc:compile-only
validate(17).catchAll {
  case NegativeAgeException(age) => ???
  case IllegalAgeException(age)  => ???
}
```

2. **It doesn't help us to write total functions** — When we use `try`/`catch` the compiler doesn't know about errors at compile-time, so if we forgot to handle one of the exceptions the compiler doesn't help us to write total functions. This code will crash at runtime because we forgot to handle the `IllegalAgeException` case:

```scala
try {
  validate(17)
} catch {
  case NegativeAgeException(age) => ???
  //  case IllegalAgeException(age) => ???
}
```

When we are using typed errors we can have exhaustive checking support of the compiler. So, for example, when we are catching all errors if we forgot to handle one of the cases, the compiler warns us about that:

```scala mdoc
validate(17).catchAll {
  case NegativeAgeException(age) => ???
}

// match may not be exhaustive.
// It would fail on the following input: IllegalAgeException(_)
```

This helps us cover all cases and write _total functions_ easily.

> **Note:**
>
> When a function is defined for all possible input values, it is called a _total function_ in functional programming.

3. **Its error model is broken and lossy** — The error model based on the `try`/`catch`/`finally` statement is broken. Because if we have the combinations of these statements we can throw many exceptions, and then we are only able to catch one of them. All the other ones are lost. They are swallowed into a black hole, and also the one that we catch is the wrong one. It is not the primary cause of the failure.

To be more specific, if the `try` block throws an exception, and the `finally` block throws an exception as well, then, if these are caught at a higher level, only the finalizer's exception will be caught normally, not the exception from the try block.

In the following example, we are going to show this behavior:

```scala mdoc:silent
 try {
    try throw new Error("e1")
    finally throw new Error("e2")
 } catch {
   case e: Error => println(e) 
 }
 
// Output:
// e2
```

The above program just prints the `e2`, which is lossy. The `e2` is not the primary cause of failure.

In ZIO, all the errors will still be reported. So even though we are only able to catch one error, the other ones will be reported which we have full control over them. They don't get lost.

Let's write a ZIO version:

```scala mdoc:silent
ZIO.fail("e1")
  .ensuring(ZIO.succeed(throw new Exception("e2")))
  .catchAll {
    case "e1" => Console.printLine("e1")
    case "e2" => Console.printLine("e2")
  }

// Output:
// e1
```

ZIO guarantees that no errors are lost. It has a _lossless error model_. This guarantee is provided via a hierarchy of supervisors and information made available via data types such as `Exit` and `Cause`. All errors will be reported. If there's a bug in the code, ZIO enables us to find about it.

## Expected and Unexpected Errors

Inside an application, there are two distinct categories of errors:

1. **Expected Errors**— They are also known as _recoverable errors_, _declared errors_ or _errors_.

Expected errors are those errors in which we expected them to happen in normal circumstances, and we can't prevent them. They can be predicted upfront, and we can plan for them. We know when, where, and why they occur. So we know when, where, and how to handle these errors. By handling them we can recover from the failure, this is why we say they are _recoverable errors_. All domain errors, business errors are expected once because we talk about them in workflows and user stories, so we know about them in the context of business flows.

For example, when accessing an external database, that database might be down for some short period of time, so we retry to connect again, or after some number of attempts, we might decide to use an alternative solution, e.g. using an in-memory database.

2. **Unexpected Errors**— _non-recoverable errors_, _defects_.

We know there is a category of things that we are not going to expect and plan for. These are the things we don't expect but of course, we know they are going to happen. We don't know what is the exact root of these errors at runtime, so we have no idea how to handle them. They are actually going to bring down our production application, and then we have to figure out what went wrong to fix them.

For example, the corrupted database file will cause an unexpected error. We can't handle that in runtime. It may be necessary to shut down the whole application in order to prevent further damage.

Most of the unexpected errors are rooted in programming errors. This means, we have just tested the _happy path_, so in case of _unhappy path_ we encounter a defect. When we have defects in our code we have no way of knowing about them otherwise we investigate, test, and fix them.

One of the common programming errors is forgetting to validate unexpected errors that may occur when we expect an input but the input is not valid, while we haven't validated the input. When the user inputs the invalid data, we might encounter the divide by zero exception or might corrupt our service state or a cause similar defect. These kinds of defects are common when we upgrade our service with the new data model for its input, while one of the other services is not upgraded with the new data contract and is calling our service with the deprecated data model. If we haven't a validation phase, they will cause defects!

Another example of defects is memory errors like buffer overflows, stack overflows, out-of-memory, invalid access to null pointers, and so forth. Most of the time these unexpected errors are occurs when we haven't written a memory-safe and resource-safe program, or they might occur due to hardware issues or uncontrollable external problems. We as a developer don't know how to cope with these types of errors at runtime. We should investigate to find the exact root cause of these defects.

As we cannot handle unexpected errors, we should instead log them with their respective stack traces and contextual information. So later we could investigate the problem and try to fix them. The best we can do with unexpected errors is to _sandbox_ them to limit the damage that they do to the overall application. For example, an unexpected error in browser extension shouldn't crash the whole browser.

So the best practice for each of these errors is as follows:

1. **Expected Errors** — we handle expected errors with the aid of the Scala compiler, by pushing them into the type system. In ZIO there is the error type parameter called `E`, and this error type parameter is for modeling all the expected errors in the application.

A ZIO value has a type parameter `E` which is the type of _declared errors_ it can fail with. `E` only covers the errors which were specified at the outset. The same ZIO value could still throw exceptions in unforeseen ways. These unforeseen situations are called _defects_ in a ZIO program, and they lie outside `E`.

Bringing abnormal situations from the domain of defects into that of `E` enables the compiler to help us keep a tab on error conditions throughout the application, at compile time. This helps ensure the handling of domain errors in domain-specific ways.

2. **Unexpected Errors** — We handle unexpected errors by not reflecting them to the type system because there is no way we could do it, and it wouldn't provide any value if we could. At best as we can, we simply sandbox that to some well-defined area of the application.

Note that _defects_, can creep silently to higher levels in our application, and, if they get triggered at all, their handling might eventually be in more general ways.

So for ZIO, expected errors are reflected in the type of the ZIO effect, whereas unexpected errors are not so reflective, and that is the distinction.

That is the best practice. It helps us write better code. The code that we can reason about its error properties and potential expected errors. We can look at the ZIO effect and know how it is supposed to fail.

So to summarize
1. Unexpected errors are impossible to recover and they will eventually shut down the application but expected errors can be recovered by handling them.
2. We do not type unexpected errors, but we type expected errors either explicitly or using general `Throwable` error type.
3. Unexpected errors mostly is a sign of programming errors, but expected errors part of domain errors.

## Exceptional and Unexceptional Effects

Besides the `IO` type alias, ZIO has four different type aliases which can be categorized into two different categories:
- **Exceptional Effect** — `Task` and `RIO` are two effects whose error parameter is fixed to `Throwable`, so we call them exceptional effects.
- **Unexceptional Effect** - `UIO` and `URIO` have error parameters that are fixed to `Nothing`, indicating that they are unexceptional effects. So they can't fail, and the compiler knows about it.

So when we compose different effects together, at any point of the codebase we can determine this piece of code can fail or cannot. As a result, typed errors offer a compile-time transition point between this can fail and this can't fail.

For example, the `ZIO.acquireReleaseWith` API asks us to provide three different inputs: _require_, _release_, and _use_. The `release` parameter requires a function from `A` to `URIO[R, Any]`. So, if we put an exceptional effect, it will not compile:

```scala
def acquireReleaseWith[R, E, A, B](
  acquire: => ZIO[R, E, A],
  release: A => URIO[R, Any],
  use: A => ZIO[R, E, B]
): ZIO[R, E, B]
```

## Typed Errors Don't Guarantee the Absence of Defects and Interruptions

Having an effect of type `ZIO[R, E, A]`, means it can fail because of some failure of type `E`, but it doesn't mean it can't die or be interrupted. So the error channel is only for `failure` errors.

In the following example, the type of the `validateNonNegativeNumber` function is `ZIO[Any, String, Int]` which denotes it is a typed exceptional effect. It can fail of type `String` but it still can die with the type of `NumberFormatException` defect:

```scala mdoc:silent
import zio._

def validateNonNegativeNumber(input: String): ZIO[Any, String, Int] =
  input.toIntOption match {
    case Some(value) if value >= 0 =>
      ZIO.succeed(value)
    case Some(other) =>
      ZIO.fail(s"the entered number is negative: $other")
    case None =>
      ZIO.die(
        new NumberFormatException(
          s"the entered input is not in the correct number format: $input"
        )
      )
  }
```

Also, its underlying fiber can be interrupted without affecting the error channel:

```scala mdoc:compile-only
import zio._

val myApp: ZIO[Any, String, Int] =
  for {
    f <- validateNonNegativeNumber("5").fork
    _ <- f.interrupt
    r <- f.join
  } yield r
```

Therefore, if we run the `myApp` effect, it will be interrupted before it gets the chance to finish.

## Sequential and Parallel Errors

A simple and regular ZIO application usually fails with one error, which is the first error encountered by the ZIO runtime.

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val fail = ZIO.fail("Oh uh!")
  val die = ZIO.dieMessage("Boom!")
  val interruption = ZIO.interrupt

  def run = (fail <*> die) *> interruption
}
```

This application will fail with the first error which is "Oh uh!":

```scala
timestamp=2022-03-09T09:50:22.067072131Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
	at <empty>.MainApp.fail(MainApp.scala:4)"
```

In some cases, we may run into multiple errors. When we perform parallel computations, the application may fail due to multiple errors:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.fail("Oh!") <&> ZIO.fail("Uh!")
}
```

If we run this application, we can see two exceptions in two different fibers that caused the failure (`zio-fiber-0` and `zio-fiber-14`):

```scala
timestamp=2022-03-09T08:05:48.703035927Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-13" java.lang.String: Oh!
	at <empty>.MainApp.run(MainApp.scala:4)
Exception in thread "zio-fiber-14" java.lang.String: Uh!
	at <empty>.MainApp.run(MainApp.scala:4)"
```

ZIO has a combinator called `ZIO#parallelErrors` that exposes all parallel failure errors in the error channel:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, ::[String], Nothing] = 
  (ZIO.fail("Oh uh!") <&> ZIO.fail("Oh Error!")).parallelErrors
```

Note that this operator is only for failures, not defects or interruptions.

Also, when we work with resource-safety operators like `ZIO#ensuring` we can have multiple sequential errors. Why? because regardless of the original effect has any errors or not, the finalizer is uninterruptible. So the finalizer will be run. Unless the finalizer should be an unexceptional effect (`URIO`), it may die because of a defect. Therefore, it creates multiple sequential errors:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.fail("Oh uh!").ensuring(ZIO.dieMessage("Boom!"))
}
```

When we run this application, we can see that the original failure (`Oh uh!`) was suppressed by another defect (`Boom!`):

```scala
timestamp=2022-03-09T08:30:56.563179230Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
	at <empty>.MainApp.run(MainApp.scala:4)
	Suppressed: java.lang.RuntimeException: Boom!
		at <empty>.MainApp.run(MainApp.scala:4)"
```

## Recovering From Errors

### 1. Catching

#### Catching Failures

If we want to catch and recover from all _typed error_ and effectfully attempt recovery, we can use the `ZIO#catchAll` operator:

```scala
trait ZIO[-R, +E, +A] {
  def catchAll[R1 <: R, E2, A1 >: A](h: E => ZIO[R1, E2, A1]): ZIO[R1, E2, A1]
}
```

We can recover from all errors while reading a file and then fallback to another operation:

```scala mdoc:invisible
import java.io.{ FileNotFoundException, IOException }

def readFile(s: String): ZIO[Any, IOException, Array[Byte]] = 
  ZIO.attempt(???).refineToOrDie[IOException]
```

```scala mdoc:silent
import zio._

val z: ZIO[Any, IOException, Array[Byte]] = 
  readFile("primary.json").catchAll(_ => 
    readFile("backup.json"))
```

In the callback passed to `ZIO#catchAll`, we may return an effect with a different error type (or perhaps `Nothing`), which will be reflected in the type of effect returned by `ZIO#catchAll`.

When using this operator, the match cases should be exhaustive:

```scala mdoc:compile-only
val result: ZIO[Any, Nothing, Int] =
  validate(20)
  .catchAll {
    case NegativeAgeException(age) =>
      ZIO.debug(s"negative age: $age").as(-1)
    case IllegalAgeException(age) =>
      ZIO.debug(s"illegal age: $age").as(-1)
  }
```

If we forget to catch all cases and the match fails, the original **failure** will be lost and replaced by a `MatchError` **defect**:

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  val result: ZIO[Any, Nothing, Int] =
    validate(15)
      .catchAll {
        case NegativeAgeException(age) =>
          ZIO.debug(s"negative age: $age").as(-1)
//        case IllegalAgeException(age) =>
//          ZIO.debug(s"illegal age: $age").as(-1)
      }

  def run = result
}

// Output:
// timestamp=2022-03-01T06:33:13.454651904Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" scala.MatchError: MainApp$IllegalAgeException (of class MainApp$IllegalAgeException)
//	at MainApp$.$anonfun$result$1(MainApp.scala:6)
//	at scala.util.Either.fold(Either.scala:190)
//	at zio.ZIO.$anonfun$foldZIO$1(ZIO.scala:945)
//  ...
//	at zio.internal.FiberContext.runUntil(FiberContext.scala:538)"
```

Another important note about `ZIO#catchAll` is that this operator only can recover from _failures_. So it can't recover from defects or fiber interruptions.

Let's try what happens if we `catchAll` on a dying effect:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val die: ZIO[Any, String, Nothing] = 
    ZIO.dieMessage("Boom!") *> ZIO.fail("Oh uh!") 
  
  def run = die.catchAll(_ => ZIO.unit)
}

// Output:
// timestamp=2022-03-03T11:04:41.209169849Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.RuntimeException: Boom!
// 	at <empty>.MainApp.die(MainApp.scala:6)
//	at <empty>.MainApp.run(MainApp.scala:8)"
```

Also, if we have a fiber interruption, we can't catch that using this operator:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val interruptedEffect: ZIO[Any, String, Nothing] =
    ZIO.interrupt *> ZIO.fail("Oh uh!")

  def run = interruptedEffect.catchAll(_ => ZIO.unit)
}

// Output:
// timestamp=2022-03-03T11:10:15.573588420Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.InterruptedException: Interrupted by thread "zio-fiber-"
//	at <empty>.MainApp.die(MainApp.scala:6)
//	at <empty>.MainApp.run(MainApp.scala:8)"
```

If we want to catch and recover from only some types of exceptions and effectfully attempt recovery, we can use the `catchSome` method:

```scala
trait ZIO[-R, +E, +A] {
  def catchSome[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[E, ZIO[R1, E1, A1]]
  ): ZIO[R1, E1, A1]
}
```

Now we can do the same:

```scala mdoc:compile-only
import zio._

val data: ZIO[Any, IOException, Array[Byte]] = 
  readFile("primary.data").catchSome {
    case _ : FileNotFoundException => 
      readFile("backup.data")
  }
```

The `ZIO#catchSome` cannot eliminate the error type, although it can widen the error type to a broader class of errors. So unlike the `ZIO#catchAll` we are not required to provide every match case.

#### Catching Defects

Like catching failures, ZIO has two operators to catch _defects_: `ZIO#catchAllDefect` and `ZIO#catchSomeDefect`. Let's try the former one:

```scala mdoc:compile-only
import zio._

ZIO.dieMessage("Boom!")
  .catchAllDefect {
    case e: RuntimeException if e.getMessage == "Boom!" =>
      ZIO.debug("Boom! defect caught.")
    case _: NumberFormatException =>
      ZIO.debug("NumberFormatException defect caught.")
    case _ =>
      ZIO.debug("Unknown defect caught.")
  } 
```

We should note that using these operators, we can only recover from a dying effect, and it cannot recover from a failure or fiber interruption.

A defect is an error that cannot be anticipated in advance, and there is no way to respond to it. Our rule of thumb is to not recover defects since we don't know about them. We let them crash the application.

Although, in some cases, we might need to reload a part of the application instead of killing the entire application. Assume we have written an application that can load plugins at runtime. During the runtime of the plugins, if a defect occurs, we don't want to crash the entire application; rather, we log all defects and then reload the plugin.

#### Catching Causes

So far, we have only studied how to catch _failures_ and _defects_. But what about _fiber interruptions_ or how about the specific combination of these errors?

With the help of the `ZIO#catchAllCause` operator we can catch all errors of an effect and recover from them:

```scala mdoc:compile-only
import zio._

val exceptionalEffect = ZIO.attempt(???)

exceptionalEffect.catchAllCause {
  case Cause.Empty =>
    ZIO.debug("no error caught")
  case Cause.Fail(value, _) =>
    ZIO.debug(s"a failure caught: $value")
  case Cause.Die(value, _) =>
    ZIO.debug(s"a defect caught: $value")
  case Cause.Interrupt(fiberId, _) =>
    ZIO.debug(s"a fiber interruption caught with the fiber id: $fiberId")
  case Cause.Stackless(cause: Cause.Die, _) =>
    ZIO.debug(s"a stackless defect caught: ${cause.value}")
  case Cause.Stackless(cause: Cause[_], _) =>
    ZIO.debug(s"an unknown stackless defect caught: ${cause.squashWith(identity)}")
  case Cause.Then(left, right) =>
    ZIO.debug(s"two consequence causes caught")
  case Cause.Both(left, right) =>
    ZIO.debug(s"two parallel causes caught")
}
```

Additionally, there is a partial version of this operator called `ZIO#catchSomeCause`, which can be used when we don't want to catch all causes, but some of them.

#### Catching Traces

The two `ZIO#catchAllTrace` and `ZIO#catchSomeTrace` operators are useful to catch the typed error as well as stack traces of exceptional effects:

```scala
trait ZIO[-R, +E, +A] {
  def catchAllTrace[R1 <: R, E2, A1 >: A](
    h: ((E, ZTrace)) => ZIO[R1, E2, A1]
  ): ZIO[R1, E2, A1]
  
  def catchSomeTrace[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[(E, ZTrace), ZIO[R1, E1, A1]]
  ): ZIO[R1, E1, A1]
}
```

In the below example, let's try to catch a failure on the line number 4:

```scala mdoc:compile-only
import zio._

ZIO
  .fail("Oh uh!")
  .catchAllTrace {
    case ("Oh uh!", trace)
      if trace.toJava
        .map(_.getLineNumber)
        .headOption
        .contains(4) =>
      ZIO.debug("caught a failure on the line number 4")
    case _ =>
      ZIO.debug("caught other failures")
  }
```

#### Catching Non-Fatal

We can use the `ZIO#catchNonFatalOrDie` to recover from all non-fatal errors, in case of occurring any [fatal error](#3-fatal-errors), it will die.

```scala
openFile("data.json").catchNonFatalOrDie(_ => openFile("backup.json"))
```

### 2. Fallback

1. **`ZIO#orElse`**— We can try one effect, or if it fails, try another effect with the `orElse` combinator:

```scala
trait ZIO[-R, +E, +A] {
  def orElse[R1 <: R, E2, A1 >: A](that: => ZIO[R1, E2, A1]): ZIO[R1, E2, A1]
}
```

Let's try an example:

```scala mdoc:compile-only
val primaryOrBackupData: ZIO[Any, IOException, Array[Byte]] = 
  readFile("primary.data").orElse(readFile("backup.data"))
```

2. **`ZIO#orElseEither`**— This operator run the orginal effect, and if it run the specified effect and return the result as Either: 

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

3. **`ZIO#orElseSucceed`/`ZIO#orElseFail`**— These two operators convert the original failure with constant succeed or failure values:

```scala
trait ZIO[-R, +R, +E] {
  def orElseFail[E1](e1: => E1): ZIO[R, E1, A]
  def orElseSucceed[A1 >: A](a1: => A1): ZIO[R, Nothing, A1]
}
```

The `ZIO#orElseFail` will always replace the original failure with the new one, so `E1` does not have to be a supertype of `E`. It is useful when we have `Unit` as an error, and we want to unify that with something else:

```scala mdoc:compile-only
import zio._

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

4. **`ZIO#orElseOptional`**— When dealing with optional failure types, we might need to fall back to another effect when the failure value is `None`. This operator helps to do so:

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
      if (input.isBlank)
        ZIO.fail(None)
      else
        ZIO.fail(Some(s"invalid non-integer input: $input"))
  }

val result = parseInt("  ").orElseOptional(ZIO.succeed(0)).debug
```

5. **`ZIO.firstSuccessOf`/`ZIO#firstSuccessOf`**— These two operators make it easy for a user to run an effect, and in case it fails, it will run a series of ZIO effects until one succeeds:

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

### 3. Folding

Scala's `Option` and `Either` data types have `fold`, which let us handle both failure and success at the same time. In a similar fashion, `ZIO` effects also have several methods that allow us to handle both failure and success.

1. **`ZIO#fold`/`ZIO#foldZIO`**— The first fold method, `ZIO#fold`, lets us non-effectfully handle both failure and success, by supplying a non-effectful handler for each case. The second fold method, `ZIO#foldZIO`, lets us effectfully handle both failure and success, by supplying an effectful (but still pure) handler for each case:

```scala
trait ZIO[-R, +E, +A] {
  def fold[B](
    failure: E => B, 
    success: A => B
  ): ZIO[R, Nothing, B]
  
  def foldZIO[R1 <: R, E2, B](
    failure: E => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  ): ZIO[R1, E2, B]
}
```

Let's try an example:

```scala mdoc:silent
import zio._

lazy val DefaultData: Array[Byte] = Array(0, 0)

val primaryOrDefaultData: UIO[Array[Byte]] = 
  readFile("primary.data").fold(_ => DefaultData, data => data)
```

We can ignore any failure and success values:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Nothing, Unit] =
  ZIO
    .fail("Uh oh!")         // ZIO[Any, String, Int]
    .as(5)                  // ZIO[Any, String, Int]
    .fold(_ => (), _ => ()) // ZIO[Any, Nothing, Unit]
```

It is equivalent to use the `ZIO#ignore` operator instead:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Nothing, Unit] = ZIO.fail("Uh oh!").as(5).ignore
```

Now let's try the effectful version of the fold operation. In this example, in case of failure on reading from the primary file, we will fallback to another effectful operation which will read data from the secondary file:

```scala mdoc:compile-only
val primaryOrSecondaryData: IO[IOException, Array[Byte]] = 
  readFile("primary.data").foldZIO(
    failure = _    => readFile("secondary.data"),
    success = data => ZIO.succeed(data)
  )
```

Nearly all error handling methods are defined in terms of `foldZIO`, because it is both powerful and fast.

In the following example, `foldZIO` is used to handle both failure and success of the `readUrls` method:

```scala mdoc:invisible
sealed trait Content
case class NoContent(t: Throwable) extends Content
case class OkContent(s: String) extends Content
def readUrls(file: String): Task[List[String]] = IO.succeed("Hello" :: Nil)
def fetchContent(urls: List[String]): UIO[Content] = IO.succeed(OkContent("Roger"))
```

```scala mdoc:silent
val urls: UIO[Content] =
  readUrls("urls.json").foldZIO(
    error   => IO.succeed(NoContent(error)), 
    success => fetchContent(success)
  )
```

It's important to note that both `ZIO#fold` and `ZIO#foldZIO` operators cannot catch fiber interruptions. So the following application will crash due to `InterruptedException`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = (ZIO.interrupt *> ZIO.fail("Uh oh!")).fold(_ => (), _ => ())
}
```

And here is the output:

```scala
timestamp=2022-02-24T13:41:01.696273024Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.InterruptedException: Interrupted by thread "zio-fiber-"
   at <empty>.MainApp.run(MainApp.scala:4)"
```

2. **`ZIO#foldCause`/`ZIO#foldCauseZIO`**— This cause version of the `fold` operator is useful to access the full cause of the underlying fiber. So in case of failure, based on the exact cause, we can determine what to do:

```scala
trait ZIO[-R, +E, +A] {
  def foldCause[B](
    failure: Cause[E] => B,
    success: A => B
  ): ZIO[R, Nothing, B]

  def foldCauseZIO[R1 <: R, E2, B](
    failure: Cause[E] => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  ): ZIO[R1, E2, B]
}
```

Among the fold operators, these are the most powerful combinators. They can recover from any error, even fiber interruptions.

In the following example, we are printing the proper message according to what cause occurred due to failure:

```scala mdoc:compile-only
import zio._

val exceptionalEffect: ZIO[Any, Throwable, Unit] = ???

val myApp: ZIO[Console, IOException, Unit] =
  exceptionalEffect.foldCauseZIO(
    failure = {
      case Cause.Fail(value, _)        => Console.printLine(s"failure: $value")
      case Cause.Die(value, _)         => Console.printLine(s"cause: $value")
      case Cause.Interrupt(failure, _) => Console.printLine(s"${failure.threadName} interrupted!")
      case _                           => Console.printLine("failed due to other causes")
    },
    success = succeed => Console.printLine(s"succeeded with $succeed value")
  )
```

When catching errors using this operator, if our cases were not exhaustive, we may receive a defect of the type `scala.MatchError` :

```scala mdoc:compile-only
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {
  val exceptionalEffect: ZIO[Any, Throwable, Unit] = ZIO.interrupt

  val myApp: ZIO[Console, IOException, Unit] =
    exceptionalEffect.foldCauseZIO(
      failure = {
        case Cause.Fail(value, _) => ZIO.debug(s"failure: $value")
        case Cause.Die(value, _) => ZIO.debug(s"cause: ${value.toString}")
        // case Cause.Interrupt(failure, _) => ZIO.debug(s"${failure.threadName} interrupted!")
      },
      success = succeed => ZIO.debug(s"succeeded with $succeed value")
    )

  def run = myApp
}
``` 

The output:

```scala
timestamp=2022-02-24T11:05:40.241436257Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" scala.MatchError: Interrupt(Runtime(2,1645700739),ZTrace(Runtime(2,1645700739),Chunk(<empty>.MainApp.exceptionalEffect(MainApp.scala:6),<empty>.MainApp.myApp(MainApp.scala:9)))) (of class zio.Cause$Interrupt)
	at MainApp$.$anonfun$myApp$1(MainApp.scala:10)
	at zio.ZIO$TracedCont$$anon$33.apply(ZIO.scala:6167)
	at zio.ZIO$TracedCont$$anon$33.apply(ZIO.scala:6165)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:885)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:538)"
```

3. **`ZIO#foldTraceZIO`**— This version of fold, provide us the facility to access the trace info of the failure:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Nothing, Int] =
  validate(5).foldTraceZIO(
    failure = {
      case (_: NegativeAgeException, trace) =>
        ZIO.succeed(0).debug(
          "The entered age is negative\n" +
            s"trace info: ${trace.stackTrace.mkString("\n")}"
        )
      case (_: IllegalAgeException, trace) =>
        ZIO.succeed(0).debug(
          "The entered age in not legal\n" +
            s"trace info: ${trace.stackTrace.mkString("\n")}"
        )
    },
    success = s => ZIO.succeed(s)
  )
```

Note that similar to `ZIO#fold` and `ZIO#foldZIO` this operator cannot recover from fiber interruptions.

### 4. Retrying

When we are building applications we want to be resilient in the face of a transient failure. This is where we need to retry to overcome these failures.

There are a number of useful methods on the ZIO data type for retrying failed effects:

1. **`ZIO#retry`**— The most basic of these is `ZIO#retry`, which takes a `Schedule` and returns a new effect that will retry the first effect if it fails, according to the specified policy:

```scala
trait ZIO[-R, +E, +A] {
  def retry[R1 <: R, S](policy: => Schedule[R1, E, S]): ZIO[R1 with Clock, E, A]
}
```

In this example, we try to read from a file. If we fail to do that, it will try five more times:

```scala mdoc:compile-only
import zio._

val retriedOpenFile: ZIO[Clock, IOException, Array[Byte]] = 
  readFile("primary.data").retry(Schedule.recurs(5))
```

2. **`ZIO#retryN`**— In case of failure, a ZIO effect can be retried as many times as specified:

```scala mdoc:compile-only
import zio._

val file = readFile("primary.data").retryN(5)
```

3. **`ZIO#retryOrElse`**— The next most powerful function is `ZIO#retryOrElse`, which allows specification of a fallback to use, if the effect does not succeed with the specified policy:

```scala
trait ZIO[-R, +E, +A] {
  def retryOrElse[R1 <: R, A1 >: A, S, E1](
    policy: => Schedule[R1, E, S],
    orElse: (E, S) => ZIO[R1, E1, A1]
  ): ZIO[R1 with Clock, E1, A1] =
}
```

The `orElse` is the recovery function that has two inputs:
1. The last error message
2. Schedule output

So based on these two values, we can decide what to do as the fallback operation. Let's try an example:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    Random
      .nextIntBounded(11)
      .flatMap { n =>
        if (n < 9)
          ZIO.fail(s"$n is less than 9!").debug("failed")
        else
          ZIO.succeed(n).debug("succeeded")
      }
      .retryOrElse(
        policy = Schedule.recurs(5),
        orElse = (lastError, scheduleOutput: Long) =>
          ZIO.debug(s"after $scheduleOutput retries, we couldn't succeed!") *>
            ZIO.debug(s"the last error message we received was: $lastError") *>
            ZIO.succeed(-1)
      )
      .debug("the final result")
}
```

4. **`ZIO#retryOrElseEither`**— This operator is almost the same as the **`ZIO#retryOrElse`** except it can return a different type for the fallback:

```scala mdoc:compile-only
import zio._

trait LocalConfig
trait RemoteConfig

def readLocalConfig: ZIO[Any, Throwable, LocalConfig] = ???
def readRemoteConfig: ZIO[Any, Throwable, RemoteConfig] = ???

val result: ZIO[Clock, Throwable, Either[RemoteConfig, LocalConfig]] =
  readLocalConfig.retryOrElseEither(
    schedule = Schedule.fibonacci(1.seconds),
    orElse = (_, _: Duration) => readRemoteConfig
  )
```

5. **`ZIO#retryUntil`/`ZIO#retryUntilZIO`**— We can retry an effect until a condition on the error channel is satisfied:

```scala
trait ZIO[-R, +E, +A] {
  def retryUntil(f: E => Boolean): ZIO[R, E, A]
  def retryUntilZIO[R1 <: R](f: E => URIO[R1, Boolean]): ZIO[R1, E, A]
}
```

Assume we have defined the following remote service call:

```scala mdoc:silent
sealed trait  ServiceError extends Exception
case object TemporarilyUnavailable extends ServiceError
case object DataCorrupted          extends ServiceError

def remoteService: ZIO[Any, ServiceError, Unit] = ???
```

In the following example, we repeat the failed remote service call until we reach the `DataCorrupted` error:

```scala mdoc:compile-only
remoteService.retryUntil(_ == DataCorrupted)
```

To provide an effectful predicate we use the `ZIO#retryUntilZIO` operator.

6. **`ZIO#retryUntilEqual`**— Like the previous operator, it tries until its error is equal to the specified error:

```scala mdoc:compile-only
remoteService.retryUntilEquals(DataCorrupted)
```

7. **`ZIO#retryWhile`/`ZIO#retryWhileZIO`**— Unlike the `ZIO#retryUntil` it will retry the effect while its error satisfies the specified predicate:

```scala
trait ZIO[-R, +E, +A] {
  def retryWhile(f: E => Boolean): ZIO[R, E, A]
  def retryWhileZIO[R1 <: R](f: E => URIO[R1, Boolean]): ZIO[R1, E, A]
}
```

In the following example, we repeat the failed remote service call while we have the `TemporarilyUnavailable` error:

```scala mdoc:compile-only
remoteService.retryWhile(_ == TemporarilyUnavailable)
```

To provide an effectful predicate we use the `ZIO#retryWhileZIO` operator.

8. **`ZIO#retryWhileEquals`**— Like the previous operator, it tries while its error is equal to the specified error:

```scala mdoc:compile-only
remoteService.retryWhileEquals(TemporarilyUnavailable)
```

### 5. Timing out

1. **`ZIO#timeout`**— ZIO lets us timeout any effect using the `ZIO#timeout` method, which returns a new effect that succeeds with an `Option`. A value of `None` indicates the timeout elapsed before the effect completed. If an effect times out, then instead of continuing to execute in the background, it will be interrupted so no resources will be wasted.

Assume we have the following effect:

```scala mdoc:silent
import zio._

val myApp =
  for {
    _ <- ZIO.debug("start doing something.")
    _ <- ZIO.sleep(2.second)
    _ <- ZIO.debug("my job is finished!")
  } yield "result"
```

We should note that when we use the `ZIO#timeout` operator on the `myApp`, it doesn't return until one of the following situations happens:
1. The original effect returns before the timeout elapses so the output will be `Some` of the produced value by the original effect.

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    myApp
      .timeout(3.second)
      .debug("output")
      .timed
      .map(_._1.toSeconds)
      .debug("execution time of the whole program in second")
}

// Output:
// start doing something.
// my job is finished!
// output: Some(result)
// execution time of the whole program in second: 2
```

2. The original effect interrupted after the timeout elapses:

    - If the effect is interruptible it will be immediately interrupted, and finally, the timeout operation produces `None` value.

    ```scala mdoc:compile-only
    import zio._
    
    object MainApp extends ZIOAppDefault {
      def run =
        myApp
          .timeout(1.second)
          .debug("output")
          .timed
          .map(_._1.toSeconds)
          .debug("execution time of the whole program in second")
    }
    
    // Output:
    // start doing something.
    // output: None
    // execution time of the whole program in second: 1
    ```
   
    - If the effect is uninterruptible it will be blocked until the original effect safely finished its work, and then the timeout operator produces the `None` value:
    
    ```scala mdoc:compile-only
    import zio._
    
    object MainApp extends ZIOAppDefault {
      def run =
        myApp
          .uninterruptible
          .timeout(1.second)
          .debug("output")
          .timed
          .map(_._1.toSeconds)
          .debug("execution time of the whole program in second")
    }
    
    // Output:
    // start doing something.
    // my job is finished!
    // output: None
    // execution time of the whole program in second: 2
    ```

Instead of waiting for the original effect to be interrupted, we can use `effect.disconnect.timeout` which first disconnects the effect's interruption signal before performing the timeout. By using this technique, we can return early after the timeout has passed and before an underlying effect has been interrupted. 

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  def run =
    myApp
      .uninterruptible
      .disconnect
      .timeout(1.second)
      .debug("output")
      .timed
      .map(_._1.toSeconds)
      .debug("execution time of the whole program in second")
}

// Output:
// start doing something.
// output: None
// execution time of the whole program in second: 1
```

By using this technique, the original effect will be interrupted in the background.

2. **`ZIO#timeoutTo`**— This operator is similar to the previous one, but it also allows us to manually create the final result type:

```scala mdoc:silent
import zio._

val delayedNextInt: ZIO[Random with Clock, Nothing, Int] =
  Random.nextIntBounded(10).delay(2.second)

val r1: ZIO[Random with Clock, Nothing, Option[Int]] =
  delayedNextInt.timeoutTo(None)(Some(_))(1.seconds)
  
val r2: ZIO[Random with Clock, Nothing, Either[String, Int]] =
  delayedNextInt.timeoutTo(Left("timeout"))(Right(_))(1.seconds)
  
val r3: ZIO[Random with Clock, Nothing, Int] =
  delayedNextInt.timeoutTo(-1)(identity)(1.seconds)
```

3. **`ZIO#timeoutFail`/`ZIO#timeoutFailCause`**— In case of elapsing the timeout, we can produce a particular error message:

```scala mdoc:compile-only
import zio._
import scala.concurrent.TimeoutException

val r1: ZIO[Random with Clock, TimeoutException, Int] =
  delayedNextInt.timeoutFail(new TimeoutException)(1.second)

val r2: ZIO[Random with Clock, Nothing, Int] =
  delayedNextInt.timeoutFailCause(Cause.die(new Error("timeout")))(1.second)
```

### 6. Sandboxing

1. **`ZIO#sandbox`**— We know that a ZIO effect may fail due to a failure, a defect, a fiber interruption, or a combination of these causes. So a ZIO effect may contain more than one cause. Using the `ZIO#sandbox` operator, we can sandbox all errors of a ZIO application, whether the cause is a failure, defect, or a fiber interruption or combination of these. This operator exposes the full cause of a ZIO effect into the error channel:

```scala
trait ZIO[-R, +E, +A] {
  def sandbox: ZIO[R, Cause[E], A]
}
```

We can use the `ZIO#sandbox` operator to uncover the full causes of an _exceptional effect_. So we can see all the errors that occurred as a type of `Case[E]` at the error channel of the `ZIO` data type. So then we can use normal error-handling operators such as `ZIO#catchSome` and `ZIO#catchAll` operators:

```scala mdoc:silent
import zio._

object MainApp extends ZIOAppDefault {
  val effect: ZIO[Any, String, String] =
    ZIO.succeed("primary result") *> ZIO.fail("Oh uh!")

  val myApp: ZIO[Any, Cause[String], String] =
    effect.sandbox.catchSome {
      case Cause.Interrupt(fiberId, _) =>
        ZIO.debug(s"Caught interruption of a fiber with id: $fiberId") *>
          ZIO.succeed("fallback result on fiber interruption")
      case Cause.Die(value, _) =>
        ZIO.debug(s"Caught a defect: $value") *>
          ZIO.succeed("fallback result on defect")
      case Cause.Fail(value, _) =>
        ZIO.debug(s"Caught a failure: $value") *>
          ZIO.succeed("fallback result on failure")
    }

  val finalApp: ZIO[Any, String, String] = myApp.unsandbox.debug("final result")

  def run = finalApp
}

// Output:
// Caught a failure: Oh uh!
// final result: fallback result on failure
```

Using the `sandbox` operation we are exposing the full cause of an effect. So then we have access to the underlying cause in more detail. After handling exposed causes using `ZIO#catch*` operators, we can undo the `sandbox` operation using the `unsandbox` operation. It will submerge the full cause (`Case[E]`) again:

```scala mdoc:compile-only
import zio._

val effect: ZIO[Any, String, String] =
  ZIO.succeed("primary result") *> ZIO.fail("Oh uh!")
    
effect            // ZIO[Any, String, String]
  .sandbox        // ZIO[Any, Cause[String], String]
  .catchSome(???) // ZIO[Any, Cause[String], String]
  .unsandbox      // ZIO[Any, String, String]
```

2. **`ZIO#sandboxWith`**— There is another version of sandbox called `ZIO#sandboxWith`. This operator helps us to sandbox, then catch all causes, and then unsandbox back:

```scala
trait ZIO[-R, +E, +A] {
  def sandboxWith[R1 <: R, E2, B](f: ZIO[R1, Cause[E], A] => ZIO[R1, Cause[E2], B])
}
```

Let's try the previous example using this operator:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val effect: ZIO[Any, String, String] =
    ZIO.succeed("primary result") *> ZIO.fail("Oh uh!")

  val myApp =
    effect.sandboxWith[Any, String, String] { e =>
      e.catchSome {
        case Cause.Interrupt(fiberId, _) =>
          ZIO.debug(s"Caught interruption of a fiber with id: $fiberId") *>
            ZIO.succeed("fallback result on fiber interruption")
        case Cause.Die(value, _) =>
          ZIO.debug(s"Caught a defect: $value") *>
            ZIO.succeed("fallback result on defect")
        case Cause.Fail(value, _) =>
          ZIO.debug(s"Caught a failure: $value") *>
            ZIO.succeed("fallback result on failure")
      }
    }
  def run = myApp.debug
}

// Output:
// Caught a failure: Oh uh!
// fallback result on failure
```

## Error Accumulation

Sequential combinators such as `ZIO#zip` and `ZIO.foreach` stop when they reach the first error and return immediately. So their policy on error management is to fail fast.

In the following example, we can see that the `ZIO#zip` operator will fail as soon as it reaches the first failure. As a result, we only see the first error in the stack trace.

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

### `ZIO#validate`

It is similar to the `ZIO#zip` operator, it sequentially zips two ZIO effects together, if both effects fail, it combines their causes with `Cause.Then`:

```scala
trait ZIO[-R, +E, +A] {
  def validate[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)]
}
```

If any of the effecful operations doesn't fail, it results like the `zip` operator. Otherwise, when it reaches the first error it won't stop, instead, it will continue the zip operation until reach the final effect while combining:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val f1 = ZIO.succeed(1).debug 
  val f2 = ZIO.succeed(2) *> ZIO.fail("Oh uh!")
  val f3 = ZIO.succeed(3).debug
  val f4 = ZIO.succeed(4) *> ZIO.fail("Oh error!")
  val f5 = ZIO.succeed(5).debug

  val myApp: ZIO[Any, String, ((((Int, Int), Int), Int), Int)] =
    f1 validate f2 validate f3 validate f4 validate f5

  def run = myApp.cause.debug.uncause
}

// Output:
// 1
// 3
// 5
// Then(Fail(Oh uh!,ZTrace(None,Chunk())),Fail(Oh error!,ZTrace(None,Chunk())))
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
// Both(Fail(Oh uh!,ZTrace(None,Chunk())),Fail(Oh error!,ZTrace(None,Chunk())))
// timestamp=2022-03-14T09:16:00.670444190Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
//  	at <empty>.MainApp.run(MainApp.scala:13)
// Exception in thread "zio-fiber-2" java.lang.String: Oh error!
//  	at <empty>.MainApp.run(MainApp.scala:13)"
```

In addition, it has a `ZIO#validateWith` variant, which is useful for providing combiner function (`f: (A, B) => C`) to combine pair values.

### `ZIO.validate`

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

### `ZIO.validateFirst`

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

### `ZIO.partition`

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

## Error Channel Operations

### map and flatMap on Error Channel

Other than `ZIO#map` and `ZIO#flatMap`, ZIO has several other operators to manage errors while mapping:

1. **`ZIO#mapError`/`ZIO#mapErrorCause`**— Let's begin with `ZIO#mapError` and `ZIO#mapErrorCause`. These operators help us to access the error channel as a raw error value or as a type of `Cause` and map their values:

```scala
trait ZIO[-R, +E, +A] {
  def mapError[E2](f: E => E2): ZIO[R, E2, A]
  def mapErrorCause[E2](h: Cause[E] => Cause[E2]): ZIO[R, E2, A]
}
```

Here are two simple examples for these operators:

````scala mdoc:compile-only
import zio._

def parseInt(input: String): ZIO[Any, NumberFormatException, Int] = ???
  
// mapping the error of the original effect to its message
val r1: ZIO[Any, String, Int] =
  parseInt("five")                // ZIO[Any, NumberFormatException, Int]
    .mapError(e => e.getMessage)  // ZIO[Any, String, Int]

// mapping the cause of the original effect to be untraced    
val r2 = parseInt("five")         // ZIO[Any, NumberFormatException, Int]
  .mapErrorCause(_.untraced)      // ZIO[Any, NumberFormatException, Int]
````

2. **`ZIO#mapAttempt`**— Using operations that can throw exceptions inside of `ZIO#map` such as `effect.map(_.unsafeOpThatThrows)` will result in a defect (an unexceptional effect that will die).

In the following example, when we use the `ZIO#map` operation. So, if the `String#toInt` operation throws `NumberFormatException` it will be converted to a defect:

```scala mdoc:compile-only
import zio._

val result: ZIO[Console, Nothing, Int] =
  Console.readLine.orDie.map(_.toInt)
```

As a result, when the map operation is unsafe, it may lead to buggy programs that may crash, as shown below:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Console, Nothing, Unit] =
    Console.print("Please enter a number: ").orDie *>
      Console.readLine.orDie
        .map(_.toInt)
        .map(_ % 2 == 0)
        .flatMap {
          case true =>
            Console.printLine("You have entered an even number.").orDie
          case false =>
            Console.printLine("You have entered an odd number.").orDie
        }

  def run = myApp
}
```

In the previous example, if we enter a non-integer number, e.g. "five", it will die because of a `NumberFormatException` defect:

```scala
Please enter a number: five
timestamp=2022-03-17T14:01:33.323639073Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.NumberFormatException: For input string: "five"
	at java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)
	at java.base/java.lang.Integer.parseInt(Integer.java:660)
	at java.base/java.lang.Integer.parseInt(Integer.java:778)
	at scala.collection.StringOps$.toInt$extension(StringOps.scala:910)
	at MainApp$.$anonfun$myApp$3(MainApp.scala:7)
	at MainApp$.$anonfun$myApp$3$adapted(MainApp.scala:7)
	at zio.ZIO.$anonfun$map$1(ZIO.scala:1168)
	at zio.ZIO$FlatMap.apply(ZIO.scala:6182)
	at zio.ZIO$FlatMap.apply(ZIO.scala:6171)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:885)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1130)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:630)
	at java.base/java.lang.Thread.run(Thread.java:831)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:538)
	at <empty>.MainApp.myApp(MainApp.scala:8)
	at <empty>.MainApp.myApp(MainApp.scala:9)"
```

We can see that the error channel of `myApp` is typed as `Nothing`, so it's not an exceptional error. If we want typed effects, this behavior is not intended. So instead of `ZIO#map` we can use the `mapAttempt` combinator which is a safe map operator that translates all thrown exceptions into typed exceptional effect:

```scala
trait ZIO[-R, +E, +A] {
  def map[B](f: A => B): ZIO[R, E, B]
  def mapAttempt[B](f: A => B): ZIO[R, Throwable, B]
}
```

To prevent converting exceptions to defects, we can use `ZIO#mapAttempt` which converts any exceptions to exceptional effects:

```scala mdoc:compile-only
import zio._

val result: ZIO[Console, Throwable, Int] =
  Console.readLine.orDie.mapAttempt(_.toInt)
```

Having typed errors helps us to catch errors explicitly and handle them in the right way:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Console, Nothing, Unit] =
    Console.print("Please enter a number: ").orDie *>
      Console.readLine.orDie
        .mapAttempt(_.toInt)
        .map(_ % 2 == 0)
        .flatMap {
          case true =>
            Console.printLine("You have entered an even number.").orDie
          case false =>
            Console.printLine("You have entered an odd number.").orDie
        }.catchAll(_ => myApp)

  def run = myApp
}

// Please enter a number: five
// Please enter a number: 4
// You have entered an even number.
```

3. **`ZIO#mapBoth`**— It takes two map functions, one for the error channel and the other for the success channel, and maps both sides of a ZIO effect:

```scala
trait ZIO[-R, +E, +A] {
  def mapBoth[E2, B](f: E => E2, g: A => B): ZIO[R, E2, B]
}
```

Here is a simple example:

```scala mdoc:compile-only
import zio._

val result: ZIO[Console, String, Int] =
  Console.readLine.orDie.mapAttempt(_.toInt).mapBoth(
    _ => "non-integer input",
    n => Math.abs(n)
  )
```

4. **`ZIO#flatMapError`**— Unlike `ZIO#flatMap` the `ZIO#flatMapError` combinator chains two effects, where the second effect is dependent on the error channel of the first effect:

```scala
trait ZIO[-R, +E, +A] {
  def flatMapError[R1 <: R, E2](
    f: E => ZIO[R1, Nothing, E2]
  ): ZIO[R1, E2, A]
}
```

In the following example, we are trying to find a random prime number between 1000 and 10000. We will use the `ZIO#flatMapError` to collect all errors inside a `Ref` of type `List[String]`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def isPrime(n: Int): Boolean =
    if (n <= 1) false else (2 until n).forall(i => n % i != 0)

  def findPrimeBetween(
      minInclusive: Int,
      maxExclusive: Int
  ): ZIO[Random, List[String], Int] =
    for {
      errors <- Ref.make(List.empty[String])
      number <- Random
        .nextIntBetween(minInclusive, maxExclusive)
        .reject {
          case n if !isPrime(n) =>
            s"non-prime number rejected: $n"
        }
        .flatMapError(error => errors.updateAndGet(_ :+ error))
        .retryUntil(_.length >= 5)
    } yield number

  val myApp: ZIO[Console with Random, Nothing, Unit] =
    findPrimeBetween(1000, 10000)
      .flatMap(prime => Console.printLine(s"found a prime number: $prime").orDie)
      .catchAll { (errors: List[String]) =>
        Console.printLine(
          s"failed to find a prime number after 5 attempts:\n  ${errors.mkString("\n  ")}"
        )
      }
      .orDie

  def run = myApp
}
```

### Filtering the Success Channel Values

ZIO has a variety of operators that can filter values on the success channel based on a given predicate, and if the predicate fails, we can use different strategies:
- Failing the original effect (`ZIO#filterOrFail`)
- Dying the original effect (`ZIO#filterOrDie` and `ZIO#filterOrDieMessage`)
- Running an alternative ZIO effect (`ZIO#filterOrElse` and `ZIO#filterOrElseWith`)

```scala mdoc:compile-only
import zio._

def getNumber: ZIO[Console, Nothing, Int] =
  (Console.print("Please enter a non-negative number: ") *>
    Console.readLine.mapAttempt(_.toInt))
    .retryUntil(!_.isInstanceOf[NumberFormatException]).orDie

val r1: ZIO[Random, String, Int] =
  Random.nextInt.filterOrFail(_ >= 0)("random number is negative")
  
val r2: ZIO[Random, Nothing, Int] =
  Random.nextInt.filterOrDie(_ >= 0)(
    new IllegalArgumentException("random number is negative")
  )
  
val r3: ZIO[Random, Nothing, Int] =
  Random.nextInt.filterOrDieMessage(_ >= 0)("random number is negative")
  
val r4: ZIO[Console with Random, Nothing, Int] =
  Random.nextInt.filterOrElse(_ >= 0)(getNumber)
  
val r5: ZIO[Random, Nothing, Int] =
  Random.nextInt.filterOrElseWith(_ >= 0)(x => ZIO.succeed(-x))
```

### Tapping Errors

Like [tapping for success values](zio.md#tapping) ZIO has several operators for tapping error values. So we can peek into failures or underlying defects or causes:

```scala
trait ZIO[-R, +E, +A] {
  def tapError[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapErrorCause[R1 <: R, E1 >: E](f: Cause[E] => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapErrorTrace[R1 <: R, E1 >: E](f: ((E, ZTrace)) => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapDefect[R1 <: R, E1 >: E](f: Cause[Nothing] => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapBoth[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any], g: A => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapEither[R1 <: R, E1 >: E](f: Either[E, A] => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
}
```

Let's try an example:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Console, NumberFormatException, Int] =
    Console.readLine
      .mapAttempt(_.toInt)
      .refineToOrDie[NumberFormatException]
      .tapError { e =>
        ZIO.debug(s"user entered an invalid input: ${e}").when(e.isInstanceOf[NumberFormatException])
      }

  def run = myApp
}
```

### Putting Errors Into Success Channel and Submerging Them Back Again

1. **`ZIO#either`**— The `ZIO#either` convert a `ZIO[R, E, A]` effect to another effect in which its failure (`E`) and success (`A`) channel have been lifted into an `Either[E, A]` data type as success channel of the `ZIO` data type:

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

val myApp: ZIO[Console, IOException, Unit] =
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

2. **`ZIO#absolve`/`ZIO.absolve`**— The `ZIO#abolve` operator and the `ZIO.absolve` constructor perform the inverse. They submerge the error case of an `Either` into the `ZIO`:

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

### Converting Defects to Failures

Both `ZIO#resurrect` and `ZIO#absorb` are symmetrical opposite of the `ZIO#orDie` operator. The `ZIO#orDie` takes failures from the error channel and converts them into defects, whereas the `ZIO#absorb` and `ZIO#resurrect` take defects and convert them into failures:

```scala
trait ZIO[-R, +E, +A] {
  def absorb(implicit ev: E IsSubtypeOfError Throwable): ZIO[R, Throwable, A]
  def absorbWith(f: E => Throwable): ZIO[R, Throwable, A]
  def resurrect(implicit ev1: E IsSubtypeOfError Throwable): ZIO[R, Throwable, A]
}
```

Below are examples of the `ZIO#absorb` and `ZIO#resurrect` operators:

```scala mdoc:compile-only
import zio._

val effect1 =
  ZIO.fail(new IllegalArgumentException("wrong argument"))  // ZIO[Any, IllegalArgumentException, Nothing]
    .orDie                                                  // ZIO[Any, Nothing, Nothing]
    .absorb                                                 // ZIO[Any, Throwable, Nothing]
    .refineToOrDie[IllegalArgumentException]                // ZIO[Any, IllegalArgumentException, Nothing]
    
val effect2 =
  ZIO.fail(new IllegalArgumentException("wrong argument"))  // ZIO[Any, IllegalArgumentException , Nothing]
    .orDie                                                  // ZIO[Any, Nothing, Nothing]
    .resurrect                                              // ZIO[Any, Throwable, Nothing]
    .refineToOrDie[IllegalArgumentException]                // ZIO[Any, IllegalArgumentException, Nothing]
```

So what is the difference between `ZIO#absorb` and `ZIO#resurrect` operators?

1. The `ZIO#absorb` can recover from both `Die` and `Interruption` causes. Using this operator we can absorb failures, defects and interruptions using `ZIO#absorb` operation. It attempts to convert all causes into a failure, throwing away all information about the cause of the error:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val effect1 =
    ZIO.dieMessage("Boom!") // ZIO[Any, Nothing, Nothing]
      .absorb               // ZIO[Any, Throwable, Nothing]
      .ignore
  val effect2 =
    ZIO.interrupt           // ZIO[Any, Nothing, Nothing]
      .absorb               // ZIO[Any, Throwable, Nothing]
      .ignore

  def run =
    (effect1 <*> effect2)
      .debug("application exited successfully")
}
```

The output would be as below:

```scala
application exited successfully: ()
```

2. Whereas, the `ZIO#resurrect` will only recover from `Die` causes:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val effect1 =
    ZIO
      .dieMessage("Boom!") // ZIO[Any, Nothing, Nothing]
      .resurrect           // ZIO[Any, Throwable, Nothing]
      .ignore
  val effect2 =
    ZIO.interrupt          // ZIO[Any, Nothing, Nothing]
      .resurrect           // ZIO[Any, Throwable, Nothing]
      .ignore

  def run =
    (effect1 <*> effect2)
      .debug("couldn't recover from fiber interruption")
}
```

And, here is the output:

```scala
timestamp=2022-02-18T14:21:52.559872464Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.InterruptedException: Interrupted by thread "zio-fiber-"
	at <empty>.MainApp.effect2(MainApp.scala:10)
	at <empty>.MainApp.effect2(MainApp.scala:11)
	at <empty>.MainApp.effect2(MainApp.scala:12)
	at <empty>.MainApp.run(MainApp.scala:15)
	at <empty>.MainApp.run(MainApp.scala:16)"
```

### Error Refinement 

ZIO has some operators useful for converting defects into failures. So we can take part in non-recoverable errors and convert them into the typed error channel and vice versa.

Note that both `ZIO#refine*` and `ZIO#unrefine*` do not alter the error behavior, but only change the error model. That is to say, if an effect fails or die, then after `ZIO#refine*` or `ZIO#unrefine*`, it will still fail or die; and if an effect succeeds, then after `ZIO#refine*` or `ZIO#unrefine*`, it will still succeed; only the manner in which it signals the error will be altered by these two methods:
1. The `ZIO#refine*` pinches off a piece of _failure_ of type `E`, and converts it into a _defect_.
2. The `ZIO#unrefine*` pinches off a piece of a _defect_, and converts it into a _failure_ of type `E`.

#### Refining

1. **`ZIO#refineToOrDie`**— This operator **narrows** down the type of the error channel from `E` to the `E1`. It leaves the rest errors untyped, so everything that doesn't fit is turned into a defect. So it makes the error space **smaller**.

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

2. **`ZIO#refineOrDie`**— The `ZIO#refineOrDie` is the more powerful version of the previous operator. Using this combinator instead of refining to one specific error type, we can refine to multiple error types using a partial function:

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

3. **`ZIO#refineOrDieWith`**— In the two previous refine combinators, we were dealing with exceptional effects whose error channel type was `Throwable` or a subtype of that. The `ZIO#refineOrDieWith` operator is a more powerful version of refining operators. It can work with any exceptional effect whether they are `Throwable` or not. When we narrow down the failure space, some failures become defects. To convert those failures to defects, it takes a function from `E` to `Throwable`:

```scala mdoc:compile-only
trait ZIO[-R, +E, +A] {
  def refineOrDieWith[E1](pf: PartialFunction[E, E1])(f: E => Throwable): ZIO[R, E1, A]
}
```

In the following example, we excluded the `BazError` from recoverable errors, so it will be converted to a defect. In another word, we narrowed the whole space of `String` errors down to just "FooError" and "BarError":

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def effect(i: String): ZIO[Random, String, Nothing] = {
    if (i == "foo") ZIO.fail("FooError")
    else if (i == "bar") ZIO.fail("BarError")
    else ZIO.fail("BazError")
  }

  val refined: ZIO[Random, String, Nothing] =
    effect("baz").refineOrDieWith {
      case "FooError" | "BarError" => "Oh Uh!"
    }(e => new Throwable(e))

  def run = refined.catchAll(_ => ZIO.unit)
}
```

#### Unrefining

1. **`ZIO#unrefineTo[E1 >: E]`**— This operator **broadens** the type of the error channel from `E` to the `E1` and embeds some defects into it. So it is going from some fiber failures back to errors and thus making the error type **larger**:

```scala
ZIO[-R, +E, +A] {
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

2. **`ZIO#unrefine`**— It is a more powerful version of the previous operator. It takes a partial function from `Throwable` to `E1` and converts those defects to recoverable errors:

```scala
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

3. **`ZIO#unrefineWith`**- This is the most powerful version of unrefine operators. It takes a partial function, as the previous operator, and then tries to broaden the failure space by converting some of the defects to typed recoverable errors. If it doesn't find any defect, it will apply the `f` which is a function from `E` to `E1`, and map all typed errors using this function:

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

  val effect: ZIO[Random, Foo, Nothing] =
    ZIO.ifZIO(Random.nextBoolean)(
      onTrue = ZIO.fail(Foo("Oh uh!")),
      onFalse = ZIO.die(Bar("Boom!"))
    )

  val unrefined: ZIO[Random, String, Nothing] =
    effect
      .unrefineWith {
        case e: Bar => e.getMessage
      }(e => e.getMessage)
  
  def run = unrefined.cause.debug
}
```

### Converting Option on Values to Option on Errors and Vice Versa

We can extract a value from a Some using `ZIO.some` and then we can unsome it again using `ZIO#unsome`:

```scala
ZIO.attempt(Option("something")) // ZIO[Any, Throwable, Option[String]]
  .some                          // ZIO[Any, Option[Throwable], String]
  .unsome                        // ZIO[Any, Throwable, Option[String]]
```

### Flattening Optional Error Types

If we have an optional error of type `E` in the error channel, we can flatten it to the `E` type using the `ZIO#flattenErrorOption` operator:

```scala mdoc:compile-only
import zio._

def parseInt(input: String): ZIO[Any, Option[String], Int] =
  if (input.isEmpty)
    ZIO.fail(Some("empty input"))
  else
    try {
      ZIO.succeed(input.toInt)
    } catch {
      case _: NumberFormatException => ZIO.fail(None)
    }

def flattenedParseInt(input: String): ZIO[Any, String, Int] =
  parseInt(input).flattenErrorOption("non-numeric input")

val r1: ZIO[Any, String, Int] = flattenedParseInt("zero")
val r2: ZIO[Any, String, Int] = flattenedParseInt("")
val r3: ZIO[Any, String, Int] = flattenedParseInt("123")
```

### Merging the Error Channel into the Success Channel

With `ZIO#merge` we can merge the error channel into the success channel:

```scala mdoc:compile-only
import zio._

val merged : ZIO[Any, Nothing, String] = 
  ZIO.fail("Oh uh!") // ZIO[Any, String, Nothing]
    .merge           // ZIO[Any, Nothing, String]
```

If the error and success channels were of different types, it would choose the supertype of both.

### Flipping the Error and Success Channels

Sometimes, we would like to apply some methods on the error channel which are specific for the success channel, or we want to apply some methods on the success channel which are specific for the error channel. Therefore, we can flip the error and success channel and before flipping back, we can perform the right operator on flipped channels:

```scala
trait ZIO[-R, +E, +A] {
  def flip: ZIO[R, A, E]
  def flipWith[R1, A1, E1](f: ZIO[R, A, E] => ZIO[R1, A1, E1]): ZIO[R1, E1, A1]
}
```

Assume we have the following example:

```scala mdoc:silent
import zio._

val evens: ZIO[Any, List[String], List[Int]] =
  ZIO.validate(List(1, 2, 3, 4, 5)) { n =>
    if (n % 2 == 0)
      ZIO.succeed(n)
    else
      ZIO.fail(s"$n is not even")
  }
```

We want to reverse the order of errors. In order to do that instead of using `ZIO#mapError`, we can map the error channel by using flip operators:

```scala mdoc:compile-only
import zio._

val r1: ZIO[Any, List[String], List[Int]] = evens.mapError(_.reverse)
val r2: ZIO[Any, List[String], List[Int]] = evens.flip.map(_.reverse).flip
val r3: ZIO[Any, List[String], List[Int]] = evens.flipWith(_.map(_.reverse))
```

### Rejecting Some Success Values

We can reject some success values using the `ZIO#reject` operator:

```scala
trait ZIO[-R, +E, +A] {
  def reject[E1 >: E](pf: PartialFunction[A, E1]): ZIO[R, E1, A]
  
  def rejectZIO[R1 <: R, E1 >: E](
    pf: PartialFunction[A, ZIO[R1, E1, E1]]
  ): ZIO[R1, E1, A]
}
```

If the `PartialFunction` matches, it will reject that success value and convert that to a failure, otherwise it will continue with the original success value:

```scala mdoc:compile-only
import zio._

val myApp: ZIO[Random, String, Int] =
  Random
    .nextIntBounded(20)
    .reject {
      case n if n % 2 == 0 => s"even number rejected: $n"
      case 5               => "number 5 was rejected"
    }
    .debug
```

### Zoom in/out on Left or Right Side of An Either Value 

With `Either` ZIO values, we can zoom in or out on the left or right side of an `Either`, as well as we can do the inverse and zoom out.

```scala mdoc:compile-only
import zio._

val eitherEffect: ZIO[Any, Exception, Either[String, Int]] = ???

eitherEffect // ZIO[Any, Exception, Either[String, Int]]
  .left      // ZIO[Any, Either[Exception, Int], String]
  .unleft    // ZIO[Any, Exception, Either[String, Int]]

eitherEffect // ZIO[Any, Exception, Either[String, Int]]
  .right     // ZIO[Any, Either[String, Exception], Int]
  .unright   // ZIO[Any, Exception, Either[String, Int]]
```

### Converting Optional Values to Optional Errors and Vice Versa

Assume we have the following effect:

```scala mdoc:silent
import zio._

val nextRandomEven: ZIO[Random, String, Option[Int]] =
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
nextRandomEven // ZIO[Random, String, Option[Int]] 
  .some        // ZIO[Random, Option[String], Int] 
  .unsome      // ZIO[Random, String, Option[Int]]
```

```scala mdoc:invisible:reset

```

Sometimes instead of converting optional values to optional errors, we can perform one of the following operations:
- Failing the original operation (`ZIO#someOrFail` and `ZIO#someOrFailException`)
- Succeeding with an alternate value (`ZIO#someOrElse`)
- Running an alternative ZIO effect (`ZIO#someOrElseZIO`)

### Uncovering the Underlying Cause of an Effect

Using the `ZIO#cause` operation we can expose the cause, and then by using `ZIO#uncause` we can reverse this operation:

```scala
trait ZIO[-R, +E, +A] {
  def cause: URIO[R, Cause[E]]
  def uncause[E1 >: E](implicit ev: A IsSubtypeOfOutput Cause[E1]): ZIO[R, E1, Unit]
}
```

In the following example, we expose and then untrace the underlying cause:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val f1: ZIO[Any, String, Int] = 
    ZIO.fail("Oh uh!").as(1)
    
  val f2: ZIO[Any, String, Int] = 
    ZIO.fail("Oh error!").as(2)

  val myApp: ZIO[Any, String, (Int, Int)] = f1 zipPar f2

  def run = myApp.cause.map(_.untraced).debug
}
```

Sometimes the [`ZIO#mapErrorCause`](#map-and-flatmap-on-error-channel) operator is a better choice when we just want to map the underlying cause without exposing the cause.

## Best Practices

### Model Domain Errors Using Algebraic Data Types

It is best to use _algebraic data types (ADTs)_ when modeling errors within the same domain or subdomain.

Sealed traits allow us to introduce an error type as a common supertype and all errors within a domain are part of that error type by extending that:

```scala
sealed trait UserServiceError extends Exception

case class InvalidUserId(id: ID) extends UserServiceError
case class ExpiredAuth(id: ID)   extends UserServiceError
```


In this case, the super error type is `UserServiceError`. We sealed that trait, and we extend it by two cases, `InvalidUserId` and `ExpiredAuth`. Because it is sealed, if we have a reference to a `UserServiceError` we can match against it and the Scala compiler knows there are two possibilities for a `UserServiceError`:

```scala
userServiceError match {
  case InvalidUserId(id) => ???
  case ExpiredAuth(id)   => ???
}
```

This is a sum type, and also an enumeration. The Scala compiler knows only two of these `UserServiceError` exist. If we don't match on all of them, it is going to warn us. We can add the `-Xfatal-warnings` compiler option which treats warnings as errors. By turning on the fatal warning, we will have type-safety control on expected errors. So sealing these traits gives us great power.

Also extending all of our errors from a common supertype helps the ZIO's combinators like flatMap to auto widen to the most specific error type.

Let's say we have this for-comprehension here that calls the `userAuth` function, and it can fail with `ExpiredAuth`, and then we call `userProfile` that fails with `InvalidUserID`, and then we call `generateEmail` that can't fail at all, and finally we call `sendEmail` which can fail with `EmailDeliveryError`. We have got a lot of different errors here:

```scala
val myApp: IO[Exception, Receipt] = 
  for {
    service <- userAuth(token)                // IO[ExpiredAuth, UserService]
    profile <- service.userProfile(userId)    // IO[InvalidUserId, Profile]
    body    <- generateEmail(orderDetails)    // IO[Nothing, String]
    receipt <- sendEmail("Your order detail", 
       body, profile.email)                   // IO[EmailDeliveryError, Unit]
  } yield receipt
```

In this example, the flatMap operations auto widens the error type to the most specific error type possible. As a result, the inferred error type of this for-comprehension will be `Exception` which gives us the best information we could hope to get out of this. We have lost information about the particulars of this. We no longer know which of these error types it is. We know it is some type of `Exception` which is more information than nothing.


### Use Union Types to Be More Specific About Error Types

In Scala 3, we have an exciting new feature called union types. By using the union operator, we can encode multiple error types. Using this facility, we can have more precise information on typed errors.

Let's see an example of `Storage` service which have `upload`, `download` and `delete` api:

```scala
import zio._

type Name = String

enum StorageError extends Exception {
  case ObjectExist(name: Name)            extends StorageError
  case ObjectNotExist(name: Name)         extends StorageError
  case PermissionDenied(cause: String)    extends StorageError
  case StorageLimitExceeded(limit: Int)   extends StorageError
  case BandwidthLimitExceeded(limit: Int) extends StorageError
}

import StorageError.*

trait Storage {
  def upload(
      name: Name,
      obj: Array[Byte]
  ): ZIO[Any, ObjectExist | StorageLimitExceeded, Unit]

  def download(
      name: Name
  ): ZIO[Any, ObjectNotExist | BandwidthLimitExceeded, Array[Byte]]

  def delete(name: Name): ZIO[Any, ObjectNotExist | PermissionDenied, Unit]
}
```

Union types allow us to get rid of the requirement to extend some sort of common error types like `Exception` or `Throwable`. This allows us to have completely unrelated error types.

In the following example, the `FooError` and `BarError` are two distinct error. They have no super common type like `FooBarError` and also they are not extending `Exception` or `Throwable` classes:

```scala
import zio.*

// Two unrelated errors without having a common supertype
trait FooError
trait BarError

def foo: IO[FooError, Nothing] = ZIO.fail(new FooError {})
def bar: IO[BarError, Nothing] = ZIO.fail(new BarError {})

val myApp: ZIO[Any, FooError | BarError, Unit] = for {
  _ <- foo
  _ <- bar
} yield ()
```

### Don't Type Unexpected Errors

When we first discover typed errors, it may be tempting to put every error into the error type parameter. That is a mistake because we can't recover from all types of errors. When we encounter unexpected errors we can't do anything in those cases. We should let the application die. Let it crash is the erlang philosophy. It is a good philosophy for all unexpected errors. At best, we can sandbox it, but we should let it crash.

The context of a domain determines whether an error is expected or unexpected. When using typed errors, sometimes it is necessary to make a typed-error un-typed because in that case, we can't handle the error, and we should let the application crash.

For example, in the following example, we don't want to handle the `IOException` so we can call `ZIO#orDie` to make the effect's failure unchecked. This will translate effect's failure to the death of the fiber running it:

```scala mdoc:compile-only
import zio._

Console.printLine("Hello, World") // ZIO[Console, IOException, Unit]
  .orDie                          // ZIO[Console, Nothing, Unit]
```

If we have an effect that fails for some `Throwable` we can pick certain recoverable errors out of that, and then we can just let the rest of them kill the fiber that is running that effect. The ZIO effect has a method called `ZIO#refineOrDie` that allows us to do that.

In the following example, calling `ZIO#refineOrDie` on an effect that has an error type `Throwable` allows us to refine it to have an error type of `TemporaryUnavailable`:

```scala mdoc:invisible
import java.net.URL
trait TemporaryUnavailable extends Throwable

trait Response

object httpClient {
  def fetchUrl(url: URL): Response = ???
}

val url = new URL("https://zio.dev")
```

```scala mdoc:compile-only
import zio._

val response: ZIO[Clock, Nothing, Response] =
  ZIO
    .attemptBlocking(
      httpClient.fetchUrl(url)
    ) // ZIO[Any, Throwable, Response]
    .refineOrDie[TemporaryUnavailable] {
      case e: TemporaryUnavailable => e
    } // ZIO[Any, TemporaryUnavailable, Response]
    .retry(
      Schedule.fibonacci(1.second)
    ) // ZIO[Clock, TemporaryUnavailable, Response]
    .orDie // ZIO[Clock, Nothing, Response]
```

In this example, we are importing the `fetchUrl` which is a blocking operation into a `ZIO` value. We know that in case of a service outage it will throw the `TemporaryUnavailable` exception. This is an expected error, so we want that to be typed. We are going to reflect that in the error type. We only expect it, so we know how to recover from it.

Also, this operation may throw unexpected errors like `OutOfMemoryError`, `StackOverflowError`, and so forth. Therefore, we don't include these errors since we won't be handling them at runtime. They are defects, and in case of unexpected errors, we should let the application crash.

Therefore, it is quite common to import a code that may throw exceptions, whether that uses expected errors for error handling or can fail for a wide variety of unexpected errors like disk unavailable, service unavailable, and so on. Generally, importing these operations end up represented as a `Task` (`ZIO[Any, Throwable, A]`). So in order to make recoverable errors typed, we use the `ZIO#refineOrDie` method.

### Don't Reflexively Log Errors

In modern async concurrent applications with a lot of subsystems, if we do not type errors, we are not able to see what section of our code fails with what error. Therefore, this can be very tempting to log errors when they happen. So when we lose type-safety in the whole application it makes us be more sensitive and program defensively. Therefore, whenever we are calling an API we tend to catch its errors, log them as below:

```scala
import zio._

sealed trait UploadError extends Exception
case class FileExist(name: String)          extends UploadError
case class FileNotExist(name: String)       extends UploadError
case class StorageLimitExceeded(limit: Int) extends UploadError

/**
 * This API fail with `FileExist` failure when the provided file name exist.
 */ 
def upload(name: String): Task[Unit] = {
    if (...)  
      ZIO.fail(FileExist(name))
    else if (...)
      ZIO.fail(StorageLimitExceeded(limit)) // This error is undocumented unintentionally
    else
      ZIO.attempt(...)
}

upload("contacts.csv").catchAll {
  case FileExist(name) => delete("contacts.csv") *> upload("contacts.csv")
  case _ =>
    for {
      _ <- ZIO.log(error.toString) // logging the error
      _ <- ZIO.fail(error) // failing again (just like rethrowing exceptions in OOP)
    } yield ()
}
```

In the above code when we see the `upload`'s return type we can't find out what types of error it may fail with. So as a programmer we need to read the API documentation, and see in what cases it may fail. Due to the fact that the documents may be outdated and they may not provide all error cases, we tend to add another case to cover all the other errors. Expert developers may prefer to read the implementation to find out all expected errors, but it is a tedious task to do.

We don't want to lose any errors. So if we do not use typed errors, it makes us defensive to log every error, regardless of whether they will occur or not.

When we are programming with typed errors, that allows us to never lose any errors. Even if we don't handle all, the error channel of our effect type demonstrate the type of remaining errors:

```scala
val myApp: ZIO[Any, UploadError, Unit] = 
  upload("contacts.csv")
    .catchSome { 
      case FileExist(name) => delete(name) *> upload(name)
    }
```

It is still going to be sent an unhandled error type as a result. Therefore, there is no way to lose any errors, and they propagate automatically through all the different subsystems in our application, which means we don't have to be fearful anymore. It will be handled by higher-level code, or if it doesn't it will be passed off to something that can.

If we handle all errors using `ZIO#catchAll` the type of error channel become `Nothing` which means there is no expected error remaining to handle:

```scala
val myApp: ZIO[Any, Nothing, Unit] = 
  upload("contacts.csv")
    .catchAll {
      case FileExist(name) =>
        ZIO.unit // handling FileExist error case
      case StorageLimitExceeded(limit) =>
        ZIO.unit // handling StorageLimitExceeded error case
    }
```

When we type errors, we know that they can't be lost. So typed errors give us the ability to log less.

## Debugging

When we are writing an application using the ZIO effect, we are writing workflows as data transformers. So there are lots of cases where we need to debug our application by seeing how the data transformed through the workflow. We can add or remove debugging capability without changing the signature of our effect:

```scala mdoc:silent:nest
import zio._

ZIO.ifZIO(
  Random.nextIntBounded(10)
    .debug("random number")
    .map(_ % 2)
    .debug("remainder")
    .map(_ == 0)
)(
  onTrue = ZIO.succeed("Success"),
  onFalse = ZIO.succeed("Failure")
).debug.repeatWhile(_ != "Success")
``` 

The following could be one of the results of this program:

```
random number: 5
remainder: 1
Failure
random number: 1
remainder: 1
Failure
random number: 2
remainder: 0
Success
```
## Logging

ZIO has built-in logging functionality. This allows us to log within our application without adding new dependencies. ZIO logging doesn't require any services from the environment. 

We can easily log inside our application using the `ZIO.log` function:

```scala mdoc:silent:nest
ZIO.log("Application started!")
```

The output would be something like this:

```bash
[info] timestamp=2021-10-06T07:23:29.974297029Z level=INFO thread=#2 message="Application started!" file=ZIOLoggingExample.scala line=6 class=zio.examples.ZIOLoggingExample$ method=run
```

To log with a specific log-level, we can use the `ZIO.logLevel` combinator:

```scala mdoc:silent:nest
ZIO.logLevel(LogLevel.Warning) {
  ZIO.log("The response time exceeded its threshold!")
}
```
Or we can use the following functions directly:

* `ZIO.logDebug`
* `ZIO.logError`
* `ZIO.logFatal`
* `ZIO.logInfo`
* `ZIO.logWarning`

```scala mdoc:silent:nest
ZIO.logError("File does not exist: ~/var/www/favicon.ico")
```

It also supports logging spans:

```scala mdoc:silent:nest
ZIO.logSpan("myspan") {
  ZIO.sleep(1.second) *> ZIO.log("The job is finished!")
}
```

ZIO Logging calculates and records the running duration of the span and includes that in logging data:

```bash
[info] timestamp=2021-10-06T07:29:57.816775631Z level=INFO thread=#2 message="The job is done!" myspan=1013ms file=ZIOLoggingExample.scala line=8 class=zio.examples.ZIOLoggingExample$ method=run
```

## Example

Let's write an application that takes numerator and denominator from the user and then print the result back to the user:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ")
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r")
    } yield ()
    
  def readNumber(msg: String): ZIO[Console, IOException, Int] =
    Console.print(msg) *> Console.readLine.map(_.toInt)

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] =
    if (b == 0)
      ZIO.die(new ArithmeticException("divide by zero")) // unexpected error
    else
      ZIO.succeed(a / b)
}
```

Now let's try to enter the zero for the second number and see what happens:

```scala
Please enter the first number  (a): 5
Please enter the second number (b): 0
timestamp=2022-02-14T09:39:53.981143209Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.ArithmeticException: divide by zero
at MainApp$.$anonfun$divide$1(MainApp.scala:16)
at zio.ZIO$.$anonfun$die$1(ZIO.scala:3384)
at zio.internal.FiberContext.runUntil(FiberContext.scala:255)
at zio.internal.FiberContext.run(FiberContext.scala:115)
at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1130)
at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:630)
at java.base/java.lang.Thread.run(Thread.java:831)
at <empty>.MainApp.divide(MainApp.scala:16)"
```

As we see, because we entered the zero for the denominator, the `ArithmeticException` defect, makes the application crash.

Defects are any _unexpected errors_ that we are not going to handle. They will propagate through our application stack until they crash the whole.

Defects have many roots, most of them are from a programming error. Errors will happen when we haven't written the application with best practices. For example, one of these practices is that we should validate the inputs before providing them to the `divide` function. So if the user entered the zero as the denominator, we can retry and ask the user to return another number:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ").repeatUntil(_ != 0)
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r")
    } yield ()

  def readNumber(msg: String): ZIO[Console, IOException, Int] =
    Console.print(msg) *> Console.readLine.map(_.toInt)

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] = ZIO.succeed(a / b)
}
```

Another note about defects is that they are invisible, and they are not typed. We cannot expect what defects will happen by observing the typed error channel. In the above example, when we run the application and enter noninteger input, another defect, which is called `NumberFormatException` will crash the application:

```scala
Enter the first number  (a): five
timestamp=2022-02-18T06:36:25.984665171Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.NumberFormatException: For input string: "five"
	at java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)
	at java.base/java.lang.Integer.parseInt(Integer.java:660)
	at java.base/java.lang.Integer.parseInt(Integer.java:778)
	at scala.collection.StringOps$.toInt$extension(StringOps.scala:910)
	at MainApp$.$anonfun$readNumber$3(MainApp.scala:16)
	at MainApp$.$anonfun$readNumber$3$adapted(MainApp.scala:16)
  ...
	at <empty>.MainApp.run(MainApp.scala:9)"
```

The cause of this defect is also is a programming error, which means we haven't validated input when parsing it. So let's try to validate the input, and make sure that is it is a number. We know that if the entered input does not contain a parsable `Int` the `String#toInt` throws the `NumberFormatException` exception. As we want this exception to be typed, we import the `String#toInt` function using the `ZIO.attempt` constructor. Using this constructor the function signature would be as follows:

```scala mdoc:compile-only
import zio._ 

def parseInput(input: String): ZIO[Any, Throwable, Int] =
  ZIO.attempt(input.toInt)
```

Since the `NumberFormatException` is an expected error, and we want to handle it. So we type the error channel as `NumberFormatException`.

To be more specific, we would like to narrow down the error channel to the `NumberFormatException`, so we can use the `refineToOrDie` operator:

```scala mdoc:compile-only
import zio._

def parseInput(input: String): ZIO[Any, NumberFormatException, Int] =
  ZIO.attempt(input.toInt)                 // ZIO[Any, Throwable, Int]
    .refineToOrDie[NumberFormatException]  // ZIO[Any, NumberFormatException, Int]
```

The same result can be achieved by succeeding the `String#toInt` and then widening the error channel using the `ZIO#unrefineTo` operator:

```scala mdoc:compile-only
import zio._

def parseInput(input: String): ZIO[Any, NumberFormatException, Int] =
  ZIO.succeed(input.toInt)                 // ZIO[Any, Nothing, Int]
    .unrefineTo[NumberFormatException]     // ZIO[Any, NumberFormatException, Int]
```

Now, let's refactor the example with recent changes:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ").repeatUntil(_ != 0)
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r")
    } yield ()
    
  def parseInput(input: String): ZIO[Any, NumberFormatException, Int] =
    ZIO.attempt(input.toInt).refineToOrDie[NumberFormatException]

  def readNumber(msg: String): ZIO[Console, IOException, Int] =
    (Console.print(msg) *> Console.readLine.flatMap(parseInput))
      .retryUntil(!_.isInstanceOf[NumberFormatException])
      .refineToOrDie[IOException]

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] = ZIO.succeed(a / b)
}
```
