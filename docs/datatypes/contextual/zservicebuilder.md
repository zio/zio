---
id: zservicebuilder
title: "ZServiceBuilder"
---

A `ZServiceBuilder[-RIn, +E, +ROut]` describes a service builder of an application: every service builder in an application requires some services as input `RIn` and produces some services as the output `ROut`. 

Service builders are:

1. **Recipes for Creating Services** — They describe how a given dependencies produces another services. For example, the `ZServiceBuilder[Logging with Database, Throwable, UserRepo]` is a recipe for building a service that requires `Logging` and `Database` service, and it produces a `UserRepo` service.

2. **An Alternative to Constructors** — We can think of `ZServiceBuilder` as a more powerful version of a constructor, it is an alternative way to represent a constructor. Like a constructor, it allows us to build the `ROut` service in terms of its dependencies (`RIn`).

3. **Composable** — Because of their excellent **composition properties**, service builders are the idiomatic way in ZIO to create services that depend on other services. We can define service builders that are relying on each other. 

4. **Effectful and Resourceful** — The construction of ZIO service builders can be effectful and resourceful, they can be acquired and safely released when the services are done being utilized.

5. **Asynchronous** — Unlike class constructors which are blocking, ZServiceBuilder is fully asynchronous and non-blocking.

For example, a `ZServiceBuilder[Blocking with Logging, Throwable, Database]` can be thought of as a function that map `Blocking` and `Logging` services into `Database` service: 

```scala
(Blocking, Logging) => Database
```

So we can say that the `Database` service has two dependencies: `Blocking` and `Logging` services.

Let's see how we can create a service builder:

## Creation

`ZServiceBuilder` is an **alternative to a class constructor**, a recipe to create a service. This recipe may contain the following information:

1. **Dependencies** — To create a service, we need to indicate what other service we are depending on. For example, a `Database` service might need `Socket` and `Blocking` services to perform its operations.

2. **Acquisition/Release Action** — It may contain how to initialize a service. For example, if we are creating a recipe for a `Database` service, we should provide how the `Database` will be initialized, via acquisition action. Also, it may contain how to release a service. For example, how the `Database` releases its connection pools.

In some cases, a `ZServiceBuilder` may not have any dependencies or requirements from the environment. In this case, we can specify `Any` for the `RIn` type parameter. The `ServiceBuilder` type alias provided by ZIO is a convenient way to define a service builder without requirements.

There are many ways to create a ZServiceBuilder. Here's an incomplete list:
 - `ZServiceBuilder.succeed` to create a service builder from an existing service
 - `ZServiceBuilder.succeedMany` to create a service builder from a value that's one or more services
 - `ZServiceBuilder.fromFunction` to create a service builder from a function from the requirement to the service
 - `ZServiceBuilder.fromEffect` to lift a `ZIO` effect to a service builder requiring the effect environment
 - `ZServiceBuilder.fromAcquireRelease` for a service builder based on resource acquisition/release. The idea is the same as `ZManaged`.
 - `ZServiceBuilder.fromService` to build a service builer from a service
 - `ZServiceBuilder.fromServices` to build a service builder from a number of required services
 - `ZServiceBuilder.identity` to express the requirement for a dependency
 - `ZIO#toServiceBuilder` or `ZManaged#toServiceBuilder` to construct a service builder from an effect

Where it makes sense, these methods have also variants to build a service effectfully (suffixed by `ZIO`), resourcefully (suffixed by `Managed`), or to create a combination of services (suffixed by `Many`).

Let's review some of the `ZServiceBuilder`'s most useful constructors:

### From Simple Values

With `ZServiceBuilder.succeed` we can construct a `ZServiceBuilder` from a value. It returns a `UServiceBuilder[A]` value, which represents a service builder of an application that _has_ a service of type `A`:

```scala
def succeed[A: Tag](a: A): UServiceBuilder[A]
```

In the following example, we are going to create a `nameServiceBuilder` that provides us the name of `Adam`.

```scala mdoc:invisible
import zio._
```

