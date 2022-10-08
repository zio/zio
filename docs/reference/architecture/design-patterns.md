---
id: design-patterns
title: "Design Patterns"
---

When designing an API, there are patterns that are commonly used. In this section, we are going to talk about some of these patterns.

## Functional Design Patterns

  1. Functional Data Modeling
  2. Functional Domain Modeling
     1. Declarative Encoding
     2. Executable Encoding

### Functional Data Modeling

Before we start talking about functional data modeling, let's first recap the object-oriented way of modeling data.

The essence of object-oriented data modeling is inheritance. We have classes, traits, abstract classes, and subtyping. The core idea is to describe the commonalities between different types of data in a base interface or abstract class, and then extend it to describe the differences in the subclasses; so each subclass has its type-specific details while sharing the commonalities with the base type:

```scala mdoc:compile-only
abstract class Event {
  def id: String
  def timestamp: Long
}

case class ClickEvent(id: String, timestamp: Long, element: String) extends Event
case class ViewEvent(id: String, timestamp: Long, page: String) extends Event
```

There is some problem with this approach, let's iterate some of them:

One of the problems with this approach is that it is not a good fit when we want to write generic operations on the `Event` type. For example, if we want to write an operation that changes the timestamp of an event, we should match the input event and then do the transformation for each case. Then finally, we should use `asInstanceOf` to cast the result back to the original type:

```scala mdoc:compile-only
def updateTimestamp[E <: Event](event: E, timestamp: Long): E =
  event match {
    case e: ClickEvent => e.copy(timestamp = timestamp).asInstanceOf[E]
    case e: ViewEvent => e.copy(timestamp = timestamp).asInstanceOf[E]
  }
```

This introduces a lot of boilerplate code. It also has a type-safety issue. If we forget to add all the cases for the match expression, the compiler will not be able to detect it.

In functional data modeling, we don't use inheritance. All tools we have are sum types and product types. By using these two types, we can mathematically model the data. Let's try to model the previous example using sum and product types:

```scala
case class Event(id: String, timestamp: Long, details: EventType)

sealed trait EventType
object EventType {
  final case class ClickEvent(element: String) extends EventType
  final case class ViewEvent(page: String) extends EventType
}
```

In this approach, we describe commonalities with product types, and differences with sum types. So we push `id`, `timestamp`, and `details` to the `Event` case class, and encode the type-specific details of the event in the `EventType` which is a sum type.

The product and sum types are called "Algebraic Data Types" (ADT). They are the building blocks of modeling data in functional programming:

- **Product types** are the cartesian product of the types they contain. For example, `Event` is the product of `String`, `Long`, and `EventType`. In scala, we use `case class` to model product types.
- **Sum types** are the disjoint union of the types they represent. For example, `EventType` is the either `ClickEvent` or `ViewEvent`. In scala 2, we use `sealed trait`s and In Scala 3, we use `enum`s to model sum types.

### Functional Domain Modeling

Functional domain modeling is the process of modeling solutions to problems in a specific domain using functional programming. It is a very broad topic, and we are not going to cover all the details here. However, we are going to talk about the general patterns that are commonly used in functional domain modeling, in a nutshell.

In functional programming, we have two primary tools:

1. Nouns (Data)
2. Verbs (Operators)

So to provide a solution to every domain problem, we should follow these steps:

1. **Extracting the Core Model**— First, we need to focus on extracting the "minimum" information required to describe the "solution" to the "fundamental problem" in that domain. So we should ask ourselves, "what is the most fundamental problem we have in this domain?".

2. **Providing Operators**— Once we find out what is the core model of our domain, we should provide a set of "orthogonal operators" that are going to provide a "solution" to the "complex problems" by combining sub-problems.

3. **Packaging the Data Type**— To achieve great modularity, we package both the "core model" and "operators" in one place which is called "data type".

4. **Defining Constructors**— Also to have a better ergonomic API, we put all solutions to the "basic and simple problems" in that domain in the companion object of the "data type".

The ZIO ecosystem defines all data types in such a way, including ZIO, Fiber, Reference, Stream, etc.

There are two main encoding styles in functional domain modeling:

1. **Executable Encoding** is a functional domain modeling where we use operators and constructors to provide the "final" solution to the domain problems.

2. **Declarative Encoding** is a functional domain modeling where we use constructors and operators to provide the "description" of the solution to the domain problems. Later, we interpret the description to get the "final" solution.

A real-life example will help you understand the differences between these two approaches. Let's say we want to write an effect system from scratch. To keep things simple we will create an effect system that only supports sequencial effects. So at the end, we would like to write a program like this:

