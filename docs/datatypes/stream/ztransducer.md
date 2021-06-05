---
id: ztransducer 
title: "ZTransducer"
---

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
