---
id: with-cats-effect
title: "How to Interop with Cats Effect?"
---

[`interop-cats`](https://github.com/zio/interop-cats) has instances for the [Cats](https://typelevel.org/cats/), [Cats MTL](https://github.com/typelevel/cats-mtl) and [Cats Effect](https://typelevel.org/cats-effect/) libraries, which allow you to use ZIO with any libraries that rely on these, like [Doobie](https://github.com/tpolecat/doobie), [Http4s](https://github.com/http4s/http4s), [FS2](https://github.com/functional-streams-for-scala/fs2) or [Circe](https://github.com/circe/circe)

Depends on which version of Cats Effect we are using, we should pick the right version of `zio-interop-cats`. In this tutorial, whenever we're working with Cats Effect 2.x, we are using:

```scala
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "2.5.1.0"
```

And whenever we are using Cats Effect 3.x instances, we are using:

```scala
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "3.1.1.0"
```

Most of the interop functionality resides in the following package:

```scala mdoc:silent
import zio.interop.catz._
```

## Cats Effect Instances

ZIO integrates with Typelevel libraries by providing instances of Cats Effect type classes. These type classes are used by `fs2`, `doobie`, `http4s`, and a variety of other libraries in the functional Scala ecosystem.

Due to the limitations of the Cats Effect, ZIO cannot provide instances for arbitrary error types. Instead, we can obtain instances only for effects whose error type extends `Throwable`.

For convenience, ZIO includes the `Task` and `RIO` type aliases, which fix the error type to `Throwable`, and may be useful for interop with Cats Effect:

```scala mdoc:invisible
import zio.{ZIO, Task, RIO}
```

```scala mdoc:silent:nest
type Task[+A]    = ZIO[Any, Throwable, A]
type RIO[-R, +A] = ZIO[  R, Throwable, A]
```

### Providing Runtime Manually

To use Cats Effect instances for these types, we should have an implicit `Runtime[R]` in scope for the environment type of our effects. The following code snippet creates an implicit `Runtime` for all the modules built into ZIO:

```scala mdoc:silent:nest
import cats.implicits._
import zio.interop.catz._

object ZioCatsEffectInterop extends scala.App {
  def catsEffectApp[F[_]: cats.effect.Sync]: F[Unit] =
    cats.effect.Sync[F].delay(
        println("Hello from Cats Effect World!")
      )
      
  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  val zioApp: zio.Task[Unit] = catsEffectApp[zio.Task]
  runtime.unsafeRun(zioApp.exitCode)
}
```

If we are working with Cats Effect 3.x, the `catsEffectApp[Task]` will be expanded as if we called the following code explicitly:

```scala mdoc:invisible
import ZioCatsEffectInterop.catsEffectApp
```

```scala mdoc:silent:nest
object ZioCatsEffectInterop extends scala.App {
  val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default
  
  val zioApp: zio.Task[Unit] = catsEffectApp[zio.Task](
    zio.interop.catz.asyncRuntimeInstance(runtime) 
  )
  
  runtime.unsafeRun(zioApp.exitCode) 
}
```

And if we are working with Cats Effect 2.x, it will be expanded as if we called following code explicitly:

```scala
object ZioCatsEffectInterop extends scala.App {
  val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  val zioApp = catsEffectApp[zio.Task](zio.interop.catz.taskConcurrentInstance)
  runtime.unsafeRun(zioApp.exitCode)
}
```

If we are using `RIO` for a custom environment `R`, then we will have to create our own `Runtime[R]`, and ensure that implicit wherever we need Cats Effect instances.

### Using `CatsApp` Runtime

As a convenience, our application can extend `CatsApp`, which automatically brings an implicit `Runtime[ZEnv]` into our scope:

```scala mdoc:silent:nest
import zio.interop.catz._
import cats.implicits._

object ZioCatsEffectInteropWithCatsApp extends CatsApp {
  def catsEffectApp[F[_]: cats.effect.Sync]: F[Unit] =
    cats.effect.Sync[F].delay(println("Hello from Cats Effect World!"))

  override def run(args: List[String]): zio.URIO[zio.ZEnv, zio.ExitCode] = 
    catsEffectApp[zio.Task].exitCode
}
```

This example works properly in both Cats Effect 2.x and 3.x versions.

### Cats Effect 2.x Instances

### Timer

In order to get a `cats.effect.Timer[zio.Task]` instance, we need an extra import (`zio.interop.catz.implicits._`):

```scala
import java.util.concurrent.TimeUnit

import cats.implicits._
import zio.interop.catz._
import zio.interop.catz.implicits._ // Provides `zio.Task instance` for `cats.effect.Time` type class
import zio.{ ExitCode, Task, URIO }

import scala.concurrent.duration.DurationInt

object ZioCatsEffectTimerInterop extends zio.interop.catz.CatsApp {
  override def run(args: List[String]): zio.URIO[zio.ZEnv, zio.ExitCode] =
    catsEffectTimerApp[zio.Task].exitCode

  def catsEffectTimerApp[F[_]: cats.effect.Clock: cats.effect.Timer: cats.effect.Sync]: F[Unit] = for {
    t2 <- cats.effect.Clock[F].monotonic(TimeUnit.SECONDS)
    _  <- cats.effect.Timer[F].sleep(2.seconds)
    t1 <- cats.effect.Clock[F].monotonic(TimeUnit.SECONDS)
    _  <- cats.effect.Sync[F].delay(println(t1 - t2))
  } yield ()
}
```

The reason a `cats.effect.Timer[zio.Task]` instance is not provided by the default _interop_ import is that it makes testing programs that require timing capabilities very difficult. The extra import (wherever needed) makes reasoning about timing-related effects much easier.

If we're using `RIO` for a custom environment then our environment must use the `Clock` service, e.g. `R <: Clock` to get a timer.

## Resource Instance

To convert Cats Effect `Resource` into `ZManaged`, we can call `toManaged` on `Resource`.

For example, assume we have the following `File` API:

```scala mdoc:silent
case class File[F[_]: cats.effect.Sync]() {
  import cats.syntax.apply._
  def read: F[String] =
    cats.effect.Sync[F].delay(println("Reading file.")) *>
      cats.effect.Sync[F].pure("Hello, World!")
  def close: F[Unit]  =
    cats.effect.Sync[F].delay(println("Closing file."))
}

object File {
  import cats.syntax.apply._
  def open[F[_]: cats.effect.Sync](name: String): F[File[F]] =
    cats.effect.Sync[F].delay(println(s"opening $name file")) *>
      cats.effect.Sync[F].delay(File())
}
```

And, also assume we have `fileResource` defined as follows:

```scala mdoc:invisible
import cats.effect.{ Resource, Sync }
```

```scala mdoc:silent
def fileResource[F[_]: Sync](name: String): Resource[F, File[F]] =
  Resource.make(File.open[F](name))(_.close)
```

We can convert that to `ZManaged`:

```scala mdoc:invisible
import zio.ZManaged
```

```scala
val resource: ZManaged[ZEnv, Throwable, File[Task]] =
  fileResource[Task]("log.txt").toManaged[ZEnv]
```

Let's try a complete working example:

```scala
import cats.effect.{ Resource, Sync }
import zio.console._
import zio.interop.catz._
import zio.{ ExitCode, Task, URIO, ZEnv }

object CatsEffectResourceInterop extends CatsApp {
  def fileResource[F[_]: Sync](name: String): Resource[F, File[F]] =
    Resource.make(File.open[F](name))(_.close)

  def myApp = for {
    c <- fileResource[Task]("log.txt").toManaged[ZEnv].use(_.read)
    _ <- putStr(s"file content: $c")
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```

## FS2 Streams

By importing `zio.stream.interop.fs2z._` int to our application, the `toZManages` extension method converts a `fs2.Stream` to `ZStream`:

```scala
import zio.stream.ZStream
import zio.stream.interop.fs2z._
val zstream: ZStream[Any, Throwable, Int] = fs2.Stream.range(1, 10).toZStream()
```

Also, the `ZStream#toFs2Stream` converts a ZIO Stream into FS2 Stream:

```scala
import zio.stream.ZStream
import zio.Chunk
import zio.stream.interop.fs2z._
val fs2stream = ZStream.fromChunks(Chunk(1, 2, 3, 4)).toFs2Stream
```

## ZQueue
The `interop-cats` library has an `import zio.interop.Queue` package to lift creation of `ZQueue` effect from `UIO[Queue[A]]` to `F[Queue[F, A]]` which enables us to run `ZQueue` under run `ZQueue` under Cats Effect library. It supports all variants of `ZQueue` like `bounded`, `unbounded`, `sliding` and `dropping`.

```scala
def bounded[F[+_], A](capacity: Int)(implicit R: Runtime[ZEnv], F: LiftIO[F]): F[Queue[F, A]]
```

In the following example, we are going to lift the `ZQueue` creation effect to Cats `IO` effect:

```scala
import zio.interop.Queue
import cats.effect.IO

implicit val runtime = Runtime.default
def liftedToIO: IO[List[Int]] = for {
  q <- Queue.bounded[IO, Int](100)
  _  <- q.offer(1)
  _  <- q.offer(2)
  r <- q.takeAll
} yield (r)
```

## STM

The `zio.interop.stm` provides a wrapper data type on `STM[Throwable, A]` which enables us to run `STM` in the Cats Effect library.

Currently, the `interop-cats` support `TRef`, `TPromise`,  `TQueue` and `TSemaphore` data types.

## Examples

Cats Effect and Type-Level libraries are older than the ZIO ecosystem. So there are very nice libraries like doobie and http4s, that a ZIO user would like to use in its application.

We have provided some full working example of using these important libraries:

### Doobie and FS2
The following example shows how to use ZIO with Doobie (a library for JDBC access) and FS2 (a streaming library), which both rely on Cats Effect instances:

```scala
import doobie._
import doobie.implicits._
import fs2.Stream
import zio.interop.catz._
import zio.{Runtime, Task}

case class User(id: String, name: String, age: Int)

def xa: Transactor[Task] =
  Transactor.fromDriverManager[Task](
    "org.h2.Driver",
    "jdbc:h2:mem:users;DB_CLOSE_DELAY=-1"
  )

def createTable: doobie.ConnectionIO[Int] =
  sql"""|CREATE TABLE IF NOT EXISTS USERS(
        |id   INT SERIAL UNIQUE,
        |name VARCHAR NOT NULL UNIQUE,
        |age  SMALLINT
        |)""".stripMargin.update.run

def dropTable: doobie.ConnectionIO[Int] = 
  sql"""DROP TABLE IF EXISTS USERS""".update.run

def insert(name: String, age: Int): doobie.ConnectionIO[Int] =
  sql"insert into users (name, age) values ($name, $age)".update.run

def loadUsers: Stream[doobie.ConnectionIO, User] =
  sql"""SELECT * FROM users""".query[User].stream

val doobieApp: Stream[doobie.ConnectionIO, User] = for {
  _ <- fs2.Stream.eval(dropTable)
  _ <- fs2.Stream.eval(createTable)
  _ <- fs2.Stream.eval(insert("Olivia", 21))
  _ <- fs2.Stream.eval(insert("Oliver", 30))
  u <- loadUsers 
} yield u

implicit val runtime: Runtime[zio.ZEnv] = Runtime.default

val run: Stream[Task, User] = doobieApp.transact(xa)

val allUsers: List[User] =
  Runtime.default.unsafeRun(run.compile.toList)
```

Sounds good, but what about managing blocking operations? How they managed? We shouldn't run blocking JDBC operations on the main thread pool. ZIO provides a specific blocking thread pool for blocking operations. The `doobie-hikari` module helps us create a transactor with two separated executors, one for blocking operations like JDBC operations, and the other one for non-blocking operations like performing awaiting connections to the database.

So let's fix this issue in the previous example. In the following snippet we are going to create a `ZMHikari` of Hikari transactor:

```scala
import zio.ZManaged
import zio.blocking.Blocking
import zio.{ Runtime, Task, ZIO, ZManaged }
import doobie.hikari.HikariTransactor
import cats.effect.Blocker

def transactor: ZManaged[Blocking, Throwable, HikariTransactor[Task]] =
  for {
    rt <- ZIO.runtime[Any].toManaged_
    be <- zio.blocking.blockingExecutor.toManaged_ // our blocking EC
    xa <- HikariTransactor
            .newHikariTransactor[Task](
              "org.h2.Driver",                      // driver classname
              "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", // connect URL
              "sa",                                 // username
              "",                                   // password
              rt.platform.executor.asEC,            // await connection here
              Blocker.liftExecutionContext(be.asEC) // execute JDBC operations here
            )
            .toManagedZIO
  } yield xa
```

Now we can `transact` our `doobieApp` with this `transactor` and convert that to the `ZIO` effect:

```scala
val zioApp: ZIO[Blocking, Throwable, List[User]] =
  transactor.use(xa => doobieApp.transact(xa).compile.toList)
```

### Http4s

Here is the full working example of using http4s in ZIO App:

```scala
import cats.effect.{ConcurrentEffect, Sync, Timer}
import cats.implicits._
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeServerBuilder
import zio.interop.catz._
import zio.interop.catz.implicits._ 
import zio.{Task, URIO}

import scala.concurrent.ExecutionContext.global

object ZioHttp4sInterop extends CatsApp {
  def run(args: List[String]): URIO[zio.ZEnv, zio.ExitCode] =
    stream[Task].compile.drain.exitCode
  
  def stream[F[_]: ConcurrentEffect: Timer]: Stream[F, Nothing] = {
    import org.http4s.implicits._
    val httpApp = helloWorldRoute[F].orNotFound
    for {
      _ <- BlazeClientBuilder[F](global).stream
      exitCode <- BlazeServerBuilder[F](global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(httpApp)
        .serve
    } yield exitCode
  }.drain

  def helloWorldRoute[F[_]: Sync]: HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.strict[F] {
      case GET -> Root =>
        Ok("Hello, World!")
    }
  }
}
```
