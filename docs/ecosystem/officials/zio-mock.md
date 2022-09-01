---
id: zio-mock
title: "ZIO Mock"
---

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-mock" % <version>
```

## The Problem

Whenever possible, we should strive to make our functions pure, which makes testing such function easy. So we just need to assert on the return value. However, in larger applications there is a need for intermediate layers that delegate the work to specialized services.

For example, in an HTTP server, the first layers of indirection are so-called _routes_, whose job is to match the request and delegate the processing to downstream layers. Below this layer, there is often a second layer of indirection, so-called _controllers_, which comprises several business logic units grouped by their domain. In a RESTful API, that would be all operations on a certain model. The _controller_ to perform its job might call on further specialized services for communicating with the database, sending email, logging, etc.

If the job of the _capability_ is to call on another _capability_, how should we test it?

Let's say we have a `Userservice` defined as follows:

```scala
import zio._

trait UserService {
  def register(username: String, age: Int, email: String): IO[String, Unit]
}

object UserService {
  def register(username: String, age: Int, email: String): ZIO[UserService, String, Unit] =
    ZIO.serviceWithZIO(_.register(username, age, email))
}
```

The live implementation of the `UserService` has two collaborators, `EmailService` and `UserRepository`:

```scala
trait EmailService {
  def send(to: String, body: String): IO[String, Unit]
}

case class User(username: String, age: Int, email: String)

trait UserRepository {
  def save(user: User): IO[String, Unit]
}
```

Following is how the live version of `UserService` is implemented:

```scala
case class UserServiceLive(emailService: EmailService, userRepository: UserRepository) extends UserService {
  override def register(username: String, age: Int, email: String): IO[String, Unit] =
    if (age < 18) {
      emailService.send(email, "You are not eligible to register!")
    } else if (username == "admin") {
      ZIO.fail("The admin user is already registered!")
    } else {
      for {
        _ <- userRepository.save(User(username, age, email))
        _ <- emailService.send(email, "Congratulation, you are registered!")
      } yield ()
    }
}

object UserServiceLive {
  val layer: URLayer[EmailService with UserRepository, UserService] =
    (UserServiceLive.apply _).toLayer[UserService]
}
```

A pure function is such a function which operates only on its inputs and produces only its output. Command-like methods, by definition are impure, as their job is to change state of the collaborating object (performing a _side effect_). For example:

The signature of `register` method `(String, Int, String) => IO[String, Unit]` hints us we're dealing with a command. It returns `Unit` (well, wrapped in the `IO`, but it does not matter here). We can't do anything useful with `Unit`, and it does not contain any information. It is the equivalent of returning nothing.

It is also an unreliable return type, as when Scala expects the return type to be `Unit` it will discard whatever value it had (for details see [Section 6.26.1][link-sls-6.26.1] of the Scala Language Specification), which may shadow the fact that the final value produced (and discarded) was not the one we expected.

Inside the `IO` there may be a description of any side effects. It may open a file, print to the console, or connect to databases. **So the problem is "How is it possible to test a service along with its collaborators"?**

In this example, the `register` method has a service call to its collaborators, `UserRepository` and `EmailService`.  So, how can we test the live version of `UserService.register` while it has some side effects in communicating with its collaborators?

Mockists would probably claim that testing how collaborators are called during the test process allows us to test the UserService. Let's move on to the next section and see the mockists' solution in greater detail.

## The Solution

In this sort of situations we need mock implementations of our _collaborator service_. As _Martin Fowler_ puts it in his excellent article [Mocks Aren't Stubs][link-test-doubles]:

> **Mocks** are (...) objects pre-programmed with expectations which form a specification of the calls they are expected to receive.

So to test the `register` function, we can mock the behavior of its two collaborators. So instead of using production objects, we use pre-programmed mock versions of these two collaborators with some expectations. In this way, in each test case, we expect these collaborators will be called with expected inputs.

In this example, we can define these three test cases:
1. If we register a user with an age of less than 18, we expect that the `save` method of `UserRepository` shouldn't be called. Additionally, we expect that the `send` method of `EmailService` will be called with the following content: "You are not eligible to register."
2. If we register a user with a username of "admin", we expect that both `UserRepository` and `EmailService` should not be called. Instead, we expect that the `register` call will be failed with a proper failure value: "The admin user is already registered!"
3. Otherwise, we expect that the `save` method of `UserRepository` will be called with the corresponding `User` object, and the `send` method of `EmailService` will be called with this content: "Congratulation, you are registered!".

ZIO Test provides a framework for mocking our modules. In the next section, we are going to test `UserService` by mocking its collaborators.

## Mocking Collaborators

In the previous section, we learned we can test the `UserService` by mocking its collaborators. Let's see how we can mock the `EmailService` and also the `UserRepository`.

We should create a mock object by extending `Mock[EmailService]` (`zio.mock.Mock`). Then we need to define the following objects:
1. **Capability tags** — They are value objects which extend one of the `Capability[R, I, E, A]` data types, such as `Effect`, `Method`, `Sink`, or `Stream`. For each of the service capabilities, we need to create an object extending one of these data types. They encode the type of _environments_, _arguments_ (inputs), the _error channel_, and also the _success channel_ of each capability of the service.

For example, to encode the `send` capability of `EmailService` we need to extend the `Effect` capability as bellow:

  ```scala
  object Send extends Effect[(String, String), String, Unit]
  ```
2. **Compose layer** — In this step, we need to provide a layer in which used to construct the mocked object. In order to do that, we should obtain the `Proxy` data type from the environment and then implement the service interface (i.e. `EmailService`) by wrapping all capability tags with proxy.


Let's see how we can mock the `EmailService`:

```scala
// Test Sources
import zio.mock._

