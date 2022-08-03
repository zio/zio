---
id: declarative
title: "Declarative Error Handling"
---

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

## It isn't Type Safe

There is no way to know what errors can be thrown by looking the function signature. The only way to find out in which circumstance a method may throw an exception is to read and investigate its implementation. So the compiler cannot prevent us from writing unsafe codes. It is also hard for a developer to read the documentation event through reading the documentation is not suffice as it may be obsolete, or it may don't reflect the exact exceptions.

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

## Lack of Exhaustivity Checking

When we use `try`/`catch` the compiler doesn't know about errors at compile-time, so if we forgot to handle one of the exceptions the compiler doesn't help us to write total functions. This code will crash at runtime because we forgot to handle the `IllegalAgeException` case:

```scala
try {
  validate(17)
} catch {
  case NegativeAgeException(age) => ???
  //  case IllegalAgeException(age) => ???
}
```

When we are using typed errors we can have exhaustive checking support of the compiler. For example, when we are catching all errors if we forgot to handle one of the cases, the compiler warns us about that:

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

## Lossy Error Model

The error model based on the `try`/`catch`/`finally` statement is lossy and broken. Because if we have the combinations of these statements we can throw many exceptions, and then we are only able to catch one of them. All the other ones are lost. They are swallowed into a black hole, and also the one that we catch is the wrong one. It is not the primary cause of the failure.

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

The above program just prints the `e2` while it is not the primary cause of failure. That is why we say the `try`/`catch` model is lossy.

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
