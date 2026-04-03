---
id: scopedref
title: "ScopedRef: Mutable Reference For Resources"
sidebar_label: "ScopedRef"
---

`ScopedRef` is a resourceful version of `Ref` data type. So it is a `Ref` for resourceful effects.

## Operations

There are two basic operations: get and set:

- `ScopedRef#get` returns the current value of the scoped ref.

- `ScopedRef#set` sets the scoped ref to a new value by acquiring the new resource to create a new value of the scoped ref. Setting a new value releases the old resource automatically.

## Construction

The `ScopedRef` has two constructors:

```scala
object ScopedRef {
  def make[A](a: => A): ZIO[Scope, Nothing, ScopedRef[A]] = ???
  def fromAcquire[R, E, A](acquire: ZIO[R, E, A]): ZIO[R with Scope, E, ScopedRef[A]] = ???
}
```

So we have two options to create a `ScopedRef`:

- `ScopedRef.make` creates a scoped ref from an ordinary value. We can use this constructor when we don't need to acquire a resource to create a value of the scoped ref, for example, when we have a constant value.

- `ScopedRef.fromAcquire` creates a scoped ref from an effect that resourcefully produces a value.

:::note
`ScopedRef` is resourceful, so its lifetimes is scoped. Whenever we don't need it anymore, we can release it by using `ZIO#scoped` combinator.
:::

## Example

Let's see how changing the value of a `ScopedRef` automatically releases the old resource:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.unit
    r1 = ZIO.acquireRelease(
           ZIO
             .debug("acquiring the first resource")
             .as(5)
         )(_ => ZIO.debug("releasing the first resource"))
    r2 = ZIO.acquireRelease(
           ZIO
             .debug("acquiring the second resource")
             .as(10)
         )(_ => ZIO.debug("releasing the second resource"))
    sref <- ScopedRef.fromAcquire(r1)
    _    <- sref.get.debug
    _    <- sref.set(r2)
    _    <- sref.get.debug
  } yield ()
}
```

The output:

```
acquiring the first resource
5
acquiring the second resource
releasing the first resource
10
releasing the second resource
```

## Interruption

The `set` operation runs in an uninterruptible block. This means that if you want to manage a `Fiber` using a `ScopedRef` by using `forkScoped`, this fiber must be explicitly made interruptible.
