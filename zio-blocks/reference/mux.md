# Mux

> `zio-blocks-mux` is a **high-performance multiplexer for ID-multiplexed protocols** (HTTP/2, QUIC, WebSockets with multiplexing, and other stream-based transports). It manages multiple concurrent independent streams over a shared transport, each identified by a unique ID, with separate inbound/outbound message queues and automatic state machine lifecycle management.

Core types: `Mux`, `MuxStream`, `MuxError`.

Create a mux and exchange messages:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val streamOrError = mux.open(1)
val stream = streamOrError match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("Failed to open stream")
}
stream.send("hello")
```

## Introduction

Multiplexing allows a single transport connection (TCP, QUIC, WebSocket) to carry multiple independent logical streams simultaneously. Each stream has its own ID, message queues, and lifecycle independent from others. The `Mux` primitive handles all the bookkeeping: stream creation, capacity enforcement, message queuing with backpressure, and graceful shutdown semantics.

## Motivation

Without multiplexing, protocols must open a new connection per concurrent operation (HTTP/1.1 with keep-alive), which is expensive and scales poorly. With multiplexing (HTTP/2, QUIC), one connection carries many streams, reducing connection overhead and latency while maintaining logical independence.

`Mux` provides:
- **Thread-safe stream registry** — concurrent `open`, `get`, `cancel` operations on the registry (lock-based on JVM, lock-free on JS)
- **Lock-free per-stream queues** — `send()` and `offerInbound()` use lock-free ring buffers on JVM for high-throughput messaging
- **Separate inbound/outbound queues** — independent message directions, two-way communication
- **Automatic state machine** — stream lifecycle (OPEN → HALF_CLOSED_LOCAL/REMOTE → CLOSED) with proper half-close semantics
- **Backpressure** — per-stream and mux-level capacity limits to prevent unbounded buffering
- **Graceful shutdown** — `closeAll` atomically closes all streams with a terminal error
- **Thread-safe per-stream operations** — `send` and `offerInbound` are multi-thread safe; `receive` and `takeOutbound` follow single-consumer contract

## Installation

Add the dependency to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-mux" % "@VERSION@"
```

For Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-mux" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x

:::tip[Getting Started]
New to Mux? Check out the [Getting Started with Mux](../guides/getting-started-with-mux.md) tutorial for a comprehensive step-by-step guide that teaches you the core concepts, common patterns, and best practices. The tutorial is designed for newcomers and includes runnable examples.
:::

## Overview

**IMPORTANT: Error Handling with Union Types and Either**

In Scala 3, methods return union types (e.g., `Option[Out] | MuxError`). In Scala 2, they return `Either[MuxError, Option[Out]]`. You must use pattern matching to safely handle all branches:
- Success cases: `Some(msg)` or `None` (in Scala 3) / `Right(Some(msg))` or `Right(None)` (in Scala 2)
- Error cases: `MuxError` (in Scala 3) / `Left(error)` (in Scala 2)

Using higher-order functions like `.forEach()` or `.map()` without pattern matching will silently ignore error conditions and lose error information. Always explicitly pattern-match on all branches to handle both success and failure paths correctly.

The multiplexer has three core concepts:

**`Mux[Id, In, Out]`** is the entry point. You create it with a fixed capacity (maximum concurrent streams), then open, retrieve, cancel, or close streams. It enforces capacity limits and maintains a registry of active streams.

**`MuxStream[Id, In, Out]`** represents a single logical stream within the mux. It has two independent message queues: one for messages you send (outbound, drained by the protocol), and one for messages delivered to you (inbound, enqueued by the protocol). It also manages a state machine that enforces correct sequencing of sends and receives as the stream progresses through OPEN, HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, and CLOSED states.

**`MuxError`** is a sealed trait representing all failure cases: `StreamClosed` (attempting operations on a closed stream), `CapacityExceeded` (too many concurrent streams), `QueueFull` (per-stream message queue exhausted), `Cancelled` (stream was cancelled by peer), `MuxClosed` (mux itself is closed), and `ProtocolError` (invalid state transition, duplicate ID, null message).

## How They Work Together

The typical flow is:

