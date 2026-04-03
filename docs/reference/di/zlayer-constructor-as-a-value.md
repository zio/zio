---
id: zlayer-constructor-as-a-value
title: "ZLayer: Constructor as a Value"
sidebar_label: "Constructor as a Value"
---

Before jumping into the next section, which will explain dependency injection in ZIO, let's take a look at the philosophy behind the `ZLayer` data type.

In the [motivation](motivation.md) section, we find out that the ordinary Scala constructors are not powerful enough to help us to build the dependency graph easily. So `ZLayer` was created to overcome scala constructors' limitations.

We can think of `ZLayer` as an alternative to constructors but with the following powerful features:
- Composable with a nice ergonomic API
- Asynchronous so it doesn't block the thread
- Effectful and resourceful
- Support for concurrency and parallelism

Let's see the following example written using scala constructors:

```scala mdoc:silent
class Editor(formatter: Formatter, compiler: Compiler) {
  // ...
}

class Compiler() {
  // ...
}

class Formatter() {
  // ...
}
```

We can say that each constructor is a function that takes some arguments as dependencies and returns a new instance of the class:
- `() => Formatter`
- `() => Compiler`
- `(Formatter, Compiler) => Editor`

The `ZLayer` reifies the conceptual idea of scala constructor and turned it into typed value which is equipped with lots of compositional operators and also supporting asynchronous operations.

So in other words, `ZLayer` is a type-safe data type that describes the asynchronous, effectful and resourceful process of building the dependency graph. We can say that a `ZLayer[Input, E, Output]` is a recipe that takes some services as input and returns some services as output.

For example, a `ZLayer` of type `ZLayer[Any, Nothing, Formatter]` is a constructor that doesn't take any services from the input and returns `Formatter` as output. Also, a `ZLayer` of type `ZLayer[Formatter with Compiler, Nothing, Editor]` is a constructor that takes `Formatter` and `Compiler` services from the input and returns `Editor` as output:

```scala mdoc:silent
import zio._

object Formatter {
  val layer: ZLayer[Any, Nothing, Formatter] =
    ZLayer.succeed(new Formatter())
}

object Compiler {
  val layer: ZLayer[Any, Nothing, Compiler] =
    ZLayer.succeed(new Compiler())
}

object Editor {
  val layer: ZLayer[Formatter with Compiler, Nothing, Editor] =
    ZLayer {
      for {
        formatter <- ZIO.service[Formatter]
        compiler  <- ZIO.service[Compiler]
      } yield new Editor(formatter, compiler) 
    }
}
```

## Composable Constructors

With scala constructors we compose services like the below to create the dependency graph:

```scala mdoc:silent
val formatter = new Formatter()
val compiler = new Compiler()
val editor = new Editor(formatter, compiler)
```

While Scala constructors are a type of Scala function. Composable functions in Scala are not as ergonomic as ZLayer for constructing dependency graphs.

With ZLayer we can compose them using operators like `++` and `>>>`:

```scala mdoc:compile-only
val editor: ZLayer[Formatter with Compiler, Nothing, Editor] = 
  (Formatter.layer ++ Compiler.layer) >>> Editor.layer
```

Also, we can compose `Formatter` and `Editor` layers to create a new layer that takes the `Compiler` service and returns the` Editor` service:

```scala mdoc:compile-only
val editor: ZLayer[Compiler, Nothing, Editor] =
  Formatter.layer >>> Editor.layer
```

## Effectful Constructors

If we have a dependency that requires an effectful computation to be initialized, we can't model easily such an operation using ordinary Scala constructors.

In the following example, without the help of `ZIO#flatMap` or `ZLayer`, we can't easily create an instance of the `Editor` class:

```scala mdoc:silent:nest:fail
import zio._

case class Counter(ref: Ref[Int]) {
  def inc: UIO[Unit] = ref.update(_ + 1)
  def dec: UIO[Unit] = ref.update(_ - 1)
  def get: UIO[Int]  = ref.get
}

object Counter {
  // Effectful constructor
  def make: UIO[Counter] = Ref.make(0).map(new Counter(_))
}

class Editor(formatter: Formatter, compiler: Compiler, counter: Counter) {
  // ...
}

object Formatter {
  def make = new Formatter() 
}

object Compiler {
  def make = new Compiler()
}

val editor    =   
  new Editor(
    Formatter.make,
    Compiler.make,
    Counter.make // Compiler Error: Type mismatch: expected: Counter, found: UIO[Counter]
  )
```

