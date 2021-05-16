---
id: index
title: "Introduction"
---

## Introduction
The primary goal of a streaming library is to introduce a high-level API that abstracts the mechanism of reading and writing operations using data sources and destinations.

A streaming library helps us to concentrate on the business logic and separates us from low-level implementation details.

There are lots of examples of streaming that people might not recognize, this is a common problem especially for beginners. A beginner might say "I don't need a streaming library. Why should I use that?". It's because they don't see streams. Once we use a streaming library, we start to see streams everywhere but until then we don't understand where they are. 

Before diving into ZIO Streams, let's list some of the use cases of a streaming solution and see why we would want to use streaming solutions:

- **Files** — Every time an old school API interacting with a file has very low-level operators like "Open a file, get me an InputStream, and a method to read the next chunk from that InputStream, and also another method to close the file". That is a very low-level, imperative API. But there is a way to see files as streams of bytes. 

- **Sockets** — Instead of working with low-level APIs, we can use streams to provide a stream-based implementation of server socket that hides the low-level implementation details of sockets. We could model socket communication as a function from a stream of bytes to a stream of bytes. We can view the socket input as being a stream and it's output as being another stream.

- **Event-Sourcing** — In these days and age it's common to want to build event-sourced applications which work on events or messages in a queuing system like Kafka or AMQP systems and so forth. The foundation of this architecture is streaming. Also, they are useful when we want to do a lot of data analytics and so forth. 

- **UI Applications** — Streams are the foundation of almost every single modern UI application. Every time we click on something, under the hood that is an event. We can use low-level APIs like subscribing callbacks to user events but also we can view those as streams of events. So we can model subscriptions as streams of events in UI applications. 

- **HTTP Server** —  An HTTP Server can be viewed as a stream. We have a stream of requests that are being transformed to a stream of responses; a function from a stream of bytes that go to a stream of bytes.

So streams are everywhere. We can see all of these different things as being streams. Everywhere we look we can find streams. Basically, all data-driven applications, almost all data-driven applications can benefit from streams. 


## Why Streams?

ZIO Stream has super compelling advantages of using high-level streams. ZIO solution to streaming solves a lot of common streaming pain points. It shines in the following topics:

1. **High-level and Declarative** — This means in a very short snippet of very fluent code we can solve very outrageously complicated problems with just a few simple lines.

2. **Asynchronous and Non-blocking** — They're reactive streams, they don't block threads. They're super-efficient and very scalable. We can minimize our application latency and increase its performance. We can avoid wasting precious thread resources by using ZIO's non-blocking and asynchronous streams. 

3. **Concurrency** — Streams are concurrent. They have a lot of concurrent operators. All the operations on them are safe to use in presence of concurrency. 

4. **Parallelism** — There are lots of parallel operators just like ZIO gives us parallel operators with everything. We can use the parallel version of operators, like `mapMPar`, `flatMapPar`.

Parallel operators allow us to fully saturate and utilize all CPU cores of our machine. If we need to do bulk processing on a lot of data and use all the cores on our machine, so we can speed up the process by using these parallel operators. 

5. **Resource Safety** — Resource safety is not a simple thing to guarantee. Assume when we have several streams, some of them are sockets and files, some of them are API calls and database queries. When we have all these streams and we are transforming and combining them and we are timing some out, and also some of them are doing concurrent merges; what happens when things go wrong in one part of that stream graph? ZIO Streams provides the guarantee that it will never leak resources. 

So when streams have to be terminated for error or timeout or interruption reasons or whatever ZIO will always safely shut down and release the resources associated with that stream usage. 

We don't have to worry about resource management anymore. We can work at this high level and just declaratively describe our stream graph and then ZIO will handle the tricky job of executing that and taking care to make sure that no resources are leaked in the event of something bad happens or even just a timeout, or interruption, or just we're done with a result. So resources are always safely released without any leaks. 

6. **High Performance and Efficientcy** — When we are doing an I/O job, the granularity of data is not at the level of a single byte. For example, we never read or write a single element from/to a file descriptor. We always use multiple elements. So when we are doing an I/O operation it's a poor practice to read/write element by element and this decreases the performance of our program. 

So in order to achieve high efficiency, ZIO Stream implicitly chunks everything. But it still presents us with a nice API that is at the level of every single element. So we can always deal with streams of individual elements even though behind-the-scenes ZIO is doing some chunking to make that performant. This is one of the tricks that enables ZIO Streams to have such great performance. 

ZIO Streams are working at the level of chunks. Every time we are working with ZIO Streams, we are also working with chunks implicitly. So there are no streams with individual elements. Streams always use chunks. Every time we pull an element out of a ZIO Stream, we end up pulling a chunk of elements under the hood. 

7. **Seamless Integration with ZIO** — ZIO stream has a powerful seamless integrated support for ZIO. It uses ZIO's ZManaged, Schedule, and any other powerful data types in ZIO. So we can stay within the same ecosystem and get all its significant benefits.


8. **Back-Pressure** — We get back-pressuring for free. With ZIO Streams it's actually not a back-pressuring but it's equivalent. So in push-based streams like Akka Streams, Streams are push-based, so an element comes in and it's pushed downward in the pipeline. That's what leads to the need for back-pressuring. Back-pressuring makes the push-based stream much more complicated than it needs to be. 

Push-based streams are good at splitting streams because we have one element and we can push it to two different places. That is nice and elegant. But they're terrible at merging streams and that's because you end up needing to use Queues and then we run into a problem. In the case of using queues, we need back-pressuring, which leads to a complicated architecture. 

