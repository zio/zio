---
id: datatypes_fiberref
title:  "FiberRef"
---

`FiberRef[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which sets the reference to a new value, and `get`, which retrieves the current value of the reference.

As opposed to `Ref[A]`, the value of a `FiberRef[A]` is bound to an executing fiber. Different fibers who hold the same `FiberRef[A]` can independently set and retrieve values of the reference, without collisions.

You can think of `FiberRef` as Java's `ThreadLocal` on steroids.

```scala mdoc:silent
import zio._

for {
  fiberRef <- FiberRef.make[Int](0)
  _        <- fiberRef.set(10)
  v        <- fiberRef.get
} yield v == 10
```

## Operations

`FiberRef[A]` has an API almost identical to `Ref[A]`. It includes well-known methods such as:

- `FiberRef#get`. Returns the current value of the reference.
- `FiberRef#set`. Sets the current value of the reference.
- `FiberRef#update` / `FiberRef#updateSome` updates the value with the specified function.
- `FiberRef#modify`/ `FiberRef#modifySome` modifies the value with the specified function, computing a return value for the operation.

You can also use `locally` to scope `FiberRef` value only for a given effect:

```scala mdoc:silent
for {
  correlationId <- FiberRef.make[String]("")
  v1            <- correlationId.locally("my-correlation-id")(correlationId.get)
  v2            <- correlationId.get
} yield v1 == "my-correlation-id" && v2 == ""
```

## Propagation

`FiberRef[A]` has *copy-on-fork* semantics for `ZIO#fork`.

This essentially means that a child `Fiber` starts with `FiberRef` values of its parent. When the child set a new value of `FiberRef`, the change is visible only to the child itself. The parent fiber still has its own value.

```scala mdoc:silent
for {
  fiberRef <- FiberRef.make[Int](0)
  _        <- fiberRef.set(10)
  child    <- fiberRef.get.fork
  v        <- child.join
} yield v == 10
```

You can inherit the values from all `FiberRef`s from an existing `Fiber` using the `Fiber#inheritRefs` method:

```scala mdoc:silent
for {
  fiberRef <- FiberRef.make[Int](0)
  latch    <- Promise.make[Nothing, Unit]
  fiber    <- (fiberRef.set(10) *> latch.succeed(())).fork
  _        <- latch.await
  _        <- fiber.inheritRefs
  v        <- fiberRef.get
} yield v == 10
```

Note that `inheritRefs` is automatically called on `join`. This effectively means that both of the following effects behave identically:

```scala mdoc:silent
val withJoin =
for {
  fiberRef <- FiberRef.make[Int](0)
  fiber    <- fiberRef.set(10).fork
  _        <- fiber.join
  v        <- fiberRef.get
} yield v == 10
```

```scala mdoc:silent
val withoutJoin =
  for {
    fiberRef <- FiberRef.make[Int](0)
    fiber    <- fiberRef.set(10)
    v        <- fiberRef.get
  } yield v == 10
```

Furthermore you can customize how, if at all, the value will be update when a fiber is forked and how values will be combined when a fiber is merged. To do this you specify the desired behavior during `FiberRef#make`:
```scala mdoc:silent
for {
  fiberRef <- FiberRef.make(initial = 0, join = math.max)
  child    <- fiberRef.update(_ + 1).fork
  _        <- fiberRef.update(_ + 2)
  _        <- child.join
  value    <- fiberRef.get
} yield value == 2
```

## Memory Safety

The value of a `FiberRef` is automatically garbage collected once the `Fiber` owning it is finished. A `FiberRef` that is no longer reachable (has no reference to it in user-code) will cause all fiber-specific values of the reference to be garbage collected, even if they were once used in a `Fiber` that is currently executing.
