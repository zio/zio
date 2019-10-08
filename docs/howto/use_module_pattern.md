---
id: howto_use_module_pattern
title:  "Use module pattern"
---

## How to structure larger ZIO programs?

ZIO was designed around composability. Larger problems can - and should be - split into smaller ones, each piece having well defined boundaries and
tested in isolation. Smaller programs can then be composed together to form larger ones and the correctness of each piece implies the correctness of
whole application.

ZIO lets you wrap any arbitrary piece of code and lift it into its effect system, giving you much flexibility around how you structure your program.
However, as any powerful tool, it may be misused, therefore we recommend you make yourself familiar with "Environmental effects" and "Module pattern".

## Hidden inputs

Functions often need some context to perform their duties. For example, let's take a simple method that connects to database. It might look like:

```scala mdoc:invisible
trait Connection
trait DatabaseConfig
```

```scala mdoc
import scala.concurrent.Future

def connect(host: String, port: Int): Future[Connection] = ???
```

The signature of the method `(String, Int) => Future[Connection]` tells the whole story about what it can and cannot do. Obviously it uses `Future`
so it will suffer all of its limitations, but there is nothing wrong with it. However, as your application grows and you start to group methods that
are conceptually tightly related, you may end up with something like this:

```scala mdoc
class Database(config: DatabaseConfig) {

  def connect(): Future[Connection] = ???
}
```

Since you don't want to thread _host_ and _port_ parameters throughout your entire application you've moved them to `DatabaseConfig` and you require
it to be present upon creation of `Database` module. The connect method signature has now changed to `() => Future[Unit]` which tells a lie about what
this method needs to run, as it does access a hidden channel of inputs - the `this` of `Database` instance.

```scala mdoc:invisible:reset
trait Connection
trait DatabaseConfig
```

With ZIO we can use the environment to provide our function with additional context it needs to run.

```scala mdoc
import zio._

trait Database {
  def connect(): ZIO[DatabaseConfig, Unit, Connection]
}
```

We've removed the hidden input by pulling the required context into the environment type. Now the type signature tells exacly what this effect can (or
cannot) do.

## Environmental effects

The ZIO data type has an `R` type parameter, which is used to describe the type of _environment_ required by the effect. We will reference to the `R`
type as the environment from now on. This opens up another channel for typed inputs to our function which we can leverage to pass on any additional
context our function requires.

The _environmental effects_ is a pattern where you store your effects in the environment so they can be accessed by other effects.

```scala mdoc:invisible
trait UserId
trait User
```

```scala mdoc
trait UserRepository {

  def find(id: UserId): ZIO[Database with DatabaseConfig, Unit, Option[User]] =
   for {
     db   <- ZIO.environment[Database]
     conn <- db.connect
     // ...
   } yield ???
}
```

In this example we're accessing the environment to retrieve the `Database` service, from which we run the effect `connect` to retrieve a connection.

> **Note:** even if we did not explicitly typed `find` method, the compier would infer the correct `R` type to be `Database with DatabaseConfig`.

## Module pattern

We've discussed how to create effects and how the environment can be used to access them. Now you will learn how to structure your application into
inter-dependent modules. First we will define a `Postman` service, which exposes a single capability - to deliver email messages.

```scala mdoc:invisible
trait DeliveryStatus
trait Email
```

```scala mdoc
trait Postman {
  val postman: Postman.Service[Any]
}

object Postman {
  trait Service[R] {
    def deliver(email: Email): ZIO[R, Nothing, DeliveryStatus]
  }
}
```

The `Postman` trait is the _module_, which is just a container for the `Postman.Service`.

> **Note:** By convention we name the value holding the reference to the service the same as module, only with first letter lowercased.
> This is to avoid name collisions when mixing multiple modules to create the environment.

The _service_ is just an ordinary interface, defining the _capabilities_ it provides.

> **Note:** By convention we place the _service_ inside the companion object of the _module_ and name it `Service`.
> This is to have a consitent naming scheme `<Module>.Service[R]` across the entire application.
> It is also the structure required by some macros in [zio-macros][link-zio-macros] project.

The _capability_ is a method or value defined by the _service_. While these may be ordinary functions, if you want all the benefits ZIO provides,
these should all be ZIO effects.

## Dependency injection

There are many approaches to dependency injection and they divide into two categories. The dependecy graph is either resolved at compile time (and the
requirements expressed using the type system, like in [cake pattern][link-cake-pattern]) or at runtime (and the requirements passed via constructor
like in [distage][link-distage]).

They all lie somewhere on the spectrum between manual (explicit) and automatic (aka _magical_) wiring. As programmers we want to get rid of as much
boilerplate as possible, while also keeping the solution simple and flexible.

Let's define another module that will depend on `Postman`.

```scala mdoc:invisible
trait EmailAddress
trait TemplateId

trait EmailRenderer {
  val emailRenderer: EmailRenderer.Service[Any]
}

object EmailRenderer {
  trait Service[R] {
    def render(id: TemplateId, emailAddress: EmailAddress): ZIO[R, Nothing, Email]
  }
}
```

```scala mdoc
trait Newsletter {
  val newsletter: Newsletter.Service[Any]
}

object Newsletter {
  trait Service[R] {
    def send(id: TemplateId, recipients: List[EmailAddress]): ZIO[R, Nothing, Unit]
  }
}
```

