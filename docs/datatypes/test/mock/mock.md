---
id: mock
title: Mock
---

A `Mock[R]` represents a mockable environment `R`. It's a base abstract class for every service we want to mock.

## Creating a Mock Service

In order to create a mock object, we should define an object which implements the `Mock` abstract class in the test sources. To implement the `Mock` need to define _capability tags_ and the _compose layer_:

### Capability Tags

Capabilities are service functionalities that are accessible from the client-side. For example, in the following service the `send` method is a service capability:

```scala mdoc:compile-only
trait UserService {
  def register(username: String, age: Int, email: String): Task[Unit]
}
```

A **capability tag** encodes all information needed to mock the target capability. It is just a value that extends the `zio.test.mock.Capability[R, I, E, A]` type constructor, where:
- `R` is the type of _environment_ the method belongs to
- `I` is the type of _methods input arguments_
- `E` is the type of _error_ it can fail with
- `A` is the type of _return value_ it can produce

The `Capability` type is not publicly available, instead we have to extend `Mock` dependent types `Effect`, `Method`, `Sink` or `Stream`.

We can have 4 types of capabilities inside a service:
1. **`Effect`** — describes an effectful ZIO operation
2. **`Method`** — describes an ordinary scala function
3. **`Sink`** — describes an effectful ZIO Sink
4. **`Stream`** — describes an effectful ZIO Stream

Let's say we have the following service:

```scala mdoc:silent
import zio._
import zio.test.mock._
import zio.stream._

trait ExampleService {
  def exampleEffect(i: Int): Task[String]
  def exampleMethod(i: Int): String
  def exampleSink(a: Int): Sink[Throwable, Int, Nothing, List[Int]]
  def exampleStream(a: Int): Stream[Throwable, String]
}
```

Therefore, the mock service should have the following _capability tags_:

```scala mdoc:compile-only
import zio.test.mock._

object MockExampleService extends Mock[ExampleService] {
  object ExampleEffect extends Effect[Int, Throwable, String]
  object ExampleMethod extends Method[Int, Throwable, String]
  object ExampleSink   extends Sink[Any, String, Int, Nothing, List[Int]]
  object ExampleStream extends Stream[Any, String, Int]
  
  override val compose: URLayer[Proxy, ExampleService] = ???
}
```

In this example, all `ExampleEffect`, `ExampleMethod`, `ExampleSink`, and `ExampleStream` are capability tags. Each of these capability tags encodes all information needed to mock the target capability.

For example, the `ExampleEffect` capability tag encodes the type of _environments_, _arguments_ (inputs), the _error channel_, and also the _success channel_ of the `exampleEffect(i: Int)` method.

```scala mdoc:invisible:reset

```

### Modeling Input Arguments

We model input arguments according to the following scheme:

1. For zero arguments the type is `Unit`

```scala mdoc:compile-only
trait ExampleService {
  def zeroParams: Task[Int]
}
```

So the capability tag of `zeroParams` should be:

```scala mdoc:compile-only
object MockExampleService extends Mock[ExampleService] {
  object ZeroParams extends Effect[Unit, Throwable, Int]
  
  override val compose = ???
}
```

```scala mdoc:invisible:reset

```

2. For one or more arguments, regardless of how many parameter lists, the type is a `TupleN` where `N` is the size of arguments list

> **Note:**
>
> We're using tuples to represent multiple argument methods, which follows with a limit to max 22 arguments, as is Scala itself limited.

For overloaded methods, we nest a list of numbered objects, each representing subsequent overloads.

If the capability has more than one argument, we should encode the argument types in the `Tuple` data type. For example, if we have the following service:

```scala mdoc:silent
trait ExampleService {
  def manyParams(a: Int, b: String, c: Long): Task[Int]
}
```

We should encode that with the following capability tag:

```scala mdoc:compile-only
trait MockExampleService extends Mock[ExampleService] {
  object ManyParams extends Method[(Int, String, Long), Throwable, String]
  
  override val compose = ???
}
```

```scala mdoc:invisible:reset

```

### Polymorphic capabilities