**1. Create a mux** with a fixed capacity:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
```

**2. Open a stream** (allocates a slot in the mux's capacity):

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val streamOrError = mux.open(1)
val stream = streamOrError match {
  case s: MuxStream[Int, String, String] => s
  case error: MuxError => throw new RuntimeException(s"Failed: $error")
}
```

**3. Exchange messages** via the stream's two-way queue:
   - **Your side sends messages** via `stream.send(msg)`, placing them in the outbound queue.
   - **The protocol drains** those messages via `stream.takeOutbound()` and transmits them over the shared transport.
   - **The protocol receives** messages from the peer and delivers them via `stream.offerInbound(msg)`, placing them in the inbound queue.
   - **Your side receives messages** via `stream.receive()`, reading from the inbound queue.

**4. Signal end-of-stream** when either side is done sending:
   - Your side calls `stream.halfClose()` to signal you're done sending (local close).
   - The protocol calls `stream.signalRemoteClose()` when the peer signals end-of-stream (remote close).
   - Once both sides close, the stream transitions to CLOSED.

**5. Close or cancel** the stream to release capacity:
   - Call `stream.close()` to forcibly close a single stream.
   - Call `mux.cancel(id, reason)` to cancel a stream externally (e.g., protocol error).
   - Call `mux.closeAll(reason)` to atomically close all active streams and reject new opens.

This design mirrors **HTTP/2 stream lifecycle**: each stream is independent, supports half-closed states for proper shutdown, and the mux enforces capacity limits and graceful shutdown semantics.

The architecture shows how application code, mux streams, and protocol layers interact:

```
┌─────────────────────────────────────────────────────────┐
│ Your Application                                        │
└──────────────────────┬──────────────────────────────────┘
                       │
         send(msg)     │      receive(msg)
              ────────┬┴────────
                      │
        ┌─────────────▼──────────────┐
        │   MuxStream (Stream 1)     │
        │ ┌─────────────────────────┐│
        │ │ Outbound Queue          ││
        │ │ (messages to peer)      ││
        │ └─────────────┬───────────┘│
        │               │            │
        │ ┌─────────────┴───────────┐│
        │ │ Inbound Queue           ││
        │ │ (messages from peer)    ││
        │ └─────────────┬───────────┘│
        └───────────────┼────────────┘
                        │
         takeOutbound() │      offerInbound()
              ────────┬─┴──────────
                      │
┌─────────────────────▼──────────────────────────────────┐
│ Protocol Layer (HTTP/2 framing, etc.)                  │
└─────────────────────┬──────────────────────────────────┘
                      │
          Shared Transport (TCP, QUIC, etc.)
```

The mux holds multiple streams in a concurrent map. Each stream can be accessed independently:

  

**Scala 2**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val s1: Either[MuxError, MuxStream[Int, String, String]] = mux.open(1)
val stream1 = s1 match {
  case Right(s) => s
  case Left(error) => throw new RuntimeException(s"Failed: $error")
}

val s2: Either[MuxError, MuxStream[Int, String, String]] = mux.open(2)
val stream2 = s2 match {
  case Right(s) => s
  case Left(error) => throw new RuntimeException(s"Failed: $error")
}

stream1.send("hello")
stream2.send("world")
mux.get(1).foreach { stream =>
  stream.receive() match {
    case Right(Some(msg)) => println(s"Received: $msg")
    case Right(None) => println("No message yet")
    case Left(error) => println(s"Error: $error")
  }
}
mux.get(2).foreach { stream =>
  stream.receive() match {
    case Right(Some(msg)) => println(s"Received: $msg")
    case Right(None) => println("No message yet")
    case Left(error) => println(s"Error: $error")
  }
}
```

  
  

**Scala 3**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)

val s1: MuxStream[Int, String, String] | MuxError = mux.open(1)
val stream1 = s1 match {
  case s: MuxStream[Int, String, String] => s
  case error: MuxError => throw new RuntimeException(s"Failed: $error")
}

val s2: MuxStream[Int, String, String] | MuxError = mux.open(2)
val stream2 = s2 match {
  case s: MuxStream[Int, String, String] => s
  case error: MuxError => throw new RuntimeException(s"Failed: $error")
}

stream1.send("hello")
stream2.send("world")
mux.get(1).foreach { stream =>
  stream.receive() match {
    case Some(msg) => println(s"Received: $msg")
    case None => println("No message yet")
    case error: MuxError => println(s"Error: $error")
  }
}
mux.get(2).foreach { stream =>
  stream.receive() match {
    case Some(msg) => println(s"Received: $msg")
    case None => println("No message yet")
    case error: MuxError => println(s"Error: $error")
  }
}
```

  

