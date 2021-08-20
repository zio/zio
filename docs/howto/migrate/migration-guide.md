---
id: zio-2.x-migration-guide
title: "ZIO 2.x Migration Guide"
---
```scala mdoc:invisible
import zio._
```

## Upgrading Dependencies

If we are using one of the following dependencies, we need to upgrade them to the `2.0.0-M2` version:

```scala
libraryDependencies += "dev.zio" %% "zio"         % "2.0.0-M2"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.0-M2"
libraryDependencies += "dev.zio" %% "zio-test"    % "2.0.0-M2"
```

## Automated Scalafix Rules
TODO

## ZLayer

### Functions to Layers

In ZIO 1.x, when we want to write a service that depends on other services, we need to use `ZLayer.fromSrevice*` variants with a lot of boilerplate:

```scala
val live: URLayer[Clock with Console, Logging] =
  ZLayer.fromServices[Clock.Service, Console.Service, Logging.Service] {
    (clock: Clock.Service, console: Console.Service) =>
      new Service {
        override def log(line: String): UIO[Unit] =
          for {
            current <- clock.currentDateTime.orDie
            _ <- console.putStrLn(current.toString + "--" + line).orDie
          } yield ()
      }
  }
```

ZIO 2.x deprecates all `ZLayer.fromService*` functions:

| ZIO 1.0                        | ZIO 2.x |
|--------------------------------|---------|
| ZLayer.fromService             | toLayer |
| ZLayer.fromServices            | toLayer |
| ZLayer.fromServiceM            | toLayer |
| ZLayer.fromServicesM           | toLayer |
| ZLayer.fromServiceManaged      | toLayer |
| ZLayer.fromServicesManaged     | toLayer |
| ZLayer.fromServiceMany         | toLayer |
| ZLayer.fromServicesMany        | toLayer |
| ZLayer.fromServiceManyM        | toLayer |
| ZLayer.fromServicesManyM       | toLayer |
| ZLayer.fromServiceManyManaged  | toLayer |
| ZLayer.fromServicesManyManaged | toLayer |

Instead, it provides the `toLayer` extension methods for functions:

```scala
case class LoggingLive(console: Console, clock: Clock) extends Logging {
  override def log(line: String): UIO[Unit] =
    for {
      current <- clock.currentDateTime.orDie
      _       <- console.putStrLn(current.toString + "--" + line).orDie
    } yield ()
}

object LoggingLive {
  val layer: URLayer[Has[Console] with Has[Clock], Has[Logging]] =
    (LoggingLive(_, _)).toLayer[Logging]
}
```

Note that the `LoggingLive(_, _)` is a `Function2` of type `(Console, Clock) => LoggingLive`. As the ZIO 2.x provides the `toLayer` extension method for all `Function` arities, we can call the `toLayer` on any function to convert that to the `ZLayer`. Unlike the `ZLayer.fromService*` functions, this can completely infer the input types, so it saves us from a lot of boilerplates we have had in ZIO 1.x.

### Accessing a Service from the Environment

Assume we have a service named `Logging`:

```scala mdoc:silent
trait Logging {
  def log(line: String): UIO[Unit]
}
```

In ZIO 1.x, when we wanted to access a service from the environment, we used the `ZIO.access` + `Has#get` combination (`ZIO.access(_.get)`):

```scala mdoc:silent:nest
val logging: URIO[Has[Logging], Logging] = ZIO.access(_.get)
```

Also, to create accessor methods, we used the following code:

```scala mdoc:silent:nest
def log(line: String): URIO[Has[Logging], Unit] = ZIO.accessM(_.get.log(line))
```

ZIO 2.x reduces one level of indirection by using `ZIO.service` operator:

```scala mdoc:silent:nest
val logging : URIO[Has[Logging], Logging] = ZIO.service
```

And to write the accessor method in ZIO 2.x, we can use `ZIO.serviceWith` operator:

```scala mdoc:silent:nest
def log(line: String): URIO[Has[Logging], Unit] = ZIO.serviceWith(_.log(line))
```

### Building the Dependency Graph

To create the dependency graph in ZIO 1.x, we should compose the required layers manually. As the ordering of layer compositions matters, and also we should care about composing layers in both vertical and horizontal manner, it would be a cumbersome job to create a dependency graph with a lot of boilerplates.

Assume we have the following dependency graph with two top-level dependencies:

```
           DocRepo                ++          UserRepo
      ____/   |   \____                       /     \
     /        |        \                     /       \
 Logging  Database  BlobStorage          Logging   Database
    |                    |                  |
 Console              Logging            Console
                         |       
                      Console    
```

In ZIO 1.x, we had to compose these different layers together to create the whole application dependency graph:

```scala mdoc:invisible:nest
trait Logging {}

trait Database {}

trait BlobStorage {}

trait UserRepo {}

trait DocRepo {}

case class LoggerImpl(console: Console) extends Logging {}

case class DatabaseImp() extends Database {}

case class UserRepoImpl(logging: Logging, database: Database) extends UserRepo {}

case class BlobStorageImpl(logging: Logging) extends BlobStorage {}

case class DocRepoImpl(logging: Logging, database: Database, blobStorage: BlobStorage) extends DocRepo {}

object Logging {
  val live: URLayer[Has[Console], Has[Logging]] =
    LoggerImpl.toLayer[Logging]
}

object Database {
  val live: URLayer[Any, Has[Database]] =
    DatabaseImp.toLayer[Database]
}

object UserRepo {
  val live: URLayer[Has[Logging] with Has[Database], Has[UserRepo]] =
    (UserRepoImpl(_, _)).toLayer[UserRepo]
}


object BlobStorage {
  val live: URLayer[Has[Logging], Has[BlobStorage]] =
    BlobStorageImpl.toLayer[BlobStorage]
}

object DocRepo {
  val live: URLayer[Has[Logging] with Has[Database] with Has[BlobStorage], Has[DocRepo]] =
    (DocRepoImpl(_, _, _)).toLayer[DocRepo]
}
  
val myApp: ZIO[Has[DocRepo] with Has[UserRepo], Nothing, Unit] = ZIO.succeed(???)
```

```scala mdoc:silent:nest
val appLayer: URLayer[Any, Has[DocRepo] with Has[UserRepo]] =
  (((Console.live >>> Logging.live) ++ Database.live ++ (Console.live >>> Logging.live >>> BlobStorage.live)) >>> DocRepo.live) ++
    (((Console.live >>> Logging.live) ++ Database.live) >>> UserRepo.live)
    
val res: ZIO[Any, Nothing, Unit] = myApp.provideLayer(appLayer)
```

As the development of our application progress, the number of layers will grow, and maintaining the dependency graph would be tedious and hard to debug.

For example, if we miss the `Logging.live` dependency, the compile-time error would be very messy:

```scala
myApp.provideLayer(
  ((Database.live ++ BlobStorage.live) >>> DocRepo.live) ++
    (Database.live >>> UserRepo.live)
)
```

```
type mismatch;
 found   : zio.URLayer[zio.Has[Logging] with zio.Has[Database] with zio.Has[BlobStorage],zio.Has[DocRepo]]
    (which expands to)  zio.ZLayer[zio.Has[Logging] with zio.Has[Database] with zio.Has[BlobStorage],Nothing,zio.Has[DocRepo]]
 required: zio.ZLayer[zio.Has[Database] with zio.Has[BlobStorage],?,?]
    ((Database.live ++ BlobStorage.live) >>> DocRepo.live) ++
```

In ZIO 2.x, we can automatically construct layers with friendly compile-time hints, using `ZIO#inject` operator:

```scala mdoc:silent:nest
val res: ZIO[Any, Nothing, Unit] =
  myApp.inject(
    Console.live,
    Logging.live,
    Database.live,
    BlobStorage.live,
    DocRepo.live,
    UserRepo.live
  )
```

The order of dependencies doesn't matter:

```scala mdoc:silent:nest
val res: ZIO[Any, Nothing, Unit] =
  myApp.inject(
    DocRepo.live,
    BlobStorage.live,
    Logging.live,
    Database.live,
    UserRepo.live,
    Console.live
  )
```

If we miss some dependencies, it doesn't compile, and the compiler gives us the clue:

```scala
val app: ZIO[Any, Nothing, Unit] =
  myApp.inject(
    DocRepo.live,
    BlobStorage.live,
//    Logging.live,
    Database.live,
    UserRepo.live,
    Console.live
  )
```

```
  ZLayer Wiring Error  

❯ missing Logging
❯     for DocRepo.live

❯ missing Logging
❯     for UserRepo.live
```

We can also directly construct a layer using `ZLayer.wire`:

```scala mdoc:silent:nest
val layer = ZLayer.wire[Has[DocRepo] with Has[UserRepo]](
  Console.live,
  Logging.live,
  DocRepo.live,
  Database.live,
  BlobStorage.live,
  UserRepo.live
)
```