object MockEmailService extends Mock[EmailService] {
  object Send extends Effect[(String, String), String, Unit]

  val compose: URLayer[Proxy, EmailService] =
    ZIO
      .service[Proxy]
      .map { proxy =>
        new EmailService {
          override def send(to: String, body: String): IO[String, Unit] =
            proxy(Send, to, body)
        }
      }
      .toLayer
}
```

And, here is the mock version of the `UserRepository`:

```scala
import zio._
import zio.mock._

object MockUserRepository extends Mock[UserRepository] {
  object Save extends Effect[User, String, Unit]

  val compose: URLayer[Proxy, UserRepository] =
    ZIO
      .service[Proxy]
      .map { proxy =>
        new UserRepository {
          override def save(user: User): IO[String, Unit] =
            proxy(Save, user)
        }
      }
      .toLayer
}
```

## Testing the Service

After writing the mock version of collaborators, now we can use their _capability tags_ to convert them to the `Expectation`, and finally create the mock layer of the service.

For example, we can create an expectation from the `Send` capability tag of the `MockEmailService`:

```scala
import zio.test._

val sendEmailExpectation: Expectation[EmailService] =
  MockEmailService.Send(
    assertion = Assertion.equalTo(("john@doe", "You are not eligible to register!")),
    result = Expectation.unit
  )
```

The `sendEmailExpectation` is an expectation, which requires a call to `send` method with `("john@doe", "You are not eligible to register!")` arguments. If this service will be called, the returned value would be `unit`.

There is an extension method called `Expectation#toLayer` which implicitly converts an expectation to the `ZLayer` environment:

```scala
import zio.test._

val mockEmailService: ULayer[EmailService] =
  MockEmailService.Send(
    assertion = Assertion.equalTo(("john@doe", "You are not eligible to register!")),
    result = Expectation.unit
  ).toLayer
```

So we do not require to convert them to `ZLayer` explicitly. It will convert them whenever required.

1. Now let's test the first scenario discussed in the [solution](#the-solution) section:

> If we register a user with an age of less than 18, we expect that the `save` method of `UserRepository` shouldn't be called. Additionally, we expect that the `send` method of `EmailService` will be called with the following content: "You are not eligible to register."

```scala
import zio.test._

test("non-adult registration") {
  val sut              = UserService.register("john", 15, "john@doe")
  val liveUserService  = UserServiceLive.layer
  val mockUserRepo     = MockUserRepository.empty
  val mockEmailService = MockEmailService.Send(
    assertion = Assertion.equalTo(("john@doe", "You are not eligible to register!")),
    result = Expectation.unit
  )

  for {
    _ <- sut.provide(liveUserService, mockUserRepo, mockEmailService)
  } yield assertTrue(true)
}
```

We used `MockUserRepository.empty` since we expect no call to the `UserRepository` service.

2. The second scenario is:

> If we register a user with a username of "admin", we expect that both `UserRepository` and `EmailService` should not be called. Instead, we expect that the `register` call will be failed with a proper failure value: "The admin user is already registered!"

