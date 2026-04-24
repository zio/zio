# ZIO 2.x Interoperation with Cats 2.x

> ```sbt
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "22.0.0.0"
```

## Installation

```sbt
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "22.0.0.0"
```

## `ZIO` Cats Effect instances

**ZIO** integrates with Typelevel libraries by providing an instance of `ConcurrentEffect` for `IO` as required, for instance, by `fs2`, `doobie` and `http4s`. Actually, I lied a little bit, it is not possible to implement `ConcurrentEffect` for any error type since `ConcurrentEffect` extends `MonadError` of `Throwable`.

For convenience we have defined an alias as follow:

```scala
  type Task[A] = IO[Throwable, A]
```

Therefore, we provide an instance of `ConcurrentEffect[Task]`.

## ConcurrentEffect

In order to get a `ConcurrentEffect[Task]` or `ConcurrentEffect[RIO[R, *]]` we need an implicit `Runtime[R]` in scope. The easiest way to get it is using `ZIO.runtime`:

```scala

def getCE = {
  ZIO.runtime.map { implicit r: Runtime[Any] =>
    val F: ConcurrentEffect[Task] = implicitly
  }
}
```

`Task.concurrentEffectWith` method can automate this pattern:

```scala

def fork = {
  Task.concurrentEffectWith { implicit CE =>
    CE.start(Task(println("Started task")))
  }
}
```

### Timer

In order to get a `cats.effect.Timer[Task]` instance we need an extra import:

```scala

```

The reason it is not provided by the default "interop" import is that it makes testing programs that require timing capabilities hard therefore an extra import wherever needed makes reasoning about it much easier.
It is not a good idea to use this import as it causes limitations in testing since it uses the `live` implementation of `zio.Clock.Service`. If you want to retain testability, do the following instead:

```scala

ZIO.runtime[Clock].flatMap { implicit rts =>
  val clock: Timer[Task] = rts.environment.get.toTimer
  val ce: ConcurrentEffect[Task] = implicitly
  
  ce.race(clock.sleep(1.second), clock.sleep(1.second))
}
```

### cats-core

If you only need instances for `cats-core` typeclasses, not `cats-effect` import `zio.interop.catz.core._`:

````scala

````

Note that this library only has an `Optional` dependency on cats-effect – if you or your libraries don't depend on it, this library will not add it to the classpath.

### Example

The following example shows how to use ZIO with Doobie (a library for JDBC access) and FS2 (a streaming library), which both rely on Cats Effect instances:

```scala

val xa: Transactor[Task] = Transactor.fromDriverManager[Task](...)

def loadUsers: Stream[Task, User] =
  sql"""SELECT * FROM users""".query[User].stream.transact(xa)

val allUsers: List[User] = unsafeRun(loadUsers.compile.toList)
```