Let's see how we can use `ZIO#flatMap` to create `Editor`:

```scala mdoc:invisible
import zio._

class Editor(formatter: Formatter, compiler: Compiler, counter: Counter) {
  // ...
}

object Formatter {
  def make = new Formatter() 
}

object Compiler {
  def make = new Compiler()
}

case class Counter(ref: Ref[Int]) {
  def inc: UIO[Unit] = ref.update(_ + 1)
  def dec: UIO[Unit] = ref.update(_ - 1)
  def get: UIO[Int]  = ref.get
}

object Counter {
  // Effectful constructor
  def make: UIO[Counter] = Ref.make(0).map(new Counter(_))
}
```

```scala mdoc:compile-only
val editor: ZIO[Any, Nothing, Editor] =
  Counter.make.map { counter =>
    new Editor(
      Formatter.make,
      Compiler.make,
      counter
    )
  }
```

While with `ZLayer`, we can easily have an effectful constructor. We can create `ZLayer` from any `ZIO` effect by using `ZLayer.fromZIO`/`ZLayer.apply` constructor:

```scala mdoc:silent:nest
case class Counter(ref: Ref[Int]) {
  def inc: UIO[Unit] = ref.update(_ + 1)
  def dec: UIO[Unit] = ref.update(_ - 1)
  def get: UIO[Int]  = ref.get
}

object Counter {
  val layer: ZLayer[Any, Nothing, Counter] = 
    ZLayer {
      Ref.make(0).map(new Counter(_))
    }
}

class Formatter {
  def format(code: String): UIO[String] = ???
}

object Formatter {
  val layer: ZLayer[Any, Nothing, Formatter] =
    ZLayer.succeed(new Formatter())
}

class Compiler {
  def compile(code: String): UIO[String] = ???
}

object Compiler {
  val layer: ZLayer[Any, Nothing, Compiler] = 
    ZLayer.succeed(new Compiler())
}

class Editor(formatter: Formatter, compiler: Compiler, counter: Counter) {
  def formatAndCompile(code: String): UIO[String] = ???
}

object Editor {
  val layer: ZLayer[Formatter with Compiler with Counter, Nothing, Editor] =
    ZLayer {
      for {
        formatter <- ZIO.service[Formatter]
        compiler  <- ZIO.service[Compiler]
        counter   <- ZIO.service[Counter]
      } yield new Editor(formatter, compiler, counter) 
    }
}
```

Let's try another example. Assume we have a `ZIO` effect that reads the application config from a file, we can create a layer from that:

```scala mdoc:compile-only
import zio._

case class AppConfig(poolSize: Int)
  
object AppConfig {
  private def loadConfig : Task[AppConfig] = 
    ZIO.attempt(???) // loading config from a file
    
  val layer: TaskLayer[AppConfig] = 
    ZLayer(loadConfig)  // or ZLayer.fromZIO(loadConfig)
} 
```

## Resourceful Constructors

Some components of our applications need to be scoped, meaning they undergo a resource acquisition phase before usage, and a resource release phase after usage (e.g. when the application shuts down). As we stated before, the construction of ZIO layers can be effectful and resourceful, this means they can be acquired and safely released when the services are done being utilized.

The `ZLayer` relies on the powerful `Scope` data type and this makes this process extremely simple. We can lift any scoped `ZIO` to `ZLayer` by providing a scoped resource to the `ZLayer.scoped` constructor:

```scala mdoc:compile-only
import zio._

case class A(a: Int)
object A {
  val layer: ZLayer[Any, Nothing, A] =
    ZLayer.scoped {
      ZIO.acquireRelease(acquire = ZIO.debug("Initializing A") *> ZIO.succeed(A(5)))(
        release = _ => ZIO.debug("Releasing A")
      )
    }
}

object ZIOApp extends ZIOAppDefault {
  val myApp: ZIO[A, Nothing, Int] =
    for {
      a <- ZIO.serviceWith[A](_.a)
    } yield a * a

  def run =
    myApp
      .debug("result")
      .provide(A.layer)
}
```

