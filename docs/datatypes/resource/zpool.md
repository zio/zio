---
id: zpool
title: "ZPool"
---

A `ZPool[E, A]` is a pool of items of type `A`, each of which may be associated with the acquisition and release of resources. An attempt to get an item `A` from a pool may fail with an error of type `E`.

## Motivation

Acquiring some resources is expensive to create and time-consuming. Such resources include network connections (sockets, database connections, remote services), threads.

There are some cases that
- We require a **fast and predictable** way of accessing resources.
- We need a solution to **scale across the number of resources**.
- On the other hand, each **resource consumption doesn't take a long time**.

If we create a new resource for every resource acquisition, consequently we will find ourselves in a constant repetition of acquisition and release of resources. This might end up with thousands of resources (e.g. connection to a database) created within a short time, which will reduce the performance of our application.

To address these issues, we can create a pool of pre-initialized resources:
- Whenever we need a new resource, we acquire that from the existing resources of the pool. So the resource acquisition will be predictable, and it will avoid the overhead of acquisition.
- When the resource is no longer needed, we release that back to the resource pool. So the released resources will be recyclable, and it will avoid the overhead of re-acquisition.

`ZPool` is an implementation of such an idea with some excellent properties that we will cover on this page.

## Introduction

`ZPool` is an asynchronous and concurrent generalized pool of reusable managed resources, that is used to create and manage a pool of objects.

```scala mdoc:invisible
import zio._
```

```scala mdoc:nest
trait ZPool[+Error, Item] {
  def get: Managed[Error, Item]
  def invalidate(item: Item): UIO[Unit]
}
```

The two fundamental operators on a `ZPool` is `get` and `invalidate`:
- The `get` operator retrieves an item from the pool in a `Managed` effect.
- The `invalidate` operator invalidates the specified item. This will cause the pool to eventually reallocate the item.

## Constructing ZPools

### Fixed-size Pools

The `make` constructor is a common way to create a `ZPool`:

```scala mdoc:silent
object ZPool {
  def make[E, A](get: Managed[E, A], size: Int): UManaged[ZPool[E, A]] = ???
}
```

```scala mdoc:reset:invisible
```

It takes a managed resource of type `A`, and the `size` of the pool. The return type will be a managed `ZPool`.
- A fixed pool size will be used to pre-allocate pool entries, so all the entries of the pool will be acquired eagerly. As a client of the `ZPool` it is recommended to analyze requirements to find out the best suitable size for the resource pool. If we set up a pool with too many eagerly-acquired resources, that may reduce the performance due to the resource contention.
- As the return type of the constructor is `UManaged[ZPool[E, A]]`, it will manage automatically the life cycle of the pool. So, as a client of `ZPool`, we do not require to shutdown the pool manually.

### Dynamically-sized Pools

The previous constructor creates a `ZPool` with a fixed size, so all of its entries are pre-allocated. There is another constructor that creates a pool with pre-allocated minimum entries (eagerly-acquired resources), plus it can increase its entries _on-demand_ (lazily-acquired resources) until reaches the specified maximum size:

```scala
def make[E, A](get: Managed[E, A],
        range: Range, // minimum and maximum size of the pool
        timeToLive: Duration): URManaged[Has[Clock], ZPool[E, A]]
```

Having a lot of resources that are over our average requirement can waste space and degrade the performance. Therefore, this variant of `ZPool` has an _eviction policy_. By taking the `timeToLive` argument, it will evict excess items that have not been acquired for more than the `timeToLive` time, until it reaches the minimum size.

Here is an example of creating pool of database connections:

```scala mdoc:invisible
import zio._

case class Connection()

val acquireDbConnection = ZManaged.succeed(Connection())

def useConnection(i: Connection) = {
  val _ = i
  ZIO.unit
}
```

```scala mdoc:silent
ZPool.make(acquireDbConnection, 10 to 20, 60.seconds).use { pool =>
  pool.get.use { conn => useConnection(conn) }
}
```
