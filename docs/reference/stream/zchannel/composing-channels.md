---
id: composing-channels
title: "Composing Channels"
---

We can write more complex channels by using `read` operators and composing them recursively. 

Let's try some examples:

## Simple Echo Channel

Assume we want to read a value from the input port and then print it to the console, we can use the `ZChannel.readWith` operator to do this:

```scala mdoc:compile-only
import zio._
import zio.stream._

val producer = 
  ZChannel.write(1)
  
val consumer = 
  ZChannel.readWith(
    (i: Int) => ZChannel.fromZIO(Console.printLine("Consumed: " + i)),
    (_: Any) => ZChannel.unit,
    (_: Any) => ZChannel.unit
  )

(producer >>> consumer).run
// Output:
// Consumed: 1
```

## Echo Channel Forever

We can also recursively compose channels to create a more complex channel. In the following example, we are going to continuously read values from the console and write them back to the console:

```scala mdoc:compile-only
import zio._
import zio.stream.ZChannel

import java.io.IOException

object MainApp extends ZIOAppDefault {
  val producer: ZChannel[Any, Any, Any, Any, IOException, String, Nothing] =
    ZChannel
      .fromZIO(Console.readLine("Please enter some text: "))
      .flatMap(i => ZChannel.write(i) *> producer)

  val consumer: ZChannel[Any, Any, String, Any, IOException, Nothing, Unit] =
    ZChannel.readWith(
      (i: String) => i match {
        case "exit" => ZChannel.unit
        case _ => ZChannel.fromZIO(Console.printLine("Consumed: " + i)) *> consumer
      },
      (_: Any) => ZChannel.unit,
      (_: Any) => ZChannel.unit
    )

  def run = (producer >>> consumer).run
}

// Output:
// Please enter some text: Foo
// Consumed: Foo
// Please enter some text: Bar
// Consumed: Bar
// Please enter some text: Baz
// Consumed: Baz
// Please enter some text: exit
```

## Replicator Channel

In this example, we are going to create a channel that replicates any input values to the output port.

```scala mdoc:compile-only
import zio._
import zio.stream._

object MainApp extends ZIOAppDefault {
  lazy val doubler: ZChannel[Any, Any, Int, Any, Nothing, Int, Unit] =
    ZChannel.readWith(
      (i: Int) => ZChannel.writeAll(i, i) *> doubler,
      (_: Any) => ZChannel.unit,
      (_: Any) => ZChannel.unit
    )
  def run = (ZChannel.writeAll(1,2,3,4,5) >>> doubler).runCollect.debug
}
// Output:
//   (Chunk(1,1,2,2,3,3,4,4,5,5),())
```

## Counter Channel

We can also use `Ref` to create a channel with an updatable state. For example, we can create a channel that keeps track number of all the values that it has read and finally returns it as the done value:

```scala mdoc:compile-only
import zio._
import zio.stream._

object MainApp extends ZIOAppDefault {
  val counter = {
      def count(c: Int): ZChannel[Any, Any, Int, Any, String, Int, Int] =
        ZChannel.readWith(
          (i: Int) => ZChannel.write(i) *> count(c + 1),
          (_: Any) => ZChannel.fail("error"),
          (_: Any) => ZChannel.succeed(c)
        )

      count(0)
    }

  def run = (ZChannel.writeAll(1, 2, 3, 4, 5) >>> counter).runCollect.debug
}

// Output:
// (Chunk(1,2,3,4,5), 5)
```

## Dedupe Channel

Sometimes we want to remove duplicate values from the input port. We need to have a state that keeps track of the values that have been seen. So if a value is seen for the first time, we can write it to the output port. If a value is duplicated, we can ignore it:

```scala mdoc:compile-only
import zio._
import zio.stream._

import scala.collection.immutable.HashSet

object MainApp extends ZIOAppDefault {
  val dedup =
    ZChannel.fromZIO(Ref.make[HashSet[Int]](HashSet.empty)).flatMap { ref =>
      lazy val inner: ZChannel[Any, Any, Int, Any, Nothing, Int, Unit] =
        ZChannel.readWith(
          (i: Int) =>
            ZChannel
              .fromZIO(ref.modify(s => (s contains i, s incl i)))
              .flatMap {
                case true  => ZChannel.unit
                case false => ZChannel.write(i)
              } *> inner,
          (_: Any) => ZChannel.unit,
          (_: Any) => ZChannel.unit
        )
      inner
    }

  def run =
    (ZChannel.writeAll(1, 2, 2, 3, 3, 4, 2, 5, 5) >>> dedup).runCollect.debug
}
// Output:
// (Chunk(1,2,3,4,5),())
```

## Buffered Channel

With help of `ZChannel.buffer` or `ZChannel.bufferChunk`, we can create a channel backed by a buffer.
- If the buffer is full, the channel puts the values in the buffer to the output port.
- If the buffer is empty, the channel reads the value from the input port and puts it in the output port.

Assume we have a channel written as follows:

```scala mdoc:silent
import zio._
import zio.stream._

def buffered(input: Int) =
  ZChannel
    .fromZIO(Ref.make(input))
    .flatMap { ref =>
      ZChannel.buffer[Any, Int, Unit](
        0,
        i => if (i == 0) true else false,
        ref
      )
    }
```

If the buffer is empty (zero value), the `buffered` channel passes the `1` to the output port:

```scala mdoc:compile-only
(ZChannel.write(1) >>> buffered(0)).runCollect.debug
// Output: (Chunk(1),())
```

If the buffer is full, the channel puts the value from the buffer to the output port:

```scala mdoc:compile-only
(ZChannel.write(1) >>> buffered(0)).runCollect.debug
// Output: (Chunk(2,1),())
```
