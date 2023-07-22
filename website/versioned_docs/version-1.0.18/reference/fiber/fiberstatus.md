---
id: fiberstatus
title: "Fiber.Status"
---

`Fiber.Status` describe the current status of a [Fiber](fiber.md).

Each fiber can be in one of the following statues:
- Done
- Finishing
- Running
- Suspended

In the following example, we are going to `await` on a never-ending fiber and determine the id of that fiber, which we are blocking on:

```scala
import zio._
import zio.console._
for {
  f1 <- ZIO.never.fork
  f2 <- f1.await.fork
  blockingOn <- f2.status
    .collect(()) { case Fiber.Status.Suspended(_, _, _, blockingOn, _) =>
      blockingOn
    }
    .eventually
} yield (assert(blockingOn == List(f1.id)))
```