```scala mdoc:silent
val nameServiceBuilder: UServiceBuilder[String] = ZServiceBuilder.succeed("Adam")
```

In most cases, we use `ZServiceBuilder.succeed` to create a service builder of type `A`.

For example, assume we have written the following service:

```scala mdoc:silent
object terminal {
  type Terminal = Terminal.Service

  object Terminal {
    trait Service {
      def printLine(line: String): UIO[Unit]
    }

    object Service {
      val live: Service = new Service {
        override def printLine(line: String): UIO[Unit] =
          ZIO.succeed(println(line))
      }
    }
  }
}
```

Now we can create a `ZServiceBuilder` from the `live` version of this service:

```scala mdoc:silent
import terminal._
val live: ZServiceBuilder[Any, Nothing, Terminal] = ZServiceBuilder.succeed(Terminal.Service.live)
```

### From Managed Resources

Some components of our applications need to be managed, meaning they undergo a resource acquisition phase before usage, and a resource release phase after usage (e.g. when the application shuts down). 

Fortunately, the construction of ZIO service builders can be effectful and resourceful, this means they can be acquired and safely released when the services are done being utilized.

`ZServiceBuilder` relies on the powerful `ZManaged` data type and this makes this process extremely simple.

We can lift any `ZManaged` to `ZServiceBuilder` by providing a managed resource to the `ZIO.fromManaged` constructor:

```scala mdoc:invisible
import scala.io.BufferedSource
```

```scala mdoc:silent:nest
val managedFile = ZManaged.fromAutoCloseable(
  ZIO.attempt(scala.io.Source.fromFile("file.txt"))
)
val fileServiceBuilder: ZServiceBuilder[Any, Throwable, BufferedSource] = 
  ZServiceBuilder.fromManaged(managedFile)
```

Also, every `ZManaged` can be converted to `ZServiceBuilder` by calling `ZServiceBuilder#toServiceBuilder`:

```scala mdoc:silent:nest
val fileServiceBuilder: ZServiceBuilder[Any, Throwable, BufferedSource] = managedFile.toServiceBuilder
```

Let's see another real-world example of creating a service builder from managed resources. Assume we have written a managed `UserRepository`:

```scala mdoc:invisible:reset
import zio._
import zio.Console._
import scala.io.Source._
import java.io.{FileInputStream, FileOutputStream, Closeable}

trait DBConfig
trait Transactor

def dbConfig: Task[DBConfig] = Task.attempt(???)
def initializeDb(config: DBConfig): Task[Unit] = Task.attempt(???)
def makeTransactor(config: DBConfig): ZManaged[Any, Throwable, Transactor] = ???

case class UserRepository(xa: Transactor)
object UserRepository {
  def apply(xa: Transactor): UserRepository = new UserRepository(xa) 
}
```

```scala mdoc:silent:nest
def userRepository: ZManaged[Console, Throwable, UserRepository] = for {
  cfg <- dbConfig.toManaged
  _ <- initializeDb(cfg).toManaged
  xa <- makeTransactor(cfg)
} yield new UserRepository(xa)
```

We can convert that to `ZServiceBuilder` with `ZServiceBuilder.fromManaged` or `ZManaged#toServiceBuilder`:

```scala mdoc:nest
val usersServiceBuilder  = userRepository.toServiceBuilder
val usersServiceBuilder_ = ZServiceBuilder.fromManaged(userRepository)
```

Also, we can create a `ZServiceBuilder` directly from `acquire` and `release` actions of a managed resource:

```scala mdoc:nest
def acquire = ZIO.attempt(new FileInputStream("file.txt"))
def release(resource: Closeable) = ZIO.succeed(resource.close())

val inputStreamServiceBuilder = ZServiceBuilder.fromAcquireRelease(acquire)(release)
```

### From ZIO Effects

We can create `ZServiceBuilder` from any `ZIO` effect by using `ZServiceBuilder.fromEffect` constructor, or calling `ZIO#toServiceBuilder` method:

