---
id: hasmany
title: "HasMany"
---

The `HasMany[K, A]` data type is used with the ZIO environment to express an effect's dependency on multiple services of type `A` which are keyed by type `A`.

The `HasMany[K, A]` is a type alias for `Has[Map[K, A]]` data type:

```scala
type HasMany[K, A] = Has[Map[K, A]]
```

In ordinary `Has[A]` data type, when we want to express dependencies of type `Console`, `Logging` and `Random`, we use the `Has[Console] with Has[Logging] with Has[Random]` data type. This is convenient, where each type uniquely identifies one service.

What about those cases where we need to express multiple services of type `Logging`? To deal with such cases we can use the `HasMany` data type. So in these cases the environment type would be something like `Has[Console] with HasMany[String, Logging] with Has[Random]`.

To access the specified service correspond to a key, we can use the `ZIO.serviceAt[Service](key)` constructor. For example, to access a `Logging` service which is specified by the "ConsoleLogger" key, we can write:

```scala mdoc:invisible
import zio._
trait Logging
```

```scala mdoc:silent:nest
val logger: URIO[HasMany[String, Logging], Option[Logging]] =
  ZIO.serviceAt[Logging]("ConsoleLogger")
```

A service can be updated at the specified key with the `ZIO#updateServiceAt` operator.
