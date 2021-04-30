---
id: has
title: "Has"
---

The trait `Has[A]` is used with ZIO environment to express an effect's dependency on a service of type `A`.

For example,`RIO[Has[Console.Service], Unit]` is an effect that requires a `Console.Service` service.

One of the most use-cases of `Has` data type is for wrapping service definition in _Module Pattern 1.0_. Inside the ZIO library, type aliases are provided as shorthands for common services, e.g.:

```scala mdoc:silent
type Console = Has[Console.Service]

object Console {
  trait Service {
    def putStrLn(line: String): UIO[Unit]
  }
}
```

Some components in an application might depend upon more than one service. Two or more `Has[_]` elements can be combined _horizontally_ using their `++` operator:

```scala mdoc:invisible
import zio._

object Repo {
  trait Service {}
}

object Logger {
  trait Service {
    def log(s: String): UIO[Unit] = UIO(???)
  }
}
```

```scala mdoc:silent
val repo: Has[Repo.Service] = Has(new Repo.Service{})
val logger: Has[Logger.Service] = Has(new Logger.Service{})

// Note the use of the infix `++` operator on `Has` to combine two `Has` elements:
val mix: Has[Repo.Service] with Has[Logger.Service] = repo ++ logger
```

At this point you might ask: what's the use of `Has` if the resulting type is just a mix of two traits? Why aren't we just relying on trait mixins?

The extra power given by `Has` is that the resulting data structure is backed by an _heterogeneous map_ from service type to service implementation, that collects each instance that is mixed in so that the instances can be accessed/extracted/modified individually, all while still guaranteeing supreme type safety.

```scala mdoc:silent
// get back the logger service from the mixed value:
val log = mix.get[Logger.Service].log("Hello modules!")
```

As per the recipe above, it is extremely convenient to define a type alias for `Has[Service]`.
Usually we don't create a `Has` directly. Instead, we create a `Has` via `ZLayer`.
