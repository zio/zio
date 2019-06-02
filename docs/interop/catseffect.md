---
id: interop_catseffect
title:  "Cats Effect"
---

Checkout `interop-cats` module for inter-operation support.

## `IO` Cats Effect's instances

**ZIO** integrates with Typelevel libraries by providing an instance of `ConcurrentEffect` for `IO` as required, for instance, by `fs2`, `doobie` and `http4s`. Actually, I lied a little bit, it is not possible to implement `ConcurrentEffect` for any error type since `ConcurrentEffect` extends `MonadError` of `Throwable`.

For convenience we have defined an alias as follow:

```scala
  type Task[A] = IO[Throwable, A]
```

Therefore, we provide an instance of `ConcurrentEffect[Task]`.

### Timer

In order to get a `cats.effect.Timer[Task]` instance we need an extra import:

```scala
import zio.interop.catz.implicits._
```

The reason it is not provided by the default "interop" import is that it makes testing programs that require timing capabilities hard so an extra import wherever needed makes reasoning about it much easier.

### Example

The following example shows how to use ZIO with Doobie (a library for JDBC access) and FS2 (a streaming library), which both rely on Cats Effect instances:

```scala
import doobie.imports._
import fs2.Stream
import zio.Task
import zio.interop.catz._

val xa: Transactor[Task] = Transactor.fromDriverManager[Task](...)

def loadUsers: Stream[Task, User] =
  sql"""SELECT * FROM users""".query[User].stream.transact(xa)

val allUsers: List[User] = unsafeRun(loadUsers.compile.toList)
```
