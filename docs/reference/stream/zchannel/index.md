---
id: index
title: "Introduction To ZChannels"
---

Channels are the nexus of communications, which support both reading and writing. They allow us to have a unidirectional flow of data from the input to the output.

A `ZChannel[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]` requires some environment `Env` and have two main operations:

- It can read some data `InElem` from the input port, and finally can terminate with a done value of type `InDone`. If the read operation fails, the channel will terminate with an error of type `InErr`.

- It can write some data `OutElem` to the output port, and finally terminate the channel with a done value of type `OutDone`. If the write operation fails, the channel will terminate with an error of type `OutErr`.

They are an underlying abstraction for `ZStream`, `ZPipeline`, and `ZSink`. In ZIO Streams, we call the input port `ZStream`, the output port `ZSink`, and the middle part `ZPipeline`:

- A `Channel` can write some elements to the _output_, and it can terminate with some sort of _done_ value. The `Channel` uses this _done_ value to notify the downstream `Channel` that its emission of elements is finished. In ZIO, the `ZStream` is encoded as an output side of the `Channel`.

- A `Channel` can read from its input, and it can also terminate with some sort of _done_ value, which is an upstream result. So a `Channel` has the _input type_, and the _input done type_. The `Channel` uses this _done_ value to determine when the upstream `Channel` finishes its emission. In ZIO, the `ZSink` is encoded as an input side of the `Channel`.

- A `Channel` can read from its input, do some transformation on the elements, and write to its output. In ZIO, the `ZPipeline` is encoded as a middle part of both sides of the `Channel`. Pipelines accept a stream as input and return the transformed stream as output.

:::caution

`ZChannel` is an underlying abstraction. So we do not usually need to use it directly. So if you are learning ZIO Streams, we recommend you to focus on `ZStream`, `ZPipeline`, and `ZSink` data types.
:::

Let's take a look at how `ZStream`, `ZPipeline` and `ZSink` are defined using `ZChannel`:

```scala
trait ZChannel[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone] 

case class ZStream[-R, +E, +A] (
  val channel: ZChannel[R, Any, Any, Any, E, Chunk[A], Any]
)

case class ZSink[-R, +E, -In, +L, +Z] (
  val channel: ZChannel[R, ZNothing, Chunk[In], Any, E, Chunk[L], Z]
)

case class ZPipeline[-R, +E, -In, +Out] (
  val channel: ZChannel[R, ZNothing, Chunk[In], Any, E, Chunk[Out], Any]
)
```

So we can say that:

- `ZStream[R, E, A]` is a channel that uses `R` as its environment, produce `Chunk[A]` to its output port, can terminate with `Any` success value or can terminate with a failure of type `E`.

- `ZPipeline[R, Err, In, Out]` is a channel that uses `R` as its environment, consumes `Chunk[Int]` from its input port, and produces `Chunk[Out]` to its output port.

- `ZSink[R, E, In, L , Z]` is a channel that uses `R` as its environment, consumes `Chunk[In]` from its input port, and produces `Chunk[L]` to its output port as its leftovers, and can terminate with a success value of type `Z` or can terminate with a failure of type `E`.

![ZIO Streams 2.x](/img/assets/zio-streams-2.x.svg)

Channels compose in a variety of ways:

- **Piping**— One channel can be piped to another channel, assuming the input type of the second is the same as the output type of the first. We can pipe data from a channel that reads from the input port to a channel that writes to the output port, by using the `pipeTo` or `>>>` operator.

- **Sequencing**— The terminal value of one channel can be used to create another channel, and both the first channel and the function that makes the second channel can be composed into a channel. We use the `ZChannel#flatMap` to sequence the channels.

- **Concating**— The output of one channel can be used to create other channels, which are all concatenated together. The first channel and the function that makes the other channels can be composed into a channel. We use `ZChannel#concat*` operators to do this.

Finally, we can run a channel by using the `ZChannel#run*` operators.
