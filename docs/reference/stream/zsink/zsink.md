---
id: zsink
title: "ZSink"
---

```scala mdoc:invisible
import zio._
import zio.stream._
import zio.Console._
import java.io.IOException
import java.nio.file.{Path, Paths}
```








## Leftovers

### Collecting Leftovers

A sink consumes a variable amount of `I` elements (zero or more) from the upstream. If the upstream is finite, we can collect leftover values by calling `ZSink#collectLeftover`. It returns a tuple that contains the result of the previous sink and its leftovers:

```scala mdoc:silent:nest
val s1: ZIO[Any, Nothing, (Chunk[Int], Chunk[Int])] =
  ZStream(1, 2, 3, 4, 5).run(
    ZSink.take(3).collectLeftover
  )
// Output: (Chunk(1, 2, 3), Chunk(4, 5))


val s2: ZIO[Any, Nothing, (Option[Int], Chunk[Int])] =
  ZStream(1, 2, 3, 4, 5).run(
    ZSink.head[Int].collectLeftover
  )
// Output: (Some(1), Chunk(2, 3, 4, 5))
```

### Ignoring Leftovers

If we don't need leftovers, we can drop them by using `ZSink#ignoreLeftover`:

```scala mdoc:silent:nest
ZSink.take[Int](3).ignoreLeftover
```