```scala
import zio.test._

test("user cannot register pre-defined admin user") {
  val sut = UserService.register("admin", 30, "admin@doe")

  for {
    result <- sut.provide(
      UserServiceLive.layer,
      MockEmailService.empty,
      MockUserRepository.empty
    ).exit
  } yield assertTrue(
    result match {
      case Exit.Failure(cause)
        if cause.contains(
          Cause.fail("The admin user is already registered!")
        ) => true
      case _ => false
    }
  )
}
```

3. Finally, we have to check the _happy path_ scenario:

> We expect that the `save` method of `UserRepository` will be called with the corresponding `User` object, and the `send` method of `EmailService` will be called with this content: "Congratulation, you are registered!".


```scala
import zio.test._

test("a valid user can register to the user service") {
  val sut              = UserService.register("jane", 25, "jane@doe")
  val liveUserService  = UserServiceLive.layer
  val mockUserRepo     = MockUserRepository.Save(
    Assertion.equalTo(User("jane", 25, "jane@doe")),
    Expectation.unit
  )
  val mockEmailService = MockEmailService.Send(
    assertion = Assertion.equalTo(("jane@doe", "Congratulation, you are registered!")),
    result = Expectation.unit
  )

  for {
    _ <- sut.provide(liveUserService, mockUserRepo, mockEmailService)
  } yield assertTrue(true)
}
```

## Built-in Mock Services

For each built-in ZIO service, we will find their mockable counterparts in `zio.mock` package:
- [`MockClock`] for `zio.Clock`
- [`MockConsole`] for `zio.Console`
- [`MockRandom`] for `zio.Random`
- [`MockSystem`] for `zio.System`

A `Mock[R]` represents a mockable environment `R`. It's a base abstract class for every service we want to mock.

## Creating a Mock Service

In order to create a mock object, we should define an object which implements the `Mock` abstract class in the test sources. To implement the `Mock` need to define _capability tags_ and the _compose layer_:

## Encoding Service Capabilities

Capabilities are service functionalities that are accessible from the client-side. For example, in the following service the `send` method is a service capability:

```scala
import zio._

trait UserService {
  def register(username: String, age: Int, email: String): Task[Unit]
}
```

A **capability tag** encodes all information needed to mock the target capability. It is just a value that extends the `zio.mock.Capability[R, I, E, A]` type constructor, where:
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

```scala
import zio._
import zio.mock._
import zio.stream._

trait ExampleService {
  def exampleEffect(i: Int): Task[String]
  def exampleMethod(i: Int): String
  def exampleSink(a: Int): Sink[Throwable, Int, Nothing, List[Int]]
  def exampleStream(a: Int): Stream[Throwable, String]
}
```

Therefore, the mock service should have the following _capability tags_:

```scala
import zio.mock._

object MockExampleService extends Mock[ExampleService] {
  object ExampleEffect extends Effect[Int, Throwable, String]
  object ExampleMethod extends Method[Int, Throwable, String]
  object ExampleSink   extends Sink[Any, Throwable, Int, Nothing, List[Int]]
  object ExampleStream extends Stream[Int, Throwable, String]
  
  override val compose: URLayer[Proxy, ExampleService] = ???
}
```

In this example, all `ExampleEffect`, `ExampleMethod`, `ExampleSink`, and `ExampleStream` are capability tags. Each of these capability tags encodes all information needed to mock the target capability.

For example, the `ExampleEffect` capability tag encodes the type of _environments_, _arguments_ (inputs), the _error channel_, and also the _success channel_ of the `exampleEffect(i: Int)` method.

```scala

```

We encode service capabilities according to the following scheme:

### Encoding Zero Argument Capability

For zero arguments the type is `Unit`

```scala
import zio._

trait ZeroParamService {
  def zeroParams: Task[Int]
}
```

So the capability tag of `zeroParams` should be:

```scala
import zio.mock._

object MockZeroParamService extends Mock[ZeroParamService] {
  object ZeroParams extends Effect[Unit, Throwable, Int]
  
  override val compose = ???
}
```

```scala

```

### Encoding Multiple Arguments Capability

For one or more arguments, regardless of how many parameter lists, the type is a `TupleN` where `N` is the size of arguments list

> **Note:**
>
> We're using tuples to represent multiple argument methods, which follows with a limit to max 22 arguments, as is Scala itself limited.

If the capability has more than one argument, we should encode the argument types in the `Tuple` data type. For example, if we have the following service:

```scala
import zio._

trait ManyParamsService {
  def manyParams(a: Int, b: String, c: Long): Task[Int]
  def manyParamLists(a: Int, b: String)(c: Long): Task[Int]
}
```

We should encode that with the following capability tag:

