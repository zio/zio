---
id: mock
title: Mock
---

A `Mock[R]` represents a mockable environment `R`. It is a base abstract class for every services that we want to mock. 

## Motivation

Assume we have a `Userservice` defined as follows:

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

And also the following `EmailService`:

```scala mdoc:silent
trait EmailService {
  def send(to: String, body: String): IO[String, Unit]
}
```

The live implementation of this service should be something as below:

```scala mdoc:compile-only

case class UserServiceLive(emailService: EmailService) extends UserService {
  override def register(username: String, age: Int, email: String): IO[String, Unit] =
    if (age < 18) {
      emailService.send(email, "Your are not eligible to register.")
    } else if (username == "admin") {
      ZIO.fail("The admin user is already registered")
    } else {
      for {
        _ <- ZIO.logInfo(s"New user ($username) registered to the database.")
        _ <- emailService.send(email, "Congratulation, your are registered!")
      } yield ()
    }
}

object UserServiceLive {
  val layer: URLayer[EmailService, UserService] =
    (UserServiceLive.apply _).toLayer[UserService]
}
```

The question is, how we can test the live `register` version of `UserService`? The `register` function doesn't return any useful data. So we cannot test its behavior by using its returned value. Instead, it has two collaborators: `UserRepository` and `EmailService`. We can test the `UserService` by testing how its collaborators are called during the test process.

To test the `register` function, we can mock the behavior of these two collaborators. So instead of using production objects, we use pre-programmed mock versions of these two collaborators with some expectations, which form a specification of the calls we expect them to receive. In this way, in each test case, we expect these collaborators will be called with expected inputs. So we can check these expectations.

In this example we can define these three test cases:
1. If we register a user with an age of less than 18, we expect that the `save` method of `UserRepository` shouldn't be called. Also, we expect that the `send` method of `EmailService` will be called with this content: "You are not eligible to register."
2. If we register a user with a username of "admin", we expect that both `UserRepository` and `EmailService` should not be called. Instead, we expect that the `register` call will be failed with a proper failure value: "The admin user is already registered!"
3. Otherwise, we expect that the `save` method of `UserRepository` will be called with the corresponding `User` object, and the `send` method of `EmailService` will be called with this content: "Congratulation, you are registered!".

ZIO Test has a built-in functionalities to mock services. In the next section we are going to test `UserService` by mocking its collaborators.
