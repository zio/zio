---
id: datatypes_fiberref
title:  "FiberRef"
---

`FiberRef[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which fills the `Ref` with a new value, and `get`, which retrieves its current content. As opposed to `Ref[A]`, a value is bound to an executing `Fiber` only.  You can think of it as Java's `ThreadLocal` on steroids.


```scala mdoc:silent
import zio._

for {
  fiberRef <- FiberRef.make[Int](0)
  _        <- fiberRef.set(10)
  v        <- fiberRef.get
} yield v == 10
```

## Operations

`FiberRef[A]` has almost identical API as `Ref[A]`. It includes well known methods such as:

- `get` returns the current, possibly non-existent, fiber-bound `A`
- `set` binds an `A` to the current fiber
- `update` / `updateSome` modifies the value with the specified function
- `modify`/ `modifySome` modifies the value with the specified function, which computes a return value for the modification

You can also use `locally` to scope `FiberRef` value only for a given effect:

```scala mdoc:silent
for {
  correlationId <- FiberRef.make[String]("")
  v             <- correlationId.locally("my-correlation-id")(correlationId.get)
  v2            <- correlationId.get
} yield v == "my-correlation-id" && v2 == ""
```


## Propagation

`FiberRef[A]` has *copy-on-fork* semantics regarding `fork`. This essentially means that a child `Fiber` starts with `FiberRef` values of its parent. When the child set a new value of `FiberRef`, the change is visible only to the child itself. Parent still gets its own value.

```scala mdoc:silent
for {
  fiberRef <- FiberRef.make[Int](0)
  _        <- fiberRef.set(10)
  child    <- fiberRef.get.fork
  v        <- child.join
 
} yield v == 10
```

But it goes other way as well. You can also inherit values from all `FiberRef`s from an already running `Fiber`.

```scala mdoc:silent
for {
  fiberRef <- FiberRef.make[Int](0)
  latch    <- Promise.make[Nothing, Unit]
  fiber    <- (fiberRef.set(10) *> latch.succeed(())).fork
  _        <- latch.await
  _        <- fiber.inheritFiberRefs
  v        <- fiberRef.get
} yield v == 10
```

Method `inheritFiberRefs` is also automatically called on `join`. This effectively means that both pieces of code below behave identically.

 ```scala mdoc:silent
 for {
   fiberRef <- FiberRef.make[Int](0)
   fiber    <- fiberRef.set(10).fork
   _        <- fiber.join
   v        <- fiberRef.get
 } yield v == 10
 ```
 
  ```scala mdoc:silent
  for {
    fiberRef <- FiberRef.make[Int](0)
    fiber    <- fiberRef.set(10)
    v        <- fiberRef.get
  } yield v == 10
  ```

## Memory safety

Value is automatically garbage collected once the `Fiber` owning it is finished. `FiberRef` that is no longer reachable (has no reference to it in user code) will cause all values related to it to be garbage collected (even if they were used in a `Fiber` that is still executing.)