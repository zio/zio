---
id: motivation
title: "Motivation"
---

:::caution
In this section, we are going to study how ZIO supports dependency injection by providing pedagogical examples. Examples provided in these sections are not idiomatic and are not meant to be used as a reference. We will discuss the idiomatic way to use dependency injection in ZIO later.

So feel free to skip reading this section if you are not interested to learn the underlying concepts in detail.
:::

Assume we have two services called `Formatter` and `Compiler` like the below:

```scala mdoc:silent
import zio._

class Formatter {
  def format(code: String): UIO[String] = 
    ZIO.succeed(code) // dummy implementation
}

class Compiler {
  def compile(code: String): UIO[String] = 
    ZIO.succeed(code) // dummy implementation
}
```

We want to create an editor service, which uses these two services. Hence, we are going to instantiate the required services inside the `Editor` class:

```scala mdoc:compile-only
class Editor {
  private val formatter: Formatter = new Formatter()
  private val compiler: Compiler   = new Compiler()

  def formatAndCompile(code: String): UIO[String] =
    formatter.format(code).flatMap(compiler.compile)
}
```

There are some problems with this approach:
1. Users of the `Editor` service haven't any control over how dependencies will be created.
2. Users of the `Editor` service cannot use different implementations of `Formatter` and `Compiler` services. For example, we would like to test the `Editor` service with a mock version of `Formatter` and `Compiler`. With this approach, mocking these dependencies is hard.
3. The `Editor` service is tightly coupled with `Formatter` and `Compiler`. This means any change to these services, may introduce a new change in the `Editor` class.
4. Creating the object graph is a manual process.

Let's see how we can provide a solution to these problems. In the following sections, we will step by step solve these problems, and finally, we will see how ZIO solves the dependency injection problem.

## Step 1: Inversion of Control

On solution to the first problem is inverting the control to the user of the `Editor` service, which is called _Inversion of Control_.

Now lets instead of instantiating the dependencies inside the `Editor` service, create them outside the `Editor` service and pass them to the `Editor` service:

```scala mdoc:silent
class Editor(formatter: Formatter, compiler: Compiler) {
  def formatAndCompile(code: String): UIO[String] =
    formatter.format(code).flatMap(compiler.compile)
}
```

Now the `Editor` service is decoupled from how the `Formatter` and `Compiler` services are created. The client of the `Editor` service can instantiate the `Formatter` and `Compiler` services and pass them to the `Editor` service:

```scala mdoc:compile-only
val formatter = new Formatter() // creating formatter
val compiler  = new Compiler()  // creating compiler
val editor = new Editor(formatter, compiler) // assembling formatter and compiler into editor

editor.formatAndCompile("println(\"Hello, world!\")")
``` 

## Step 2: Decoupling from Implementations

In the previous step, we delegated the creation of dependencies to the client of the `Editor` service. This decouples the `Editor` service from the creation of the dependencies. But it is not enough. We still coupled to the concrete classes called `Formatter` and `Compiler`. The user of the `Editor` service cannot use different implementations rather than the `Formatter` and `Compiler` services. This is where the object-oriented approach comes into play. By programming to interfaces, we can encapsulate the `Editor` service and make it independent of concrete implementations:

```scala mdoc:silent:nest
trait Formatter {
  def format(code: String): UIO[String]
}

class ScalaFormatter extends Formatter {
  def format(code: String): UIO[String] = 
    ZIO.succeed(code) // dummy implementation
}

trait Compiler {
  def compile(code: String): UIO[String]
}

class ScalaCompiler extends Compiler {
  def compile(code: String): UIO[String] = 
    ZIO.succeed(code) // dummy implementation
}

trait Editor {
  def formatAndCompile(code: String): UIO[String]
}

class EditorLive(formatter: Formatter, compiler: Compiler) extends Editor {
  def formatAndCompile(code: String): UIO[String] =
    formatter.format(code).flatMap(compiler.compile)
}

val formatter = new ScalaFormatter() // Creating Formatter
val compiler  = new ScalaCompiler()  // Creating Compiler
val editor    = new EditorLive(formatter, compiler) // Assembling formatter and compiler into CodeEditor

editor.formatAndCompile("println(\"Hello, world!\")")
```

