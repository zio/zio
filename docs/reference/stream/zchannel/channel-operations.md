---
id: channel-operations
title: "Channel Operations"
---

## Piping

The values from the output port of the first channel are passed to the input port of the second channel when we pipe a channel to another channel:

```scala mdoc:compile-only
import zio.stream._

(ZChannel.writeAll(1,2,3) >>> (ZChannel.read[Int] <*> ZChannel.read[Int])).runCollect.debug
// Output: (Chunk(),(1,2))
```

## Sequencing

In order to sequence channels, we can use the `ZChannel#flatMap` operator. When we use the `flatMap` operator, we have the ability to chain two channels together. After the first channel is finished, we can create a new channel based on the terminal value of the first channel:

```scala mdoc:compile-only
import zio._
import zio.stream._

ZChannel
  .fromZIO(
    Console.readLine("Please enter a number: ").map(_.toInt)
  )
  .flatMap {
    case n if n < 0 => ZChannel.fail("Number must be positive")
    case n          => ZChannel.writeAll((0 to n): _*)
  }
  .runCollect
  .debug
// Sample Output:
// Please enter a number: 5
// (Chunk(0,1,2,3,4,5),())
```

## Concatenating

Suppose there is a channel that creates a new channel for each element of the outer channel and emits them to the output port. We can use `concatOut` to concatenate all the inner channels into a single channel:

```scala mdoc:compile-only
import zio.stream._

ZChannel
  .writeAll("a", "b", "c")
  .mapOut { l =>
    ZChannel.writeAll((1 to 3).map(i => s"$l$i"):_*) 
  }
  .concatOut
  .runCollect
  .debug
// Output: (Chunk(a1,a2,a3,b1,b2,b3,c1,c2,c3),())
```

We can do the same with `ZChannel.concatAll`:

```scala mdoc:compile-only
import zio.stream._

ZChannel
  .concatAll(
    ZChannel
      .writeAll("a", "b", "c")
      .mapOut { l =>
        ZChannel.writeAll((1 to 3).map(i => s"$l$i"): _*)
      }
  )
  .runCollect
  .debug
  
// Output: (Chunk(a1,a2,a3,b1,b2,b3,c1,c2,c3),())
```

## Zipping

We have two categories of `zip` operators: ordinary `zipXYZ` operators which run sequentially, and parallel `zipXYZ` operators which run in parallel.

1. `zip`/`<*>` operator:

```scala mdoc:silent
import zio.stream._

val first = ZChannel.write(1,2,3) *> ZChannel.succeed("Done!")
val second = ZChannel.write(4,5,6) *> ZChannel.succeed("Bye!")

(first <*> second).runCollect.debug
// Output: (Chunk((1,2,3),(4,5,6)),(Done!,Bye!))
```

2. `zipRight`/`*>` operator:

```scala mdoc:compile-only
(first *> second).runCollect.debug
// Output: (Chunk((1,2,3),(4,5,6)),Bye!)
```

3. `zipLeft`/`<*` operator:

```scala mdoc:compile-only
(first <* second).runCollect.debug
// Output: (Chunk((1,2,3),(4,5,6)),Done!)
```

```scala mdoc:invisible:reset

```

## Mapping

### Mapping The Terminal Done Value (`OutDone`)

The ordinary `map` operator is used to map the done value of a channel:

```scala mdoc:compile-only
import zio.stream._

ZChannel.writeAll(1, 2, 3).map(_ => 5).runCollect.debug 
// (Chunk(1,2,3),5)
```

### Mapping The Done Value of The Input Port (`InDone`)

To map the done value of the input port, we use the `contramap` operator:

```scala mdoc:compile-only
import zio.stream._

(ZChannel.succeed("5") >>>
  ZChannel
    .readWith(
      (i: Int) => ZChannel.write(ZChannel.write(i)),
      (_: Any) => ZChannel.unit,
      (d: Int) => ZChannel.succeed(d * 2)
    )
    .contramap[String](_.toInt)).runCollect.debug
// Output: (Chunk(),(10))
```

### Mapping The Error Value of The Output Port (`OutErr`)

To map the failure value of a channel, we use the `mapError` operator:

```scala mdoc:compile-only
import zio._
import zio.stream._

val channel =
  ZChannel
    .fromZIO(Console.readLine("Please enter you name: "))
    .mapError(_.toString)
```

### Mapping The Output Elements of a Channel (`OutElem`)

To map the output elements of a channel, we use the `mapOutput` operator:

```scala mdoc:compile-only
import zio.stream._

ZChannel.writeAll(1,2,3).mapOut(_ * 2).runCollect.debug
// Output: (Chunk(2,4,6),())
```

### Mapping The Input Elements of a Channel (`InElem`)

To map the input elements of a channel, we use the `contramapIn` operator:

```scala mdoc:compile-only
import zio.stream._

(ZChannel.write("123") >>> ZChannel.read[Int].contramapIn[String](_.toInt * 2)).runCollect.debug
// Output: (Chunk(),(246))
```

## Merging

Merge operators are used to merging multiple channels into a single channel. They are used to combine the output port of channels concurrently. Every time any of the channels produces a value, the output port of the resulting channel will produce a value.

Assume we have the following channel:

```scala mdoc:silent
import zio._
import zio.stream._

def iterate(
    from: Int,
    to: Int
): ZChannel[Any, Any, Any, Any, Nothing, Int, Unit] =
  if (from <= to)
    ZChannel.write(from) *>
      ZChannel.fromZIO(
        Random
          .nextLongBounded(1000)
          .flatMap(delay => ZIO.sleep(Duration.fromMillis(delay)))
      ) *> iterate(from + 1, to)
  else ZChannel.unit
```

Now let's merge some channels:

```scala mdoc:compile-only
import zio._
import zio.stream._

ZChannel
  .mergeAllUnbounded(
    ZChannel.writeAll(
      iterate(1, 3),
      iterate(4, 6),
      iterate(6, 9)
    )
  )
  .mapOutZIO(i => Console.print(i + " "))
  .runDrain
// Sample output: 1 4 6 7 8 2 3 5 6 9 
```

The `ZChannel.mergeAllUnbounded` uses the maximum buffer size, which is `Int.MaxValue` by default. This means that if we use this operator for long-running channels, which produce a lot of values, it can cause the program to run out of memory.

We have another operator called `ZChannel.mergeAll`, which allows us to specify the buffer size, the concurrency level, and also the strategy for merging the channels.

Note that if we want to merge channels sequentially, we can use the `zip` or `flatMap` operators:

```scala mdoc:silent
import zio.stream._

(iterate(1, 3) <*> iterate(4, 6) <*> iterate(6, 9)).runCollect.debug
// Output: (Chunk(1,2,3,4,5,6,7,8,9),())
```

```scala mdoc:invisible:reset

```

## Collecting

1. `collectElements` collects all the elements of the channel along with its done value as a tuple and returns a new channel with a terminal value of that tuple:

```scala mdoc:compile-only
import zio.stream._

ZChannel.writeAll(1,2,3,4,5)
  .collectElements
  .runCollect
  .debug
// Output: (Chunk(),(Chunk(1,2,3,4,5),()))
```

2. `emitCollect` is like the `collectElements` operator, but it emits the result of the collection to the output port of the new channel:

```scala mdoc:compile-only
import zio.stream._

ZChannel.writeAll(1,2,3,4,5)
  .emitCollect
  .runCollect
  .debug
// Output: (Chunk((Chunk(1,2,3,4,5),())),())
```

## Converting

We can convert a channel to other data types using the `ZChannel.toXYZ` methods:

- `ZChannel#toStream`
- `ZChannel#toPipeline`
- `ZChannel#toSink`
- `ZChannel#toPull`
- `ZChannel#toQueue`

## concatMap

`concatMap` is a combination of two operators: mapping and concatenation. Using this operator, we can map every emitted element of a channel (outer channel) to a new channel (inner channels), and then concatenate all the inner channels into a single channel. The concatenation is done **sequentially**, so we use this operator when the order of the elements is important:

```scala mdoc:compile-only
import zio.stream._

ZChannel
  .writeAll("a", "b", "c")
  .concatMap { l =>
    def inner(from: Int, to: Int): ZChannel[Any, Any, Any, Any, Nothing, String, Unit] =
      if (from <= to) ZChannel.write(s"$l$from") *> inner(from + 1, to)
      else ZChannel.unit  
    inner(0, 5)
  }
  .runCollect
  .debug
// Output: (Chunk(a0,a1,a2,a3,a4,a5,b0,b1,b2,b3,b4,b5,c0,c1,c2,c3,c4,c5),())
```

In the above example, we create a new channel for every element of the outer channel. The new inner channel is responsible for emitting from zero to five with the label of the outer channel. When an inner channel is done, it moves to the next inner channel sequentially. There is a similar operator called `mergeMap` that works in parallel and doesn't preserve the order of the elements.

## mergeMap

`mergeMap` is a combination of two operators: mapping and merging. Using this operator, we can map every emitted element of a channel (outer channel) to a new channel (inner channel), and then run all the inner channels in parallel and merge them into a single channel. The merge operation is done **in parallel**, so we use this operator when the order of the elements is not important, and we want to process all inner channels in parallel:

```scala mdoc:compile-only
import zio.stream._
import zio.stream.ZChannel._

ZChannel
  .writeAll("a", "b", "c")
  .mergeMap(8, 1, MergeStrategy.BackPressure) { l =>
    def inner(
        from: Int,
        to: Int
    ): ZChannel[Any, Any, Any, Any, Nothing, String, Unit] =
      if (from <= to) ZChannel.write(s"$l$from") *> inner(from + 1, to)
      else ZChannel.unit
    inner(0, 5)
  }
  .runCollect
  .debug
// Non-deterministic output: (Chunk(a0,a1,a2,b0,b1,b2,b3,c0,b4,c1,a3,c2,b5,a4,c3,c4,a5,c5),())
```

## collect

`collect` is a combination of two operations: filtering and mapping. Using this operator, we can filter the elements of a channel using a partial function, and then map the filtered elements:

```scala mdoc:compile-only
import zio.stream._

ZChannel
  .writeAll((1 to 10): _*)
  .collect { case i if i % 3 == 0 => i * 2 }
  .runCollect
  .debug
// Output: (Chunk(6,12,18),())
```
