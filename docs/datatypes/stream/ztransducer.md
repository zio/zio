---
id: ztransducer 
title: "ZTransducer"
---

```scala mdoc:invisible
import zio.stream.{UStream, ZStream, ZTransducer}
import zio.Chunk
```

## Introduction

A `ZTransducer[R, E, I, O]` is a stream transformer. Transducers accept a stream as input, and return the transformed stream as output.

ZTransducers can be thought of as a recipe for calling a bunch of methods on a source stream, to yield a new (transformed) stream. A nice mental model is the following type alias:

```scala
type ZTransducer[Env, Err, In, Out] = ZStream[R, E, O] => ZStream[R, E, O]
```

There is no fundamental requirement for transducers to exist, because everything transducers do can be done directly on a stream. However, because transducers separate the stream transformation from the source stream itself, it becomes possible to abstract over stream transformations at the level of values, creating, storing, and passing around reusable transformation pipelines that can be applied to many different streams. 

## Built-in Transducers

### head and last

The `ZTransducer.head` and `ZTransducer.last` are two transducers that return the _first_ and _last_ element of a stream:

```scala mdoc:silent:nest
val stream: UStream[Int] = ZStream(1, 2, 3, 4)
val head: UStream[Option[Int]] = stream.transduce(ZTransducer.head)
val last: UStream[Option[Int]] = stream.transduce(ZTransducer.last)
```

### Splitting

**ZTransducer.splitOn** — A transducer that splits strings on a delimiter:

```scala mdoc:silent:nest
ZStream("1-2-3", "4-5", "6", "7-8-9-10")
  .transduce(ZTransducer.splitOn("-"))
  .map(_.toInt)
// Ouput: 1, 2, 3, 4, 5, 6, 7, 8, 9 10
```

**ZTransducer.splitLines** — A transducer that splits strings on newlines. Handles both Windows newlines (`\r\n`) and UNIX newlines (`\n`):

```scala mdoc:silent:nest
ZStream("This is the first line.\nSecond line.\nAnd the last line.")
  .transduce(ZTransducer.splitLines)
// Output: "This is the first line.", "Second line.", "And the last line."
```

**ZTransducer.splitOnChunk** — A transducer that splits elements on a delimiter and transforms the splits into desired output:

```scala mdoc:silent:nest
ZStream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  .transduce(ZTransducer.splitOnChunk(Chunk(4, 5, 6)))
// Output: Chunk(1, 2, 3), Chunk(7, 8, 9, 10)
```

### Dropping

**ZTransducer.dropWhile** — Creates a transducer that starts consuming values as soon as one fails the given predicate:

```scala mdoc:silent:nest
ZStream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  .transduce(ZTransducer.dropWhile(_ <= 5))
// Output: 6, 7, 8, 9, 10
```

The `ZTransudcer` also has `dropWhileM` which takes an effectful predicate `p: I => ZIO[R, E, Boolean]`.

### Folding

**ZTransudcer.fold** — Using `ZTransudcer.fold` we can fold incoming elements until we reach the false predicate, then the transducer emits the computed value and restarts the folding process:

```scala mdoc:silent:nest
ZStream
  .range(0, 8)
  .transduce(
    ZTransducer.fold[Int, Chunk[Int]](Chunk.empty)(_.length < 3)((s, i) =>
      s ++ Chunk(i)
    )
  )
// Ouput: Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6, 7)
```

Note that the `ZTransducer.foldM` is like `fold`, but it folds effectfully.

**ZTransducer.foldWeighted** — Creates a transducer that folds incoming elements until reaches the `max` worth of elements determined by the `costFn`, then the transducer emits the computed value and restarts the folding process:

```scala
object ZTransducer {
  def foldWeighted[I, O](z: O)(costFn: (O, I) => Long, max: Long)(
    f: (O, I) => O
  ): ZTransducer[Any, Nothing, I, O] = ???
}
```

In the following example, each time we consume a new element we return one as the weight of that element using cost function. After three times, the sum of the weights reaches to the `max` number, and the folding process restarted. So we expect this transducer to group each three elements in one `Chunk`:

```scala mdoc:silent:nest
ZStream(3, 2, 4, 1, 5, 6, 2, 1, 3, 5, 6)
  .aggregate(
    ZTransducer
      .foldWeighted(Chunk[Int]())(
        (_, _: Int) => 1,
        3
      ) { (acc, el) =>
        acc ++ Chunk(el)
      }
  )
// Output: Chunk(3,2,4),Chunk(1,5,6),Chunk(2,1,3),Chunk(5,6)
```

Another example is when we want to group element which sum of them equal or less than a specific number:

```scala mdoc:silent:nest
ZStream(1, 2, 2, 4, 2, 1, 1, 1, 0, 2, 1, 2)
  .aggregate(
    ZTransducer
      .foldWeighted(Chunk[Int]())(
        (_, i: Int) => i.toLong,
        5
      ) { (acc, el) =>
        acc ++ Chunk(el)
      }
  )
// Output: Chunk(1,2,2),Chunk(4),Chunk(2,1,1,1,0),Chunk(2,1,2)
```

> _**Note**_
>
> The `ZTransducer.foldWeighted` cannot decompose elements whose weight is more than the `max` number. So elements that have an individual cost larger than `max` will force the transducer to cross the `max` cost. In the last example, if the source stream was `ZStream(1, 2, 2, 4, 2, 1, 6, 1, 0, 2, 1, 2)` the output would be `Chunk(1,2,2),Chunk(4),Chunk(2,1),Chunk(6),Chunk(1,0,2,1),Chunk(2)`. As we see, the `6` element crossed the `max` cost.
>
> To decompose these elements, we should use `ZTransducer.foldWeightedDecompose` function.

## Compressed streams

### Decompression

If you read `Content-Encoding: deflate`, `Content-Encoding: gzip` or streams other such streams of compressed data, following transducers can be helpful:
* `inflate` transducer allows to decompress stream of _deflated_ inputs, according to [RFC 1951](https://tools.ietf.org/html/rfc1951).
* `gunzip` transducer can be used to decompress stream of _gzipped_ inputs, according to [RFC 1952](https://tools.ietf.org/html/rfc1952).

Both decompression methods will fail with `CompressionException` when input wasn't properly compressed.

```scala mdoc:silent
import zio.stream.ZStream
import zio.stream.Transducer.{ gunzip, inflate }
import zio.stream.compression.CompressionException

def decompressDeflated(deflated: ZStream[Any, Nothing, Byte]): ZStream[Any, CompressionException, Byte] = {
  val bufferSize: Int = 64 * 1024 // Internal buffer size. Few times bigger than upstream chunks should work well.
  val noWrap: Boolean = false     // For HTTP Content-Encoding should be false.
  deflated.transduce(inflate(bufferSize, noWrap))
}

def decompressGzipped(gzipped: ZStream[Any, Nothing, Byte]): ZStream[Any, CompressionException, Byte] = {
  val bufferSize: Int = 64 * 1024 // Internal buffer size. Few times bigger than upstream chunks should work well.
  gzipped.transduce(gunzip(bufferSize))
}

```

### Compression

The `deflate` transducer compresses a stream of bytes as specified by [RFC 1951](https://tools.ietf.org/html/rfc1951).

```scala mdoc:silent
import zio.stream.ZStream
import zio.stream.Transducer.deflate
import zio.stream.compression.{CompressionLevel, CompressionStrategy, FlushMode}

def compressWithDeflate(clearText: ZStream[Any, Nothing, Byte]): ZStream[Any, Nothing, Byte] = {
  val bufferSize: Int = 64 * 1024 // Internal buffer size. Few times bigger than upstream chunks should work well.
  val noWrap: Boolean = false // For HTTP Content-Encoding should be false.
  val level: CompressionLevel = CompressionLevel.DefaultCompression
  val strategy: CompressionStrategy = CompressionStrategy.DefaultStrategy
  val flushMode: FlushMode = FlushMode.NoFlush
  clearText.transduce(deflate(bufferSize, noWrap, level, strategy, flushMode))
}

def deflateWithDefaultParameters(clearText: ZStream[Any, Nothing, Byte]): ZStream[Any, Nothing, Byte] =
  clearText.transduce(deflate())
```
