---
id: imperative-vs-declarative
title: "Imperative vs. Declarative Error Handling"
sidebar_label: "Imperative vs. Declarative"
---

To figure out the benefit of typed errors in declarative error handling, we need to understand the drawbacks of the imperative approach and then see how the declarative approach can be used to solve the same problem.

## Imperative Error Handling

In the imperative style, when we encounter a wrong state, we throw an exception, and to handle exceptions, we have to use the `try`/`catch` language construct. Whenever we encounter an exception inside a `try` block, the control flow will jump to the `catch` block. In the catch block, we can handle the exception and decide what to do next. This is a very common pattern in imperative programming.

For example, we can write the `divide` function like this:

```scala mdoc:silent
def divide(a: Int, b: Int): Int =
  if (b == 0)
    throw new IllegalArgumentException("Division by zero")
  else
    a / b
```

As we know that this function throws an exception when `b` is zero, so we need to handle the exception when we call this function:

```scala mdoc:compile-only
def readFromConsole: (Int, Int) = ???

val (a, b) = readFromConsole

try {
  Some(divide(a, b))
} catch {
  case _: IllegalArgumentException => None
}
```

## Declarative Error Handling

In declarative error handling, we treat errors as values instead of throwing exceptions. So instead of breaking the flow of the program, we can return a value that represents the error. When we have a workflow of type `ZIO[R, E, A]`, the `E` type parameter is used to represent that our workflow may fail with an error of type `E`.

For example, the following program written with ZIO may fail with an error of type `AgeValidationException`:

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

We can handle errors using `catchAll`/`catchSome` methods instead of using `try`/`catch` blocks:

```scala mdoc:compile-only
validate(17).catchAll {
  case NegativeAgeException(age) => ???
  case IllegalAgeException(age)  => ???
}
```

## Imperative vs. Declarative

### Referential Transparency

We say that an expression is referentially transparent if it can be replaced with its value without changing the behavior of the program. This property helps us to reason about a program easily. Also writing tests for our programs becomes much easier.

Unfortunately, when we throw an exception, we lose the ability to reason about our programs. Exceptions break the referential transparency of our programs. For example, let's say we have the following function:

```scala mdoc:compile-only
def divide10By(b: Int): Option[Int] = {
  val result = divide(10, b)
  try {
    Some(result)
  } catch {
    case _: IllegalArgumentException => None
  }
}
```

If we call `divide10By(0)`, we will get an exception (`IllegalArgumentException`). Now, let's see what happens if we replace the result with its value like this:

```scala mdoc:compile-only
def divide10By(b: Int): Option[Int] = 
  try {
    Some(divide(10, b))
  } catch {
    case _: IllegalArgumentException => None
  }
```

In this case, if we call `divide10By(0)`, we will get the `None` value. The behavior of the function will be changed. We cannot reason about the behavior of our program by substituting expressions with their values. In this style of error handling, the behavior of the program is dependent on where we call our expressions, inside or outside the `try` block.

When we model our programs with the `ZIO`, we sure that our programs are referentially transparent. We can reason about our programs very easily without having to worry about changing the behavior of our programs.

### Type-safety

There is no way to know what errors can be thrown by looking at the function signature. The only way to find out in which circumstance a method may throw an exception is to read and investigate its implementation. So the compiler cannot prevent us from writing unsafe codes. It is also hard for a developer to read the documentation event through reading the documentation is not sufficient as it may be obsolete, or it may don't reflect the exact exceptions.

In ZIO when we see the type of the effect, we can determine what kind of error it can fail with. This helps us to have compile-time type-safety on our programs not only on success values but also on failure values.

### Exhaustivity Checking

When we use `try`/`catch` the compiler doesn't know about errors at compile time, so if we forgot to handle one of the exceptions the compiler doesn't help us to write total functions. This code will crash at runtime because we forgot to handle the `IllegalAgeException` case:

```scala
try {
  validate(17)
} catch {
  case NegativeAgeException(age) => ???
  //  case IllegalAgeException(age) => ???
}
```

When we are using typed errors we can have exhaustive checking support from the compiler. For example, when we are catching all errors if we forgot to handle one of the cases, the compiler warns us about that:

```scala mdoc:silent
validate(17).catchAll {
  case NegativeAgeException(age) => ???
}

// match may not be exhaustive.
// It would fail on the following input: IllegalAgeException(_)
```

In the example above, if we only handle `NegativeAgeException`, the compiler will complain about the `IllegalAgeException` being unhandled. This helps us cover all cases and write _total functions_ easily.

> **Note:**
>
> When a function is defined for all possible input values, it is called a _total function_ in functional programming.

### Error Model

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

ZIO guarantees that no errors are lost. It has a _lossless error model_. This guarantee is provided via a hierarchy of supervisors and information made available via data types such as `Exit` and `Cause`. All errors will be reported. If there's a bug in the code, ZIO enables us to find out about it.