Mocking polymorphic methods is also supported, but the interface must require `zio.Tag` implicit evidence for each type parameter.

```scala mdoc:silent
// main sources

trait PolyExample {
  def polyInput[I: Tag](input: I): Task[String]
  def polyError[E: Tag](input: Int): IO[E, String]
  def polyOutput[A: Tag](input: Int): Task[A]
  def polyAll[I: Tag, E: Tag, A: Tag](input: I): IO[E, A]
}
```

In the test sources we construct partially applied _capability tags_ by extending `Method.Poly` family. The unknown types
must be provided at call site. To produce a final monomorphic `Method` tag we must use the `of` combinator and pass the
missing types.

```scala mdoc:silent
// test sources
object PolyExampleMock extends Mock[PolyExample] {

  object PolyInput  extends Poly.Effect.Input[Throwable, String]
  object PolyError  extends Poly.Effect.Error[Int, String]
  object PolyOutput extends Poly.Effect.Output[Int, Throwable]
  object PolyAll    extends Poly.Effect.InputErrorOutput

  val compose: URLayer[Proxy, PolyExample] =
    ZIO.serviceWithZIO[Proxy] { proxy =>
      withRuntime[Any].map { rts =>
        new PolyExample {
          def polyInput[I: Tag](input: I)                     = proxy(PolyInput.of[I], input)
          def polyError[E: Tag](input: Int)                   = proxy(PolyError.of[E], input)
          def polyOutput[A: Tag](input: Int)                  = proxy(PolyOutput.of[A], input)
          def polyAll[I: Tag, E: Tag, A: Tag](input: I) = proxy(PolyAll.of[I, E, A], input)
        }
      }
    }.toLayer
}
```

Similarly, we use the same `of` combinator to refer to concrete monomorphic call in our test suite when building expectations:

```scala mdoc:silent
import PolyExampleMock._

val exp06 = PolyInput.of[String](equalTo("foo"), value("bar"))
val exp07 = PolyInput.of[Int](equalTo(42), failure(new Exception))
val exp08 = PolyInput.of[Long](equalTo(42L), value("baz"))

val exp09 = PolyAll.of[Int, Throwable, String](equalTo(42), value("foo"))
val exp10 = PolyAll.of[Int, Throwable, String](equalTo(42), failure(new Exception))
```


### Compose layer
In this step, we need to provide a layer in which used to construct the mocked object. To do that, we should obtain the `Proxy` data type from the environment and then implement the service interface (i.e. `EmailService`) by wrapping all capability tags with proxy.

```scala mdoc:silent
// main sources

import zio._
import zio.stream.{ ZSink, ZStream }

trait Example {
  val static                                 : UIO[String]
  def zeroArgs                               : UIO[Int]
  def zeroArgsWithParens()                   : UIO[Long]
  def singleArg(arg1: Int)                   : UIO[String]
  def multiArgs(arg1: Int, arg2: Long)       : UIO[String]
  def multiParamLists(arg1: Int)(arg2: Long) : UIO[String]
  def command(arg1: Int)                     : UIO[Unit]
  def overloaded(arg1: Int)                  : UIO[String]
  def overloaded(arg1: Long)                 : UIO[String]
  def function(arg1: Int)                    : String
  def sink(a: Int)                           : ZSink[Any, String, Int, Int, List[Int]]
  def stream(a: Int)                         : ZStream[Any, String, Int]
}
```

```scala mdoc:silent
// test sources

import zio._
import zio.test.mock._

object ExampleMock extends Mock[Example] {
  object Static             extends Effect[Unit, Nothing, String]
  object ZeroArgs           extends Effect[Unit, Nothing, Int]
  object ZeroArgsWithParens extends Effect[Unit, Nothing, Long]
  object SingleArg          extends Effect[Int, Nothing, String]
  object MultiArgs          extends Effect[(Int, Long), Nothing, String]
  object MultiParamLists    extends Effect[(Int, Long), Nothing, String]
  object Command            extends Effect[Int, Nothing, Unit]
  object Overloaded {
    object _0 extends Effect[Int, Nothing, String]
    object _1 extends Effect[Long, Nothing, String]
  }
  object Function extends Method[Int, Throwable, String]
  object Sink     extends Sink[Any, String, Int, Int, List[Int]]
  object Stream   extends Stream[Any, String, Int]

  val compose: URLayer[Proxy, Example] = ???
}
```

