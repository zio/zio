---
id: execution-strategy
title: "Execution Strategy"
---

ZIO Test has two different strategies to run members of a test suite: _sequential_ and _parallel_. Accordingly, there are two test aspects for specifying the execution strategy:

## Parallel

The default strategy is parallel. We can explicitly enable it using `TestAspect.parallel`:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

suite("Parallel")(
  test("A")(Live.live(ZIO.attempt("Running Test A").delay(1.second)).debug.map(_ => assertTrue(true))),
  test("B")(ZIO.attempt("Running Test B").debug.map(_ => assertTrue(true))),
  test("C")(Live.live(ZIO.attempt("Running Test C").delay(500.millis)).debug.map(_ => assertTrue(true)))
) @@ TestAspect.parallel
```

After running this suite, we have the following output:

```
Running Test B
Running Test C
Running Test A
+ Parallel
  + A
  + B
  + C
```

To change the degree of the parallelism, we can use the `parallelN` test aspect. It takes the number of fibers and executes the members of a suite in parallel up to the specified number of concurrent fibers.

## Sequential

To execute them sequentially, we can use the `sequential` test aspect:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

suite("Sequential")(
  test("A")(Live.live(ZIO.attempt("Running Test A").delay(1.second)).debug.map(_ => assertTrue(true))),
  test("B")(ZIO.attempt("Running Test B").debug.map(_ => assertTrue(true))),
  test("C")(Live.live(ZIO.attempt("Running Test C").delay(500.millis)).debug.map(_ => assertTrue(true)))
) @@ TestAspect.sequential
```

And here is the output:

```
Running Test A
Running Test B
Running Test C
+ Sequential
  + A
  + B
  + C
```