## Diagram

To see the mux data flow in action, use this interactive explorer. It shows three concurrent streams sharing the same mux. Select a stream tab, then click actions in either zone to watch messages move through the queues and observe how the state machine responds.

- **Application code** (top) — calls `send()` to enqueue outbound messages and `receive()` to dequeue inbound messages.
- **MuxStream** (middle) — holds the outbound and inbound ring-buffer queues and tracks stream state (`OPEN`, `HALF_CLOSED_LOCAL`, `HALF_CLOSED_REMOTE`, `CLOSED`).
- **Protocol layer** (bottom) — calls `takeOutbound()` to drain messages for transmission and `offerInbound()` to deliver messages arriving from the peer.

Use the **Lifecycle** controls to drive the state machine: `halfClose()` signals your side is done sending; `signalRemoteClose()` signals the peer is done; `close()` forces immediate closure. The event log shows the exact return values the API would produce, including `QueueFull` and `StreamClosed` errors.

## Common Patterns

**Capacity Management**

Mux enforces a fixed capacity (e.g., 100 concurrent streams). When the limit is reached, `open` returns `CapacityExceeded`. Close or cancel streams to free capacity:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](5)
for (i <- 1 to 5) {
  mux.open(i) match {
    case _: MuxStream[Int, String, String] => ()
    case e: MuxError                       => println(s"Failed to open stream $i: $e")
  }
}
val result = mux.open(6) // returns CapacityExceeded — mux is full

mux.cancel(1, MuxError.Cancelled(1, "freed"))
val newStream = mux.open(6) // succeeds now that stream 1 was freed
```

**Half-Close Shutdown**

Streams support half-closed states (like HTTP/2). Your side calls `halfClose()` to signal it's done sending; the protocol calls `signalRemoteClose()` when the peer closes their sending side:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

stream.halfClose()
stream.receive()
stream.signalRemoteClose()
val sendResult = stream.send("fail")
```

**Backpressure and Queue Management**

Each stream has separate inbound and outbound queues (capacity 256 per queue). If the protocol producer is faster than the consumer, the queue fills and `offerInbound` or `send` returns `QueueFull`:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

for (i <- 1 to 256) {
  stream.send(s"msg-$i") match {
    case ()          => ()
    case e: MuxError => sys.error(s"send failed: $e")
  }
}
val result = stream.send("overflow")
stream.takeOutbound()
val recovered = stream.send("now-ok")
```

**Graceful Shutdown**

Call `closeAll` to atomically close all streams and prevent new opens. After closure, `receive()` / `takeOutbound()` return the terminal error once their queues are drained:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
for (i <- 1 to 10) {
  mux.open(i) match {
    case _: MuxStream[?, ?, ?] => ()
    case e: MuxError           => sys.error(s"Failed to open stream $i: $e")
  }
}

mux.closeAll(MuxError.MuxClosed)

mux.activeCount
val newOpen = mux.open(11)
```

## Integration Points

`Mux` is designed as a protocol-independent multiplexing layer. It integrates with:

- **Transport protocols**: HTTP/2, QUIC, multiplexed WebSockets — any ID-based multiplexed protocol
- **Message types**: Generic over message types (`In`, `Out`) — use your protocol's frame/message types
- **Stream IDs**: Generic over ID type — use the protocol's stream ID type (Int for HTTP/2, Long for QUIC, etc.)
- **Error handling**: Terminal errors from protocol errors or local cancellation become available to user code via `receive()` and `takeOutbound()`

The mux does not depend on external modules except `zio-blocks-ringbuffer` for its lock-free ring buffer queues. It is pure and zero-dependency beyond that.

---

## MuxError

`MuxError` is a sealed trait representing all failure conditions in mux operations.

  

**Scala 2**

Here are the error type definitions for both Scala versions:

```scala
sealed trait MuxError

object MuxError {
  final case class StreamClosed(id: Any) extends MuxError
  final case class CapacityExceeded(limit: Int) extends MuxError
  final case class QueueFull(queueCapacity: Int) extends MuxError
  final case class Cancelled(id: Any, reason: String) extends MuxError
  case object MuxClosed extends MuxError
  final case class ProtocolError(message: String) extends MuxError
}
```

  
  