### Defining Compose Layer

Finally, we need to define a _compose layer_ that can create our environment from a `Proxy`. A `Proxy` holds the mock state and serves predefined responses to calls:

```scala mdoc:invisible
def withRuntime[R]: URIO[R, Runtime[R]] = ???
```

```scala mdoc:silent
import zio._
import zio.test.mock._
import ExampleMock._

val compose: URLayer[Proxy, Example] =
  ZIO.serviceWithZIO[Proxy] { proxy =>
    withRuntime[Any].map { rts =>
      new Example {
        val static                                 = proxy(Static)
        def zeroArgs                               = proxy(ZeroArgs)
        def zeroArgsWithParens()                   = proxy(ZeroArgsWithParens)
        def singleArg(arg1: Int)                   = proxy(SingleArg, arg1)
        def multiArgs(arg1: Int, arg2: Long)       = proxy(MultiArgs, arg1, arg2)
        def multiParamLists(arg1: Int)(arg2: Long) = proxy(MultiParamLists, arg1, arg2)
        def command(arg1: Int)                     = proxy(Command, arg1)
        def overloaded(arg1: Int)                  = proxy(Overloaded._0, arg1)
        def overloaded(arg1: Long)                 = proxy(Overloaded._1, arg1)
        def function(arg1: Int)                    = rts.unsafeRunTask(proxy(Function, arg1))
        def sink(a: Int)                           = rts.unsafeRun(proxy(Sink, a).catchAll(error => UIO(ZSink.fail[String](error))))
        def stream(a: Int)                         = rts.unsafeRun(proxy(Stream, a))
      }
    }
  }.toLayer
```

> **Note:** The `withRuntime` helper is defined in `Mock`. It accesses the Runtime via `ZIO.runtime` and if you're on JS platform, it will replace the executor to an unyielding one.

A reference to this layer is passed to _capability tags_, so it can be used to automatically build environment for composed expectations on multiple services.

> **Note:**
>
> For non-effectful capabilities we need to unsafely run the final effect to satisfy the required interface. For `ZSink` we also need to map the error into a failed sink as demonstrated above.

### The Complete Example

```scala mdoc:invisible:reset
trait AccountEvent
```

```scala mdoc:silent
// main sources

import zio._
import zio.test.mock._

trait AccountObserver {
  def processEvent(event: AccountEvent): UIO[Unit]
  def runCommand(): UIO[Unit]
}

object AccountObserver {
  def processEvent(event: AccountEvent) =
    ZIO.serviceWithZIO[AccountObserver](_.processEvent(event))

  def runCommand() =
    ZIO.serviceWithZIO[AccountObserver](_.runCommand())
}

case class AccountObserverLive(console: Console) extends AccountObserver {
  def processEvent(event: AccountEvent): UIO[Unit] =
    for {
      _    <- console.printLine(s"Got $event").orDie
      line <- console.readLine.orDie
      _    <- console.printLine(s"You entered: $line").orDie
    } yield ()

  def runCommand(): UIO[Unit] =
    console.printLine("Done!").orDie
}

object AccountObserverLive {
  val layer = (AccountObserverLive.apply _).toLayer[AccountObserver]
}
```

```scala mdoc:silent
// test sources

object AccountObserverMock extends Mock[AccountObserver] {

  object ProcessEvent extends Effect[AccountEvent, Nothing, Unit]
  object RunCommand   extends Effect[Unit, Nothing, Unit]

  val compose: URLayer[Proxy, AccountObserver] =
    ZIO.service[Proxy].map { proxy =>
      new AccountObserver {
        def processEvent(event: AccountEvent) = proxy(ProcessEvent, event)
        def runCommand(): UIO[Unit]           = proxy(RunCommand)
      }
    }.toLayer
}
```