```scala mdoc
val serviceBuilder = ZServiceBuilder.fromZIO(ZIO.succeed("Hello, World!"))
val serviceBuilder_ = ZIO.succeed("Hello, World!").toServiceBuilder
```

Assume we have a `ZIO` effect that read the application config from a file, we can create a service builder from that:

```scala mdoc:invisible
trait AppConfig
```

```scala mdoc:nest
def loadConfig: Task[AppConfig] = Task.attempt(???)
val configServiceBuilder = ZServiceBuilder.fromZIO(loadConfig)
```

### From another Service

Every `ZServiceBuilder` describes an application that requires some services as input and produces some services as output. Sometimes when we are creating a service builder, we may need to access and depend on one or several services.

The `ZServiceBuilder.fromService` construct a service builder that purely depends on the specified service:

```scala
def fromService[A: Tag, B: Tag](f: A => B): ZServiceBuilder[A, Nothing, B]
```

Assume we want to write a `live` version of the following logging service:

```scala mdoc:silent:nest
object logging {
  type Logging = Logging.Service

  object Logging {
    trait Service {
      def log(msg: String): UIO[Unit]
    }
  }
}
```

We can create that by using `ZServiceBuilder.fromService` constructor, which depends on `Console` service:

```scala mdoc:invisible
import logging.Logging
import logging.Logging._
```

```scala mdoc:silent:nest:warn
val live: ZServiceBuilder[Console, Nothing, Logging] = ZServiceBuilder.fromService(console =>
  new Service {
    override def log(msg: String): UIO[Unit] = console.printLine(msg).orDie
  }
)
```

## Vertical and Horizontal Composition

We said that we can think of the `ZServiceBuilder` as a more powerful _constructor_. Constructors are not composable, because they are not values. While a constructor is not composable, `ZServiceBuilder` has a nice facility to compose with other `ZServiceBuilder`s. So we can say that a `ZServiceBuilder` turns a constructor into values.

`ZServiceBuilder`s can be composed together horizontally or vertically:

1. **Horizontal Composition** — They can be composed together horizontally with the `++` operator. When we compose service builders horizontally, the new service builder requires all the services that both of them require and produces all services that both of them produce. Horizontal composition is a way of composing two service builders side-by-side. It is useful when we combine two service builders that they don't have any relationship with each other. 

2. **Vertical Composition** — If we have a service builder that requires `A` and produces `B`, we can compose this with another service builder that requires `B` and produces `C`; this composition produces a service builder that requires `A` and produces `C`. The feed operator, `>>>`, stack them on top of each other by using vertical composition. This sort of composition is like _function composition_, feeding an output of one service builder to an input of another.

Let's get into an example, assume we have these services with their implementations:

```scala mdoc:invisible:reset
import zio._
```

```scala mdoc:silent:nest
trait Logging { }
trait Database { }
trait BlobStorage { }
trait UserRepo { }
trait DocRepo { }

case class LoggerImpl(console: Console) extends Logging { }
case object DatabaseImp extends Database { }
case class UserRepoImpl(logging: Logging, database: Database) extends UserRepo { } 
case class BlobStorageImpl(logging: Logging) extends BlobStorage { }
case class DocRepoImpl(logging: Logging, database: Database, blobStorage: BlobStorage) extends DocRepo { }
```

We can't compose these services together, because their constructors are not value. `ZServiceBuilder` can convert these services into values, then we can compose them together.

Let's assume we have lifted these services into `ZServiceBuilder`s:

```scala mdoc:silent
val logging: URServiceBuilder[Console, Logging] = 
  (LoggerImpl.apply _).toServiceBuilder
val database: URServiceBuilder[Any, Database] = 
  ZServiceBuilder.succeed(DatabaseImp)
val userRepo: URServiceBuilder[Logging with Database, UserRepo] = 
  (UserRepoImpl(_, _)).toServiceBuilder
val blobStorage: URServiceBuilder[Logging, BlobStorage] = 
  (BlobStorageImpl(_)).toServiceBuilder
val docRepo: URServiceBuilder[Logging with Database with BlobStorage, DocRepo] = 
  (DocRepoImpl(_, _, _)).toServiceBuilder
```

