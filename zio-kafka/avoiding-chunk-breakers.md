# Avoiding chunk-breakers

> ZIO streams are not just a simple sequence of elements. The elements are grouped into chunks, which makes many
operations faster. Zio-kafka helps with this by guaranteeing that all records (for a given partition), fetched together
from the broker, are in the same chunk.

## A warning about `mapZIO` and other chunk-breakers

ZIO streams are not just a simple sequence of elements. The elements are grouped into chunks, which makes many
operations faster. Zio-kafka helps with this by guaranteeing that all records (for a given partition), fetched together
from the broker, are in the same chunk.

Be careful when using `mapZIO`, `tap` and some other stream operators that break the chunking structure of the stream
(or more precisely, the resulting stream has chunks with a single element). The throughput may be significantly lower
than with the original chunking structure intact.

Chunk-breaking operators can be found by looking at their scaladocs. Starting with zio-streams 2.1.17, these scaladocs
contain the words `This combinator destroys the chunking structure`.

You can regain full throughput by processing all elements in a chunk together in one go. However, there is a catch: the
order of processing changes. For example, given a stream with elements `a` and `b` in the same chunk, for
`stream.mapZIO(f).mapZIO(g)` the evaluation order is `f(a), g(a), f(b), g(b)`. For the alternatives listed below the
evaluation order changes to `f(a), f(b), g(a), g(b)`. Now imagine that `g(a)` fails, with `mapZIO`, `f(b)` is not
executed, but with the alternatives it _is_ executed. It is up to you to decide if this is a problem.

### Use `mapZIOChunked`

_Available since zio-streams 2.1.17._

The simplest alternative is the stream operator `mapZIOChunked`; it has the same signature as `mapZIO` .

```scala
def f(a: A): ZIO[R, E, B]

stream               // ZStream[R, E, A]
  .mapZIOChunked(f)  // ZStream[R, E, B]
```

### Use `chunksWith`

If you have a single processing step that needs to work on a chunk, you can use `chunksWith`.

```scala
def f(a: A): ZIO[R, E, B]

stream             // ZStream[R, E, A]
  .chunksWith { chunkStream =>
    chunkStream.mapZIO(chunk => ZIO.foreach(chunk)(f))
  }                // ZStream[R, E, B]
```

### Expose chunking structure with `chunks`

Use `chunks` when you have multiple processing steps that can all work on a chunk at a time. Since `chunks` exposes the
chunking structure explicitly, the program can no longer accidentally break the chunking structure (unless
`flattenChunks` is also used).

```scala
def f(a: A): ZIO[R, E, B]
def g(b: B): ZIO[R, E, C]

stream                                         // ZStream[R, E, A]
  .chunks                                      // ZStream[R, E, Chunk[A]]
  .mapZIO { chunk => ZIO.foreach(chunk)(f) }   // ZStream[R, E, Chunk[B]]
  .mapZIO { chunk => ZIO.foreach(chunk)(g) }   // ZStream[R, E, Chunk[C]]
  .flattenChunks                               // ZStream[R, E, C]
```

### Side effects per chunk with `tapChunks`

_Available since zio-streams 2.1.17._

Unlike `tap`, the `tapChunks` stream operator preserves the chunking structure and allows side effects per chunk.

```scala
stream                                                     // ZStream[R, E, A]
  .tapChunks(c => ZIO.logInfo(s"Chunk of size ${c.size}")) // ZStream[R, E, A]
```

### Avoid `rechunk`

ZStream also provides the `rechunk` operator. This operator rebuilds the chunking structure by grouping elements into
chunks of fixed size. If there are not enough elements available for a chunk, the operator waits for these elements to
arrive. Only when the stream ends a smaller chunk is emitted.

In low-volume kafka applications, waiting for enough elements to arrive can take a long time! In high-volume kafka
applications, this operator causes data from multiple polls to be processed together. This results in increased
processing latency and memory usage.

Therefore, we recommend that you avoid `rechunk` in kafka applications.
