---
layout: docs
position: 1
section: overview
title:  "Background"
---
# {{page.title}}

Non-functional Scala programs use _procedural functions_, which are:

 * **Partial** — Procedures do not return values for some inputs (for example, they throw exceptions).
 * **Non-Deterministic** — Procedures return different outputs for the same input.
 * **Impure** — Procedures perform side-effects, which mutate data or interact with the outside world.

Unlike non-functional Scala programs, functional Scala programs only use _pure functions_, which are:

 * **Total** — Functions always return an output for every input.
 * **Deterministic** — Functions return the same output for the same input.
 * **Pure** — The only effect of providing a function an input is computing the output.

Pure functions just transform input values into output values in a total, deterministic way. Pure functions are easier to understand, easier to test, easier to refactor, and easier to abstract over.

Functional programs do not interact with the external world directly, because that involves partiality, non-determinism and side-effects. Instead, functional programs construct and return _data structures_, which _describe_ (or _model_) interaction with the real world.

Immutable data structures that model procedural effects are called _functional effects_. The concept of functional effects is critical to deeply understanding how ZIO works, and is introduced in the next section.

# Programs As Values

We can build a simple description of a console program that has just three instructions:

```tut:silent
sealed trait Console[+A]
final case class Return[A](value: () => A) extends Console[A]
final case class PrintLine[A](line: String, rest: Console[A]) extends Console[A]
final case class ReadLine[A](rest: String => Console[A]) extends Console[A]
```

In this model, `Console[A]` is an immutable data structure, which represents a console program that returns a value of type `A`.

The `Console` data structure is a _tree_, and at the very end of the program, you will find a `Return` instruction that stores a value of type `A`, which is the return value of the `Console[A]` program.

Although very simple, this data structure is enough to build an interactive program:

```tut:silent
val example1: Console[Unit] = 
  PrintLine("Hello, what is your name?",
    ReadLine(name =>
      PrintLine(s"Good to meet you, ${name}", Return(() => ())))
)
```

This program is an immutable value, and doesn't do anything&mdash;it just _describes_ a program that prints out a message, asks for input, and prints out another message that depends on the input. 

Although this program is just a model, we can translate the model into effects quite simply using an interpreter, which recurses on the data structure, translating every operation into a side-effect:

```tut:silent
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

Interpreting (also called _running_ or _executing_) is not functional, but in an ideal application, it only needs to be done a single time: in the application's main function. The rest of the application can be purely functional.

Now in practice, it's not very convenient to build console programs using constructors directly. Instead, we can define helper functions, which look more like their effectful equivalents:

```tut:silent
def succeed[A](a: => A): Console[A] = Return(() => a)
def printLine(line: String): Console[Unit] =
  PrintLine(line, succeed(()))
val readLine: Console[String] =
  ReadLine(line => succeed(line))
```

Similarly, it's not easy to build `Console` values directly, but the process can be simplified if we define `map` and `flatMap` methods:

 - The `map` method lets you transform a console program that returns an `A` into a console program that returns a `B`, by supplying a function `A => B`. 
 - The `flatMap` method lets you sequentially compose a console program that returns an `A` with a callback that will be given the `A`, and can return another console program based on that `A`.

 These two methods are defined as follows:

```tut:silent
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

With these `map` and `flatMap` methods, we can now take advantage of Scala's `for` comprehensions, and write programs that look like their procedural equivalents:

```tut:silent
val example2: Console[String] =
  for {
    _    <- printLine("What's your name?")
    name <- readLine
    _    <- printLine(s"Hello, ${name}, good to meet you!")
  } yield name
```

When we wish to execute this program, we can call `interpret` on the `Console` value. 

All purely functional programs are constructed this way: instead of interacting with the real world, they build a _functional effect_, which is nothing more than an immutable, type-safe, tree-like data structure. 

Functional programmers use funtional effects to build complex, real world software without giving up the equational reasoning, composability, and type safety afforded by purely functional programming.

For an introduction to the functional effect data type in ZIO, see the [Overview](index.html).