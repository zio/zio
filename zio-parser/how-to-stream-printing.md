# How to Print to a ZStream

> Printing with `printString` builds the whole result in memory. When the output is large, or when another part of your program wants to consume it as it is produced, you can print into a `ZStream` instead. This guide shows how to use `ZStreamTarget` to do that.

The goal is to print a stream of characters from a syntax and read the first few characters from the stream.

## The Problem

`print` and `printString` return a finished `Chunk` or `String`. For a value that produces megabytes of output, the whole output sits in memory before anything can use it. A streaming API lets the consumer start working on the first characters right away.

`Printer` writes each output element to a `Target`. A `Target` is the destination for the output. `ChunkTarget` collects the output, and `ZStreamTarget` pushes it into a queue that a `ZStream` reads from.

## Prerequisites

```scala
import zio.{Runtime, Unsafe}
import zio.parser.*
import zio.parser.target.ZStreamTarget
```

## Creating the Target

`ZStreamTarget` needs a `zio` runtime to run its queue operations. The companion object's `apply` is an effect that captures the runtime of the fiber that runs it, and returns the target. In an example like this one, we run that effect directly:

```scala
val target: ZStreamTarget[Any, Char] = Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(ZStreamTarget[Char]).getOrThrowFiberFailure()
}
```

The code above:

- `ZStreamTarget[Char]` creates a target for `Char` output, and returns it as a `UIO`
- `Runtime.default.unsafe.run` runs that effect, and `getOrThrowFiberFailure` returns the target or throws if creating it failed
- `Unsafe.unsafe` is required to run an effect outside of a `ZIO` program

In an application that already runs effects, use `ZStreamTarget[Char]` inside your `ZIO` code instead.

## Printing into the Target

We print a number with a syntax, writing its characters into the target:

```scala
val number: Syntax[String, Char, Char, Int] =
  Syntax.digit.repeat.string.transform(_.toInt, _.toString)
```

```scala
number.print(42, target)
// res0: Either[String, Unit] = Right(())
```

The result is `Right(())` when printing succeeds. The characters are now in the target's queue, and nothing has read them yet.

## Reading the Stream

The target's `stream` field is a `ZStream` that reads the queue. We read two characters from it, and collect them into a `Chunk`:

```scala
val firstTwo = Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(target.stream.take(2).runCollect).getOrThrowFiberFailure()
}
// firstTwo: Chunk[Char] = IndexedSeq('4', '2')

firstTwo.mkString
// res1: String = "42"
```

We used `take(2)` on purpose. The stream never ends by itself, because nothing closes the queue when printing finishes. A consumer must take the number of elements it needs, or stop the stream with an interrupt.

:::caution
`ZStreamTarget` does not signal the end of the output. Reading the stream without `take` waits forever after the last character. Use `take`, `takeUntil` or a timeout to bound the read.
:::

:::tip
When a printer tries an alternative, the target captures its output first. Output from an alternative that fails is discarded and never reaches the stream.
:::
