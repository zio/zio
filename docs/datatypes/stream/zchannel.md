---
id: zchannel
title: "ZChannel"
---

Channels are the nexus of communications. They allow us to have a unidirectional flow of data from the input to the output. They are an underlying abstraction for both `ZStream` and `ZSink`.  In ZIO Streams, we call the input port `ZStream` and the output port `ZSink`. So streams and sinks are just Channels. Channels are the abstraction that unifies both streams and sinks.

A `ZChannel[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]` requires some environment `Env` and have two main operations:
- It can read some data `InElem` from the input port, and finally can terminate with a done value of type `InDone`. If the read operation fails, the channel will terminate with an error of type `InErr`.
- It can write some data `OutElem` to the output port, and finally terminate the channel with a done value of type `OutDone`. If the write operation fails, the channel will terminate with an error of type `OutErr`.

Channels compose in a variety of ways:

- **Piping**— One channel can be piped to another channel, assuming the input type of the second is the same as the output type of the first. We can pipe data from a channel that reads from the input port to a channel that writes to the output port, by using the `pipeTo` or `>>>` operator.

- **Sequencing**— The terminal value of one channel can be used to create another channel, and both the first channel and the function that makes the second channel can be composed into a channel. We use the `ZChannel#flatMap` to sequence the channels.

- **Concating**— The output of one channel can be used to create other channels, which are all concatenated together. The first channel and the function that makes the other channels can be composed into a channel. We use `ZChannel#concat*` operators to do this.

Finally, we can run a channel by using the `ZChannel#run*` operators.

## Creation of a Channel

### `ZChannel.succeed`

Creates a channel that succeeds with a given done value, e.g. `ZChannel.succeed(42)`:

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

### `ZChannel.fail`

Creates a channel that fails with a given error, e.g. `ZChannel.fail(new Exception("error"))`:

```scala mdoc:compile-only
import java.io.IOException
import zio.stream._

val channel: ZChannel[Any, Any, Any, Any, Exception, Nothing, Nothing] = 
  ZChannel.fail(new Exception("error"))
```

### `ZChannel.write*`

Create a channel that writes given elements to the output port:

```scala mdoc:compile-only
import zio._
import zio.stream._

ZChannel.write(1).runCollect.debug
// Output: (Chunk(1),()) 

ZChannel.writeAll(1, 2, 3).runCollect.debug
// Output: (Chunk(1,2,3),()) 

ZChannel.writeChunk(Chunk(1, 2, 3)).runCollect.debug
// Output: (Chunk(1,2,3),()) 
```

### `ZChannel.read*`

Create a channel that reads elements from the input port and returns that as a done value:

Let's start with the simplest read operation, `ZChannel.read`:

```scala mdoc:compile-only
import zio.stream._

val read: ZChannel[Any, Any, Int, Any, None.type, Nothing, Int] = 
  ZChannel.read[Int]  
```

To test this channel, we can create a writer channel and then pipe that to the reader channel:

```scala mdoc:compile-only
import zio.stream._

val read = ZChannel.read[Int] 

(ZChannel.write(1) >>> read).runCollect.debug
// Output: (Chunk(0),1) 
```

In the above example, the writer channel writes the value 1 to the output port, and the reader channel reads the value from the input port and then returns it as a done value.

If we compose multiple read operations, we can read more values from the input port:

```scala mdoc:compile-only
import zio.stream._

val read = ZChannel.read[Int] 

(ZChannel.writeAll(1, 2, 3) >>> (read *> read)).runCollect.debug
// Output: (Chunk(),2) 

(ZChannel.writeAll(1, 2, 3) >>> (read *> read *> read)).runCollect.debug
// Output: (Chunk(),3) 
```

Another useful read operation is `ZChannel.readWith`. Using this operator, after reading a value from the input port, instead of returning it as a done value, we have the ability to pass the input value to another channel.

Let's try some examples:

#### Simple Echo Channel

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

#### Echo Channel Forever

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

#### Replicator Channel

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

#### Counter Channel

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

#### Dedupe Channel

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

### Buffered Channel

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

## Operations

### Sequencing Channels

In order to sequence channels, we can use the `ZChannel#flatMap` operator. When we use the `flatMap` operator, we have the ability to chain two channels together. After the first channel is finished, we can create a new channel based on the terminal value of the first channel:

```scala mdoc:compile-only
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

### Zipping

We have two categories of `zip` operators: ordinary `zipXYZ` operators which run sequentially, and parallel `zipXYZ` operators which run in parallel.

1. `zip`/`<*>` operator:

```scala mdoc:silent
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

### Piping Channels

The values from the output port of the first channel are passed to the input port of the second channel when we pipe a channel to another channel:

```scala mdoc:compile-only
import zio.stream._

(ZChannel.writeAll(1,2,3) >>> (ZChannel.read[Int] <*> ZChannel.read[Int])).runCollect.debug
// Output: (Chunk(),(1,2))
```

### Mapping Channels

#### Mapping The Terminal Done Value (`OutDone`)

The ordinary `map` operator is used to map the done value of a channel:

```scala mdoc:compile-only
import zio.stream._

ZChannel.writeAll(1, 2, 3).map(_ => 5).runCollect.debug 
// (Chunk(1,2,3),5)
```

#### Mapping The Done Value of The Input Port (`InDone`)

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

#### Mapping The Error Value of The Output Port (`OutErr`)

To map the failure value of a channel, we use the `mapError` operator:

```scala mdoc:compile-only
import zio._
import zio.stream._

val channel =
  ZChannel
    .fromZIO(Console.readLine("Please enter you name: "))
    .mapError(_.toString)
```

#### Mapping The Output Elements of a Channel (`OutElem`)

To map the output elements of a channel, we use the `mapOutput` operator:

```scala mdoc:compile-only
import zio.stream._

ZChannel.writeAll(1,2,3).mapOut(_ * 2).runCollect.debug
// Output: (Chunk(2,4,6),())
```

#### Mapping The Input Elements of a Channel (`InElem`)

To map the input elements of a channel, we use the `contramapIn` operator:

```scala mdoc:compile-only
import zio.stream._

(ZChannel.write("123") >>> ZChannel.read[Int].contramapIn[String](_.toInt * 2)).runCollect.debug
// Output: (Chunk(),(246))
```

### Merging Channels

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

### concatMap

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

### mergeMap 

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

### collect

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

### Collecting Channels

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

### Concatenating Channels

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

### Converting Channels

We can convert a channel to other data types using the `ZChannel.toXYZ` methods:

- `ZChannel#toStream`
- `ZChannel#toPipeline`
- `ZChannel#toSink`
- `ZChannel#toPull`
- `ZChannel#toQueue`

### Running Channels

To run a channel, we can use the `ZChannel.runXYZ` methods:

- `ZChannel#run`— The `run` method is the simplest way to run a channel. It only runs a channel that doesn't read any input or write any output.
- `ZChannel#runCollect`— It will run a channel and collects the output and finally returns it along with the done value of the channel.
- `ZChannel#runDrain`— It will run a channel and ignore any emitted output.
- `ZChannel#runScoped`— Using this method, we can run a channel in a scope. So all the finalizers of the scope will be run before the channel is closed.

### Channel Interruption

We can interrupt a channel using the `ZChannel.interruptWhen` operator. It takes a ZIO effect that will be evaluated, if it finishes before the channel is closed, it will interrupt the channel, and the terminal value of the returned channel will be the success value of the effect:

```scala mdoc:silent
import zio._
import zio.stream._

def randomNumbers: ZChannel[Any, Any, Any, Any, Nothing, Int, Nothing] =
  ZChannel
    .fromZIO(Random.nextIntBounded(100))
    .flatMap(ZChannel.write) *>
    ZChannel.fromZIO(ZIO.sleep(1.second)) *> randomNumbers

randomNumbers.interruptWhen(ZIO.sleep(3.seconds).as("Done!")).runCollect.debug
// One output: (Chunk(84,57,70),Done!)
```

Another version of `interruptWhen` takes a `Promise` as an argument. It will interrupt the channel when the promise is fulfilled:

```scala mdoc:compile-only
import zio.stream._

for {
  p <- Promise.make[Nothing, Unit]
  f <- randomNumbers
    .interruptWhen(p)
    .mapOutZIO(e => Console.printLine(e))
    .runDrain
    .fork
  _ <- p.succeed(()).delay(5.seconds)
  _ <- f.join
} yield ()

// Output:
// 74
// 60
// 52
// 52
// 79
``` 