**Scala 3**

Scala 3 uses the same structure with union return types in method signatures:

```scala
sealed trait MuxError

object MuxError {
  final case class StreamClosed(id: Any) extends MuxError
  final case class CapacityExceeded(limit: Int) extends MuxError
  final case class QueueFull(queueCapacity: Int) extends MuxError
  final case class Cancelled(id: Any, reason: String) extends MuxError
  case object MuxClosed extends MuxError
  final case class ProtocolError(message: String) extends MuxError
}
```

  

**Variants**

**`StreamClosed(id: Any)`** — Returned when attempting to send or receive on a stream that is already closed. The `id` field is typed as `Any` to keep `MuxError` non-generic; you can pattern-match on it or cast it at the use site if you need the concrete type.

**`CapacityExceeded(limit: Int)`** — Returned by `open` when the number of active streams has reached the mux's capacity limit. The `limit` field shows the configured capacity. Close or cancel other streams to free capacity.

**`QueueFull(queueCapacity: Int)`** — Returned by `send` (outbound queue full) or `offerInbound` (inbound queue full) when the per-stream message queue has exhausted its capacity (typically 256). Drain the queue (by calling `takeOutbound()` or `receive()`) to resume sending/receiving.

**`Cancelled(id: Any, reason: String)`** — Returned when a stream gets cancelled via `mux.cancel(id, reason)`. The `reason` field explains why the stream gets cancelled. Pending `receive()` calls return this error.

**`MuxClosed`** — Set when the mux itself is closed via `closeAll`. After this, `open` returns this error and all active streams transition to CLOSED.

**`ProtocolError(message: String)`** — Returned when the protocol contract is violated (e.g., duplicate stream ID, null message, invalid state transition). These indicate bugs in the protocol implementation or incorrect API usage.

---



`Mux[Id, In, Out]` is the entry point for multiplexed stream coordination. It manages a registry of active streams, enforces capacity limits, and provides operations to open, retrieve, cancel, and close streams.

**Factory**

To create a multiplexer, use the factory constructor with a fixed capacity:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
```

The capacity must be positive; zero or negative capacity throws `IllegalArgumentException`. Capacity is fixed at construction and never changes.

**Core Operations**

**`def open(id: Id): MuxStream[Id, In, Out] | MuxError`** (Scala 3) / **`def open(id: Id): Either[MuxError, MuxStream[Id, In, Out]]`** (Scala 2) — Open a new stream with the given ID. Transitions the stream from IDLE to OPEN state. Returns the new stream on success, or an error if:
- Mux is closed (returns `MuxClosed`)
- Stream ID already exists (returns `ProtocolError`)
- Capacity exceeded (returns `CapacityExceeded`)

Opening a stream with error handling:

  

**Scala 2**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)

mux.open(1) match {
  case Right(stream) => println(s"Stream ${stream.id} opened")
  case Left(error) => println(s"Failed: $error")
}
```

  
  

**Scala 3**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)

mux.open(1) match {
  case stream: MuxStream[Int, String, String] => println(s"Stream ${stream.id} opened")
  case error: MuxError => println(s"Failed: $error")
}
```

  

**`def get(id: Id): Option[MuxStream[Id, In, Out]]`** — Retrieve an existing stream by ID. Returns `Some(stream)` if the stream is open, `None` otherwise. This is a non-blocking lookup and does not modify any state.

Looking up a stream:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val result = mux.open(1)
val retrieved = mux.get(1)
```

**`def cancel(id: Id, reason: MuxError): Unit`** — Cancel a stream by ID, removing it from the mux and setting a terminal error. The stream transitions to CLOSED and any pending `receive()` calls on that stream return the terminal error. Cancelling a non-existent stream is a no-op.

Cancelling a stream:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
mux.open(1)
mux.cancel(1, MuxError.Cancelled(1, "peer error"))
val retrieved = mux.get(1)
```

**`def closeAll(reason: MuxError): Unit`** — Close all active streams atomically with a terminal error. Transitions all streams to CLOSED, clears the stream registry, and sets the mux to a closed state. After `closeAll`, `open` returns `MuxClosed`.

Closing all streams:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
for (i <- 1 to 10) {
  mux.open(i) match {
    case _: MuxStream[?, ?, ?] => ()
    case e: MuxError           => sys.error(s"Failed to open stream $i: $e")
  }
}
mux.closeAll(MuxError.MuxClosed)
val count = mux.activeCount
```

