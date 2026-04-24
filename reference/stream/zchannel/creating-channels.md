# Creating Channels

> `ZChannel` have several constructors and also built-in channels, where suitable to create more complex channels.

`ZChannel` have several constructors and also built-in channels, where suitable to create more complex channels.

Without further ado, let's learn them one by one:

## `ZChannel.succeed`

Creates a channel that succeeds with a given done value, e.g. `ZChannel.succeed(42)`:

```scala

val channel: ZChannel[Any, Any, Any, Any, Nothing, Nothing, Int] = 
  ZChannel.succeed(42)
```

This channel doesn't produce any data but succeeds with a done value of type `Int`. Let's try to `runCollect` this channel and see what happens:

```scala
channel.runCollect.debug
```

The output of the `runCollect` operation is a tuple of two elements: the first is a chunk of data that the channel produced, and the second is the done value. Because this channel doesn't produce any data, the first element is an empty chunk, but it has a 42 as the done value in the second element.

## `ZChannel.fail`

Creates a channel that fails with a given error, e.g. `ZChannel.fail(new Exception("error"))`:

```scala

val channel: ZChannel[Any, Any, Any, Any, Exception, Nothing, Nothing] = 
  ZChannel.fail(new Exception("error"))
```

## `ZChannel.write*`

Create a channel that writes given elements to the output port:

```scala

ZChannel.write(1).runCollect.debug
// Output: (Chunk(1),()) 

ZChannel.writeAll(1, 2, 3).runCollect.debug
// Output: (Chunk(1,2,3),()) 

ZChannel.writeChunk(Chunk(1, 2, 3)).runCollect.debug
// Output: (Chunk(1,2,3),())
```

## `ZChannel.read*`

Create a channel that reads elements from the input port and returns that as a done value:

Let's start with the simplest read operation, `ZChannel.read`:

```scala

val read: ZChannel[Any, Any, Int, Any, None.type, Nothing, Int] = 
  ZChannel.read[Int]
```

To test this channel, we can create a writer channel and then pipe that to the reader channel:

```scala

val read = ZChannel.read[Int] 

(ZChannel.write(1) >>> read).runCollect.debug
// Output: (Chunk(0),1)
```

In the above example, the writer channel writes the value 1 to the output port, and the reader channel reads the value from the input port and then returns it as a done value.

If we compose multiple read operations, we can read more values from the input port:

```scala

val read = ZChannel.read[Int] 

(ZChannel.writeAll(1, 2, 3) >>> (read *> read)).runCollect.debug
// Output: (Chunk(),2) 

(ZChannel.writeAll(1, 2, 3) >>> (read *> read *> read)).runCollect.debug
// Output: (Chunk(),3)
```

Another useful read operation is `ZChannel.readWith`. Using this operator, after reading a value from the input port, instead of returning it as a done value, we have the ability to pass the input value to another channel.