Now, we can compose logging and database horizontally:

```scala mdoc:silent
val newServiceBuilder: ZServiceBuilder[Console, Throwable, Logging with Database] = logging ++ database
```

And then we can compose the `newServiceBuilder` with `userRepo` vertically:

```scala mdoc:silent
val myServiceBuilder: ZServiceBuilder[Console, Throwable, UserRepo] = newServiceBuilder >>> userRepo
```

## Service Builder Memoization

One important feature of `ZIO` service builders is that **they are shared by default**, meaning that if the same service builder is used twice, the service builder will only be allocated a single time. 

For every service builder in our dependency graph, there is only one instance of it that is shared between all the service builders that depend on it. 

If we don't want to share a module, we should create a fresh, non-shared version of it through `ZServiceBuilder#fresh`.

## Updating Local Dependencies

```scala mdoc:invisible:reset
import zio._

trait DBError
trait Product
trait ProductId
trait DBConnection
case class User(id: UserId, name: String)
case class UserId(value: Long)


type UserRepo = UserRepo.Service

object UserRepo {
  trait Service {
    def getUser(userId: UserId): IO[DBError, Option[User]]
    def createUser(user: User): IO[DBError, Unit]
  }

  // This simple in-memory version has no dependencies.
  // This could be useful for tests where you don't want the additional
  // complexity of having to manage DB Connections.
  val inMemory: ServiceBuilder[Nothing, UserRepo] = ZServiceBuilder.succeed(
    new Service {
      def getUser(userId: UserId): IO[DBError, Option[User]] = UIO(???)
      def createUser(user: User): IO[DBError, Unit] = UIO(???)
    }
  )

  //accessor methods
  def getUser(userId: UserId): ZIO[UserRepo, DBError, Option[User]] =
    ZIO.accessZIO(_.get.getUser(userId))

  def createUser(user: User): ZIO[UserRepo, DBError, Unit] =
    ZIO.accessZIO(_.get.createUser(user))
}


type Logging = Logging.Service

object Logging {
  trait Service {
    def info(s: String): UIO[Unit]
    def error(s: String): UIO[Unit]
  }

  val consoleLogger: ZServiceBuilder[Console, Nothing, Logging] = ZServiceBuilder.fromFunction( console =>
    new Service {
      def info(s: String): UIO[Unit]  = console.printLine(s"info - $s").orDie
      def error(s: String): UIO[Unit] = console.printLine(s"error - $s").orDie
    }
  )

  //accessor methods
  def info(s: String): URIO[Logging, Unit] =
    ZIO.accessZIO(_.get.info(s))

  def error(s: String): URIO[Logging, Unit] =
    ZIO.accessZIO(_.get.error(s))
}



import java.sql.Connection
def makeConnection: UIO[Connection] = UIO(???)
val connectionServiceBuilder: ServiceBuilder[Nothing, Connection] =
    ZServiceBuilder.fromAcquireRelease(makeConnection)(c => UIO(c.close()))
val postgresServiceBuilder: ZServiceBuilder[Connection, Nothing, UserRepo] =
  ZServiceBuilder.fromFunction { hasC: Connection =>
    new UserRepo.Service {
      override def getUser(userId: UserId): IO[DBError, Option[User]] = UIO(???)
      override def createUser(user: User): IO[DBError, Unit] = UIO(???)
    }
  }

val fullRepo: ServiceBuilder[Nothing, UserRepo] = connectionServiceBuilder >>> postgresServiceBuilder



val user2: User = User(UserId(123), "Tommy")
val makeUser: ZIO[Logging with UserRepo, DBError, Unit] = for {
  _ <- Logging.info(s"inserting user")  // URIO[Logging, Unit]
  _ <- UserRepo.createUser(user2)       // ZIO[UserRepo, DBError, Unit]
  _ <- Logging.info(s"user inserted")   // URIO[Logging, Unit]
} yield ()


// compose horizontally
val horizontal: ZServiceBuilder[Console, Nothing, Logging with UserRepo] = Logging.consoleLogger ++ UserRepo.inMemory

// fulfill missing services, composing vertically
val fullServiceBuilder: ServiceBuilder[Nothing, Logging with UserRepo] = Console.live >>> horizontal

// provide the services to the program
makeUser.provideSome(fullServiceBuilder)
```

