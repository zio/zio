# Talk to an Interactive Process

> This guide shows how to talk to a program that reads a request, write a response, and then wait for the next request. Examples include a REPL like `node -i` or `python -i`, and an SSH session. To talk to such a program, we need to send input while it is still running and read its output as it arrives, instead of waiting for it to finish.

Three pieces make this work. A `Queue` holds the requests we want to send. `ProcessInput.fromQueue` feeds that queue into the process's standard input and flushes each chunk right away. `Command#run` starts the process without waiting for it to end, so we can read its standard output while we keep sending input. The example needs these imports:

```scala
import zio._
import zio.process._

import java.nio.charset.StandardCharsets
```

## Sending Requests to a Node.js REPL

To talk to an interactive Node.js shell, we create the queue, start `node -i` with the queue as its input, and read each response line on a separate fiber:

```scala
val replSession: ZIO[Any, Throwable, Unit] =
  for {
    commandQueue <- Queue.unbounded[Chunk[Byte]]
    process      <- Command("node", "-i").stdin(ProcessInput.fromQueue(commandQueue)).run
    sep          <- System.lineSeparator
    fiber        <- process.stdout.linesStream.foreach { line =>
                      ZIO.debug(s"Response from REPL: $line")
                    }.fork
    _            <- commandQueue.offer(Chunk.fromArray(s"1+1${sep}".getBytes(StandardCharsets.UTF_8)))
    _            <- commandQueue.offer(Chunk.fromArray(s"2**8${sep}".getBytes(StandardCharsets.UTF_8)))
    _            <- commandQueue.offer(Chunk.fromArray(s"process.exit(0)${sep}".getBytes(StandardCharsets.UTF_8)))
    _            <- fiber.join
  } yield ()
```

The `ProcessStream#linesStream` fiber prints each line as the REPL writes it. Requests go in through the queue, so the REPL receives them one at a time, and the `process.exit(0)` request ends the session so that `fiber.join` can finish.

A helper for the repeated `Queue#offer` calls is useful in real code. This example keeps everything inline so the steps are easy to follow.

## Platform Support

The Node.js REPL example is tested on the JVM and Scala Native, and it is skipped on Scala.js. The test that feeds a shell script through a queue and expects each reply right away runs only on the JVM. Treat queue-based interaction as JVM-tested, and check it on your own process before relying on it in Scala.js.

Node.js must be installed for the REPL example to run. See [Platform Support](./platform-support.md) for the runtime requirements on each platform.
