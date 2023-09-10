---
id: automatic-zlayer-derivation
title: "Automatic ZLayer Derivation"
---

ZIO's `ZLayer` is a powerful tool for building modular, testable, and composable applications. With the `ZLayer.derive`
utility, you can automatically derive simple `ZLayer` instances for your services, reducing boilerplate and simplifying
your codebase.

## Basic Use Cases

```scala mdoc:compile-only
import zio._

class Database(connection: String)
object Database { 
  val layer: ZLayer[String, Nothing, Database] = ZLayer.derive[Database]
}

class UserService(db: Database) 
object UserService {
  val layer: ZLayer[Database, Nothing, UserService] = ZLayer.derive[UserService]
}
```

## Default Values

For services that might have default values or configurations, `ZLayer.derive` can use implicit `ZLayer.Default[A]` values:

### Pre-defined Default Values

There are some pre-defined `ZLayer.Default[A]` instances for the following types:

#### `Config[A]`

When a service `A` has a constructor parameter `B` and there's an implicit `Config[B]` instance, `ZLayer.derive`
automatically loads `B` using `ZIO.config`. Refer to [Configuration](../configuration/index.md) for more about `Config`.

#### Some Concurrency Primitives

- `Promise[E, A]`
- `Queue[A]` (using `Queue.unbounded`)
- `Hub[A]` (using `Hub.unbounded`)
- `Ref[A]` (when `A` has a default instance)

### Creating New Default Value

There are three main ways to create a `ZLayer.Default`:

1. `ZLayer.Default.succeed` for creating default values from simple values.
2. `ZLayer.Default.fromZIO` for creating default values from effects.
3. `ZLayer.Default.fromLayer` for creating default values from layers.

### Overriding Predefined Default Values

At times, you may want to override a default value in specific scenarios. To achieve this, you can define your own
implicit value in a scope with a higher implicit priority, like a closer lexical scope.

A common scenario for this is when you want to discard a pre-defined default value and instead treat it as a dependency.
Use `ZLayer.Default.service` for this purpose:

```scala mdoc:compile-only
import zio._

class Wheels(number: Int)
object Wheels {
  implicit val defaultWheels: ZLayer.Default.WithContext[Any, Nothing, Wheels] =
    ZLayer.Default.succeed(new Wheels(4)) 
}
class Car(wheels: Wheels)

val carLayer1: ZLayer[Any, Nothing, Car] = ZLayer.derive[Car] // wheels.number == 4
val carLayer2: ZLayer[Wheels, Nothing, Car] = locally {
  // The default instance is discarded
  implicit val newWheels: ZLayer.Default.WithContext[Wheels, Nothing, Wheels] =
     ZLayer.Default.service[Wheels]
  
  ZLayer.derive[Car]
}
```

### Caveat: Use `ZLayer.Default.WithContext[R, E, A]` instead of `ZLayer.Default[A]` for type annotation

When providing type annotations for `ZLayer.derive`, you must use `ZLayer.Default.WithContext[R, E, A]` instead of the
more general `ZLayer.Default[A]`. Using the latter will result in a compilation error due to missing type details.

If you're uncertain about the exact type signature, a practical approach is to omit the type annotation initially. Then,
use your IDE's autocomplete feature to insert the inferred type.

## Lifecycle Hooks

For services requiring initialization or cleanup, `ZLayer.derive` offers built-in support for lifecycle hooks.
When a service `A` implements the `ZLayer.Derive.Scoped[R, E]` trait, `ZLayer.derive[A]` automatically recognizes
it. As a result, the `scoped` effect is executed during the layer's construction and finalization
phases.

Additionally, there's the `ZLayer.Derive.AcquireRelease[R, E, A]` trait. This is a specialized version of
`ZLayer.Derive.Scoped` designed for added convenience, allowing users to define initialization and finalization hooks
distinctly.

```scala mdoc:compile-only
import zio._
import java.io.File

def acquireLockFile(path: String): ZIO[Any, Throwable, File] = ???
def deleteFile(file: File): ZIO[Any, Throwable, Unit] = ???

class ASingletonService(lockFilePath: String) extends ZLayer.Derive.AcquireRelease[Any, Throwable, File] {

  override def acquire: ZIO[Any, Throwable, File] =
     acquireLockFile(lockFilePath)

  override def release(lockFile: File): ZIO[Any, Nothing, Any] =
     deleteFile(lockFile).ignore
}

object ASingletonService {
  // Note: it's for illustrative example. In a real-world application, you probably won't want
  //       `String` as layer input.
  val layer: ZLayer[String, Throwable, ASingletonService] = ZLayer.derive[ASingletonService]
}
```

### Caveat: Manual layers do not respect `ZLayer.Derive.Scoped` and `ZLayer.Derive.AcquireRelease`

When manually creating `ZLayer` instances without using `ZLayer.derive`, the lifecycle hooks won't be automatically
invoked. Refer to [Resource Management in ZIO](../resource/index.md) for more details about general resource management
in ZIO.
