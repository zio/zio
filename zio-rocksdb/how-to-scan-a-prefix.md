# How to Scan a Prefix

> Read only the RocksDB entries whose key starts with a given prefix.

To list all keys that start with a prefix, start a scan at the prefix and stop at the first key that no longer matches. [Iterators and Range Scans](./iterators.md) explains positions and directions.

## Scan Forward from the Prefix

`Position.Target` seeks to the first key that is greater than or equal to the prefix. Because keys are sorted bytewise, all keys with that prefix come next to each other, so `ZStream#takeWhile` ends the scan right after the last one:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.RocksDB
import zio.rocksdb.iterator.{ Direction, Position }
import zio.stream.ZStream

def withPrefix(prefix: String): ZStream[RocksDB, Throwable, (String, String)] =
  ZStream
    .serviceWithStream[RocksDB](
      _.newIterator(Direction.Forward, Position.Target(Chunk.fromArray(prefix.getBytes(UTF_8))))
    )
    .map { case (key, value) => new String(key, UTF_8) -> new String(value, UTF_8) }
    .takeWhile { case (key, _) => key.startsWith(prefix) }
```

## Limit the Result

Because the result is a stream, `ZStream#take` stops the scan early and closes the native iterator, so reading the first page of a long prefix is cheap:

```scala
import zio._
import zio.stream.ZStream

def firstPage(entries: ZStream[Any, Throwable, (String, String)]): ZIO[Any, Throwable, Chunk[(String, String)]] =
  entries.take(20).runCollect
```

:::note
`ZStream#takeWhile` compares text in this example. If your keys are binary, compare the bytes of the key instead, for example with `key.take(prefix.length).sameElements(prefix)`.
:::
