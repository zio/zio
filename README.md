# scalaz-ioqueue

[![Gitter](https://badges.gitter.im/scalaz/scalaz-ioqueue.svg)](https://gitter.im/scalaz/scalaz-ioqueue?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Goal

IOQueue provides a high-performance, in-memory, user-friendly queue implementation with composable back-pressure and purely-functional semantics built on ZIO.

## Introduction & Highlights

IOQueue provides a high-performance, purely-functional, type-safe queue built on ZIO with composable, transparent back-pressure. 
A queue allows to adding (enqueue) and removing (dequeue) elements in First In First Out (FIFO) manner. 

IOQueue is an asynchronous queue, which works without locks or blocking, and exposes its functionality in two simple methods: offer, to add an item to the queue; and take, to remove an item from the queue.

* Purely-functional interface that cleanly integrates with ZIO and Scalaz.
* Fully asynchronous implementation, without locks or blocking.
* Multithreaded and optimized for concurrent usage from many fibers. 
* Composable back-pressure to ensure producers cannot _flood_ consumers.


## Competition

| | FS2 | Monix Queue | Java Blocking Queue | 
---|---|---|---
Purely-functional| ✓ | 𐄂 | 𐄂 |
Composable Backpressure| ✓ | 𐄂 | 𐄂 |
Asynchronous| ✓ | ✓ | 𐄂 |
High-performance| ✓ | 𐄂 | 𐄂 |
Scalaz Integration| 𐄂 | 𐄂 | 𐄂 |

## Background

* <a href = "https://www.youtube.com/watch?v=Eihz7kqn6mU"> John De Goes - SCALAZ 8 VS AKKA ACTORS </a>
* <a href= "https://functional-streams-for-scala.github.io/fs2/concurrency-primitives"> FS2 Queue documentation </a>|<a href= "https://github.com/functional-streams-for-scala/fs2/blob/series/1.0/core/shared/src/main/scala/fs2/async/mutable/Queue.scala">CODE </a>
* <a href= "https://github.com/monix/monix/blob/master/monix-execution/shared/src/main/scala/monix/execution/misc/AsyncQueue.scala"> Monix Queue </a>
* <a href= "https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/BlockingQueue.html"> Java Blocking Queue </a>

Adam Warsky blogs <a href="https://blog.softwaremill.com/scalaz-8-io-vs-akka-typed-actors-vs-monix-part-1-5672657169e1"> Part 1 </a>|<a href= "https://blog.softwaremill.com/akka-vs-zio-vs-monix-part-2-communication-9ce7261aa08c"> Part 2 </a>|<a href= "https://blog.softwaremill.com/akka-vs-zio-vs-monix-part-2-communication-9ce7261aa08c"> Part 3 </a>


