---
id: index
title: Introduction to ZIO Mock
sidebar_label: Introduction
---

## The Problem

Whenever possible, we should strive to make our functions pure, which makes testing such function easy. So we just need to assert on the return value. However, in larger applications there is a need for intermediate layers that delegate the work to specialized services.

For example, in an HTTP server, the first layers of indirection are so-called _routes_, whose job is to match the request and delegate the processing to downstream layers. Below this layer, there is often a second layer of indirection, so-called _controllers_, which comprises several business logic units grouped by their domain. In a RESTful API, that would be all operations on a certain model. The _controller_ to perform its job might call on further specialized services for communicating with the database, sending email, logging, etc.

If the job of the _capability_ is to call on another _capability_, how should we test it?

Let's say we have a `Userservice` defined as follows:

```scala mdoc:silent
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

```scala mdoc:silent
trait EmailService {
  def send(to: String, body: String): IO[String, Unit]
}

case class User(username: String, age: Int, email: String)

trait UserRepository {
  def save(user: User): IO[String, Unit]
}
```

Following is how the live version of `UserService` is implemented:

```scala mdoc:silent
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

```scala mdoc:silent
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

```scala mdoc:silent
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

```scala mdoc:compile-only
import zio.test._

val sendEmailExpectation: Expectation[EmailService] =
  MockEmailService.Send(
    assertion = Assertion.equalTo(("john@doe", "You are not eligible to register!")),
    result = Expectation.unit
  )
```

The `sendEmailExpectation` is an expectation, which requires a call to `send` method with `("john@doe", "You are not eligible to register!")` arguments. If this service will be called, the returned value would be `unit`.

There is an extension method called `Expectation#toLayer` which implicitly converts an expectation to the `ZLayer` environment:

```scala mdoc:compile-only
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

```scala mdoc:compile-only
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

```scala mdoc:compile-only
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


```scala mdoc:compile-only
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
- [`MockClock`](mock-clock.md) for `zio.Clock`
- [`MockConsole`](mock-console.md) for `zio.Console`
- [`MockRandom`](mock-random.md) for `zio.Random`
- [`MockSystem`](mock-system.md) for `zio.System`