```scala
import zio.mock._

trait MockExampleService extends Mock[ManyParamsService] {
  object ManyParams     extends Method[(Int, String, Long), Throwable, String]
  object ManyParamLists extends Method[(Int, String, Long), Throwable, String]
  
  override val compose = ???
}
```

### Encoding Overloaded Capabilities

For overloaded methods, we nest a list of numbered objects, each representing subsequent overloads:

```scala
// Main sources

import zio._
import zio.stream.{ ZSink, ZStream }

trait OverloadedService {
  def overloaded(arg1: Int)                  : UIO[String]
  def overloaded(arg1: Long)                 : UIO[String]
}
```

We encode both overloaded capabilities by using numbered objects inside a nested object:

```scala
// Test sources

import zio._
import zio.mock._

object MockOervloadedService extends Mock[OverloadedService] {
  object Overloaded {
    object _0 extends Effect[Int, Nothing, String]
    object _1 extends Effect[Long, Nothing, String]
  }

  val compose: URLayer[Proxy, OverloadedService] = ???
}
```

```scala

```

### Encoding Polymorphic Capabilities

Mocking polymorphic methods is also supported, but the interface must require `zio.Tag` implicit evidence for each type parameter:

```scala
// main sources
import zio._

trait PolyService {
  def polyInput[I: Tag](input: I): Task[String]
  def polyError[E: Tag](input: Int): IO[E, String]
  def polyOutput[A: Tag](input: Int): Task[A]
  def polyAll[I: Tag, E: Tag, A: Tag](input: I): IO[E, A]
}
```

In the test sources we construct partially applied _capability tags_ by extending `Method.Poly` family. The unknown types must be provided at call site. To produce a final monomorphic `Method` tag we must use the `of` combinator and pass the missing types:

```scala
// test sources
import zio.mock._

object MockPolyService extends Mock[PolyService] {

  object PolyInput  extends Poly.Effect.Input[Throwable, String]
  object PolyError  extends Poly.Effect.Error[Int, String]
  object PolyOutput extends Poly.Effect.Output[Int, Throwable]
  object PolyAll    extends Poly.Effect.InputErrorOutput

  // We will learn about the compose layer in the next section
  val compose: URLayer[Proxy, PolyService] =
    ZIO.serviceWithZIO[Proxy] { proxy =>
      withRuntime[Any].map { rts =>
        new PolyService {
          def polyInput[I: Tag](input: I)               = proxy(PolyInput.of[I], input)
          def polyError[E: Tag](input: Int)             = proxy(PolyError.of[E], input)
          def polyOutput[A: Tag](input: Int)            = proxy(PolyOutput.of[A], input)
          def polyAll[I: Tag, E: Tag, A: Tag](input: I) = proxy(PolyAll.of[I, E, A], input)
        }
      }
    }.toLayer
}
```

Similarly, we use the same `of` combinator to refer to concrete monomorphic call in our test suite when building expectations:

```scala
import zio.test._
import MockPolyService._

val exp06 = PolyInput.of[String](
  Assertion.equalTo("foo"),
  Expectation.value("bar")
)

val exp07 = PolyInput.of[Int](
  Assertion.equalTo(42),
  Expectation.failure(new Exception)
)

val exp08 = PolyInput.of[Long](
  Assertion.equalTo(42L),
  Expectation.value("baz")
)

val exp09 = PolyAll.of[Int, Throwable, String](
  Assertion.equalTo(42),
  Expectation.value("foo")
)

val exp10 = PolyAll.of[Int, Throwable, String](
  Assertion.equalTo(42),
  Expectation.failure(new Exception)
)
```


## Defining a Layer for the Mocked Service

Finally, we need to define a _compose layer_ that can create our environment from a `Proxy`. A `Proxy` holds the mock state and serves predefined responses to calls.

So again, assume we have the following service:

```scala
import zio._
import zio.mock._

trait ExampleService {
  def exampleEffect(i: Int): Task[String]
  def exampleMethod(i: Int): String
  def exampleSink(a: Int): stream.Sink[Throwable, Int, Nothing, List[Int]]
  def exampleStream(a: Int): stream.Stream[Throwable, String]
}
```

In this step, we need to provide a layer in which used to construct the mocked object. To do that, we should obtain the `Proxy` data type from the environment and then implement the service interface by wrapping all capability tags with proxy:

```scala
import zio.mock._

object MockExampleService extends Mock[ExampleService] {
  object ExampleEffect extends Effect[Int, Throwable, String]
  object ExampleMethod extends Method[Int, Throwable, String]
  object ExampleSink extends Sink[Any, Throwable, Int, Nothing, List[Int]]
  object ExampleStream extends Stream[Int, Throwable, String]

  override val compose: URLayer[Proxy, ExampleService] =
    ZIO.serviceWithZIO[Proxy] { proxy =>
      withRuntime[Any].map { rts =>
        new ExampleService {
          override def exampleEffect(i: Int): Task[String] =
            proxy(ExampleEffect, i)

          override def exampleMethod(i: Int): String =
            rts.unsafeRunTask(proxy(ExampleMethod, i))

          override def exampleSink(a: Int): stream.Sink[Throwable, Int, Nothing, List[Int]] =
            rts.unsafeRun(proxy(ExampleSink, a))

          override def exampleStream(a: Int): stream.Stream[Throwable, String] =
            rts.unsafeRun(proxy(ExampleStream, a))
        }
      }
    }.toLayer
}
```

> **Note:** The `withRuntime` helper is defined in `Mock`. It accesses the Runtime via `ZIO.runtime` and if you're on JS platform, it will replace the executor to an unyielding one.

A reference to this layer is passed to _capability tags_, so it can be used to automatically build environment for composed expectations on multiple services.

```scala

```

## The Complete Example

```scala
trait AccountEvent
```

```scala
// main sources

import zio._
import zio.mock._

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

```scala
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

## More examples

We can find more examples in the `examples` and `test-tests` subproject:

- [MockExampleSpec][link-gh-mock-example-spec]
- [EmptyMockSpec][link-gh-empty-mock-spec]
- [ComposedMockSpec][link-gh-composed-mock-spec]
- [ComposedEmptyMockSpec][link-gh-composed-empty-mock-spec]
- [PolyMockSpec][link-gh-poly-mock-spec]

[doc-contextual-types]: ../../reference/contextual/index.md
[link-sls-6.26.1]: https://scala-lang.org/files/archive/spec/2.13/06-expressions.html#value-conversions
[link-test-doubles]: https://martinfowler.com/articles/mocksArentStubs.html
[link-gh-mock-example-spec]: https://github.com/zio/zio/blob/master/examples/shared/src/test/scala/zio/examples/test/MockExampleSpec.scala
[link-gh-empty-mock-spec]: https://github.com/zio/zio/blob/master/test-tests/shared/src/test/scala/zio/test/mock/EmptyMockSpec.scala
[link-gh-composed-mock-spec]: https://github.com/zio/zio/blob/master/test-tests/shared/src/test/scala/zio/test/mock/ComposedMockSpec.scala
[link-gh-composed-empty-mock-spec]: https://github.com/zio/zio/blob/master/test-tests/shared/src/test/scala/zio/test/mock/ComposedEmptyMockSpec.scala
[link-gh-poly-mock-spec]: https://github.com/zio/zio/blob/master/test-tests/shared/src/test/scala/zio/test/mock/PolyMockSpec.scala

An `Expectation[R]` is an immutable tree structure that represents expectations on environment `R`.

## Defining Expectations

ZIO Test has a variety of expectations, such as `value`, `unit`, `failure`, and `never`. In this section we are going to learn each of these expectations and their variant, by mocking the `UserService` service. So let's assume we have the following service:

```scala
import zio._
import zio.mock._
import zio.test._

case class User(id: String, name: String)

trait UserService {
  def insert(user: User): IO[String, Unit]
  def remove(id: String): IO[String, Unit]
  def totalUsers: IO[String, Int]
  def recentUsers(n: Int): IO[String, List[User]]
  def removeAll: IO[String, Unit]
}

object UserService {
  def totalUsers: ZIO[UserService, String, Int] =
    ZIO.serviceWithZIO(_.totalUsers)

  def insert(user: User): ZIO[UserService, String, Unit] =
    ZIO.serviceWithZIO(_.insert(user))

  def recentUsers(n: Int): ZIO[UserService, String, List[User]] =
    ZIO.serviceWithZIO(_.recentUsers(n))

  def remove(id: String): ZIO[UserService, String, Unit] =
    ZIO.serviceWithZIO(_.remove(id))
   
  def removeAll: ZIO[UserService, String, Unit] = 
    ZIO.serviceWithZIO(_.removeAll) 
}
```

We can write the mock version of this class as below:

