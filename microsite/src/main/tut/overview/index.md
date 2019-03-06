---
layout: docs
position: 3
section: overview
title:  "Overview"
---
# {{page.title}}

ZIO is a library for asynchronous and concurrent programming that is based on pure functional programming.

Unlike non-functional Scala programs, ZIO programs only use _pure functions_, which are:

 * **Total** — Functions return a value for every input.
 * **Deterministic** — Functions return the same value for the same input.
 * **Free of Side Effects** — The only effect of function application is computing the return value.

Pure functions are easier to understand, easier to test, and easier to refactor.

Functional programs do not interact with the external world, because that involves non-determinism and side-effects. Instead, functional programs construct and return _data structures_ that _describe_ interaction with the real world.

This concept, which is the heart of ZIO, is introduced in the next section.

## Programs As Values

We can build a simple description of a console program that has just three instructions:

```tut
sealed trait Console[+A]
final case class Return[A](value: () => A) extends Console[A]
final case class PrintLine[A](line: String, rest: Console[A]) extends Console[A]
final case class ReadLine[A](rest: String => Console[A]) extends Console[A]
```

In this model, `Console[A]` is an immutable data structure, which represents a console program that returns a value of type `A`. The `Console` data structure is a _tree_, and at the very end of the program, you will find a `Return` instruction that stores a value of type `A`, the return value of the `Console[A]` program.

Using this data structure, we can build an interactive program:

```tut
val example1: Console[Unit] = 
  PrintLine("Hello, what is your name?",
    ReadLine(name =>
      PrintLine(s"Good to meet you, ${name}", Return(() => ())))
)
```

This program is an immutable value, and doesn't do anything&mdash;it just _describes_ a program that prints out a message, asks for input, and prints out another message that depends on the input. 

Although this program doesn't do anything, we can translate the model into effects quite simply using an interpreter, which recurses on the data structure:

```tut
def interpret[A](program: Console[A]): A = program match {
  case Return(value) => 
    value()
  case PrintLine(line, next) => 
    println(line)
    interpret(next)
  case ReadLine(next) =>
    interpret(next(scala.io.StdIn.readLine()))
}
```

Interpreting (also called _running_ or _executing_) is not functional, but it only needs to be done a single time: in the main function of the otherwise purely functional application.

In practice, it's not very convenient to build console programs using constructors directly. Instead, we can define helper functions, which look more like their effectful equivalents:

```tut
def succeed[A](a: => A): Console[A] = Return(() => a)
def printLine(line: String): Console[Unit] =
  PrintLine(line, succeed(()))
val readLine: Console[String] =
  ReadLine(line => succeed(line))
```

Similarly, it's not easy to compose `Console` values directly, but it's easy to define `map` and `flatMap` methods:

```tut
implicit class ConsoleSyntax[+A](self: Console[A]) {
  def map[B](f: A => B): Console[B] =
    flatMap(a => succeed(f(a)))

  def flatMap[B](f: A => Console[B]): Console[B] =
    self match {
      case Return(value) => f(value())
      case PrintLine(line, next) =>
        PrintLine(line, next.flatMap(f))
      case ReadLine(next) =>
        ReadLine(line => next(line).flatMap(f))
    }
}
```

With these `map` and `flatMap` methods, we can now take advantage of Scala's `for` comprehensions, and write programs that look like their effectful equivalents:

```tut
val example2: Console[String] =
  for {
    _    <- printLine("What's your name?")
    name <- readLine
    _    <- printLine(s"Hello, ${name}, good to meet you!")
  } yield name
```

When we wish to execute this program, we can call `interpret` on the `Console` value. 

All purely functional programs are constructed this way: instead of interacting with the real world, they build a tree-like data structure. This model of a program _describes_ interaction with the real world, but doesn't do anything itself&mdash;it's just a type-safe, immutable data structure.

ZIO programs build `ZIO` data structures, which are far more powerful, and model asynchronous and concurrent effects, with powerful features and strong guarantees that help you solve the most challenging problems imagineable.

# ZIO

At the core of ZIO is `ZIO`, a powerful data type inspired by Haskell's `IO` monad. The `ZIO` data type allows you to model asynchronous, concurrent, and effectful computations as a pure value.

Effect types like `ZIO` are how purely functional programs interact with the real world. Functional programmers use them to build complex, real world software without giving up the equational reasoning, composability, and type safety afforded by purely functional programming.

There are many benefits of building your programs using `ZIO`, including all of the following:

 * **Asynchronicity**. Like Scala's own `Future`, `ZIO` lets you easily write asynchronous code without blocking or callbacks. Compared to `Future`, `ZIO` has significantly better performance and cleaner, more expressive, and more composable semantics.
 * **Composability**. Purely functional code can't be combined with impure code that has side-effects without sacrificing the straightforward reasoning properties of functional programming. `ZIO` lets you wrap up all effects into a purely functional package that lets you build composable real world programs.
 * **Concurrency**. `ZIO` has all the concurrency features of `Future`, and more, based on a clean fiber concurrency model designed to scale well past the limits of native threads. `ZIO`'s concurrency primitives do not leak resources.
 * **Interruptibility**. All concurrent computations can be interrupted, in a way that still guarantees resources are cleaned up safely, allowing you to write aggressively parallel code that doesn't waste valuable resources or bring down production servers.
 * **Resource Safety**. `ZIO` provides composable resource-safe primitives that ensure resources like threads, sockets, and file handles are not leaked, which allows you to build long-running, robust applications. These applications will not leak resources, even in the presence of errors or interruption.
 * **Testability**. `ZIO` allows you to declare dependencies for your code (like database, configuration, or web service) in a compositional, type-safe way, and easily inject both live and test versions, so you can build fast, deterministic unit tests that give you confidence to refactor safely.
 * **Immutability**. `ZIO`, like Scala's immutable collection types, is an immutable data structure. All `ZIO` methods and functions return new `ZIO` values. This lets you reason about `ZIO` values the same way you reason about immutable collections.
 * **Reification**. `ZIO` reifies programs. In non-functional Scala programming, you cannot pass programs around or store them in data structures, because programs are not values. But `ZIO` turns your programs into ordinary values, and lets you pass them around and compose them with ease.
 * **Performance**. Although simple, synchronous `ZIO` programs tend to be slower than the equivalent imperative Scala, `ZIO` is extremely fast given all the expressive features and strong guarantees it provides. Ordinary imperative Scala could not provide these features and performance without tedious, error-prone boilerplate that no one would ever write.

Nearly all programmers will find the features of `ZIO` help them build scalable, performant, concurrent, and leak-free applications faster and with stronger correctness guarantees than legacy techniques allow.