Given a service builder, it is possible to update one or more components it provides. We update a dependency in two ways:

1. **Using the `update` Method** — This method allows us to replace one requirement with a different implementation:

```scala mdoc:silent:nest
val withPostgresService = horizontal.update[UserRepo.Service]{ oldRepo  => new UserRepo.Service {
      override def getUser(userId: UserId): IO[DBError, Option[User]] = UIO(???)
      override def createUser(user: User): IO[DBError, Unit] = UIO(???)
    }
  }
```

2. **Using Horizontal Composition** — Another way to update a requirement is to horizontally compose in a service builder that provides the updated service. The resulting composition will replace the old service builder with the new one:

```scala mdoc:silent:nest
val dbServiceBuilder: ServiceBuilder[Nothing, UserRepo] = ZServiceBuilder.succeed(new UserRepo.Service {
    override def getUser(userId: UserId): IO[DBError, Option[User]] = ???
    override def createUser(user: User): IO[DBError, Unit] = ???
  })

val updatedHorizontal2 = horizontal ++ dbServiceBuilder
```

## Hidden Versus Passed Through Dependencies

One design decision regarding building dependency graphs is whether to hide or pass through the upstream dependencies of a service. `ZServiceBuilder` defaults to hidden dependencies but makes it easy to pass through dependencies as well.

To illustrate this, consider the Postgres-based repository discussed above:

```scala mdoc:silent:nest
val connection: ZServiceBuilder[Any, Nothing, Connection] = connectionServiceBuilder
val userRepo: ZServiceBuilder[Connection, Nothing, UserRepo] = postgresServiceBuilder
val serviceBuilder: ZServiceBuilder[Any, Nothing, UserRepo] = connection >>> userRepo
```

Notice that in `serviceBuilder`, the dependency `UserRepo` has on `Connection` has been "hidden", and is no longer expressed in the type signature. From the perspective of a caller, `serviceBuilder` just outputs a `UserRepo` and requires no inputs. The caller does not need to be concerned with the internal implementation details of how the `UserRepo` is constructed.

To provide only some inputs, we need to explicitly define what inputs still need to be provided:

```scala mdoc:silent:nest
trait Configuration

val userRepoWithConfig: ZServiceBuilder[Configuration with Connection, Nothing, UserRepo] = 
  ZServiceBuilder.succeed(new Configuration{}) ++ postgresServiceBuilder
val partialServiceBuilder: ZServiceBuilder[Configuration, Nothing, UserRepo] = 
  (ZServiceBuilder.environment[Configuration] ++ connection) >>> userRepoWithConfig
``` 

In this example the requirement for a `Connection` has been satisfied, but `Configuration` is still required by `partialServiceBuilder`.

This achieves an encapsulation of services and can make it easier to refactor code. For example, say we want to refactor our application to use an in-memory database:

```scala mdoc:silent:nest
val updatedServiceBuilder: ZServiceBuilder[Any, Nothing, UserRepo] = dbServiceBuilder
```

No other code will need to be changed, because the previous implementation's dependency upon a `Connection` was hidden from users, and so they were not able to rely on it.

However, if an upstream dependency is used by many other services, it can be convenient to "pass through" that dependency, and include it in the output of a service builder. This can be done with the `>+>` operator, which provides the output of one service builder to another service builder, returning a new service builder that outputs the services of _both_.


```scala mdoc:silent:nest
val serviceBuilder: ZServiceBuilder[Any, Nothing, Connection with UserRepo] = connection >+> userRepo
```

Here, the `Connection` dependency has been passed through, and is available to all downstream services. This allows a style of composition where the `>+>` operator is used to build a progressively larger set of services, with each new service able to depend on all the services before it.

