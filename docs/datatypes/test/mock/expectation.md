---
id: expectation
title: Expectation
---

An `Expectation[R]` is an immutable tree structure that represents expectations on environment `R`.

## Expectations

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
}
```

We can write the mock version of this class as below:

```scala mdoc:silent

object MockUserService extends Mock[UserService] {

  object Insert      extends Effect[User, String, Unit]
  object Remove      extends Effect[String, String, Unit]
  object RecentUsers extends Effect[Int, String, List[User]]
  object TotalUsers  extends Effect[Unit, String, Int]

  val compose: URLayer[mock.Proxy, UserService] =
    ZIO.service[mock.Proxy]
      .map { proxy =>
        new UserService {
          override def insert(user: User):  IO[String, Unit]       = proxy(Insert, user)
          override def remove(id: String):  IO[String, Unit]       = proxy(Remove, id)
          override def recentUsers(n: Int): IO[String, List[User]] = proxy(RecentUsers, n)
          override def totalUsers:          IO[String, Int]        = proxy(TotalUsers)
        }
      }.toLayer
      
}
```

Now, let's look at each expectation:

### `value`

Expecting a simple value:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("expecting simple value") {
  for {
    total <- UserService.totalUsers.provideLayer(
      MockUserService.TotalUsers(Expectation.value(14)))
  } yield assertTrue(total == 14)
} 
```

### `valueF`

Expecting a value based on input arguments:

```scala mdoc:silent
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("an expectation based on input arguments") {
  for {
    users <- UserService.recentUsers(3).provideLayer(
      MockUserService.RecentUsers(
        Assertion.isPositive,
        Expectation.valueF(n =>
          (1 to n).map(id => User(id.toString, "name")).toList
        )
      )
    )
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
  for {
    users <- UserService.recentUsers(3).provideLayer(
      MockUserService.RecentUsers(
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
    )
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
  for {
    res <- UserService.remove("1").provideLayer(
      MockUserService.Remove(
        Assertion.isNonEmptyString,
        Expectation.unit
      )
    ).exit
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
  for {
    total <- UserService.totalUsers.provideLayer(
      MockUserService.TotalUsers(Expectation.failure("connection failed"))
    ).exit
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
  for {
    r <- Live.live(
      UserService.totalUsers.provideLayer(
        MockUserService.TotalUsers(
          Expectation.never
        )
      ).timeout(500.millis)
    )
  } yield assertTrue(r.isEmpty)
}
```

## Composing Expectations

We can combine our expectation to build complex scenarios using combinators defined in `zio.test.mock.Expectation`:

### `and`

The `and` (alias `&&`) operator composes two expectations, producing a new expectation to **satisfy both in any order**:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("satisfy both expectations with a logical `and` operator") {
  for {
    total <- (UserService.recentUsers(5) *> UserService.totalUsers).provideLayer(
      MockUserService.TotalUsers(Expectation.value(1)).and(
        MockUserService.RecentUsers(
          Assertion.isPositive,
          Expectation.value(List(User("1", "user"))))
      )
    )
  } yield assertTrue(total == 1)
}
```

### `or`

Th `or` (alias `||`) composes two expectations, producing a new expectation to **satisfy only one of them**:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("satisfy one of expectations with a logical `or` operator") {
  for {
    total <- UserService.totalUsers.provideLayer(
      MockUserService.TotalUsers(Expectation.value(1)).or(
        MockUserService.RecentUsers(
          Assertion.isPositive,
          Expectation.value(List(User("1", "user"))))
      )
    )
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
  for {
    total <- (UserService.recentUsers(5) *> UserService.totalUsers).provideLayer(
      MockUserService.RecentUsers(
        Assertion.isPositive,
        Expectation.value(List(User("1", "user")))
      ) andThen MockUserService.TotalUsers(Expectation.value(1))
    )
  } yield assertTrue(total == 1)
}
```

In the example above, changing the SUT to `UserService.totalUsers *> UserService.remove(1)` will fail the test.

### Exact Repetition

1. **`exactly`** — Produces a new expectation to satisfy itself exactly the given number of times.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("satisfying exact repetition of a method call") {
  for {
    _ <- ZIO.foreach(List("1", "2", "3", "4"))(id => UserService.remove(id)).provideLayer(
      MockUserService.Remove(
        Assertion.isNonEmptyString
      ).exactly(4)
    )
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
  for {
    _ <- (Random.nextInt *> Random.nextInt).provideLayer(
      MockRandom.NextInt(Expectation.value(42))
        .repeats(2 to 4)
    )
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
  for {
    _ <- (Random.nextInt *> Random.nextBoolean *> Random.nextInt).provideLayer(
      (MockRandom.NextInt(value(42)) ++ MockRandom.NextBoolean(value(true)))
        .repeats(1 to 2)
    )
  } yield assertTrue(true)
} @@ TestAspect.failing
```

2. The **`atLeast(min: Int)`**, **`atMost(max: Int)`**, and **`optional`** expectations are other variants for `repeats` expectation.
  - The `atLeast` operator is a lower-bounded variant of `repeated`, produces a new expectation to satisfy **itself sequentially at least given number of times**.
  - The `atMost` operator is an upper-bounded variant of `repeated`, produces a new expectation to satisfy **itself sequentially at most given number of times**.
  - The `optional` operator is an alias for `atMost(1)`, produces a new expectation to satisfy **itself at most once**.
