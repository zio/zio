---
layout: docs
section: usage
title:  "Performance"
---

# Performance

`scalaz.zio` has excellent performance, featuring a hand-optimized, low-level interpreter that achieves zero allocations for right-associated binds, and minimal allocations for left-associated binds.

The `benchmarks` project may be used to compare `IO` with other effect monads, including `Future` (which is not an effect monad but is included for reference), Monix `Task`, and Cats `IO`.

As of the time of this writing, `IO` is significantly faster than or at least comparable to all other purely functional solutions.

# Thread Shifting - JVM

By default, fibers make no guarantees as to which thread they execute on. They may shift between threads, especially as they execute for long periods of time.

Fibers only ever shift onto the thread pool of the runtime system, which means that by default, fibers running for a sufficiently long time will always return to the runtime system's thread pool, even when their (asynchronous) resumptions were initiated from other threads.

For performance reasons, fibers will attempt to execute on the same thread for a (configurable) minimum period, before yielding to other fibers. Fibers that resume from asynchronous callbacks will resume on the initiating thread, and continue for some time before yielding and resuming on the runtime thread pool.

These defaults help guarantee stack safety and cooperative multitasking. They can be changed in `RTS` if automatic thread shifting is not desired.
