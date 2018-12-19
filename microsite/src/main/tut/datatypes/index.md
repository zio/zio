---
layout: docs
position: 4
section: datatypes
title:  "Data Types"
---

# {{page.title}}

ZIO contains a small number of data types that can help you solve complex problems in asynchronous and concurrent programming.

 - **[Fiber](fiber.html)** — A fiber value models an `IO` value that has started running, and is the moral equivalent of a green thread.
 - **[FiberLocal](fiberlocal.html)** — A `FiberLocal` is a variable whose value depends on the fiber that accesses it, and is the moral equivalent of Java's `ThreadLocal`.
 - **[IO](io.html)** — An `IO` is a value that describes an effectful program, which might fail or succeed.
 - **[Managed](managed.html)** — A `Managed` is a value that describes an `IO` perishable resource that may be consumed only once inside a given scope.
 - **[Promise](promise.html)** — A `Promise` is a model of a variable that may be set a single time, and awaited on by many fibers.
 - **[Queue](queue.html)** — A `Queue` is an asynchronous queue that never blocks, which is safe for multiple concurrent producers and consumers.
 - **[Schedule](schedule.html)** — A `Schedule` is a model of a recurring schedule, which can be used for repeating successful `IO` values, or retrying failed `IO` values.
 - **[Semaphore](semaphore.html) — A `Semaphore` is an asynchronous (non-blocking) semaphore that plays well with ZIO's interruption.
 - **[Sink](sink.html)** — A `Sink` is a consumer of values from a `Stream`, which may produces a value when it has consumed enough.
 - **[Stream](stream.html)** — A `Stream` is a lazy, concurrent, asynchronous source of values.

To learn more about these data types, please explore the pages above, or check out the Scaladoc documentation.
