# Feed Input to a Program

> This guide shows how to send data to the standard input of a program, from a string, bytes, a file or a stream. By default, a command inherits standard input from the current program. To send your own input, set `Command#stdin` to a `ProcessInput`. The examples need these imports:

```scala
import zio._
import zio.process._
import zio.stream.ZStream

import java.io.File
import java.nio.charset.StandardCharsets
```

## Send a String

`Command#<<` sends a UTF-8 string and is shorthand for `Command#stdin` with `ProcessInput.fromUTF8String`:

```scala
val echoed: ZIO[Any, CommandError, String] = (Command("cat") << "hello from ZIO Process").string
```

To use another encoding, build the input with `ProcessInput.fromString`:

```scala
val utf16Input: ProcessInput = ProcessInput.fromString("hello", StandardCharsets.UTF_16)
```

## Send Bytes

`ProcessInput.fromByteArray` sends an array of bytes:

```scala
val fromBytes: ZIO[Any, CommandError, String] =
  Command("cat").stdin(ProcessInput.fromByteArray("bytes".getBytes(StandardCharsets.UTF_8))).string
```

## Send a File

`ProcessInput.fromFile` streams a file into standard input without loading it into memory. `ProcessInput.fromPath` does the same with a `java.nio.file.Path`, which is a `String` path on Scala.js:

```scala
val fromFile: ZIO[Any, CommandError, Chunk[String]] =
  Command("cat").stdin(ProcessInput.fromFile(new File("input.txt"))).lines
```

`ProcessInput.fromFile` takes an optional chunk size on the JVM and Scala.js. On Scala Native it does not.

## Send a Stream

`ProcessInput.fromStream` pipes a `ZStream` of bytes into standard input. Set `flushChunksEagerly` to `true` when the program answers each line before it reads the next:

```scala
val source: ZStream[Any, CommandError, Byte] =
  ZStream.fromChunk(Chunk.fromArray("streamed input".getBytes(StandardCharsets.UTF_8)))

val streamed: ZIO[Any, CommandError, String] =
  Command("cat").stdin(ProcessInput.fromStream(source, flushChunksEagerly = false)).string
```

Streaming standard input is tested on the JVM only, so verify it in your own program on other platforms. For a program that you talk to while it runs, see [Talk to an Interactive Process](./interactive-processes.md).