And also the `ZLayer.wireSome` helps us to construct a layer which requires on some service and produces some other services (`URLayer[Int, Out]`) using `ZLayer.wireSome[In, Out]`:

```scala mdoc:silent:nest
val layer = ZLayer.wireSome[Has[Console], Has[DocRepo] with Has[UserRepo]](
  Logging.live,
  DocRepo.live,
  Database.live,
  BlobStorage.live,
  UserRepo.live
)
```

In ZIO 1.x, the `ZIO#provideSomeLayer` provides environment partially:

```scala mdoc:silent:nest
val app: ZIO[Has[Console], Nothing, Unit] =
  myApp.provideSomeLayer[Has[Console]](
    ((Logging.live ++ Database.live ++ (Console.live >>> Logging.live >>> BlobStorage.live)) >>> DocRepo.live) ++
      (((Console.live >>> Logging.live) ++ Database.live) >>> UserRepo.live)
  )
```

In ZIO 2.x, we have a similar functionality but for injection, which is the `ZIO#injectSome[Rest](l1, l2, ...)` operator:

```scala mdoc:silent:nest
val app: ZIO[Has[Console], Nothing, Unit] =
  myApp.injectSome[Has[Console]](
    Logging.live,
    DocRepo.live,
    Database.live,
    BlobStorage.live,
    UserRepo.live
  )
```

In ZIO 1.x, the `ZIO#provideCustomLayer` takes the part of the environment that is not part of `ZEnv` and gives us an effect that only depends on the `ZEnv`:

```scala mdoc:silent:nest
val app: ZIO[zio.ZEnv, Nothing, Unit] = 
  myApp.provideCustomLayer(
    ((Logging.live ++ Database.live ++ (Logging.live >>> BlobStorage.live)) >>> DocRepo.live) ++
      ((Logging.live ++ Database.live) >>> UserRepo.live)
  )
```

In ZIO 2.x, the `ZIO#injectCustom` does the similar but for the injection:

```scala mdoc:silent:nest
val app: ZIO[zio.ZEnv, Nothing, Unit] =
  myApp.injectCustom(
    Logging.live,
    DocRepo.live,
    Database.live,
    BlobStorage.live,
    UserRepo.live
  )
```

> Note:
> All `provide*` methods are not deprecated, and they are still necessary and useful for low-level and custom cases. But, in ZIO 2.x, in most cases, it's easier to use `inject`/`wire` methods.


| ZIO 1.x and 2.x (manually)                    | ZIO 2.x (automatically) |
|-----------------------------------------------|-------------------------|
| `ZIO#provide`                                 | `ZIO#inject`            |
| `ZIO#provideSomeLayer`                        | `ZIO#injectSome`        |
| `ZIO#provideCustomLayer`                      | `ZIO#injectCustom`      |
| Composing manually using `ZLayer` combinators | `ZLayer#wire`           |
| Composing manually using `ZLayer` combinators | `ZLayer#wireSome`       |

## ZIO Streams
TODO

## ZIO Services

There are two significant changes in ZIO Services:

| ZIO 1.x                  | ZIO 2.x                 |
|--------------------------|-------------------------|
| zio.Clock.Service.live   | zio.Clock.ClockLive     |
| zio.Console.Service.live | zio.Console.ConsoleLive |
| zio.System.Service.live  | zio.System.SystemLive   |
| zio.Random.Service.live  | zio.Random.RandomLive   |

