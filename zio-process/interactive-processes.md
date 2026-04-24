# Interactive Processes

> Sometimes you want to interact with a process in a back-and-forth manner by sending requests to the process and receiving responses back. For example, interacting with a repl-like process like `node -i`, `python -i`, etc. or an ssh server.

Sometimes you want to interact with a process in a back-and-forth manner by sending requests to the process and receiving responses back. For example, interacting with a repl-like process like `node -i`, `python -i`, etc. or an ssh server.

Here is an example of communicating with an interactive NodeJS shell:

```scala
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

You would probably want to create a helper for the repeated code, but this just a minimal example to help get you started.