Now, we can test the `Editor` service easily without having to worry about the implementation of the `Formatter` and `Compiler` services. To test the `Editor` service, we can use a mock implementation of its dependencies:

```scala mdoc:compile-only
class MockFormatter extends Formatter {
  def format(code: String): UIO[String] = 
    ZIO.succeed(code) // dummy implementation
}

class MockCompiler extends Compiler {
  def compile(code: String): UIO[String] = 
    ZIO.succeed(code) // dummy implementation
}

val formatter = new MockFormatter() // Creating mock formatter
val compiler  = new MockCompiler()  // Creating mock compiler
val editor    = new EditorLive(formatter, compiler) // Assembling formatter and compiler into CodeEditor

import zio.test._

val expectedOutput = ???
for {
  r <- editor.formatAndCompile("println(\"Hello, world!\")") 
} yield assertTrue(r == expectedOutput)
```

## Step 3: Binding Interfaces to their Implementations

In the previous step, we successfully decoupled the `Editor` service from concrete dependencies. However, there is still a problem. When the application grows, the number of dependencies might increase. So, instead of injecting the dependencies manually whenever needed, we would like to maintain a mapping from interfaces to their implementations in a container, and then whenever needed, we can ask for the required dependency from the container.

So we need a container that maintains this mapping. ZIO has a type-level map, called `ZEnvironment`, which can do that for us:

```scala mdoc:silent
val scalaFormatter = new ScalaFormatter() // Creating Formatter
val scalaCompiler  = new ScalaCompiler() // Creating Compiler
val myEditor       = // Assembling Formatter and Compiler into an Editor
  new EditorLive(
    scalaFormatter,
    scalaCompiler
  )

val environment = ZEnvironment[Formatter, Compiler, Editor](scalaFormatter, scalaCompiler, myEditor)
// Map(
//  Formatter -> scalaFormatter,
//  Compiler  -> scalaCompiler
//  Editor    -> myEditor
//)
```

Now, whenever we need an object of type `Formatter`, `Compiler`, or `Editor`, we can ask the `environment` for them.

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  def run = 
    environment.get[Editor].formatAndCompile("println(\"Hello, world!\")")
}
```

Here is another example:

```scala mdoc:compile-only
val workflow: ZIO[Any, Nothing, Unit] =
  for {
    f <- environment.get[Formatter].format("println(\"Hello, world!\")")
    _ <- environment.get[Compiler].compile(f)
  } yield ()
```

## Step 4: Effectful Constructors

Until now, we discussed the creation of services where the creation process was not effectful. But, assume in order to implement the `Editor` service, we need the `Counter` service, and the creation of `Counter` itself is effectful:

```scala mdoc:silent:nest
trait Counter {
  def inc: UIO[Unit]
  def dec: UIO[Unit]
  def get: UIO[Int]
}

case class CounterLive(ref: Ref[Int]) extends Counter {
  def inc: UIO[Unit] = ref.update(_ + 1)
  def dec: UIO[Unit] = ref.update(_ - 1)
  def get: UIO[Int]  = ref.get
}

object CounterLive {
  // Effectful constructor
  def make: UIO[Counter] = Ref.make(0).map(new CounterLive(_))
}

class EditorLive(
    formatter: Formatter,
    compiler: Compiler,
    counter: Counter
) extends Editor {
  def formatAndCompile(code: String): UIO[String] = ???
}
```

To instantiate `EditorLive` we can't use the same technique as before:

```scala mdoc:silent:fail
val scalaFormatter = new ScalaFormatter() // Creating Formatter
val scalaCompiler  = new ScalaCompiler()  // Creating Compiler
val myEditor       =                      // Assembling Formatter and Compiler into an Editor
  new EditorLive(
    scalaFormatter,
    scalaCompiler,
    CounterLive.make // Compiler Error: Type mismatch: expected: Counter, found: UIO[Counter]
  )
```


We can use `ZIO#flatMap` to create the dependency graph but to make it easier, we have a special data type called `ZLayer`. It is effectful, so we can use it to create the dependency graph effectfully:

```scala mdoc:silent:nest
trait Formatter {
  def format(code: String): UIO[String]
}

case class ScalaFormatter() extends Formatter {
  def format(code: String): UIO[String] = 
    ZIO.succeed(code) // dummy implementation
}

object ScalaFormatter {
  val layer: ULayer[Formatter] = ZLayer.succeed(ScalaFormatter())
}

trait Compiler {
  def compile(code: String): UIO[String]
}

case class ScalaCompiler() extends Compiler {
  def compile(code: String): UIO[String] = ZIO.succeed(code)
}
object ScalaCompiler {
  val layer = ZLayer.succeed(ScalaCompiler())
}

trait Editor {
  def formatAndCompile(code: String): UIO[String]
}

trait Counter {
  def inc: UIO[Unit]
  def dec: UIO[Unit]
  def get: UIO[Int]
}

case class CounterLive(ref: Ref[Int]) extends Counter {
  def inc: UIO[Unit] = ref.update(_ + 1)
  def dec: UIO[Unit] = ref.update(_ - 1)
  def get: UIO[Int]  = ref.get
}

object CounterLive {
  // Effectful constructor
  def make: UIO[Counter] = Ref.make(0).map(new CounterLive(_))

  val layer: ULayer[Counter] = ZLayer.fromZIO(CounterLive.make)
}

case class EditorLive(
    formatter: Formatter,
    compiler: Compiler,
    counter: Counter
) extends Editor {
  def formatAndCompile(code: String): UIO[String] = ???
}

object EditorLive {
  val layer: ZLayer[Counter with Compiler with Formatter, Nothing, Editor] =
    ZLayer {
      for {
        // we will discuss ZIO.service later
        formatter <- ZIO.service[Formatter] 
        compiler  <- ZIO.service[Compiler]
        counter   <- ZIO.service[Counter]
      } yield EditorLive(formatter, compiler, counter)
    }
}

object MainApp extends ZIOAppDefault {
  val environment =
    ((ScalaFormatter.layer ++ ScalaCompiler.layer ++ CounterLive.layer) >>> EditorLive.layer).build

  def run =
    for {
      editor <- environment.map(_.get[Editor])
      _      <- editor.formatAndCompile("println(\"Hello, world!\")")
    } yield ()
}
```

:::note
`ZLayer` is not only an effectful constructor, but also it supports concurrency and resource safety when constructing layers.
:::

## Step 5: Using ZIO Environment To Declare Dependencies

So far, we learned that the `ZEnvironment` can act as an IoC container. Whenever we need a dependency, we can ask for it from the environment:

```scala mdoc:compile-only
val workflow: ZIO[Scope, Nothing, Unit] =
  for {
    env <- (ScalaFormatter.layer ++ ScalaCompiler.layer).build
    f   <- env.get[Formatter].format("println(\"Hello, world!\")")
    _   <- env.get[Compiler].compile(f)
  } yield ()
```

While this is a pretty good solution, there is a problem with it. Every time we need a dependency, we are asking for that instantly. In a large codebase, this imperative style of asking for dependencies can be tedious. This is an imperative style. It's better to make this declarative. So instead of **asking for dependencies** it is better to **declare dependencies**.
Accordingly, we can use the `R` type-parameter of the `ZIO` data type which supports the declarative style:

```scala mdoc:silent:nest
val workflow: ZIO[Compiler with Formatter, Nothing, String] =
 for {
   f  <- ZIO.service[Formatter] 
   r1 <- f.format("println(\"Hello, world!\")")
   c  <- ZIO.service[Compiler]
   r1 <- c.compile(r1)
 } yield r1
```

This is a much better solution. We just declare that we need the `Compiler` and the `Formatter` services using `ZIO.service` and then we compose pieces of our program to create the final application. The final workflow has all requirements in its type signature. For example, the `ZIO[Compiler with Formatter, Nothing, String]` type says that I need the `Compiler` and the `Formatter` services to produce the final result as a `String`.

Finally, we can provide all the dependencies through the `ZIO#provideEnvironment` method:

```scala mdoc:compile-only
workflow.provideLayer(ScalaCompiler.layer ++ ScalaFormatter.layer)
```

## Step 6: Automatic Dependency Graph Generation

For large applications, it can be tedious to manually create the dependency graph. ZIO has a built-in mechanism empowered by using macros to automatically generate the dependency graph. To use this feature, we can use the `ZIO#provide` method:

```scala mdoc:compile-only
workflow.provide(ScalaCompiler.layer, ScalaFormatter.layer)
```

We should provide all required dependencies and then the ZIO will construct the dependency graph and provide that to our application.
