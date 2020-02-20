---
id: use_layers
title:  "Use modules and layers"
---

# Unleash ZIO environment with `ZLayer`

`ZIO` is designed around 3 parameters, `R, E, A`. `R` represents the _requirement_ for the effect to run, meaning we need to fulfill
the requirement in order to make the effect _runnable_. We will dedicate this post to explore what we can do with `R`, as it plays a crucial role in `ZIO`.

## A Simple case for `ZIO` environment
Let's build a simple program for user management, that must get a user (if exists), and create a user. 
To access the DB we need a `DBConnection`, and each step in out program represents this through the environment type. We can then combine the two (small) steps through `flatMap` or more conveniently through a `for` comprehension.

The result is a program that, in turn, depends on the `DBConnection`.

```scala
case class DBConnection(...)
def getUser(userId: UserId): ZIO[DBConnection, Nothing, Option[User]]
def createUser(user: User): ZIO[DBConnection, Nothing, Unit]

val user: User = ???
val created: ZIO[DBConnection, Nothing, Boolean] = for {
  maybeUser <- getUser(user.id)
  res       <- maybeUser.fold(createUser(user).as(true))( _ =>ZIO.successful(false))
} yield res
```

To run the program we must supply a `DBConnection` through `provide`, before feeding it to ZIO runtime.

```scala
val dbConnection: DBConnection = ???
val runnable: ZIO[Any, Nothing, boolean] = getProductWithRelated.provide(dbConnection)

val finallyCreated  = runtime.unsafeRun(runnable)
```

Notice that the act of `provide`ing an effect with its environment eliminates the environment dependency in the resulting effect type, well represented by type `Any` of the resulting environment.

In general we need more than just a DB connection though. We need components that enable us to perform different operations, and we need to be able to wire them together. This is what _modules_ are for.

