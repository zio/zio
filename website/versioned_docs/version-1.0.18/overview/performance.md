---
id: overview_performance
title: "Performance"
---

`zio` has excellent performance, featuring a hand-optimized, low-level interpreter that achieves zero allocations for right-associated binds, and minimal allocations for left-associated binds.

The `benchmarks` project may be used to compare `IO` with other effect monads, including `Future` (which is not an effect monad but is included for reference), Monix `Task`, and Cats `IO`.

As of the time of this writing, `IO` is significantly faster than or at least comparable to all other purely functional solutions.
