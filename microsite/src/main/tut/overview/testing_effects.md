---
layout: docs
section: overview
title:  "Testing Effects"
---

# {{page.title}}

There are many approaches to testing functional effects, including using free monads, using tagless-final, and using environmental effects. Although all of these approaches are compatible with ZIO, the simplest and most natural is _environmental effects_.

This section introduces environmental effects and shows how to write testable functional code using them.

```scala mdoc:invisible
import scalaz.zio._
import scalaz.zio.console._
```

# Environments

The ZIO data type has an `R` type parameter, which is used to describe the type of _environment_ required by the effect. 

ZIO effects can access the environment using `ZIO.environment`, which provides direct access to the environment, as a value of type `R`.

```scala mdoc:silent
for {
  env <- ZIO.environment[Int]
  _   <- putStrLn(s"The value of the environment is: $env")
} yield env
```

The environment does not have to be a primitive value like an integer. It can be much more complex. 

When the environment is a type with fields, then the `ZIO.access` method can be used to access a given part of the environment in a single method call.

```scala mdoc:silent
case class Config(server: String, port: Int)

val configString: ZIO[Config, Nothing, String] = 
  for {
    server <- ZIO.access[Config](_.server)
    port   <- ZIO.access[Config](_.port)
  } yield s"Server: $server, port: $port"
```

Effects themselves can be stored in the environment. In this case, to access and execute an effect, the `ZIO.accessM` method can be used.

```scala mdoc:silent
trait DatabaseOps {
  def tableNames: Task[List[String]]
  def columnNames(table: String): Task[List[String]]
}

val tablesAndColumns: ZIO[DatabaseOps, Throwable, (List[String], List[String])] = 
  for {
    tables  <- ZIO.accessM[DatabaseOps](_.tableNames)
    columns <- ZIO.accessM[DatabaseOps](_.columnNames("user_table"))
  } yield (tables, columns)
```

When an effect is accessed from the environment, the effect is called an _environmental effect_. Later, we'll see how environmental effects provide an easy way to test ZIO applications.

Effects that require any type of environment cannot be run without first _providing_ their environment to them.

The simplest way to provide an effect the environment that it requires is to use the `ZIO#provide` method:


```scala mdoc:silent
val square: ZIO[Int, Nothing, Int] = 
  for {
    env <- ZIO.environment[Int]
  } yield env * env

val result: UIO[Int] = square.provide(42)
```

The combination of `ZIO.accessM` and `ZIO#provide` are all that is necessary to fully use environmental effects for easy testability.

# Environmental Effects

The fundamental idea behind environmental effect is to _program to an interface, not an implementation_. Rather than passing around interfaces manually, or injecting them using dependency injection, we take advantage of ZIO environment to automatically pass interfaces wherever they are required.

In this section, we'll explore environmental effects by developing a testable database service.

## Define the Service

We will define the database service with the help of a module, which is an interface that contains only a single field, which provides access to the service.

```scala mdoc:invisible
trait UserID
trait UserProfile
val userId = new UserID { }
```

```scala mdoc:silent
object Database {
  trait Service {
    def lookup(id: UserID): Task[UserProfile]
    def update(id: UserID, profile: UserProfile): Task[Unit]
  }
}
trait Database {
  def database: Database.Service
}
```

In this example, the type `Database` is the _module_, which contains the `Database.Service` _service_. The _service_ is just an ordinary interface, placed inside the companion object of the module, which contains effectful functions that represent the _capabilities_ of the database service.

## Provide Helpers

In order to make it easier to access the database service as an environmental effect, we will define helper functions that use `ZIO.accessM`.

```scala mdoc:silent
object db {
  def lookup(id: UserID): ZIO[Database, Throwable, UserProfile] =
    ZIO.accessM(_.database.lookup(id))

  def update(id: UserID, profile: UserProfile): ZIO[Database, Throwable, Unit] =
    ZIO.accessM(_.database.update(id, profile))
}
```

## Use the Service

We're now ready to build an example that uses the database service:

```scala mdoc:silent
val lookedupProfile: ZIO[Database, Throwable, UserProfile] = 
  for {
    profile <- db.lookup(userId)
  } yield profile
```

The effect in this example interacts with the database solely through the environment, which in this case, is a module that provides access to the database service.

To actually run such an effect, we need to implement the database module.

## Implement Live Service

Now we can implement a live database module, which will actually interact with our production database:

```scala mdoc:silent
trait DatabaseLive extends Database {
  def database: Database.Service = ???
}
object DatabaseLive extends DatabaseLive
```

(The real implementation is not provided because that would require details beyond the scope of this section.)

## Run the Database Effect

We can now provide the live database module to our application, using `ZIO.provide`:

```scala mdoc:silent
def main: ZIO[Database, Throwable, Unit] = ???

def main2: ZIO[Any, Throwable, Unit] = 
  main.provide(DatabaseLive)
```

The resulting effect has no requirements, so it can now be executed.

## Implement Test Service

To test code that interacts with the database, we would like to not interact with a real database, because that will make our test slow and brittle, and fail randomly even when our application logic is correct.

Although you can use mocking libraries to do this, in this section, we will simply create a test service:

```scala mdoc:silent
class TestService extends Database.Service {
  private var map: Map[UserID, UserProfile] = Map()

  def setTestData(map0: Map[UserID, UserProfile]): Task[Unit] = 
    Task { map = map0 }

  def getTestData: Task[Map[UserID, UserProfile]] = 
    Task(map)

  def lookup(id: UserID): Task[UserProfile] = 
    Task(map(id))

  def update(id: UserID, profile: UserProfile): Task[Unit] = 
    Task.effect { map = map + (id -> profile) }
}
trait TestDatabase extends Database {
  val database: TestService = new TestService
}
object TestDatabase extends TestDatabase
```

## Test Database Code

To test code that requires the database, we need only provide it with our test database service.

```scala mdoc:silent
def code: ZIO[Database, Throwable, Unit] = ???

def code2: ZIO[Any, Throwable, Unit] = 
  code.provide(TestDatabase)
```

The same code can work with either our production database module, or our test database module.

# Next Steps

If you are comfortable with testing effects, then the next step is to learn about [running effects](running_effects.html).
