---
layout: docs
position: 3
section: overview
title:  "Overview"
---
# {{page.title}}

ZIO is a library for asynchronous and concurrent programming that is based on pure functional programming.

Unlike non-functional Scala programs, ZIO programs only use pure functions, which are:

 * **Total** — Functions return a value for every input.
 * **Deterministic** — Functions return the same value for the same input.
 * **Free of Side Effects** — The only effect that calling a function has is computing the return value.

Pure functions are easier to understand, easier to test, and easier to refactor.

Pure programs do not interact with the real world. However, they can build data structures that _describe_ interaction with the real world.

The next section introduces this concept.

## Programs As Values

We can build a simple model of a console program that has just three instructions:

```scala
sealed trait Console[+A]
case class Return[A](value: () => A) extends Console[A]
case class PrintLine[A](line: String, rest: Console[A]) extends Console[A]
case class ReadLine[A](rest: String => Console[A]) extends Console[A]
```

In this model, `Console[A]` represents a console program that returns a value of type `A`. The `Console` data structure is a tree, and at the very end of the program, you will find a `Return` instruction that returns the specified value.

Using this data structure, we can build an interactive program:

```scala
PrintLine("Hello, what is your name?",
  ReadLine(name =>
    PrintLine(s"Good to meet you, ${name}"))
)
```

This program is an immutable value. However, it can be translated into a series of effects quite simply:

```scala
def interpret[A](program: Program[A]): A = program match {
  case Return(value) => value()
  case PrintLine(line, rest) => println(line); interpret(rest)
  case ReadLine(rest) interpret(rest(scala.io.StdIn.readLine()))
}
```

It's not very convenient to build console programs using the three constructors, so instead, we can define helper functions:

```scala
def printLine(line: String): Console[Unit] =
  PrintLine(line, Return(()))
val readLine: Console[String] =
  ReadLine(line => Return(line))
def success(a: A): Program[A] = Return(a)
```

Similarly, it's not easy to compose these values into more complex values, but if we add `map` and `flatMap` methods to `Console`, then we can use Scala's for comprehension syntax to build programs in a very imperative-looking way:

```scala
sealed trait Console[+A] { self =>
  def map[B](f: A => B): Console[B] =
    flatMap(a => success(f(a)))

  def flatMap[B](f: A => Console[B]): Console[B] =
    self match {
      case Return(value) => f(value())
      case PrintLine(line, rest) =>
        PrintLine(line, rest.flatMap(f))
      case ReadLine(rest) =>
        ReadLine(line => rest(line).flatMap(f))
    }
}
```

Now we can compose programs very simply, using for comprehensions:

```scala
val program: Console[String] =
  for {
    _    <- printLine("What's your name?")
    name <- readLine
    _    <- printLine(s"Hello, ${name}, good to meet you!")
  } yield name
```

When we wish to execute this program (which is not a functional operation), we can call `interpret` on the `Console` value.

Purely functional programs are like this: instead of interacting with the real world, they build a tree-like data structure, which describes interaction with the real world.

At the end of the world, typically in your application's main function, you use an interpreter, called a _runtime system_ in ZIO, to translate the data structure, which models an effectful program, into the real world interactions that it describes.

This approach lets you write purely functional programs and benefit from the advanced features of ZIO's runtime system, which are not possible with non-functional programming.

## IO

At the core of ZIO is `IO`, a powerful data type inspired by Haskell's `IO` monad. The `IO` data type allows you to model asynchronous, concurrent, and effectful computations as a pure value.

Effect types like `IO` are how purely functional programs interact with the real world. Functional programmers use them to build complex, real world software without giving up the equational reasoning, composability, and type safety afforded by purely functional programming.

However, there are many practical reasons to build your programs using `IO`, including all of the following:

 * **Asynchronicity**. Like Scala's own `Future`, `IO` lets you easily write asynchronous code without blocking or callbacks. Compared to `Future`, `IO` has significantly better performance and cleaner, more expressive, and more composable semantics.
 * **Composability**. Purely functional code can't be combined with impure code that has side-effects without sacrificing the straightforward reasoning properties of functional programming. `IO` lets you wrap up all effects into a purely functional package that lets you build composable real world programs.
 * **Concurrency**. `IO` has all the concurrency features of `Future`, and more, based on a clean fiber concurrency model designed to scale well past the limits of native threads. `IO`'s concurrency primitives do not leak resources under any circumstances.
 * **Interruptibility**. All concurrent computations can be interrupted, in a way that still guarantees resources are cleaned up safely, allowing you to write aggressively parallel code that doesn't waste valuable resources or bring down production servers.
 * **Resource Safety**. `IO` provides composable resource-safe primitives that ensure resources like threads, sockets, and file handles are not leaked, which allows you to build long-running, robust applications. These applications will not leak resources, even in the presence of errors or interruption.
 * **Immutability**. `IO`, like Scala's immutable collection types, is an immutable data structure. All `IO` methods and functions return new `IO` values. This lets you reason about `IO` values the same way you reason about immutable collections.
 * **Reification**. `IO` reifies programs. In non-functional Scala programming, you cannot pass programs around or store them in data structures, because programs are not values. But `IO` turns your programs into ordinary values, and lets you pass them around and compose them with ease.
 * **Performance**. Although simple, synchronous `IO` programs tend to be slower than the equivalent imperative Scala, `IO` is extremely fast given all the expressive features and strong guarantees it provides. Ordinary imperative Scala could not match this level of expressivity and performance without tedious, error-prone boilerplate that no one would write in real-life.

While functional programmers *must* use `IO` (or something like it) to represent effects, nearly all programmers will find the features of `IO` help them build scalable, performant, concurrent, and leak-free applications faster and with stronger correctness guarantees than legacy techniques allow.

Use `IO` because it's simply not practical to write real-world, correct software without it.
