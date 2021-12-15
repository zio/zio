---
id: expectation
title: Expectation
---

An `Expectation[R]` is an immutable tree structure that represents expectations on environment `R`.

## Defining Expectations

ZIO Test has a variety of expectations, such as `value`, `unit`, `failure`, and `never`. In this section we are going to learn each of these expectations and their variant, by mocking the `UserService` service. So let's assume we have the following service:

```scala mdoc:silent
import zio._
import zio.test._
import zio.test.mock._

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

```scala mdoc:silent

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

```scala mdoc:compile-only
import zio.test._
import zio.test.mock._

val exp01 = MockUserService.RecentUsers( // capability to build an expectation for
  Assertion.equalTo(5), // assertion of the expected input argument
  Expectation.value(List(User("1", "Jane Doe"), User("2", "John Doe"))) // result, that will be returned
)
```

2. For methods that take no input, we only define the expected output:

```scala mdoc:compile-only
val exp02 = MockUserService.TotalUsers(Expectation.value(42))
```

3. For methods that may return `Unit`, we may skip the predefined result (it will default to successful value) or use `unit` helper:

```scala mdoc:compile-only
val exp03 = MockUserService.Remove(
  Assertion.equalTo("1"),
  Expectation.unit
)
```

4. For methods that may return `Unit` and take no input we can skip both:

```scala mdoc:compile-only
val exp04 = MockUserService.RemoveAll()
```

### Providing Mocked Environment

Each expectation can be taught of a mocked environment. They can be converted to a `ZLayer` implicitly. Therefore, we can compose them together and provide them to the environment of the SUT (System Under Test).

```scala mdoc:compile-only
import zio.test._

import zio._
import zio.test.{test, _}
import zio.test.mock._

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

```scala mdoc:compile-only
import zio.test._
import zio.test.mock._

object MaybeConsoleSpec extends ZIOSpecDefault {
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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

In the most robust example, the result can be either a successful value or a failure. In this section we are going to introduce all these cases, by using the proper expectation from `zio.test.mock.Expectations` companion object:

### `value`

Expecting a simple value:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

### `valueM`

Expecting a value based on the input arguments and also the result of an effectful operation:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("effectful expectation") {
  val sut     = UserService.recentUsers(3)
  val mockEnv = MockUserService.RecentUsers(
    Assertion.isPositive,
    Expectation.valueM(n =>
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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

There are also `failureF` and `failureM` variants like what we described for `value` expectation.

### `never`

This expectation simulates a never-ending loop:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

We can combine our expectation to build complex scenarios using combinators defined in `zio.test.mock.Expectation`.

### `and`

The `and` (alias `&&`) operator composes two expectations, producing a new expectation to **satisfy both in any order**:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

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

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._
import zio.test.mock.Expectation._

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