```scala mdoc:invisible
type Baker = Baker.Service
type Ingredients = Ingredients.Service
type Oven = Oven.Service
type Dough = Dough.Service
type Cake = Cake.Service

object Baker {
  trait Service
}

object Ingredients {
  trait Service
}

object Oven {
  trait Service
}

object Dough {
  trait Service
}

object Cake {
  trait Service
}
```

```scala mdoc
lazy val baker: ZServiceBuilder[Any, Nothing, Baker] = ???
lazy val ingredients: ZServiceBuilder[Any, Nothing, Ingredients] = ???
lazy val oven: ZServiceBuilder[Any, Nothing, Oven] = ???
lazy val dough: ZServiceBuilder[Baker with Ingredients, Nothing, Dough] = ???
lazy val cake: ZServiceBuilder[Baker with Oven with Dough, Nothing, Cake] = ???

lazy val all: ZServiceBuilder[Any, Nothing, Baker with Ingredients with Oven with Dough with Cake] =
  baker >+>       // Baker
  ingredients >+> // Baker with Ingredients
  oven >+>        // Baker with Ingredients with Oven
  dough >+>       // Baker with Ingredients with Oven with Dough
  cake            // Baker with Ingredients with Oven with Dough with Cake
```

`ZServiceBuilder` makes it easy to mix and match these styles. If you pass through dependencies and later want to hide them you can do so through a simple type ascription:

```scala mdoc:silent
lazy val hidden: ZServiceBuilder[Any, Nothing, Cake] = all
```

And if you do build your dependency graph more explicitly, you can be confident that dependencies used in multiple parts of the dependency graph will only be created once due to memoization and sharing.

## Cyclic Dependencies

The `ZServiceBuilder` mechanism makes it impossible to build cyclic dependencies, making the initialization process very linear, by construction.

## Asynchronous Service Construction

Another important note about `ZServiceBuilder` is that, unlike constructors which are synchronous, `ZServiceBuilder` is _asynchronous_. Constructors in classes are always synchronous. This is a drawback for non-blocking applications. Because sometimes we might want to use something that is blocking the inside constructor.

For example, when we are constructing some sort of Kafka streaming service, we might want to connect to the Kafka cluster in the constructor of our service, which takes some time. So that wouldn't be a good idea to blocking inside a constructor. There are some workarounds for fixing this issue, but they are not perfect as the ZIO solution.

Well, with ZIO ZServiceBuilder, our constructor could be asynchronous, and they also can block definitely. And that is because `ZServiceBuilder` has the full power of ZIO. And as a result, we have strictly more power on our constructors with ZServiceBuilder. 

We can acquire resources asynchronously or in a blocking fashion, and spend some time doing that, and we don't need to worry about it. That is not an anti-pattern. This is the best practice with ZIO.

## Examples

### The simplest ZServiceBuilder application

This application demonstrates a ZIO program with a single dependency on a simple string value:

```scala mdoc:compile-only
import zio._

object Example extends ZIOAppDefault {

  // Define our simple ZIO program
  val zio: ZIO[String, Nothing, Unit] = for {
    name <- ZIO.access[String](_.get)
    _    <- UIO(println(s"Hello, $name!"))
  } yield ()

  // Create a ZServiceBuilder that produces a string and can be used to satisfy a string
  // dependency that the program has
  val nameServiceBuilder: UServiceBuilder[String] = ZServiceBuilder.succeed("Adam")

  // Run the program, providing the `nameServiceBuilder`
  def run = zio.provideSome(nameServiceBuilder)
}

```

### ZServiceBuilder application with dependencies 

In the following example, our ZIO application has several dependencies:
 - `zio.Clock`
 - `zio.Console`
 - `ModuleB`

`ModuleB` in turn depends upon `ModuleA`:

```scala
import zio._
import zio.Clock._
import zio.Console._
import java.io.IOException

object moduleA {
  type ModuleA = ModuleA.Service

  object ModuleA {
    trait Service {
      def letsGoA(v: Int): UIO[String]
    }

    val any: ZServiceBuilder[ModuleA, Nothing, ModuleA] =
      ZServiceBuilder.requires[ModuleA]

    val live: ServiceBuilder[Nothing, Service] = ZServiceBuilder.succeed {
      new Service {
        def letsGoA(v: Int): UIO[String] = UIO(s"done: v = $v ")
      }
    }
  }

  def letsGoA(v: Int): URIO[ModuleA, String] =
    ZIO.accessZIO(_.get.letsGoA(v))
}

import moduleA._

object moduleB {
  type ModuleB = ModuleB.Service

  object ModuleB {
    trait Service {
      def letsGoB(v: Int): UIO[String]
    }

    val any: ZServiceBuilder[ModuleB, Nothing, ModuleB] =
      ZServiceBuilder.requires[ModuleB]

    val live: ZServiceBuilder[ModuleA, Nothing, ModuleB] = ZServiceBuilder.fromService { (moduleA: ModuleA.Service) =>
      new Service {
        def letsGoB(v: Int): UIO[String] =
          moduleA.letsGoA(v)
      }
    }
  }

  def letsGoB(v: Int): URIO[ModuleB, String] =
    ZIO.accessZIO(_.get.letsGoB(v))
}

object ZServiceBuilderApp0 extends zio.App {

  import moduleB._

  val env = Console.live ++ Clock.live ++ (ModuleA.live >>> ModuleB.live)
  val program: ZIO[Console with Clock with moduleB.ModuleB, IOException, Unit] =
    for {
      _ <- printLine(s"Welcome to ZIO!")
      _ <- sleep(1.second)
      r <- letsGoB(10)
      _ <- printLine(r)
    } yield ()

  def run(args: List[String]) =
    program.provideSome(env).exitCode

}

// output: 
// [info] running ZServiceBuilderApp 
// Welcome to ZIO!
// done: v = 10 
```

### ZServiceBuilder example with complex dependencies

In this example, we can see that `ModuleC` depends upon `ModuleA`, `ModuleB`, and `Clock`. The service builder provided to the runnable application shows how dependency service builders can be combined using `++` into a single combined service builder. The combined service builder will then be able to produce both of the outputs of the original sets as a single service builder:

```scala mdoc:compile-only
import zio._
import zio.Clock._

object ZServiceBuilderApp1 extends scala.App {
  val rt = Runtime.default

  type ModuleA = ModuleA.Service

  object ModuleA {

    trait Service {}

    val any: ZServiceBuilder[ModuleA, Nothing, ModuleA] =
      ZServiceBuilder.environment[ModuleA]

    val live: ZServiceBuilder[Any, Nothing, ModuleA] =
      ZServiceBuilder.succeed(new Service {})
  }

  type ModuleB = ModuleB.Service

  object ModuleB {

    trait Service {}

    val any: ZServiceBuilder[ModuleB, Nothing, ModuleB] =
      ZServiceBuilder.environment[ModuleB]

    val live: ZServiceBuilder[Any, Nothing, ModuleB] =
      ZServiceBuilder.succeed(new Service {})
  }

  type ModuleC = ModuleC.Service

  object ModuleC {

    trait Service {
      def foo: UIO[Int]
    }

    val any: ZServiceBuilder[ModuleC, Nothing, ModuleC] =
      ZServiceBuilder.environment[ModuleC]

    val live: ZServiceBuilder[ModuleA with ModuleB with Clock, Nothing, ModuleC] =
      ZServiceBuilder.succeed {
        new Service {
          val foo: UIO[Int] = UIO.succeed(42)
        }
      }

    val foo: URIO[ModuleC, Int] =
      ZIO.accessZIO(_.get.foo)
  }

  val env = (ModuleA.live ++ ModuleB.live ++ ZServiceBuilder.environment[Clock]) >>> ModuleC.live

  val res = ModuleC.foo.provideCustom(env)

  val out = rt.unsafeRun(res)
  println(out)
  // 42
}
```