> **Note:** ZIO provides some useful macros to help you generate repetitive code, see [Scrapping the boilerplate with macros][doc-macros].

## Mocking Collaborators

### Providing Mocked Environment

```scala mdoc:silent
import zio.test._

object AccountObserverSpec extends DefaultRunnableSpec {
  def spec = suite("processEvent")(
    test("calls printLine > readLine > printLine and returns unit") {
      val result = app.provideLayer(mockEnv >>> AccountObserverLive.layer)
      assertM(result)(isUnit)
    }
  )
}
```

### Mocking Unused Collaborators

Often the dependency on a collaborator is only in some branches of the code. To test the correct behaviour of branches without dependencies, we still have to provide it to the environment, but we would like to assert it was never called. With the `Mock.empty` method we can obtain a `ZLayer` with an empty service (no calls expected):

```scala mdoc:silent
object MaybeConsoleSpec extends DefaultRunnableSpec {
  def spec = suite("processEvent")(
    test("expect no call") {
      def maybeConsole(invokeConsole: Boolean) =
        ZIO.when(invokeConsole)(Console.printLine("foo"))

      val maybeTest1 = maybeConsole(false).unit.provideLayer(MockConsole.empty)
      val maybeTest2 = maybeConsole(true).unit.provideLayer(MockConsole.PrintLine(equalTo("foo"), unit))
      assertM(maybeTest1)(isUnit) *> assertM(maybeTest2)(isUnit)
    }
  )
}
```

### Mocking Multiple Collaborators

In some cases we have more than one collaborating service being called. We can create mocks for rich environments and as you enrich the environment by using _capability tags_ from another service, the underlying mocked layer will be updated.

```scala mdoc:silent
import zio.test.mock.MockRandom

val combinedEnv: ULayer[Console with Random] = (
  MockConsole.PrintLine(equalTo("What is your name?"), unit) ++
  MockConsole.ReadLine(value("Mike")) ++
  MockRandom.NextInt(value(42)) ++
  MockConsole.PrintLine(equalTo("Mike, your lucky number today is 42!"), unit)
)

val combinedApp =
  for {
    _    <- Console.printLine("What is your name?")
    name <- Console.readLine.orDie
    num  <- Random.nextInt
    _    <- Console.printLine(s"$name, your lucky number today is $num!")
  } yield ()

val result = combinedApp.provideLayer(combinedEnv)
assertM(result)(isUnit)
```

## More examples

We can find more examples in the `examples` and `test-tests` subproject:

- [MockExampleSpec][link-gh-mock-example-spec]
- [EmptyMockSpec][link-gh-empty-mock-spec]
- [ComposedMockSpec][link-gh-composed-mock-spec]
- [ComposedEmptyMockSpec][link-gh-composed-empty-mock-spec]
- [PolyMockSpec][link-gh-poly-mock-spec]

[doc-contextual-types]: ../../contextual/index.md
[doc-macros]: ../../../howto/howto-macros.md
[link-sls-6.26.1]: https://scala-lang.org/files/archive/spec/2.13/06-expressions.html#value-conversions
[link-test-doubles]: https://martinfowler.com/articles/mocksArentStubs.html
[link-gh-mock-example-spec]: https://github.com/zio/zio/blob/master/examples/shared/src/test/scala/zio/examples/test/MockExampleSpec.scala
[link-gh-empty-mock-spec]: https://github.com/zio/zio/blob/master/test-tests/shared/src/test/scala/zio/test/mock/EmptyMockSpec.scala
[link-gh-composed-mock-spec]: https://github.com/zio/zio/blob/master/test-tests/shared/src/test/scala/zio/test/mock/ComposedMockSpec.scala
[link-gh-composed-empty-mock-spec]: https://github.com/zio/zio/blob/master/test-tests/shared/src/test/scala/zio/test/mock/ComposedEmptyMockSpec.scala
[link-gh-poly-mock-spec]: https://github.com/zio/zio/blob/master/test-tests/shared/src/test/scala/zio/test/mock/PolyMockSpec.scala