```scala

object MockUserService extends Mock[UserService] {

  object Insert      extends Effect[User, String, Unit]
  object Remove      extends Effect[String, String, Unit]
  object RecentUsers extends Effect[Int, String, List[User]]
  object TotalUsers  extends Effect[Unit, String, Int]
  object RemoveAll   extends Effect[Unit, String, Unit]

  val compose: URLayer[mock.Proxy, UserService] =
    ZIO.service[mock.Proxy]
      .map { proxy =>
        new UserService {
          override def insert(user: User):  IO[String, Unit]       = proxy(Insert, user)
          override def remove(id: String):  IO[String, Unit]       = proxy(Remove, id)
          override def recentUsers(n: Int): IO[String, List[User]] = proxy(RecentUsers, n)
          override def totalUsers:          IO[String, Int]        = proxy(TotalUsers)
          override def removeAll:           IO[String, Unit]       = proxy(RemoveAll)
        }
      }.toLayer
      
}
```

To create expectations we use the previously defined _capability tags_.

1. For methods that take input, the first argument will be an assertion on input, and the second the predefined result.

```scala
import zio.mock._
import zio.test._

val exp01 = MockUserService.RecentUsers( // capability to build an expectation for
  Assertion.equalTo(5), // assertion of the expected input argument
  Expectation.value(List(User("1", "Jane Doe"), User("2", "John Doe"))) // result, that will be returned
)
```

2. For methods that take no input, we only define the expected output:

```scala
val exp02 = MockUserService.TotalUsers(Expectation.value(42))
```

3. For methods that may return `Unit`, we may skip the predefined result (it will default to successful value) or use `unit` helper:

```scala
val exp03 = MockUserService.Remove(
  Assertion.equalTo("1"),
  Expectation.unit
)
```

4. For methods that may return `Unit` and take no input we can skip both:

```scala
val exp04 = MockUserService.RemoveAll()
```

### Providing Mocked Environment

Each expectation can be taught of a mocked environment. They can be converted to a `ZLayer` implicitly. Therefore, we can compose them together and provide them to the environment of the SUT (System Under Test).

```scala
import zio.test._

import zio._
import zio.mock._
import zio.test.{test, _}

test("expecting simple value on call to nextInt") {
  val sut     = Random.nextInt
  val mockEnv = MockRandom.NextInt(Expectation.value(5))
  for {
    total <- sut.provideLayer(mockEnv)
  } yield assertTrue(total == 5)
} 
```

### Mocking Unused Collaborators

Often the dependency on a collaborator is only in some branches of the code. To test the correct behaviour of branches without dependencies, we still have to provide it to the environment, but we would like to assert it was never called. With the `Mock.empty` method we can obtain a `ZLayer` with an empty service (no calls expected):

```scala
import zio.mock._
import zio.test._

object MaybeConsoleSpec extends MockSpecDefault {
  def spec = suite("processEvent")(
    test("expect no call") {
      def maybeConsole(invokeConsole: Boolean) =
        ZIO.when(invokeConsole)(Console.printLine("foo"))

      val sut1     = maybeConsole(false).unit
      val mockEnv1 = MockConsole.empty

      val sut2     = maybeConsole(true).unit
      val mockEnv2 = MockConsole.PrintLine(
        Assertion.equalTo("foo"),
        Expectation.unit
      )

      for {
        _ <- sut1.provideLayer(mockEnv1)
        _ <- sut2.provideLayer(mockEnv2)
      } yield assertTrue(true)
    }
  )
}
```

### Mocking Multiple Collaborators

In some cases we have more than one collaborating service being called. We can create mocks for rich environments and as you enrich the environment by using _capability tags_ from another service, the underlying mocked layer will be updated.

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("mocking multiple collaborators") {
  val sut =
    for {
      _ <- Console.printLine("What is your name?")
      name <- Console.readLine.orDie
      num <- Random.nextInt
      _ <- Console.printLine(s"$name, your lucky number today is $num!")
    } yield ()

  val mockEnv: ULayer[Console with Random] = MockConsole.PrintLine(
    Assertion.equalTo("What is your name?"),
    Expectation.unit
  ) ++ MockConsole.ReadLine(Expectation.value("Mike")) ++
    MockRandom.NextInt(Expectation.value(42)) ++
    MockConsole.PrintLine(
      Assertion.equalTo("Mike, your lucky number today is 42!"),
      Expectation.unit
    )
    
  for {
    _ <- sut.provideLayer(mockEnv)
  } yield assertTrue(true)
} 
```

## Expectations

In the most robust example, the result can be either a successful value or a failure. In this section we are going to introduce all these cases, by using the proper expectation from `zio.mock.Expectation` companion object:

### `value`

Expecting a simple value:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("expecting simple value") {
  val sut     = UserService.totalUsers
  val mockEnv = MockUserService.TotalUsers(Expectation.value(14))
  for {
    total <- sut.provideLayer(mockEnv)
  } yield assertTrue(total == 14)
} 
```

