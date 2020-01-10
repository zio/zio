---
id: howto_mock_services
title:  "Mock services"
---

## How to test interactions between services?

Whenever possible, we should strive to make our functions pure, which makes testing such function easy - you just need to assert on the return value.
However in larger applications there is a need for intermediate layers that delegate the work to specialized services.

For example, in a HTTP server the first layer of indirection are so called _routes_, whose job is to match the request and delegate the processing to
downstream layers. Often below there is a second layer of indirection, so called _controllers_, which consist of several business logic units grouped
by their domain. In a RESTful API that would be all operations on a certain model. The _controller_ to perform its job might call on further
specialized services for communicating with the database, sending emails, logging, et cetera.

If the job of the _capability_ is to call on another _capability_, how should we test it?

## Hidden outputs

A pure function is such a function which operates only on its inputs and produces only its output. Command-like methods, by definition are impure, as
their job is to change state of the collaborating object (performing a _side effect_). For example:

```scala mdoc:invisible
import scala.concurrent.ExecutionContext.Implicits.global

trait Event
```

```scala mdoc:silent
import scala.concurrent.Future

def processEvent(event: Event): Future[Unit] = Future(println(s"Got $event"))
```

The signature of this method `Event => Future[Unit]` hints us we're dealing with a command. It returns `Unit` (well, wrapped in future, but it does
not matter here), you can't do anything useful with `Unit` and it does not contain any information. It is the equivalent of returning nothing. It is
also an unreliable return type, as when Scala expects the return type to be `Unit` it will discard whatever value it had (for details see
[Section 6.26.1][link-sls-6.26.1] of the Scala Language Specification), which may shadow the fact that the final value produced (and discarded) was
not the one you expected.

Inside the future there may be happening any side effects. It may open a file, print to console, connect to databases. We simply don't know. Let's have a look how this problem would be solved using ZIO's effect system:

```scala mdoc:invisible:reset
trait Event
```

```scala mdoc:silent
import zio._
import zio.console.Console

def processEvent(event: Event): ZIO[Console, Nothing, Unit] =
  console.putStrLn(s"Got $event")
```

With ZIO, we've regained to ability to reason about the effects called. We know that `processEvent` can only call on _capabilities_ of `Console`, so even though we still have `Unit` as the result, we have narrowed the possible effects space to a few.

> **Note:** this is true assuming the programmer disciplines themselves to only perform effects expressed in the type signature.
> There is no way (at the moment) to enforce this by the compiler. There is some research done in this space, perhaps future programming languages
> will enable us to further constrain side effects.

However, the same method could be implemented as:

```scala mdoc:silent
def processEvent2(event: Event): ZIO[Console, Nothing, Unit] =
  ZIO.unit
```

How can we test it did exactly what we expected it to do?

## Mocking

