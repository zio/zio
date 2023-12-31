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

For services that might have default values or configurations, `ZLayer.derive` can use implicit
 `ZLayer.Derive.Default[A]` values:

### Pre-defined Default Values

There are some pre-defined `ZLayer.Derive.Default[A]` instances for the following types:

#### `Config[A]`

When a service `A` has a constructor parameter `B` and there's an implicit `Config[B]` instance, `ZLayer.derive`
automatically loads `B` using `ZIO.config`.

```scala mdoc:compile-only
import zio._

case class APIClientConfig(appKey: String, secretKey: Config.Secret)
object APIClientConfig {
  // Because we have an implicit `Config[APIClientConfig]` in scope...
  implicit val config: Config[APIClientConfig] =
    (Config.string("appKey") ++ Config.secret("secretKey")).map {
      case (uri, key) => APIClientConfig(uri, key)
    }
}

class APIClient(config: APIClientConfig) { /* ... */ }
object APIClient {

  // `APIClientConfig` is automatically loaded using `ZIO.config` by `ZLayer.derive`,
  // instead of being required as a layer input.
  val layer: ZLayer[Any, Config.Error, APIClient] = ZLayer.derive[APIClient]
}

```

Refer to [Configuration](../configuration/index.md) for more about `Config`.

#### Some Concurrency Primitives

- `Promise[E, A]`
- `Queue[A]` (using `Queue.unbounded`)
- `Hub[A]` (using `Hub.unbounded`)
- `Ref[A]` (when `A` has a default instance)

### Creating New Default Value

There are three main ways to create a `ZLayer.Derive.Default`:

1. `ZLayer.Derive.Default.succeed` for creating default values from simple values.
2. `ZLayer.Derive.Default.fromZIO` for creating default values from effects.
3. `ZLayer.Derive.Default.fromLayer` for creating default values from layers.

### Overriding Predefined Default Values

At times, you may want to override a default value in specific scenarios. To achieve this, you can define your own
implicit value in a scope with a higher implicit priority, like a closer lexical scope.

A common scenario for this is when you want to discard a pre-defined default value and instead treat it as a dependency.
Use `ZLayer.Derive.Default.service` for this purpose:

```scala mdoc:compile-only
import zio._
import ZLayer.Derive.Default

class Wheels(number: Int)
object Wheels {
  implicit val defaultWheels: Default.WithContext[Any, Nothing, Wheels] =
    Default.succeed(new Wheels(4))
}
class Car(wheels: Wheels)

val carLayer1: ZLayer[Any, Nothing, Car] = ZLayer.derive[Car] // wheels.number == 4
val carLayer2: ZLayer[Wheels, Nothing, Car] = locally {
  // The default instance is discarded
  implicit val newWheels: Default.WithContext[Wheels, Nothing, Wheels] =
     Default.service[Wheels]

  ZLayer.derive[Car]
}
```

### Caveat: Use `Default.WithContext[R, E, A]` instead of `Default[A]` for type annotation

When providing type annotations for `ZLayer.derive`, you must use `ZLayer.Derive.Default.WithContext[R, E, A]` instead
of the more general `ZLayer.Derive.Default[A]`. Using the latter will result in a compilation error due to missing type
details.

If you're uncertain about the exact type signature, a practical approach is to omit the type annotation initially. Then,
use your IDE's autocomplete feature to insert the inferred type.

## Attaching Scoped Resources

For services requiring resource management, `ZLayer.derive` offers built-in support for scoped values. When a service
`A` implements the `ZLayer.Derive.Scoped[-R, +E]` trait, `ZLayer.derive[A]` automatically recognizes it. As a result,
the `scoped` effect is executed during the layer's construction and finalization phases.

The 'resource' might be a background task, a lock file, or etc., that can be managed by [`Scope`](../resource/scope.md).

```scala mdoc:compile-only
import zio._

trait Connection {
  def healthCheck: ZIO[Any, Throwable, Unit]
  // ...
}

class ThirdPartyService(connection: Connection) extends ZLayer.Derive.Scoped[Any, Nothing] {

  // Repeats health check every 10 seconds in background during the layer's lifetime
  override def scoped(implicit trace: Trace): ZIO[Scope, Nothing, Any] =
    connection.healthCheck
      .ignoreLogged
      .repeat(Schedule.spaced(10.seconds))
      .forkScoped
}

object ThirdPartyService {
  // `ZLayer.Derive.Scoped` should be used with `ZLayer.derive`
  val layer: ZLayer[Connection, Nothing, ThirdPartyService] = ZLayer.derive[ThirdPartyService]
}
```

If `scoped` fails during resource acquisition, the entire `ZLayer` initialization process fails.

### Lifecycle Hooks

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
  // Note: it's for illustrative example. In a real-world application, you will probably want to
  //       put the `String` in a config.
  val layer: ZLayer[String, Throwable, ASingletonService] = ZLayer.derive[ASingletonService]
}
```

### Caveat: Manual layers do not respect `ZLayer.Derive.Scoped` and `ZLayer.Derive.AcquireRelease`

When manually creating `ZLayer` instances without using `ZLayer.derive`, the lifecycle hooks won't be automatically
invoked. Refer to [Resource Management in ZIO](../resource/index.md) for more details about general resource management
in ZIO.