The output:

```
Initializing A
result: 25
Releasing A
```

We can see that the `A` service is initialized and carefull released when the application is shut down.

Here is another example that uses auto closeable resources:

```scala mdoc:silent:nest
import zio._
import scala.io.BufferedSource

val fileLayer: ZLayer[Any, Throwable, BufferedSource] =
  ZLayer.scoped {
    ZIO.fromAutoCloseable(
      ZIO.attemptBlocking(scala.io.Source.fromFile("file.txt"))
    )
  }
```

Finally, let's see a real-world example of creating a layer from scoped resources. Assume we have the following `UserRepository` service:

```scala mdoc:silent
import zio._
import scala.io.Source._
import java.io.{FileInputStream, FileOutputStream, Closeable}

trait DBConfig
trait Transactor
trait User

def dbConfig: Task[DBConfig] = ZIO.attempt(???)
def initializeDb(config: DBConfig): Task[Unit] = ZIO.attempt(???)
def makeTransactor(config: DBConfig): ZIO[Scope, Throwable, Transactor] = ZIO.attempt(???)

trait UserRepository {
  def save(user: User): Task[Unit]
}

case class UserRepositoryLive(xa: Transactor) extends UserRepository {
  override def save(user: User): Task[Unit] = ZIO.attempt(???)
}
```

Assume we have written a scoped `UserRepository`:

```scala mdoc:silent:nest
def scoped: ZIO[Scope, Throwable, UserRepository] = 
  for {
    cfg <- dbConfig
    _   <- initializeDb(cfg)
    xa  <- makeTransactor(cfg)
  } yield new UserRepositoryLive(xa)
```

We can convert that to `ZLayer` with `ZLayer.scoped`:

```scala mdoc:nest
object UserRepositoyLive {
  val layer : ZLayer[Any, Throwable, UserRepository] =
    ZLayer.scoped(scoped)
}
```

## Asynchronous Constructors

We should avoid using blocking operations inside Scala constructors:

```scala mdoc:compile-only
class ProducerInput

class KafkaProducer(input: ProducerInput) {
  def send(message: String): Unit = ???
}

object KafkaProducer {
  def apply() = {
    // Blocking operation, we should avoid it inside constructors
    val input = doSomeBlockingOperation()
    new KafkaProducer(input)
  }

  private def doSomeBlockingOperation(): ProducerInput = ???
}
```

While with `ZLayer`, we can easily use blocking operations:

```scala mdoc:compile-only
import zio._

class ProducerInput

class KafkaProducer(input: ProducerInput) {
    def send(message: String): Task[Unit] = ???
}

object KafkaProducer {
  val layer =
    ZLayer {
      for {
        input <- ZIO.attemptBlocking(doSomeBlockingOperation())
      } yield (new KafkaProducer(input))
    }

  private def doSomeBlockingOperation(): ProducerInput = ???
}
```

## Parallel Constructors

With `Zlayer` all layers in the dependency graph are executed in parallel:

```scala mdoc:compile-only
import zio._

case class A(a: Int)
object A {
  val layer: ZLayer[Any, Nothing, A] =
    ZLayer.fromZIO {
      for {
        _ <- ZIO.debug("Initializing A")
        _ <- ZIO.sleep(3.seconds)
        _ <- ZIO.debug("Initialized A")
      } yield A(1)
    }
}

case class B(b: Int)
object B {
  val layer: ZLayer[Any, Nothing, B] =
    ZLayer.fromZIO {
      for {
        _ <- ZIO.debug("Initializing B")
        _ <- ZIO.sleep(2.seconds)
        _ <- ZIO.debug("Initialized B")
      } yield B(2)
    }
}

object ZIOApp extends ZIOAppDefault {
  val myApp: ZIO[A with B, Nothing, Int] =
    for {
      a <- ZIO.serviceWith[A](_.a)
      b <- ZIO.serviceWith[B](_.b)
    } yield a + b

  def run =
    myApp
      .debug("result")
      .provide(A.layer, B.layer)
}
```

The output:

```
Initializing A
Initializing B
Initialized B
Initialized A
result: 3
```