In this sort of situations we need mock implementations of our collaborator service. As _Martin Fowler_ puts it in his excellent article
[Mocks Aren't Stubs][link-test-doubles]:

> **Mocks** are (...) objects pre-programmed with expectations which form a specification of the calls they are expected to receive.

ZIO Test provides a framework for mocking your modules.

## Creating a mock service

Assuming you are following the [module pattern][doc-use-module-pattern], for an `AccountListener` service we put the _capability tags_ and the access
helpers (within `>` object) in the service companion object:

```scala mdoc:invisible
trait AccountEvent
```

```scala mdoc:silent
import zio.test.mock._

type AccountObserver = Has[AccountObserver.Service]

object AccountObserver {
  trait Service {
    def processEvent(event: AccountEvent): IO[Nothing, Unit]
  }

  object Service {
    object processEvent extends Method[Service, AccountEvent, Unit]
  }

  object > {
    def processEvent(event: AccountEvent) =
      ZIO.accessM[AccountObserver](_.get.processEvent(event))
  }
}
```

A _capability tag_ is just a value which extends the `zio.test.mock.Method[M, I, A]` type constructor, where:
- `M` is the type of module the method applies to
- `I` is the type of methods input arguments
- `A` is the return type of method

We model input arguments according to following scheme:
- for zero arguments the type is `Unit`
- for one or more arguments, regardless in how many parameter lists, the type is a `TupleN` where `N` is the size of arguments list

```scala mdoc:silent
type ExampleService = Has[ExampleService.Service]
object ExampleService {
  trait Service {
    val static                                 : UIO[String]
    def zeroArgs                               : UIO[Int]
    def zeroArgsWithParens()                   : UIO[Long]
    def singleArg(arg1: Int)                   : UIO[String]
    def multiArgs(arg1: Int, arg2: Long)       : UIO[String]
    def multiParamLists(arg1: Int)(arg2: Long) : UIO[String]
    def command(arg1: Int)                     : UIO[Unit]
    def overloaded(arg1: Int)                  : UIO[String]
    def overloaded(arg1: Long)                 : UIO[String]
  }

  object static             extends Method[Service, Unit, String]
  object zeroArgs           extends Method[Service, Unit, Int]
  object zeroArgsWithParens extends Method[Service, Unit, Long]
  object singleArg          extends Method[Service, Int, String]
  object multiArgs          extends Method[Service, (Int, Long), String]
  object multiParamLists    extends Method[Service, (Int, Long), String]
  object command            extends Method[Service, Int, Unit]
  object overloaded {
    object _1 extends Method[Service, Int, String]
    object _2 extends Method[Service, Long, String]
  }
}
```

> **Note:** we're using tuples to represent multiple argument methods, which follows with a limit to max 22 arguments, as is Scala itself limited.

For overloaded methods we simply nest a list of numbered objects, each representing subsequent overloads.

Next, we create the mockable implementation of the service:

```scala mdoc:silent
implicit val mockableAccountObserver: Mockable[AccountObserver.Service] = (mock: Mock) =>
  Has(new AccountObserver.Service {
    def processEvent(event: AccountEvent): UIO[Unit] = mock(AccountObserver.Service.processEvent, event)
  })
```

> **Note:** To make our mockable implementation automatically discovered, we need to place it inside `AccountObserver` module's companion object.

## Scrapping the boilerplate

All of this machinery is repetitive and boring work, prone to simple mistakes. Using the `@accessible` and `@mockable` macros provided by
[zio-macros][link-zio-macros] we get the _capability tags_, _access helper_ and _mockable implementation_ autogenerated for us:

```scala
import zio.macros.annotation.{ accessible, mockable }
import zio.console.Console

@accessible(">")
@mockable
trait AccountObserver {
  def accountObserver: AccountObserver.Service[Any]
}

object AccountObserver {
  trait Service[R] {
    def processEvent(event: AccountEvent): ZIO[R, Nothing, Unit]
  }

  // autogenerated `object Service { ... }`
  // autogenerated `object > extends Service[AccountObserver] { ... }`
  // autogenerated `implicit val mockable: Mockable[AccountObserver] = ...`
}
```

Next we create the live version of the service with the implementation of the capabilities:

``` scala mdoc
import zio.console.Console

val accountObserverLive: ZLayer[Console, Nothing, Has[AccountObserver.Service]] = 
  ZLayer.fromService[Console.Service, Nothing, Has[AccountObserver.Service]] { console =>
    Has(new AccountObserver.Service {
      def processEvent(event: AccountEvent): UIO[Unit] =
        for {
        _    <- console.putStrLn(s"Got $event")
        line <- console.getStrLn.orDie
        _    <- console.putStrLn(s"You entered: $line")
        } yield ()
    })
  }
```

## Provided ZIO services

For each built-in ZIO service you will find their mockable counterparts in `zio.test.mock` package:
- `MockClock` for `zio.clock.Clock`
- `MockConsole` for `zio.console.Console`
- `MockRandom` for `zio.random.Random`
- `MockSystem` for `zio.system.System`

## Setting up expectations

Finally we're all set and can create ad-hoc mock environments with our services.

```scala mdoc:silent
import zio.test._
import zio.test.Assertion._
import zio.test.mock.Expectation._

import MockConsole._

val event = new AccountEvent {}
val app: ZIO[AccountObserver, Nothing, Unit] = AccountObserver.>.processEvent(event)
val mockEnv: ZLayer[Any, Nothing, Console] = (
  (putStrLn(equalTo(s"Got $event")) returns unit) *>
  (getStrLn returns value("42")) *>
  (putStrLn(equalTo("You entered: 42")) returns unit)
)
```

## Providing mocked environment

```scala mdoc
object AccountObserverSpec extends DefaultRunnableSpec {
  def spec = suite("processEvent")(
    testM("calls putStrLn > getStrLn > putStrLn and returns unit") {
      val result = app.provideLayer(mockEnv >>> accountObserverLive)
      assertM(result)(isUnit)
    }
  )
}
```

## Mocking multiple collaborators

In some cases we have more than one collaborating service being called. In such situations we need to build our expectations separately for each
service and then combine them into single environment:

```scala mdoc:silent
import zio.console.Console
import zio.random.Random
import MockConsole._
import MockRandom._

val mockConsole: ZLayer.NoDeps[Nothing, Console] = (
  (putStrLn(equalTo("What is your name?")) returns unit) *>
  (getStrLn returns value("Mike")) *>
  (putStrLn(equalTo("Mike, your lucky number today is 42!")) returns unit)
)

val mockRandom: ZLayer.NoDeps[Nothing, Random] =
  nextInt._1 returns value(42)

val combinedEnv: ZLayer.NoDeps[Nothing, Console with Random] =
  mockConsole ++ mockRandom

val combinedApp =
  for {
    _    <- console.putStrLn("What is your name?")
    name <- console.getStrLn.orDie
    num  <- random.nextInt
    _    <- console.putStrLn(s"$name, your lucky number today is $num!")
  } yield ()

val result = combinedApp.provideLayer(combinedEnv)
assertM(result)(isUnit)
```

[doc-use-module-pattern]: use_module_pattern.md
[link-sls-6.26.1]: https://scala-lang.org/files/archive/spec/2.13/06-expressions.html#value-conversions
[link-test-doubles]: https://martinfowler.com/articles/mocksArentStubs.html
[link-zio-macros]: https://github.com/zio/zio-macros
