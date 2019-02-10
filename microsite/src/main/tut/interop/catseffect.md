---
layout: docs
section: interop
title:  "Cats Effect"
---

# {{page.title}}

Checkout `interop-cats` module for inter-operation support.

### `IO` cats' `Effect` instance

**ZIO** integrates with Typelevel libraries by providing an instance of `Effect` for `IO` as required, for instance, by `fs2`, `doobie` and `http4s`. Actually, I lied a little bit, it is not possible to implement `Effect` for any error type since `Effect` extends `MonadError` of `Throwable`.

For convenience we have defined an alias as follow:

```scala
  type Task[A] = IO[Throwable, A]
```

Therefore, we provide an instance of `Effect[Task]`.

#### Example

The following example shows how to use ZIO with Doobie (a library for JDBC access) and FS2 (a streaming library), which both rely on Cats Effect instances:

```scala
import doobie.imports._
import fs2.Stream
import scalaz.zio.interop.Task
import scalaz.zio.interop.catz._

val xa: Transactor[Task] = Transactor.fromDriverManager[Task](...)

def loadUsers: Stream[Task, User] =
  sql"""SELECT * FROM users""".query[User].stream.transact(xa)

val allUsers: List[User] = unsafeRun(loadUsers.compile.toList)
```
