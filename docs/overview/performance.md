---
id: performance
title: "Performance"
---

ZIO is a high-performance framework that is powered by non-blocking fibers (which will move to _virtual threads_ under Loom).

ZIO's core execution engine minimizes allocations and automatically cancels all unused computation. All data structures included with ZIO are high-performance and non-blocking, and to the maximum extent possible on the JVM, non-boxing.

The `benchmarks` project has a variety of benchmarks that compare the performance of ZIO with other similar projects in the Scala and Java ecosystems, demonstrating 2-100x faster performance in some cases.

Benchmarks to compare the performance of HTTP, GraphQL, RDMBS, and other ZIO integrations can be found in those respective projects.