With _module pattern_ specifying dependencies is as simple as:

```scala mdoc
import zio.stream._

trait NewsletterLive extends Newsletter {

  // dependencies
  val emailRenderer: EmailRenderer.Service[Any]
  val postman: Postman.Service[Any]

  final val newsletter = new Newsletter.Service[Any] {

    def send(id: TemplateId, recipients: List[EmailAddress]): UIO[Unit] =
      ZStream
        .fromIterable(recipients)
        .mapM { emailAddress =>
          for {
            email          <- emailRenderer.render(id, emailAddress)
            deliveryStatus <- postman.deliver(email)
          } yield deliveryStatus
        }
        .run(Sink.ignoreWhile[DeliveryStatus](_ => true))
  }
}
```

In this example the dependency on `EmailRenderer.Service` and `Postman.Service` is expressed as abstract value, forceing the programmer to provide
instances of these services when constructing the environment. Note that `Newsletter` _module_ does not depend on anything. Only `NewsletterLive`
implementation depends on those. This allows you to create other implementations with diffrent (or zero) dependencies, like a helper object to access
service effects:

```scala mdoc
object Helper extends Newsletter.Service[Newsletter] {

  def send(id: TemplateId, recipients: List[EmailAddress]) =
    ZIO.accessM(_.newsletter.send(id, recipients))
}
```

> **Note:** by convention such a helper would be defined inside module's companion object and named `>`.
> This is to have a nice syntax to access effects like `Newsletter.>.send(id, recipients)`.
> You can use the `@Accessable` macro from [zio-macros][link-zio-macros] to derrive this boilerplate code automatically.

Should you need to create a test implementation it will most likely have diffrent dependencies (or none at all).

## Advantages using module pattern

First and foremost, the dependencies defined using the _module pattern_ are **checked at compile time**. If you fail to provide the required dependency
you will get a **readable and accurate compiler error**:

```scala mdoc:invisible
val id = new TemplateId {}
val recipients = List.empty[EmailAddress]
```

```scala mdoc:fail:silent
val result =
  Helper
    .send(id, recipients)
    .provide(new NewsletterLive {})
```

```
object creation impossible, since:
it has 2 unimplemented members.
/** As seen from NewsletterLive, the missing signatures are as follows.
 *  For convenience, these are usable as stub implementations.
 */
  val emailRenderer: EmailRenderer.Service[Any] = ???
  val postman: Postman.Service[Any] = ???

    .provide(new NewsletterLive {})
                 ^^^^^^^^^^^^^^^^^
```

The compiler error clearly states what is missing and where. As a counterexample, let's see would happen had we used the cake pattern:

```scala mdoc:fail:silent
trait NewsletterLiveCake extends Newsletter { self: EmailRenderer with Postman =>
  /* ... */
}

val result2 = app.provide(new NewsletterLiveCake {})
```

The compiler error contains a trainwreck of interfaces. This case is still relatively simple, but as your application grows this quickly explodes
and becomes completely unreadable.

```
illegal inheritance;
 self-type NewsletterLiveCake does not conform to NewsletterLiveCake's selftype NewsletterLiveCake with EmailRenderer with Postman
val result2 = app.provide(new NewsletterLiveCake {})
                              ^^^^^^^^^^^^^^^^^^
```

Secondly, the dependencies are **order-insensitive**, meaning, you may provide the actual modules in any order you like.

```scala mdoc:invisible
trait EmailRendererLive extends EmailRenderer {
  val emailRenderer = new EmailRenderer.Service[Any] {
    def render(id: TemplateId, emailAddress: EmailAddress): UIO[Email] =
      UIO.succeed(new Email {})
  }
}

trait PostmanLive extends Postman {
  val postman = new Postman.Service[Any] {
    def deliver(email: Email): UIO[DeliveryStatus] =
      UIO.succeed(new DeliveryStatus {})
  }
}
```

```scala mdoc
val app = Helper.send(id, recipients)

val result3 = app.provide(new EmailRendererLive with PostmanLive with NewsletterLive {})
// or
val result4 = app.provide(new PostmanLive with NewsletterLive with EmailRendererLive {})
```

Another benefit is that this approach **allows for circular dependencies** between services. The environment is a single object with all the modules
mixed in. It cannot be used until it's initialized and once it is, all of the services are ready to be used, in any order you wish.

Finally, the machinery behind _module pattern_ is plain Scala code. There is **no magic behind the scenes**. The programmer is in full control and can
rely on the compiler to hint him what environment he needs to provide in order to run the program.

## Next Steps

Depending on the role _service_ plays in the application, you may need to either assert on values returned by _capacities_ or set expectations on
_calls_ to the collaborating service. Luckily both approaches are very simple.

First learn how to [test effects][doc-test-effects] and once you are comfortable with testing effects, then the next step is to learn how to
[mock services][doc-mock-services].

[doc-test-effects]: test_effects.md
[doc-mock-services]: mock_services.md
[link-cake-pattern]: http://jonasboner.com/real-world-scala-dependency-injection-di/
[link-distage]: https://izumi.7mind.io/latest/release/doc/distage/
[link-zio-macros]: https://github.com/zio/zio-macros