### `valueF`

Expecting a value based on input arguments:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("an expectation based on input arguments") {
  val sut     = UserService.recentUsers(3)
  val mockEnv = MockUserService.RecentUsers(
    Assertion.isPositive,
    Expectation.valueF(n =>
      (1 to n).map(id => User(id.toString, "name")).toList
    )
  )

  for {
    users <- sut.provideLayer(mockEnv)
  } yield assertTrue(users.map(_.id) == List("1", "2", "3"))
}
```

### `valueZIO`

Expecting a value based on the input arguments and also the result of an effectful operation:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("effectful expectation") {
  val sut     = UserService.recentUsers(3)
  val mockEnv = MockUserService.RecentUsers(
    Assertion.isPositive,
    Expectation.valueZIO(n =>
      ZIO.foreach(1 to n) { n =>
        Random
          .nextUUID
          .map(id => User(id.toString, s"name-$n"))
          .provideLayer(Random.live)
      }.map(_.toList)
    )
  )

  for {
    users <- sut.provideLayer(mockEnv)
  } yield assertTrue(List("name-1", "name-2", "name-3") == users.map(_.name))
}
```

### `unit`

Expecting simple unit value:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("expecting unit") {
  val sut     = UserService.remove("1")
  val mockEnv = MockUserService.Remove(
    Assertion.isNonEmptyString,
    Expectation.unit
  )
  
  for {
    res <- sut.provideLayer(mockEnv).exit
  } yield assertTrue(
    res match {
      case Exit.Success(()) => true
      case _ => false
    }
  )
}
```

### `failure`

Expecting a failure:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("failure expectation") {
  val sut = UserService.totalUsers
  val mockEnv = MockUserService.TotalUsers(Expectation.failure("connection failed"))
  
  for {
    total <- sut.provideLayer(mockEnv).exit
  } yield assertTrue(
    total match {
      case Exit.Success(_) =>
        false
      case Exit.Failure(cause) =>
        cause.contains(Cause.fail("connection failed"))
    }
  )
}
```

There are also `failureF` and `failureZIO` variants like what we described for `value` expectation.

### `never`

This expectation simulates a never-ending loop:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("never ending expectation") {
  val sut     = UserService.totalUsers
  val mockEnv = MockUserService.TotalUsers(
    Expectation.never
  )

  for {
    r <- Live.live(
      sut.provideLayer(mockEnv).timeout(500.millis)
    )
  } yield assertTrue(r.isEmpty)
}
```

## Composing Expectations

We can combine our expectation to build complex scenarios using combinators defined in `zio.mock.Expectation`.

### `and`

The `and` (alias `&&`) operator composes two expectations, producing a new expectation to **satisfy both in any order**:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("satisfy both expectations with a logical `and` operator") {
  val sut     = UserService.recentUsers(5) *> UserService.totalUsers
  val mockEnv = MockUserService.TotalUsers(Expectation.value(1)).and(
    MockUserService.RecentUsers(
      Assertion.isPositive,
      Expectation.value(List(User("1", "user"))))
  )

  for {
    total <- sut.provideLayer(mockEnv)
  } yield assertTrue(total == 1)
}
```

### `or`

The `or` (alias `||`) operator composes two expectations, producing a new expectation to **satisfy only one of them**:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("satisfy one of expectations with a logical `or` operator") {
  val sut     = UserService.totalUsers
  val mockEnv = MockUserService.TotalUsers(Expectation.value(1)).or(
    MockUserService.RecentUsers(
      Assertion.isPositive,
      Expectation.value(List(User("1", "user"))))
  )

  for {
    total <- sut.provideLayer(mockEnv)
  } yield assertTrue(total == 1)
}
```

### `andThen`

The `andThen` (alias `++`) operator composes two expectations, producing a new expectation to **satisfy both sequentially**:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("satisfy sequence of two expectations with `andThen` operator") {
  val sut     = UserService.recentUsers(5) *> UserService.totalUsers
  val mockEnv = MockUserService.RecentUsers(
    Assertion.isPositive,
    Expectation.value(List(User("1", "user")))
  ) andThen MockUserService.TotalUsers(Expectation.value(1))

  for {
    total <- sut.provideLayer(mockEnv)
  } yield assertTrue(total == 1)
}
```

