---
id: use-zio-macros
title: "How to use ZIO Macros?"
---


### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-test" % "<zio-version>"
```

### Description

The `@mockable[A]` generates _capability tags_ and _mock layer_ into annotated object.

```scala
import zio.mock.mockable

@mockable[AccountObserver]
object AccountObserverMock
```

Will result in:

```scala mdoc:invisible
import zio._

trait AccountEvent
trait AccountObserver {
  def processEvent(event: AccountEvent): UIO[Unit]
}
```

```scala mdoc:compile-only
import zio._
import zio.test.mock.{Mock, Proxy}

object AccountObserverMock extends Mock[AccountObserver] {
  object ProcessEvent extends Effect[AccountEvent, Nothing, Unit]
  object RunCommand   extends Effect[Unit, Nothing, Unit]

  val compose: URLayer[Proxy, AccountObserver] =
    ZIO.serviceWithZIO[Proxy] { proxy =>
      withRuntime[Any].map { rts =>
        new AccountObserver {
          def processEvent(event: AccountEvent) = proxy(ProcessEvent, event)
          def runCommand: UIO[Unit]             = proxy(RunCommand)
        }
      }
    }.toLayer
}
```
