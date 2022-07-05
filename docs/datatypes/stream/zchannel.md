---
id: zchannel
title: "ZChannel"
---

Channels are the nexus of communications. They allow us to have a unidirectional flow of data from the input to the output. They are an underlying abstraction for both `ZStream` and `ZSink`.  In ZIO Streams, we call the input port `ZStream` and the output port `ZSink`. So streams and sinks are just Channels. Channels are the abstraction that unifies both streams and sinks.

A `ZChannel[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]` requires some environment `Env` and have two main operations:
- It can read some data `InElem` from the input port, and finally can terminate with a done value of type `InDone`. If the read operation fails, the channel will terminate with an error of type `InErr`.
- It can write some data `OutElem` to the output port, and finally terminate the channel with a done value of type `OutDone`. If the write operation fails, the channel will terminate with an error of type `OutErr`.

We can pipe data from a channel that reads from the input port to a channel that writes to the output port, by using the `ZChannel#pipeTo` or `>>>` operator.

Finally, we can run a channel by using the `ZChannel#run*` operators.

## Creation of a Channel

1. **ZChannel.succeed**— Creates a channel that succeeds with a given done value, e.g. `ZChannel.succeed(42)`:

```scala mdoc:silent
import zio.stream._

val channel: ZChannel[Any, Any, Any, Any, Nothing, Nothing, Int] = 
  ZChannel.succeed(42)
```

This channel doesn't produce any data but succeeds with a done value of type `Int`. Let's try to `runCollect` this channel and see what happens:

```scala mdoc:compile-only
channel.runCollect.debug

// Output: 
//   (Chunk(),42)
```

```scala mdoc:invisible:reset

```

The output of the `runCollect` operation is a tuple of two elements: the first is a chunk of data that the channel produced, and the second is the done value. Because this channel doesn't produce any data, the first element is an empty chunk, but it has a 42 as the done value in the second element.

2. **ZChannel.fail**— Creates a channel that fails with a given error, e.g. `ZChannel.fail(new Exception("error"))`:

```scala mdoc:compile-only
import java.io.IOException
import zio.stream._

val channel: ZChannel[Any, Any, Any, Any, Exception, Nothing, Nothing] = 
  ZChannel.fail(new Exception("error"))
```

3. **ZChannel.writeXYZ**— Create a channel that writes given elements to the output port:

```scala mdoc:compile-only
import zio.stream._

ZChannel.write(1).runCollect.debug
// Output: (Chunk(1),()) 

ZChannel.write(1, 2, 3).runCollect.debug
// Output: (Chunk(1,2,3),()) 

ZChannel.chunk(Chunk(1, 2, 3)).runCollect.debug
// Output: (Chunk(1,2,3),()) 
```

4. **ZChannel.readXYZ**— Create a channel that reads elements from the input port and returns that as a done value:

Let's start with the simplest read operation, `ZChannel.read`:

```scala mdoc:compile-only
import zio.stream._

val read: ZChannel[Any, Any, Int, Any, None.type, Nothing, Int] = 
  ZChannel.read[Int]  
```

To test this channel, we can create a writer channel and then pipe that to the reader channel:

```scala mdoc:compile-only
(ZChannel.write(1) >>> ZChannel.read[Int]).runCollect.debug
// Output: (Chunk(0),1) 
```

In the above example, the writer channel writes the value 1 to the output port, and the reader channel reads the value from the input port and then returns it as a done value.

Another useful read operation is `ZChannel.readWith`. Using this operator, after reading a value from the input port, instead of returning it as a done value, we have the ability to pass the input value to another channel.

For example, assume we want to read a value from the input port and then print it to the console, we can use the `ZChannel.readWith` operator to do this:

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

Here is another example that replicates any input to the done value:

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

We can also use `Ref` to create a channel with an updatable state. For example, we can create a channel that keeps track number of all the values that it has read and finally returns it as the done value:

```scala mdoc:compile-only
import zio._
import zio.stream._

object MainApp extends ZIOAppDefault {
  val counter =
    ZChannel.fromZIO(Ref.make[Int](0)).flatMap { ref =>
      lazy val inner: ZChannel[Any, Any, Int, Any, Nothing, Int, Unit] =
        ZChannel.readWith(
          (i: Int) =>
            ZChannel.fromZIO(ref.update(_ + 1)) *> ZChannel
              .write(i) *> inner,
          (_: Any) => ZChannel.unit,
          (_: Any) => ZChannel.unit
        )

      inner *> ZChannel.fromZIO(ref.get)
    }

  def run = (ZChannel.writeAll(1, 2, 3, 4, 5) >>> counter).runCollect.debug
}

// Output:
// (Chunk(1,2,3,4,5), 5)
```