**`def activeCount: Int`** — Return the current number of active (open) streams. This is a point-in-time snapshot; the count may change immediately after if other threads open or close streams.

Checking the number of active streams:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
mux.open(1)
mux.open(2)
val count = mux.activeCount
```

---

## MuxStream

`MuxStream[Id, In, Out]` represents a single logical stream within a mux. It has two independent message queues (inbound and outbound), a state machine, and operations to send/receive messages and manage lifecycle.

**Stream State Machine**

A stream progresses through four states:

- **OPEN** — Initial state after `open`. Both sides can send and receive.
- **HALF_CLOSED_LOCAL** — Local side calls `halfClose()`. Local side cannot send; remote side can still send (which transitions to CLOSED via `signalRemoteClose()`).
- **HALF_CLOSED_REMOTE** — Remote side calls `signalRemoteClose()`. Remote side cannot send; local side can still send (which transitions to CLOSED via `halfClose()`).
- **CLOSED** — Both sides closed or stream was forcibly closed. No operations allowed except state queries.

Attempting to send on a HALF_CLOSED_LOCAL or CLOSED stream returns `StreamClosed`. Attempting to offer inbound on a HALF_CLOSED_REMOTE or CLOSED stream returns `StreamClosed`. Attempting to receive on a fully CLOSED stream returns the terminal error after the queue drains.

The state transitions form a finite state machine:

```
          ┌─────────────────────────────────────────────┐
          │                   OPEN                      │
          │ (both sides can send and receive)           │
          └─┬─────────────────────────────────────────┬─┘
            │                                         │
            │ halfClose()                 signalRemoteClose()
            │                                         │
      ┌─────┴──────────────────┐         ┌────────────┴──────────┐
      │ HALF_CLOSED_LOCAL      │         │ HALF_CLOSED_REMOTE    │
      │ (local: done sending)  │         │ (remote: done sending)│
      │ (remote: can still rx) │         │ (local: can still tx) │
      └─┬──────────────────────┘         └──────────┬────────────┘
        │                                           │
        │ signalRemoteClose()          halfClose()  │
        │                                           │
        └──────────────┬────────────────────────────┘
                       │
                   ┌───┴────┐
                   │ CLOSED │
                   └────────┘
```

**Query Operations**

**`def id: Id`** — Return the stream's unique identifier within the mux.

**`def isClosed: Boolean`** — Return true if the stream is in CLOSED state, false otherwise.

**`def isHalfClosed: Boolean`** — Return true if the stream is in HALF_CLOSED_LOCAL or HALF_CLOSED_REMOTE state, false otherwise.

**Message Operations**

**`def send(msg: In): Unit | MuxError`** (Scala 3) / **`def send(msg: In): Either[MuxError, Unit]`** (Scala 2) — Send a message on this stream. Places the message into the outbound queue for the protocol to drain. The protocol is responsible for transmitting the message over the shared transport.

Returns `Unit` on success, or an error if:
- Stream is closed (returns `StreamClosed`)
- Stream is in HALF_CLOSED_LOCAL state (returns `StreamClosed`)
- Message is null (returns `ProtocolError`)
- Outbound queue is full (returns `QueueFull`)

Sending a message on a stream:

  

**Scala 2**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case Right(s) => s
  case Left(_) => sys.error("open failed")
}

val sendResult = stream.send("hello")
```

  
  

**Scala 3**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

val sendResult = stream.send("hello")
```

  

**`def receive(): Option[Out] | MuxError`** (Scala 3) / **`def receive(): Either[MuxError, Option[Out]]`** (Scala 2) — Receive a message from the inbound queue. This is non-blocking: it returns immediately with whatever is available.

Returns:
- `Some(msg)` if a message is available in the inbound queue
- `None` if the queue is empty (no message yet, but stream is still open)
- A `MuxError` if the stream is closed (queue has drained and a terminal error is set)

Use this in a polling loop or with a reactor to wait for messages:

  

**Scala 2**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case Right(s) => s
  case Left(_) => sys.error("open failed")
}

val result = stream.receive()
```

  
  