In the example above, changing the SUT to `UserService.totalUsers *> UserService.remove(1)` will fail the test.

### Exact Repetition

1. **`exactly`** — Produces a new expectation to satisfy itself exactly the given number of times:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("satisfying exact repetition of a method call") {
  val sut     = ZIO.foreach(List("1", "2", "3", "4"))(id => UserService.remove(id))
  val mockEnv = MockUserService.Remove(
    Assertion.isNonEmptyString
  ).exactly(4)

  for {
    _ <- sut.provideLayer(mockEnv)
  } yield assertTrue(true)
}
```

2. **`twice`** and **`thrice`** — Aliases for `exactly(2)` and `exactly(3)`.

### Range of Repetitions

1. **`Expectation#repeats(range: Range)`** — Repeats this expectation within given bounds, producing a new expectation to **satisfy itself sequentially given number of times**:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("expect repeated calls") {
  val sut     = Random.nextInt *> Random.nextInt
  val mockEnv = MockRandom.NextInt(Expectation.value(42)).repeats(2 to 4)

  for {
    _ <- sut.provideLayer(mockEnv)
  } yield assertTrue(true)
}
```

In the example above, if we repeat `nextInt` less than 2 or over 4 times, the test will fail.

Another note on repetitions is that, if we compose expectations with `andThen`/`++`, once another repetition starts executing, it must be completed in order to satisfy the composite expectation. For example `(A ++ B).repeats(1, 2)` will be satisfied by either `A->B` (one repetition) or `A->B->A->B` (two repetitions), but will fail on `A->B->A` (incomplete second repetition):

```scala
import zio._
import zio.mock._
import zio.mock.Expectation._
import zio.test.{test, _}

test("if another repetition starts executing, it must be completed") {
  val sut     = Random.nextInt *> Random.nextBoolean *> Random.nextInt
  val mockEnv = (MockRandom.NextInt(value(42)) ++ MockRandom.NextBoolean(value(true)))
    .repeats(1 to 2)
  for {
    _ <- sut.provideLayer(mockEnv)
  } yield assertTrue(true)
} @@ TestAspect.failing
```

2. The **`atLeast(min: Int)`**, **`atMost(max: Int)`**, and **`optional`** expectations are other variants for `repeats` expectation.
  - The `atLeast` operator is a lower-bounded variant of `repeated`, produces a new expectation to satisfy **itself sequentially at least given number of times**.
  - The `atMost` operator is an upper-bounded variant of `repeated`, produces a new expectation to satisfy **itself sequentially at most given number of times**.
  - The `optional` operator is an alias for `atMost(1)`, produces a new expectation to satisfy **itself at most once**.

`MockClock` is a built-in mock version of the [`Clock`] service. 

Here is an example of mocking `Clock.nanoTime` capability:

```scala
import zio._
import zio.mock._
import zio.mock.Expectation._
import zio.test.{test, _}

test("calling mocked nanoTime should return expected time") {
  val app = Clock.nanoTime
  val env = MockClock.NanoTime(value(1000L))
  val out = app.provideLayer(env)
  out.map(r => assertTrue(r == 1000L))
}
```

`MockConsole` is a built-in mock version of the [`Console`] service.

Here is an example of mocking `Console.readLine` capability:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("calling mocked readline should return expected value") {
  for {
    line <- Console.readLine.provideLayer(
      MockConsole.ReadLine(Expectation.value("foo"))
    )
  } yield assertTrue(line == "foo")
}
```

`MockRandom` is a built-in mock version of the [`Random`] service. It mocks all the random service capabilities.

Here's how we can mock the `MockRandom.nextIntBounded` capability:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("expect call with input satisfying assertion and transforming it into output") {
  for {
    out <- Random.nextIntBounded(1).provideLayer(
      MockRandom.NextIntBounded(
        Assertion.equalTo(1),
        Expectation.valueF(_ + 41)
      )
    )
  } yield assertTrue(out == 42)
}
```

`MockSystem` is a built-in mock version of the [`System`] service. It mocks all the system service capabilities.

Here's how we can mock the `MockSystem.property` capability:

```scala
import zio._
import zio.mock._
import zio.test.{test, _}

test("calling mocked property should return expected property") {
  for {
    property <- System.property("java.vm.name").provideLayer(
      MockSystem.Property(
        Assertion.equalTo("java.vm.name"),
        Expectation.value(Some("OpenJDK 64-Bit Server VM"))
      )
    )
  } yield assertTrue(property.get.contains("OpenJDK"))
}
```