1. All ZIO services moved to the `zio` package:

    | ZIO 1.x               | ZIO 2.x                       |
    |-----------------------|-------------------------------|
    | zio.blocking.Blocking | [Removed](#blocking-service)  |
    | zio.clock.Clock       | zio.Clock                     |
    | zio.console.Console   | zio.Console                   |
    | zio.random.Random     | zio.Random                    |
    | zio.system.System     | zio.System                    |

    And their live implementations renamed and moved to a new path:

    | ZIO 1.x                  | ZIO 2.x                 |
    |--------------------------|-------------------------|
    | zio.Clock.Service.live   | zio.Clock.ClockLive     |
    | zio.Console.Service.live | zio.Console.ConsoleLive |
    | zio.System.Service.live  | zio.System.SystemLive   |
    | zio.Random.Service.live  | zio.Random.RandomLive   |


2. In ZIO 2.0 all type aliases like `type Logging = Has[Logging.Service]` removed. So we should explicitly use `Has` wrappers when we want to specify dependencies on ZIO services.

So instead of writing `ZLayer[Console with Clock, Nothing, ConsoleLogger]`, we should write `ZLayer[Has[Console] with Has[Clock], Nothing, Has[ConsoleLogger]]`.

### Blocking Service

Since there is rarely a need to use a separate blocking thread pool, ZIO 2.0 created _one global blocking pool_ removed the Blocking service from `ZEnv` and the built-in services.

All blocking operations were moved to the `ZIO` data type:

| ZIO 1.x                 | ZIO 2.x |
|-------------------------|---------|
| zio.blocking.Blocking.* | ZIO.*   |

We can also provide a user-defined blocking executor in ZIO 2.x with the `Runtime#withBlockingExecutor` operator that constructs a new `Runtime` with the specified blocking executor.

### Console Service

Method names in the _Console_ service were renamed to the more readable names:

| ZIO 1.x     | ZIO 2.x        |
|-------------|----------------|
| putStr      | print          |
| putStrErr   | printError     |
| putStrLn    | printLine      |
| putStrLnErr | printLineError |
| getStrLn    | readLine       |

## Improvements

### Composable Zips

In ZIO 2.x, when we are zipping together different effects:
- `Tuple`s are not nested.
- `Unit`s do not contribute to the output.

Assume we have these effects

```scala mdoc:silent:nest
val x1: Task[Int]     = Task.succeed(???)
val x2: Task[Unit]    = Task.succeed(???)
val x3: Task[String]  = Task.succeed(???)
val x4: Task[Boolean] = Task.succeed(???)
```

In ZIO 1.x, the output of zipping together these effects are nested:

```scala
val zipped = x1 <*> x2 <*> x3 <*> x4
// zipped: ZIO[Any, Throwable, (((Int, Unit), String), Boolean)] = zio.ZIO$FlatMap@3ed3c202
```

While in ZIO 2.x, we have more ergonomics result type and also the `Unit` data-type doesn't contribute to the output:

```scala mdoc:nest
val zipped = x1 <*> x2 <*> x3 <*> x4
```

This change is not only for the `ZIO` data type but also for all other data types like `ZManaged`, `ZStream`, `ZSTM`, etc.

As we have compositional zips, we do not longer need higher arity zips in ZIO 1.x like `mapN`, `mapParN`, `Gen#zipN`, and `Gen#crossN`. They are deprecated in ZIO 2.x.

Here is the list of `zip` variants that are deprecated:

| ZIO 1.x       | ZIO 2.x    |
|---------------|------------|
| ZIO#&&&       | ZIO#zip    |
| ZIO.tupled    | ZIO.zip    |
| ZIO.tupledPar | ZIO.zipPar |
| ZIO.mapN      | ZIO.zip    |
| ZIO.mapParN   | ZIO.zipPar |

### Either Values

In ZIO 1.x, the `ZIO#left` and `ZIO#right` operators are lossy, and they don't preserve the information on the other side of `Either` after the transformation.

For example, assume we have an effect of type `ZIO[Any, Throwable, Left[Int, String]]`:

```scala
val effect         = Task.effect(Left[Int, String](5))
// effect: ZIO[Any, Throwable, Left[Int, String]]
val leftProjection = effect.left
// leftProjection: ZIO[Any, Option[Throwable], Int]
```

The error channel of `leftProjection` doesn't contain type information of the other side of the `Left[Int, String]`, which is `String`. So after projecting to the left, we can not go back to the original effect.

In ZIO 2.x, the `ZIO#left` and `ZIO#right`, contains all type information so then we can `unleft` or `unright` to inverse that projection:

```scala mdoc:nest
val effect         = ZIO.attempt(Left[Int, String](5))
val leftProjection = effect.left
val unlefted       = leftProjection.map(_ * 2).unleft 
```

So the error channel of the output of `left` and `right` operators is changed from `Option` to `Either`.

## Removed Methods

- **Arrow Combinators** — (`+++`, `|||`, `onSecond`, `onFirst`, `second`, `first`, `onRight`, `onLeft`, `andThen`, `>>>`, `compose`, `<<<`, `identity`, `swap`, `join`)

As the module pattern in ZIO 2.0 encourages users to use `Has` with the environment `R` (`Has[R]`), it doesn't make sense to have arrow combinators. An arrow makes the `R` parameter as the _input_ of the arrow function, and it doesn't match properly with environments with the `Has` data type. So In ZIO 2.0, all arrow combinators are removed, and we need to use alternatives like doing monadic for-comprehension style or `flatMap`, `provide`, `zip`, and so on.

## Deprecated Methods

In ZIO 2.0, the name of constructors and operators becomes more ergonomic and simple. They reflect more about their purpose rather than just using idiomatic jargon of category theory or functional terms in functional programming with Haskell.

Here are some of the most important changes:

- **Multiple ways of doing the same thing are removed** — For example:
    - Both `ZIO.succeed` and `ZIO.effectTotal` do the same thing. So in ZIO 2.0 we just have one version of these constructors which is `ZIO.succeed`.
    - The bind operator `>>=` is removed. So we just have one way to flatMap which is the `flatMap` method. Therefore, the `>>=` method doesn't surprise the non-Haskellers.
    - The `ZIO#get` method was essentially a more constrained version of `ZIO#some`. So the `get` method is deleted.

- **ZIO.attempt instead of ZIO.effect** — In ZIO 2.0 all ZIO constructors like `ZIO.effect*` that create a ZIO from a side effect are deprecated and renamed to the `ZIO.attempt*` version. For example, when we are reading from a file, it's more meaning full to say we are attempting to read from a file instead of saying we have an effect of reading from a file.

- **`ZIO` instead of the `M` suffix** — In effectful operations, the `M` suffix is renamed to the `ZIO` suffix. In ZIO 1.x, the `M` suffix in an effectful operation means that the operation works with monad in a monadic context. This naming convention is the legacy of Haskell jargon. In ZIO 2.x, all these suffixes are renamed to the `ZIO`. For example, the `ifM` operator is renamed to `ifZIO`.

- **`Discard` instead of the underscore `_` suffix** — The underscore suffix is another legacy naming convention from Haskell's world. In ZIO 1.x, the underscore suffix means we are going to discard the result. The underscore version works exactly like the one without the underscore, but it discards the result and returns `Unit` in the ZIO context. For example, the `collectAll_` operator renamed to `collectAllDiscard`.

- **`as`, `to`, `into` prefixes** — The `ZIO#asService` method is renamed to `ZIO#toLayer` and also the `ZIO#to` is renamed to the `ZIO#intoPromise`. So now we have three categories of conversion:
    1. **as** — The `ZIO#as` method and its variants like `ZIO#asSome`, `ZIO#asSomeError` and `ZIO#asService` are used when transforming the `A` inside of a `ZIO`, generally as shortcuts for `map(aToFoo(_))`.
    2. **to** — The `ZIO#to` method and its variants like `ZIO#toLayer`, `ZIO#toManaged`, and `ZIO#toFuture` are used when the `ZIO` is transformed into something else other than `ZIO` data-type.
    3. **into** — All `into*` methods, accept secondary data-type, modify it with the result of the current effect (e.g. `ZIO#intoPromise`, `ZStream#intoHub`, `ZStream#intoQueue` and `ZStream#intoManaged`)
    
### ZIO

| ZIO 1.x                      | ZIO 2.x                       |
|------------------------------|-------------------------------|
| ZIO#>>=                      | ZIO#flatMap                   |
| ZIO#bimap                    | ZIO#mapBoth                   |
| ZIO#mapEffect                | ZIO#mapAttempt                |
| ZIO#filterOrElse_            | ZIO#filterOrElse              |
| ZIO#foldCauseM               | ZIO#foldCauseZIO              |
| ZIO#foldM                    | ZIO#foldZIO                   |
| ZIO#foldTraceM               | ZIO#foldTraceZIO              |
|                              |                               |
| ZIO#get                      | ZIO#some                      |
| ZIO#optional                 | ZIO#unoption                  |
| ZIO#someOrElseM              | ZIO#someOrElseZIO             |
|                              |                               |
| ZIO.forkAll_                 | ZIO.forkAllDiscard            |
| ZIO.fromFiberM               | ZIO.fromFiberZIO              |
| ZIO.require                  | ZIO.someOrFail                |
| ZIO#on                       | ZIO#lockExecutionContext      |
| ZIO#rejectM                  | ZIO#rejectZIO                 |
| ZIO#run                      | ZIO#exit                      |
| ZIO#timeoutHalt              | ZIO#timeoutFailCause          |
|                              |                               |
| ZIO#to                       | ZIO#intoPromise               |
| ZIO#asService                | ZIO#toLayer                   |
|                              |                               |
| ZIO.accessM                  | ZIO.accessZIO                 |
| ZIO.fromFunctionM            | ZIO.accessZIO                 |
| ZIO.fromFunction             | ZIO.access                    |
| ZIO.services                 | ZIO.service                   |
|                              |                               |
| ZIO.bracket                  | ZIO.acquireReleaseWith        |
| ZIO.bracketExit              | ZIO.acquireReleaseExitWith    |
| ZIO.bracketAuto              | ZIO.acquireReleaseWithAuto    |
| ZIO#bracket                  | ZIO#acquireReleaseWith        |
| ZIO#bracket_                 | ZIO#acquireRelease            |
| ZIO#bracketExit              | ZIO#acquireReleaseExitWith    |
| ZIO#bracketExit              | ZIO#acquireReleaseExitWith    |
| ZIO#bracketOnError           | ZIO#acquireReleaseOnErrorWith |
| ZIO#toManaged_               | ZIO#toManaged                 |
|                              |                               |
| ZIO.collectAll_              | ZIO.collectAllDiscard         |
| ZIO.collectAllPar_           | ZIO.collectAllParDiscard      |
| ZIO.collectAllParN_          | ZIO.collectAllParNDiscard     |
| ZIO#collectM                 | ZIO#collectZIO                |
|                              |                               |
| ZIO.effect                   | ZIO.attempt                   |
| ZIO.effectAsync              | ZIO.async                     |
| ZIO.effectAsyncInterrupt     | ZIO.asyncInterrupt            |
| ZIO.effectAsyncM             | ZIO.asyncZIO                  |
| ZIO.effectAsyncMaybe         | ZIO.asyncMaybe                |
| ZIO.effectBlocking           | ZIO.attemptBlocking           |
| ZIO.effectBlockingCancelable | ZIO.attemptBlockingCancelable |
| ZIO.effectBlockingIO         | ZIO.attemptBlockingIO         |
| ZIO.effectBlockingInterrupt  | ZIO.attemptBlockingInterrupt  |
| ZIO.effectSuspend            | ZIO.suspend                   |
| ZIO.effectSuspendTotal       | ZIO.suspendSucceed            |
| ZIO.effectSuspendTotalWith   | ZIO.suspendSucceedWith        |
| ZIO.effectSuspendWith        | ZIO.suspendWith               |
| ZIO.effectTotal              | ZIO.succeed                   |
|                              |                               |
| ZIO.foreach_                 | ZIO.foreachDiscard            |
| ZIO.foreachPar_              | ZIO.foreachParDiscard         |
| ZIO.foreachParN_             | ZIO.foreachParNDiscard        |
| ZIO#replicateM               | ZIO#replicateZIO              |
| ZIO#replicateM_              | ZIO#replicateZIODiscard       |
|                              |                               |
| ZIO.halt                     | ZIO.failCause                 |
| ZIO.haltWith                 | ZIO.failCauseWith             |
|                              |                               |
| ZIO.ifM                      | ZIO.ifZIO                     |
| ZIO.loop_                    | ZIO.loopDiscard               |
| ZIO.whenCaseM                | ZIO.whenCaseZIO               |
| ZIO.whenM                    | ZIO.whenZIO                   |
| ZIO.unlessM                  | ZIO.unlessZIO                 |
| ZIO#unlessM                  | ZIO#unlessZIO                 |
| ZIO#whenM                    | ZIO#whenZIO                   |
| ZIO#repeatUntilM             | ZIO#repeatUntilZIO            |
| ZIO#repeatWhileM             | ZIO#repeatWhileZIO            |
| ZIO#retryUntilM              | ZIO#retryUntilZIO             |
| ZIO#retryWhileM              | ZIO#retryWhileZIO             |
| ZIO.replicateM               | ZIO.replicateZIO              |
| ZIO.replicateM_              | ZIO.replicateZIODiscard       |
|                              |                               |
|                              |                               |
| ZIO.validate_                | ZIO.validateDiscard           |
| ZIO.validatePar_             | ZIO.validateParDiscard        |

### ZLayer

| ZIO 1.x                  | ZIO 2.x                    |
|--------------------------|----------------------------|
| ZLayer.fromEffect        | ZLayer.fromZIO             |
| ZLayer.fromEffectMany    | ZLayer.fromZIOMany         |
| ZLayer.fromFunctionM     | ZLayer.fromFunctionZIO     |
| ZLayer.fromFunctionManyM | ZLayer.fromFunctionManyZIO |
