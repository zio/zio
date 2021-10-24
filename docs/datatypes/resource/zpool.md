---
id: zpool
title: "ZPool"
---

A `ZPool[E, A]` is a pool of items of type `A`, each of which may be associated with the acquisition and release of resources. An attempt to get an item `A` from a pool may fail with an error of type `E`.

## Introduction

`ZPool` is an asynchronous and concurrent generalized pool of reusable managed resources, that is used to create and manage a pool of objects.

```scala
trait ZPool[+Error, Item] {
  def get: Managed[Error, Item]
  def invalidate(item: Item): UIO[Unit]
}
```

The two fundamental operators on a `ZPool` is `get` and `invalidate`:
- The `get` operator retrieves an item from the pool in a `Managed` effect.
- The `invalidate` operator invalidates the specified item. This will cause the pool to eventually reallocate the item.

The `make` constructor is a common way to create a `ZPool`:

```scala
object ZPool {
  def make[E, A](get: Managed[E, A], size: Int): UManaged[ZPool[E, A]] = ???
}
```
