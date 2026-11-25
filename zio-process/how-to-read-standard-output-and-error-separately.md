# Read Standard Output and Error Separately

> This guide shows how to read both output streams of a program without stalling it. The output shortcuts, such as `Command#string`, read only standard output, so a program that writes a lot to standard error can fill that buffer and block. [Streams and Redirects](./concepts/streams-and-redirects.md) explains why.

## Read Both Streams from the Process

To read both streams, start the command with `Command#run` and read `Process#stdout` and `Process#stderr` in parallel:

```scala
import zio._
import zio.process._

val both: ZIO[Any, CommandError, (String, String)] =
  for {
    process <- Command("ls", "-l", "/tmp", "/does-not-exist").run
    results <- process.stdout.string.zipPar(process.stderr.string)
  } yield results
```

The two reads run in parallel. Reading one stream to the end before starting the other can deadlock when the program writes a lot to the stream that is not being read yet.

## Merge Standard Error into Standard Output

When the order of the messages matters more than which stream they came from, merge the streams with `Command#redirectErrorStream`, so that one stream carries both:

```scala
import zio._
import zio.process._

val merged: ZIO[Any, CommandError, String] =
  Command("ls", "-l", "/tmp", "/does-not-exist").redirectErrorStream(true).string
```

This setting is ignored on Scala.js, where standard error stays separate.

## Send Standard Error Elsewhere

If you do not need standard error, send it to the terminal or a file so that nothing is left unread. See [Redirect Output to a File](./how-to-redirect-output-to-a-file.md).