```scala
object Main extends scala.App {
  val app =
    for {
      _    <- IO.succeed(print("Enter your name: "))
      name <- IO.succeed(scala.io.StdIn.readLine())
      _    <- IO.succeed(println(s"Hello, $name"))
    } yield ()

  app.unsafeRunSync()
}
```

#### Executable Encoding

The executable encoding is straightforward. After defining the core model, we just need to think about the execution steps for each operator.

In the following example, we describe the core model as `IO` case class, which has a `thunk` of code that is going to be executed when we call `unsafeRunSync`: `case class IO[+A](private val thunk: () => A)`.

We should provide a set of operators: `IO#map`, `IO#flatMap`, and `IO#unsafeRunSync`. We have also one constructor: `IO.succeed`. Implementing these operators requires us to think about how they should actually executed to produce the desired result.

For example, when we implement `map`,  should execute the `thunk` of the current `IO` and then apply the `f` function to the result. The same applies to `flatMap` and `unsafeRunSync`:

```scala mdoc:compile-only
final case class IO[+A](private val thunk: () => A) {
  def map[B](f: A => B): IO[B]         = IO.succeed(f(thunk()))
  def flatMap[B](f: A => IO[B]): IO[B] = IO.succeed(f(thunk()).unsafeRun())
  def unsafeRunSync(): A                   = thunk()
}

object IO {
  def succeed[A](value: => A): IO[A] = IO(() => value)
}
```

Now we can run the greeting program with the above encoding.

#### Declarative Encoding

In contrast to the executable encoding, the declarative encoding is lazy. This means that the definition of the language is separate from how it is interpreted.

In this example, we describe the problem of sequencing effects as a data type called `FlatMap` that contains two information: the first effect to be executed and the function that should be applied to the result of the first effect. To describe the thunk of code that will be executed by the interpreter, we create another data type called `Succeed`. Similarly, we need another data type called `SucceedNow` to describe the already evaluated values.

```scala
sealed trait IO[+A]
object IO {
  final case class SucceedNow[A](value: A)                    extends IO[A]
  final case class Succeed[A](thunk: () => A)                 extends IO[A]
  final case class FlatMap[A, B](io: IO[A], cont: A => IO[B]) extends IO[B]
}
```

Assume we have written the following program:

```scala
val app: IO[Int] = 
  for {
    a <- IO.succeed(scala.io.StdIn.readLine("Enter the first number: ").toInt)
    b <- IO.succeed(scala.io.StdIn.readLine("Enter the second number: ").toInt) 
  } yield (a + b)
```

This program will be translated into the following tree data structure using the declarative encoding:

```scala
val app: IO[Int] = 
  IO.FlatMap(
    IO.Succeed(() => scala.io.StdIn.readLine("Enter the first number: ").toInt),
    (a: Int) =>
      IO.FlatMap(
        IO.Succeed(() => scala.io.StdIn.readLine("Enter the second number: ").toInt),
        (b: Int) => IO.SucceedNow(a + b)
      )
  )
```

The only operator that deals with the interpretation of the program is `unsafeRunSync`. Let's see how the whole encoding looks like:

```scala mdoc:compile-only
sealed trait IO[+A] { self =>
  def map[B](f: A => B): IO[B] = flatMap(f andThen IO.succeedNow)

  def flatMap[B](f: A => IO[B]): IO[B] = IO.FlatMap(self, f)

  def unsafeRunSync(): A = {
    type Cont = Any => IO[Any]

    def run(stack: List[Cont], currentIO: IO[Any]): A = {
      def continue(value: Any) =
        stack match {
          case ::(cont, next) => run(next, cont(value))
          case Nil            => value.asInstanceOf[A]
        }

      currentIO match {
        case IO.SucceedNow(value) => continue(value)
        case IO.Succeed(thunk)    => continue(thunk())
        case IO.FlatMap(io, cont) => run(stack appended cont, io)
      }
    }

    run(stack = Nil, currentIO = self)
  }
}

object IO {

  def succeedNow[A](value: A): IO[A] = IO.SucceedNow(value)

  def succeed[A](value: => A): IO[A] = IO.Succeed(() => value)

  final case class SucceedNow[A](value: A) extends IO[A]

  final case class Succeed[A](thunk: () => A) extends IO[A]

  final case class FlatMap[A, B](io: IO[A], cont: A => IO[B]) extends IO[B]

}
```

## Design Techniques

  1. Contextual Eliminators
  2. Implicit Traces
  3. Unsafe Markers
  4. Descriptive Errors Using Implicit Evidence
  5. Partial Application of Type Parameters
  6. Double Evaluation Prevention
  7. Method Naming Conventions
  8. Path Dependent Types
