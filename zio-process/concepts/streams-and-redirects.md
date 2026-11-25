# Streams and Redirects

> Every program has three standard streams: standard input, which it reads, and standard output and standard error, which it writes. ZIO Process lets each stream be connected to the library, to the terminal or to a file. This page explains the choices and the rules that follow from them.

## Where Each Stream Can Go

Standard input is described by a `ProcessInput`, and standard output and standard error are each described by a `ProcessOutput`. The defaults are the following:

- Standard input is inherited from the current program (`ProcessInput.Inherit`).
- Standard output and standard error are captured by ZIO Process (`ProcessOutput.Pipe`), so that the output methods can read them.

Changing a default is a setting on the `Command`: `Command#stdin`, `Command#stdout` and `Command#stderr`. `Command#inheritIO` connects all three to the current terminal. `Command#redirectErrorStream` merges standard error into standard output, so that one stream carries both in the order the program wrote them.

## Output Is Read Once

A captured output stream is backed by a pipe from the operating system, not by a buffer that ZIO Process keeps. Each read consumes the stream, so a second read of the same `ProcessStream` finds nothing. Reading standard output to the end before touching standard error is also unsafe: the operating system's pipe buffer is small, and a program that fills the buffer of the stream nobody reads blocks until someone does.

The shortcuts, such as `Command#string`, read only standard output. They capture standard error but never read it. A program that writes a lot to standard error can stall for that reason. [Read Standard Output and Error Separately](../how-to-read-standard-output-and-error-separately.md) shows how to read both safely, and [Redirect Output to a File](../how-to-redirect-output-to-a-file.md) shows how to send a stream somewhere that needs no reading.

## Input Is a Stream Too

A `ProcessInput` other than `Inherit` or `Pipe` is a stream of bytes that ZIO Process copies into the program's standard input while the program runs, on a separate fiber. When the stream ends, standard input is closed, which is how a program such as `cat` learns that there is no more input.

By default, the copied bytes are flushed in the stream's own chunk sizes. A program that answers each line before reading the next, such as a REPL, needs every chunk flushed right away. `ProcessInput.fromStream` has a `flushChunksEagerly` parameter for that, and `ProcessInput.fromQueue` always sets it. [Talk to an Interactive Process](../interactive-processes.md) uses it.

## Pipes Connect Streams

In a pipeline built with `Command#|`, the standard output of each command is connected to the standard input of the next. Only the leftmost command's standard input and the rightmost command's output remain available for configuration. The remaining streams between the commands are connected by ZIO Process itself.
