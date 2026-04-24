---
id: union-types
title: "Use Union Types to Be More Specific About Error Types"
sidebar_label: "Union Types"
---

In Scala 3, we have an exciting new feature called union types. By using the union operator, we can encode multiple error types. Using this facility, we can have more precise information on typed errors.

Let's see an example of `Storage` service which have `upload`, `download` and `delete` API:

```scala
import zio._

type Name = String

enum StorageError extends Exception {
  case ObjectExist(name: Name)            extends StorageError
  case ObjectNotExist(name: Name)         extends StorageError
  case PermissionDenied(cause: String)    extends StorageError
  case StorageLimitExceeded(limit: Int)   extends StorageError
  case BandwidthLimitExceeded(limit: Int) extends StorageError
}

import StorageError.*

trait Storage {
  def upload(
      name: Name,
      obj: Array[Byte]
  ): ZIO[Any, ObjectExist | StorageLimitExceeded, Unit]

  def download(
      name: Name
  ): ZIO[Any, ObjectNotExist | BandwidthLimitExceeded, Array[Byte]]

  def delete(name: Name): ZIO[Any, ObjectNotExist | PermissionDenied, Unit]
}
```

Union types allow us to get rid of the requirement to extend some sort of common error types like `Exception` or `Throwable`. This allows us to have completely unrelated error types.

In the following example, the `FooError` and `BarError` are two distinct error. They have no super common type like `FooBarError` and also they are not extending `Exception` or `Throwable` classes:

```scala
import zio.*

// Two unrelated errors without having a common supertype
trait FooError
trait BarError

def foo: IO[FooError, Nothing] = ZIO.fail(new FooError {})
def bar: IO[BarError, Nothing] = ZIO.fail(new BarError {})

val myApp: ZIO[Any, FooError | BarError, Unit] = for {
  _ <- foo
  _ <- bar
} yield ()
```
