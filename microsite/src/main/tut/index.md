---
layout: home
position: 1
section: home
title: "Home"
---

# Welcome to Scalaz ZIO

ZIO is a zero-dependency Scala library for asynchronous and concurrent programming.

Powered by highly-scalable, non-blocking fibers that never waste resources, ZIO lets you build scalable, resilient, and reactive applications that meet the needs of your business.

 - **High-performance**. Build fast applications with 100x the performance of Scala's `Future`.
 - **Type-safe**. Use the Scala compiler to find bugs at compile time.
 - **Concurrent**. Easily build massively concurrent applications.
 - **Asynchronous**. Escape callback hell with a unified model of computation.
 - **Resource-safe**. Build apps that never leak resources.
 - **Functional**. Rapidly compose solutions to complex problems from simple building blocks.

# Why IO?

At the core of ZIO is `IO`, a powerful data type inspired by Haskell's `IO` monad. The `IO` data type allows you to model asynchronous, concurrent, and effectful computations as a pure value.

Effect types like `IO` are how purely functional programs interact with the real world. Functional programmers use them to build complex, real world software without giving up the equational reasoning, composability, and type safety afforded by purely functional programming.

However, there are many practical reasons to build your programs using `IO`, including all of the following:

 * **Asynchronicity**. Like Scala's own `Future`, `IO` lets you easily write asynchronous code without blocking or callbacks. Compared to `Future`, `IO` has significantly better performance and cleaner, more expressive, and more composable semantics.
 * **Composability**. Purely functional code can't be combined with impure code that has side-effects without sacrificing the straightforward reasoning properties of functional programming. `IO` lets you wrap up all effects into a purely functional package that lets you build composable real world programs.
 * **Concurrency**. `IO` has all the concurrency features of `Future`, and more, based on a clean fiber concurrency model designed to scale well past the limits of native threads. Unlike other effect monads, `IO`'s concurrency primitives do not leak resources.
 * **Interruptibility**. All concurrent computations can be interrupted, in a way that still guarantees resources are cleaned up safely, allowing you to write aggressively parallel code that doesn't waste valuable resources or bring down production servers.
 * **Resource Safety**. `IO` provides composable resource-safe primitives that ensure resources like threads, sockets, and file handles are not leaked, which allows you to build long-running, robust applications. These applications will not leak resources, even in the presence of errors or interruption.
 * **Immutability**. `IO`, like Scala's immutable collection types, is an immutable data structure. All `IO` methods and functions return new `IO` values. This lets you reason about `IO` values the same way you reason about immutable collections.
 * **Reification**. `IO` reifies programs. In non-functional Scala programming, you cannot pass programs around or store them in data structures, because programs are not values. But `IO` turns your programs into ordinary values, and lets you pass them around and compose them with ease.
 * **Performance**. Although simple, synchronous `IO` programs tend to be slower than the equivalent imperative Scala, `IO` is extremely fast given all the expressive features and strong guarantees it provides. Ordinary imperative Scala could not match this level of expressivity and performance without tedious, error-prone boilerplate that no one would write in real-life.

While functional programmers *must* use `IO` (or something like it) to represent effects, nearly all programmers will find the features of `IO` help them build scalable, performant, concurrent, and leak-free applications faster and with stronger correctness guarantees than legacy techniques allow.

Use `IO` because it's simply not practical to write real-world, correct software without it.