## Our first ZIO module
The module pattern is _the way_ ZIO manages dependencies between application components.
In its initial formulation it was only using trait mix-ins, as shown in [Appendix](#appendix-the-classic-module-formulation-until-version-100-rc17).

Here we will see the new formulation of the module pattern, that resolves most of the shortcomings of the previous version.

### What is a module?
A module is a group of functions that deals with only one concern. Keeping the scope of a module limited improves our ability to understand code, in that we need to focus
 only on one topic at a time without juggling with too many concepts together in our head.
 
`ZIO` iself provides the basic capabilities through modules, e.g. see how `ZEnv` is defined.

### The module recipe
Let's build a module for user data access, following these simple steps:

1. Define an object that gives the name to the module, this can be (not necessarily) a package object
1. Within the module object define a `trait Service` that defines the interface our module is exposing, in our case 2 methods to retrieve a product details, and one to retrieve the related products
1. Within the module object define a type alias like `type ModuleName = Has[Service]` (see below for details on `Has`)
1. Within the module object define the different implementations of `ModuleName` through `ZLayer` (see below for details on `ZLayer`)

```scala mdoc:silent
import zio.{Has, ZLayer}

object UserRepo {
  trait Service {
    def getProductDetails(productId: ProductId): IO[DBError, Product]
    def getRelatedProducts(productId: ProductId): IO[DBError, List[Product]]
  }
  
  type UserRepo = Has[Service]
  val testRepo: ZLayer[Any, Nothing, UserRepo] = ???
  
}
```

We encountered two new data types `Has` and `ZLayer`, let's get familiar with them.

### The `Has` data type

`Has[A]` represents a dependency on a service of type `A`. Two `Has[_]` can be combined _horizontally_ through `+` and `++` operators, as in

```scala mdoc:silent
val repo: Has[Repo.Service] = ???
val logger: Has[Logger.Service] = ???

val mix: Has[Repo.Service] with Has[Logger.Service] = repo ++ logger
``` 

At this point you might ask: what's the use of `Has` if the resulting type is still a mix of two traits? The extra power given by `Has` is that the resulting data structure is backed by an _etherogeneus map_ from service type to service implementation, that collects each instance that is mixed in so that the instances can be accessed/extracted/modified individually, still guaranteeing supreme type safety.

```scala mdoc:silent
// get back the repo from the mixed value:
val log = mix.get[Has[Logger.Service]].log("Hello modules!")
```

As per the recipe above, it is extremely convenient to define a type alias for `Has[Service]`.
Usually we don't create a `Has` directly, but we do that through `ZLayer`.

### The `ZLayer` data type

`ZLayer[-RIn, +E, +ROut <: Has[_]]` is a recipe to build an environment of type `ROut`, starting from a value `RIn`, possibly producing an error `E` during creation. 

In adherence with environmental concepts, the absence of a required input is represented by `RIn = Any`, conveniently  used in the type alias `ZLayer#NoDeps`.

The `ZLayer` companion object offers a number of constructors to build layers (from pure values, from managed resources to guarantee acquire/release, from other services). 

We can compose `layerA` and `layerB`  _horizontally_ to build a layer that has the requirements of both layers, to provide the capabilities of both layers, through `layerA ++ layerB`
 
We can also compose layers _vertically_, meaning the output of one layer is used as input for the subsequent layer to build the next layer, resulting in one layer with the requirement of the first and the output of the second layer: `layerA >>> layerB` 

### Example: wiring modules together
Here we define a module to cope with CRUD operations for the `User` domain object. We provide also a live implemntation of the module that depends on a sql connection.

```scala mdoc:silent
object  userRepo {
  trait Service {
    def getUser(userId: UserId): IO[DBError, Option[User]]
    def createUser(user: User): IO[DBError, Unit]
  }

  type UserRepo = Has[Service]

  // This simple live version depends only on a DB Connection
  val inMemory: ZLayer.NoDeps[Nothing, UserRepo] = ZLayer.succeed(
      new Service {
        def getUser(userId: UserId): IO[DBError, Option[User]] = ???
        def createUser(user: User): IO[DBError, Unit] = ???
      }
    )

  //accessor methods
  def getUser(userId: UserId): ZIO[UserRepo, DBError, Option[User]] =
    ZIO.accessM(_.get.getUser(userId))

  def createUser(user: User): ZIO[UserRepo, DBError, Unit] =
    ZIO.accessM(_.get.createUser(user))
}
```

Then, we define another module to perform some basic logging. The live implementation requires a `logger: Logger`

```scala mdoc:silent
object logging {
  trait Service {
    def info(s: String): UIO[Unit]
    def error(s: String): UIO[Unit]
  }

  type Logging = Has[Service]

  val consoleLogger: ZLayer[Console, Nothing, Logging] = ZLayer.fromEnvironment( console =>
    Has(
      new Service {
        def info(s: String): UIO[Unit] = console.get.putStrLn(s"info - $s")
        def error(s: String): UIO[Unit] = console.get.putStrLn(s"error - $s")
      }
    )
  )

  //accessor methods
  def info(s: String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM(_.get.info(s))

  def error(s: String): ZIO[Logging, Nothing, Unit] =
    ZIO.accessM(_.get.error(s))
}
```

The acccessor methods are provided so that we can build programs without bothering about the implementation details of the required modules, the compiler will infer fully all the required modules to complete the task

```scala mdoc:silent
val user = User(123, "Chet")
val makeUser: ZIO[Logging with UserRepo, Nothing, Unit] = for {
  _ <- logging.info(s"inserting user") // ZIO[Logging, Nothing, Unit]
  _ <- userRepo.createUser(user)       // ZIO[UserRepo, DBError, Unit]
  _ <- logging.info(s"user inserted")  // ZIO[Logging, Nothing, Unit]
} yield ()
```

Given a program with these requirements, we can build the required layer:
```scala
// compose horizontally
val horizontal: ZLayer[Console, Nothing, Logging with UserRepo] = logging.consoleLogger ++ userRepo.inMemory

// fulfill missing deps, composing vertically
val fullLayer: ZLayer.NoDeps[Nothing, Logging with UserRepo] = Console.live >>> horizontal

// provide the layer to the program
makeUser.provideLayer(fullLayer)
  

```

### Example: providing partial environments
Let's add some extra logic to our program that creates a user

```scala mdoc:silent
val makeUser: ZIO[Logging with UserRepo with Clock with Random, DBError, Unit] = for {
    l         <- zio.random.nextLong.map(UserId)
    createdAt <- zio.clock.currentDateTime
    _         <- logging.info(s"inserting user")        
    _         <- userRepo.createUser(User(l, "Chet"))   
    _         <- logging.info(s"user inserted, created at $createdAt")
  } yield ()
```

Now the requirements of our program are richer, and we can satisfy the partially by providing our custom layers, and leaving out the layers that are covered by the standard environment `ZEnv`, in one line of code

```scala mdoc:silent
  val zEnvMakeUser: ZIO[ZEnv, DBError, Unit] = makeUser2.provideCustomLayer(fullLayer)
```

Notice that `provideCustomLayer` is just a special case of `provideSomeLayer`.

## APPENDIX: The _classic_ module formulation (until version 1.0.0-RC17)

Let's see how a service to manage users was formulated in the classic way.

Here we define a module to cope with CRUD operations for the `User` domain object

```scala mdoc:silent
trait UserRepo {
  val userRepo: UserRepo.Service
}

object UserRepo {
  trait Service {
    def getUser(userId: UserId): IO[DBError, Option[User]]
    def createUser(user: User): IO[DBError, Unit]
  }
  
  trait Live extends UserRepo {
    val dbConnection: Connection
    val userRepo: Service = new Service {
      private def runSql[A](sql: String): IO[DBError, A] = /* use dbConnection */

      def getUser(userId: UserId): IO[DBError, Option[User]] = runSql[Option[User]]("select * from users where id = $userId")
      def createUser(user: User): IO[DBError, Unit] = runSql[Unit](s"insert into users values (${user.id}, ${user.name}")
    }
  }

  //accessor methods
  def getUser(userId: UserId): ZIO[UserRepo, DBError, Option[User]] =
    ZIO.accessM(_.userRepo.getUser(userId))
  
  def createUser(user: User): ZIO[UserRepo, DBError, Unit] =
    ZIO.accessM(_.userRepo.createUser(user))

}
```

And a module to cope with logging

```scala mdoc:silent
trait Logging {
  val logging: Logging.Service
}

object Logging {
  trait Service {
    def info(s: String): UIO[Unit]
    def error(s: String): UIO[Unit]
  }

  trait Live extends Logging {
    val logger: Logger
    val logging: Logging.Service = new Service {
      def info(s: String): UIO[Unit] = logger.info(s)
      def error(s: String): UIO[Unit] = logger.error(s)      
    }
  }
  
  //accessor methods
  def info(s: String): ZIO[Logging, Nothing, Unit] = 
    ZIO.accessM(_.logging.info(s))

  def error(s: String): ZIO[Logging, Nothing, Unit] = 
    ZIO.accessM(_.logging.error(s))

}
```

Now we can combine operations provided by the different modules through the various combinators provided by ZIO, e.g. `flatMap`
```scala mdoc:silent
val user = User(123, "Chet")
val makeUser: ZIO[Logging with UserRepo, Nothing, Unit] = for {
  _ <- Logging.info(s"inserting user") // ZIO[Logging, Nothing, Unit]
  _ <- UserRepo.createUser(user)       // ZIO[UserRepo, DBError, Unit]
  _ <- Logging.info(s"user inserted")  // ZIO[Logging, Nothing, Unit]
} yield ()
```

Note that the environment type of `makeUser` is fully inferred by the compiler, and it expresses the fact that to our program requires an environment of type `Logging with UserRepo`.

To run the program we must satisfy its requirements, and feed the corresponding value to ZIO runtime

```scala mdoc:silent
val env: Logging with UserRepo = new Logging.Live with UserRepo.Live {
  val logger: Logger = Logger(LoggerFactory.getLogger("classic-module-pattern"))
  val dbConnection: Connection = ??? //this must be injected or passed somehow
}

val runnable: ZIO[Any, DBError, Unit] = makeUser.provide(env) // this effect has no requirements, it can be run 

defaultRuntime.unsafeRun(runnable)
```




#### Provide partial environments
Let's suppose we have our `makeUser: ZIO[Logging with UserRepo, Nothing, Unit] ` and we want to satisfy just part of the requirement, e.g. we want to keep the logging part but delay the 
provisioning of `UserRepo`. `ZIO[R, E, A]` has a method `provideSome[R0](f: R0 => R): ZIO[R0, E, A]` that builds the required environment starting from a part of it

```scala mdoc:silent

val makeUser: ZIO[Logging with UserRepo, Nothing, Unit] = ??? //see above 
val makeUserForRepo: ZIO[UserRepo, Nothing, Unit] = makeUser.provideSome[UserRepo] { env =>
  new Logging with UserRepo {
    val logging = Logging.Live.logging
    val userRepo = env.userRepo
  }
}
```

In this case we had a simple environment, but in case of environments coming from mixing many layers this process can be very tedious. 
The most common case is the one of a partial provisioning of an environment mixed in with `ZEnv` 
(remember `type ZEnv = Clock with Console with System with Random with Blocking`):

```scala mdoc:silent
val program: ZIO[ZEnv with UserRepo, Nothing, Unit] = ???

val programForRepo: ZIO[ZEnv, Nothing, Unit] = program.provideSome[ZEnv] { env =>
  new ZEnv with UserRepo {
    override val clock    = env.clock
    override val console  = env.console
    override val system   = env.system
    override val random   = env.random
    override val blocking = env.blocking
    val userRepo          = new UserRepo.Live
  }
}
```

While working, this approach is not really satisfactoring and daunting to people not familiar with environmental effects.


#### Vertical composition: a module depending on another module
In many cases we have modules depending on other modules, e.g. we could have a module `UserValidation` and a `User` module that depends on `UserRepo` and `Validation`. 
The way we can encode this is by forcing the depending module to be mixed in with the dependee modules. One way is to declare the services we need from the required modules as `val` of the module implementation (e.g. the `Live` implementation can have richer requirement than a test implementation)

```scala mdoc:silent
trait UserValidation {
  val userValidation: UserValidation.Service 
}

object UserValidation {
  trait Service {
    def validate(user: User): IO[ValidationError, Unit]
  }

  trait Live { /* Live implementation, e.g. calling a webservice to identity document validity */}
}

trait UserService {
  val userService: UserService.Service
}
object UserService {
  trait Service {
    def registerUser(user: User): IO[ApplicationError, Unit]
    def getUser(userId: UserID): IO[ApplicationError, Option[User]]
  }
  
  trait Live extends UserService {
    val userRepo: UserRepo.Service
    val userValidation: UserValidation.Service

    val userService = new Service {
      def registerUser(user: User): IO[ApplicationError, Unit] = for {
        _ <- userValidation.validate(user)
        _ <- userRepo.createUser(user)
      } yield ()

      def getUser(userId: UserID): IO[ApplicationError, Option[User]] = userRepo.getUser(userId)
    }
  }

}
```

When we have a program that requires `UserService`, e.g. `prg: ZIO[UserService, Nothing, Unit]` we can fulfull the requirements starting from the required module, e.g. `prg.provide(new UserService)`, the compiler will guide us in fulfilling all the required modules by mixing in all the modules our module depends on.