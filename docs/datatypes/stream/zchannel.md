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

1. **ZChannel.succeed**: Create a channel that succeeds with a given done value, e.g. `ZChannel.succeed(42)`:

```scala mdoc:compile-only
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

The output of the `runCollect` operation is a tuple of two elements: the first is a chunk of data that the channel produced, and the second is the done value. Because this channel doesn't produce any data, the first element is an empty chunk, but it has a 42 as the done value in the second element.
