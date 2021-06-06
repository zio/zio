---
id: ztransducer 
title: "ZTransducer"
---

```scala mdoc:invisible
import zio.stream.{UStream, ZStream, ZTransducer}
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
