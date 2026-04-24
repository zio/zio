# Basics

> To build a description of a command:

To build a description of a command:

```scala
val command = Command("cat", "file.txt")
```

`command.run` will return a handle to the process as `ZIO[Blocking, CommandError, Process]`. Alternatively, instead of
flat-mapping and calling methods on `Process`, there are convenience methods on `Command` itself for some common operations:

## Transforming output

### List of lines

To obtain the output as a list of lines with the type `ZIO[Blocking, CommandError, Chunk[String]]`

```scala
command.lines
```

### Stream of lines

To obtain the output as a stream of lines with the type `ZStream[Blocking, CommandError, String]`

```scala
command.linesStream
```

This is particularly useful when dealing with large files and so on as to not use an unbounded amount of memory.

### String

If you don't need a structured type, you can return the entire output as a plain string:

```scala
command.string
```

This defaults to UTF-8. To use a different encoding, specify the charset:

```scala
command.string(StandardCharsets.UTF_16)
```

### Exit code

When you don't care about the output (or there is no output), you can return just the exit code.

```scala
command.exitCode
```

Note that `Command#exitCode` will return the exit code in the ZIO's success channel whether it's 0 or not.
If you want non-zero exit codes to be considered an error, use `Command#successfulExitCode` instead. This will
return a `CommandError.NonZeroErrorCode` in ZIO's error channel when the exit code is not 0:

```scala
for {
  exitCode  <- Command("java", "--non-existent-flag").successfulExitCode
  // Won't reach this 2nd command since the previous command failed with `CommandError.NonZeroErrorCode`:
  exitCode2 <- Command("java", "--non-existent-flag").successfulExitCode
} yield ()
```

### Kill a process

You can kill a process by calling `interrupt` on the running `Fiber`:

```scala
for {
  fiber <- Command("long-running-process").exitCode.forkDaemon
  _     <- ZIO.sleep(5.seconds)
  _     <- fiber.interrupt
  _     <- fiber.join
} yield ()
```

If you use `Command#run` then you receive a handle to underlying `Process` immediately, which means ZIO's built-in
interruption model no longer applies. In this case, if you want to kill a process before it's done terminating,
you can use `kill` (the Unix SIGTERM equivalent) or `killForcibly` (the Unix SIGKILL equivalent):

```scala
for {
  process <- Command("long-running-process").run
  _       <- ZIO.sleep(5.seconds)
  _       <- process.kill
} yield ()
```

### Stream of bytes

If you need lower-level access to the output's stream of bytes, you can access them directly like so:

```scala
command.stream
```

### Access stdout and stderr separately

There are times when you need to process the output of stderr as well.

```scala
for {
  process <- Command("./some-process").run
  stdout  <- process.stdout.string
  stderr  <- process.stderr.string
  // ...
} yield ()
```

## Error handling

Errors are represented as `CommandError` in the error channel instead of `IOException`. Since `CommandError` is an ADT,
you can pattern match on it and handle specific cases rather than trying to parse the guts of `IOException.getMessage`
yourself.

For example, if you want to fallback to running a different program if it doesn't exist on the host machine, you can
match on `CommandError.ProgramNotFound`:

```scala
Command("some-program-that-may-not-exit").string.catchSome {
  case CommandError.ProgramNotFound(_) => Command("fallback-program").string
}
```