**Scala 3**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

val result = stream.receive()
```

  

**`def takeOutbound(): Option[In] | MuxError`** (Scala 3) / **`def takeOutbound(): Either[MuxError, Option[In]]`** (Scala 2) — Take the next message from the outbound queue. Called by the protocol to drain messages sent via `send()` and transmit them over the shared transport. Non-blocking: returns immediately.

Returns:
- `Some(msg)` if a message is available in the outbound queue
- `None` if the queue is empty (no outbound message yet, but stream is still open)
- A `MuxError` if the stream is closed

Draining messages from the outbound queue:

  

**Scala 2**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case Right(s) => s
  case Left(_) => sys.error("open failed")
}

stream.send("outgoing")
val outbound = stream.takeOutbound()
```

  
  

**Scala 3**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

stream.send("outgoing")
val outbound = stream.takeOutbound()
```

  

**`def offerInbound(msg: Out): Unit | MuxError`** (Scala 3) / **`def offerInbound(msg: Out): Either[MuxError, Unit]`** (Scala 2) — Deliver a message to this stream's inbound queue. Called by the protocol when a message arrives from the peer.

Returns `Unit` on success, or an error if:
- Stream is closed (returns `StreamClosed`)
- Stream is in HALF_CLOSED_REMOTE state (returns `StreamClosed`)
- Message is null (returns `ProtocolError`)
- Inbound queue is full (returns `QueueFull`)

Delivering a message to the inbound queue:

  

**Scala 2**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case Right(s) => s
  case Left(_) => sys.error("open failed")
}

stream.offerInbound("response from peer")
val received = stream.receive()
```

  
  

**Scala 3**

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

stream.offerInbound("response from peer")
val received = stream.receive()
```

  

**Lifecycle Operations**

**`def halfClose(): Unit`** — Signal that the local side is done sending. Transitions the stream based on the current state:
- OPEN → HALF_CLOSED_LOCAL (local side cannot send; remote can still receive pending messages)
- HALF_CLOSED_REMOTE → CLOSED (both sides are now closed)

After `halfClose()`, `send()` returns `StreamClosed`. You can still call `receive()` to drain any buffered inbound messages.

Signalling local half-close:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

stream.halfClose()
val isHc = stream.isHalfClosed
val sendAfter = stream.send("fail")
val recAfter = stream.receive()
```

**`def signalRemoteClose(): Unit`** — Signal that the remote side is done sending. Called by the protocol when the peer signals END_STREAM. Transitions the stream based on the current state:
- OPEN → HALF_CLOSED_REMOTE (remote side cannot send; local can still receive pending messages)
- HALF_CLOSED_LOCAL → CLOSED (both sides are now closed)

After `signalRemoteClose()`, `offerInbound()` returns `StreamClosed`. You can still call `receive()` to drain any buffered inbound messages.

Signalling remote half-close:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

stream.signalRemoteClose()
val isHc = stream.isHalfClosed
val offerAfter = stream.offerInbound("fail")
```

**`def close(): Unit`** — Forcibly close this stream immediately, transitioning it to CLOSED state and removing it from the mux. Any pending `receive()` calls on this stream will return the terminal error after the queue drains.

Forcibly closing a stream:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _: MuxError => sys.error("open failed")
}

stream.close()
val isClosed = stream.isClosed
val inMux = mux.get(1)
```

**Thread Safety**

- `send()` and `offerInbound()` are **multi-thread safe**: multiple threads can safely call these concurrently on the same stream.
- Call `receive()` and `takeOutbound()` from the same thread only. Concurrent calls to the same ring buffer produce data races and unpredictable behavior. These are single-consumer operations and must be called from a single thread. Concurrent calls will corrupt the ring buffer state and lead to incorrect results or lost messages.
- State queries (`id`, `isClosed`, `isHalfClosed`) are always safe to call from any thread.

## Performance

- JVM: `MpscRingBuffer` for inbound and outbound, lock-free and zero-alloc for multi-producer writes
- JVM: `VarHandle` CAS for stream state transitions
- JS: `ArrayDeque` fallback
- No `synchronized`, so it stays friendly to virtual threads
