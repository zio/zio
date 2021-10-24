---
id: zpool
title: "ZPool"
---

A `ZPool[E, A]` is a pool of items of type `A`, each of which may be associated with the acquisition and release of resources. An attempt to get an item `A` from a pool may fail with an error of type `E`.

## Reusable Managed Resources

The `ZPool` is an asynchronous and concurrent generalized pool of reusable managed resources:

```scala
trait ZPool[+Error, Item] {
  def get: Managed[Error, Item]
}
```

As a client of `ZPool`, we don't need to care about manually releasing resources to the pool. After using a resource, e.g. `ZManaged#use`, the finalizer of that resource will be called, and that resource will be automatically returned to the pool.