In ZIO, when we merge streams, ZIO uses pull-based streams. They need minimal computation because we pull elements at the end of our data pipeline when needed. When the sink asks for one element, then that ripples all the way back through the very edges of the system. 

So when we pull one element at the and, no additional computation takes place until we pull the next element or decide that we're done pulling and we close the stream. It causes the minimum amount of computation necessary to produce the result. 

Using the pull-based mechanism we have no producers and it prevents producing more events than necessary. So ZIO Streams does not need back-pressure even though it provides a form of that because it's lazy and on-demand using pull-based streams. 

So ZIO Stream gives us the benefits of back-pressuring, but in a cleaner conceptual model that is very efficient and doesn't require all these levels of buffering.

7. **Process Infinite Data using Finite Memory** — Streams let us work on infinite data in a finite amount of memory. When we are writing streaming logic, we don't have to worry about how much data we are ultimately going to be processing. 

That's because you're just building a workflow, a description of the processing. We are not manually loading up everything into memory, into a list, and then doing our processing on a list. That doesn't work very well because we can only fit a finite amount of memory into your computer at one time. 

ZIO Streams let us just concentrate on our business problem, and not on how much memory this program is going to consume. So we can write these computations that work over streams that are totally infinite but in a finite amount of memory and ZIO handles that for us.

## Core Abstractions

To define a stream workflow there are three core abstraction in ZIO Stream; Streams, Sinks, and transducers:

1. **[ZStream](zstream.md)** — Streams act as _sources_ of values. We get elements from them, they're producers they produce values.

2. **[Sink](sink.md)** — Sinks act as _receptacles_ or sinks for values. They consume values.

3. **Transducer** — Transducers act as _transformers_ of values. They take individual values and they transform or decode them. 

### Stream

The ZStream data type similar to the `ZIO` effect has `R`, `E`, and `A`. It has environment, error, and element type. 

The difference between the `ZIO` and `ZStream` is that:

- A `ZIO` effect will always succeed or fail. If it succeeds, it will succeed with a single element.

- A `ZStream` can succeed with zero or more elements. So we can have an empty stream in ZIO Streams. A `ZStream[R, E, A]` doesn't necessarily produce any `A`'s, it produces zero or more `A`'s. 

So that's a big difference. There's no such thing as a non-empty `ZStream`. All `ZStreams` are empty, they can produce any number of `A`'s, which could be an infinite number of A's. 

There is no way to check to see if a stream is empty or not, because that computation hasn't started. Streams are super lazy, so there's no way to say "Oh! does this stream contain anything?" No! We can't figure that out. We have to use it and try to do something with it and then we are going to figure out whether or not it had something.


### Sink

The basic idea behind Sink is that it consumes values of some type and then it ends up when it is done. When the Sink is done, it produces the value of a different type. 

Sinks are a bit like parsers; they consume some input, when they're done, they produce a value. Also, they are like databases; they read enough from input when they don't want anymore, they can produce some value or return unit.

Some Sinks will produce nothing as their return type parameter is `Nothing`, which means that the Sink is always going to accept more and more input; it is never ever going to be done. 


Just like Streams, Sinks are super compositional. Sink operators allow us to combine two Sinks together or transform them. That allows us to generate a vast variety of Sinks.

Streams and Sinks are duals in category theory. One produces value and the other one consumes them. They are mere images of each other. They both have to exist. 

A streaming library cannot be complete unless it has streams and sinks. That's why ZIO has a sort of better design than FS2 because FS2 has a stream, but it doesn't have a Sink. Its Sink is just faked. It doesn't actually have a real Sink. ZIO has a real Sink, and we can compose them to generate new Sinks.

### Transducer

With Transducers, we can transform streams from one type to another, in a stateful fashion, which is sometimes necessary when we are doing encoding and decoding. 

Transducer is a transformer of element types. Transducer accepts some element of type `A` and produces some element of type `B`, and it may fail along the way or use the environment. It just transforms elements from one type to another type in a stateful way. 

For example, we can write counter with Transducers. We take strings and then split them into lines and then we take the lines and we split them into words and then we count them. 

Another common use case of Transducers is writing codecs. We can use them to decode the bytes into strings. We have a bunch of bytes and we want to end up with a JSON and then once we are in JSON land we want to go from JSON to our user-defined data type. So, by writing a Transducer we can convert that JSON to our user-defined data type.

Transducers are very efficient. They only exist for efficiency reasons because we can do everything we need actually with Sinks. Transducers exist only to make transformations faster. Sinks are not super fast to change from one Sink to another. So Transducers were invented to make it possible to transform element types in a compositional way without any of the performance overhead associated with changing over a Sink. 

Transducers can be thought of as element transformers. They transform elements of a stream:

1. We can take a Transducer and we can stack it onto a Stream to change the element type. For example, we have a Stream of `A`'s and a Transducer that goes from `A` to `B`, so we can take that Transducer from `A` to `B` and stack it on the Stream to get back a Stream of `B`'s. 

2. Also, we can stack a Transducer onto the front of a Sink to change the input element type. If some sink consumes `B`'s and we have a Transducer from `A` to `B` we can take that Transducer stack it onto the front of the Sink and get back a new Sink that consumes `A`'s. 

Assume we are building the data pipeline, the elements come from the far left and they end up on the far right. Events come from the Stream, they end up on the Sink, along the way they're transformed by Transducers. Transducers are the middle section of the pipe that keep on transforming those elements in a stateful way. 
