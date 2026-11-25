# ProcessInput, ProcessOutput and ProcessStream

> Three types describe the standard streams of a program. `ProcessInput` says where standard input comes from, `ProcessOutput` says where standard output and standard error go, and `ProcessStream` reads a captured output of a running `Process`. [Streams and Redirects](../concepts/streams-and-redirects.md) explains how they fit together.

## ProcessInput

Pass a `ProcessInput` to `Command#stdin`. The values `Inherit` and `Pipe` are objects, and the rest are constructors on `ProcessInput`.

| Member                                         | Description                                                                                  |
| ---------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `ProcessInput.Inherit`                         | Uses the current program's standard input. This is the default.                              |
| `ProcessInput.Pipe`                            | Creates a pipe for standard input but copies nothing into it. Most code uses a constructor.  |
| `ProcessInput.fromFile(file, chunkSize)`       | Streams a file. Scala Native takes no `chunkSize`.                                           |
| `ProcessInput.fromPath(path, chunkSize)`       | Streams a `java.nio.file.Path`, or a `String` path on Scala.js.                              |
| `ProcessInput.fromByteArray(bytes)`            | Sends an array of bytes.                                                                     |
| `ProcessInput.fromString(text, charset)`       | Sends a string in the given encoding.                                                        |
| `ProcessInput.fromUTF8String(text)`            | Sends a string in UTF-8.                                                                     |
| `ProcessInput.fromStream(stream, flushChunksEagerly)` | Sends a `ZStream` of bytes. `flushChunksEagerly` defaults to `false`.                 |
| `ProcessInput.fromQueue(queue)`                | Sends chunks of bytes from a `Queue`, flushing each one right away.                          |

The case classes `ProcessInput.FromStream` and `ProcessInput.JavaStream` are what the constructors build. You rarely create them directly: prefer the constructors above.

## ProcessOutput

Pass a `ProcessOutput` to `Command#stdout` or `Command#stderr`.

| Member                                  | Description                                                                         |
| --------------------------------------- | ----------------------------------------------------------------------------------- |
| `ProcessOutput.Pipe`                    | Captures the output so that the output methods can read it. This is the default.    |
| `ProcessOutput.Inherit`                 | Sends the output to the current program's terminal.                                 |
| `ProcessOutput.FileRedirect(file)`      | Writes the output to a file, overwriting it.                                        |
| `ProcessOutput.FileAppendRedirect(file)`| Appends the output to a file.                                                       |

With a file redirect, the output is not captured, so the reading methods have nothing to read.

## ProcessStream

`ProcessStream` is the type of `Process#stdout` and `Process#stderr`. Each member reads the stream once, so a second read finds no output.

| Member                                 | Returns                                 | Description                                                   |
| -------------------------------------- | --------------------------------------- | ------------------------------------------------------------- |
| `ProcessStream#lines`                  | `ZIO[Any, CommandError, Chunk[String]]` | Reads all lines, decoded as UTF-8.                            |
| `ProcessStream#lines(charset)`         | `ZIO[Any, CommandError, Chunk[String]]` | Reads all lines in the given encoding.                        |
| `ProcessStream#linesStream`            | `ZStream[Any, CommandError, String]`    | Emits lines as they arrive.                                   |
| `ProcessStream#string`                 | `ZIO[Any, CommandError, String]`        | Reads everything as one UTF-8 string. Needs JDK 9 or later.   |
| `ProcessStream#string(charset)`        | `ZIO[Any, CommandError, String]`        | Reads everything as one string in the given encoding.         |
| `ProcessStream#stream`                 | `ZStream[Any, CommandError, Byte]`      | Emits bytes as they arrive.                                   |
