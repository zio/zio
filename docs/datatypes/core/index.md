---
id: index
title: "Summary"
---

- **[ZIO](io.md)** — A `ZIO` is a value that models an effectful program, which might fail or succeed.
- **[Fiber](fiber.md)** — A fiber value models an `IO` value that has started running, and is the moral equivalent of a green thread.
- **[FiberRef](fiberref.md)** — `FiberRef[A]` models a mutable reference to a value of type `A`. As opposed to `Ref[A]`, a value is bound to an executing `Fiber` only.  You can think of it as Java's `ThreadLocal` on steroids.
- **[ZLayer](zlayer.md)** - A `ZLayer` describes a layer of an application.
