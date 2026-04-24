---
id: fiberref
title: "FiberRef"
---

`FiberRef[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which sets the reference to a new value, and `get`, which retrieves the current value of the reference.

We can think of `FiberRef` as Java's `ThreadLocal` on steroids. So, just like we have `ThreadLocal` in Java we have `FiberRef` in ZIO. So as different threads have different `ThreadLocal`s, we can say different fibers have different `FiberRef`s. They don't intersect or overlap in any way. `FiberRef` is the fiber version of `ThreadLocal` with significant improvements in its semantics. A `ThreadLocal` only has a mutable state in which each thread accesses its own copy, but threads don't propagate their state to their children's.

As opposed to `Ref[A]`, the value of a `FiberRef[A]` is bound to an executing fiber. Different fibers who hold the same `FiberRef[A]` can independently set and retrieve values of the reference, without collisions.

```scala
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

```scala
for {
  correlationId <- FiberRef.make[String]("")
  v1            <- correlationId.locally("my-correlation-id")(correlationId.get)
  v2            <- correlationId.get
} yield v1 == "my-correlation-id" && v2 == ""
```

## Propagation

Let's go back to the `FiberRef`s analog called `ThreadLocal` and see how it works. If we have thread `A` with its `ThreadLocal` and thread `A` creates a new thread, let's call it thread `B`. When thread `A` sends thread `B` the same `ThreadLocal` then what value does thread `B` see inside the `ThreadLocal`? Well, it sees the default value of the `ThreadLocal`. It does not see `A`s value of the `ThreadLocal`. So in other words, `ThreadLocal`s do not propagate their values across the sort of graph of threads so when one thread creates another, the `ThreadLocal` value is not propagated from parent to child.

`FiberRefs` improve on that model quite dramatically. Basically, whenever a child's fiber is created from its parent, the `FiberRef` value of parent fiber propagated to its child fiber.

### Copy-on-Fork 
`FiberRef[A]` has *copy-on-fork* semantics for `ZIO#fork`. This essentially means that a child `Fiber` starts with `FiberRef` values of its parent. When the child set a new value of `FiberRef`, the change is visible only to the child itself. The parent fiber still has its own value.

So if we create a `FiberRef` and, we set its value to `5`, and we pass this `FiberRef` to a child fiber, it sees the value `5`. If the child fiber modifies the value `5` to `6`, the parent fiber can't see that change. So the child fiber gets its own copy of the `FiberRef`, and it can modify it locally. Those changes will not affect the parent fiber:

```scala
for {
  fiberRef <- FiberRef.make(5)
  promise <- Promise.make[Nothing, Int]
  _ <- fiberRef
    .updateAndGet(_ => 6)
    .flatMap(promise.succeed).fork
  childValue <- promise.await
  parentValue <- fiberRef.get
} yield assert(parentValue == 5 && childValue == 6)
```

### join Semantic
If we `join` a fiber then its `FiberRef` is merged back into the parent fiber:

```scala
for {
  fiberRef <- FiberRef.make(5)
  child <- fiberRef.set(6).fork
  _ <- child.join
  parentValue <- fiberRef.get
} yield assert(parentValue == 6)
```

So if we `fork` a fiber and that child fiber modifies a bunch of `FiberRef`s and then later we join it, we get those modifications merged back into the parent fiber. So that's the semantic model of ZIO on `join`. 

Each fiber has its `FiberRef` and modifying it locally. So when they do their job and `join` their parent, how do they get merged?  By default, the last child fiber will win, the last fiber which is going to `join` will override the parent's `FiberRef` value.

As we can see, `child1` is the last fiber, so its value which is `6`, gets merged back into its parent:

```scala
for {
  fiberRef <- FiberRef.make(5)
  child1 <- fiberRef.set(6).fork
  child2 <- fiberRef.set(7).fork
  _ <- child2.join
  _ <- child1.join
  parentValue <- fiberRef.get
} yield assert(parentValue == 6)
```

### Custom Merge
Furthermore we can customize how, if at all, the value will be update when a fiber is forked and how values will be combined when a fiber is merged. To do this you specify the desired behavior during `FiberRef#make`:
```scala
for {
  fiberRef <- FiberRef.make(initial = 0, join = math.max)
  child    <- fiberRef.update(_ + 1).fork
  _        <- fiberRef.update(_ + 2)
  _        <- child.join
  value    <- fiberRef.get
} yield assert(value == 2)
```

### await semantic
Important to note that `await`, has no such properties, so `await` waits for the child fiber to finish and gives us its value as an `Exit`:

```scala
for {
  fiberRef <- FiberRef.make(5)
  child <- fiberRef.set(6).fork
  _ <- child.await
  parentValue <- fiberRef.get
} yield assert(parentValue == 5)
```

`Join` has higher-level semantics that `await` because it will fail if the child fiber failed, and it will also merge back its value to its parent.

### inheritRefs
We can inherit the values from all `FiberRef`s from an existing `Fiber` using the `Fiber#inheritRefs` method:

```scala
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

```scala
val withJoin =
  for {
    fiberRef <- FiberRef.make[Int](0)
    fiber    <- fiberRef.set(10).fork
    _        <- fiber.join
    v        <- fiberRef.get
  } yield assert(v == 10)
```

```scala
val withoutJoin =
  for {
    fiberRef <- FiberRef.make[Int](0)
    fiber    <- fiberRef.set(10).fork
    _        <- fiber.inheritRefs
    v        <- fiberRef.get
  } yield assert(v == 10)
```

## Memory Safety

The value of a `FiberRef` is automatically garbage collected once the `Fiber` owning it is finished. A `FiberRef` that is no longer reachable (has no reference to it in user-code) will cause all fiber-specific values of the reference to be garbage collected, even if they were once used in a `Fiber` that is currently executing.
