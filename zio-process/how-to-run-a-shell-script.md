# Run a Shell Script

> This guide shows how to run a shell script, or a command that needs shell features such as globs, variables and pipes written as text. ZIO Process starts a program directly, with no shell between, so a feature of the shell works only when a shell is the program.

## Run a Script File

To run a script, make the shell the program and the script its first argument. This works even when the script is not executable:

```scala
import zio._
import zio.process._

val output: ZIO[Any, CommandError, String] = Command("sh", "backup.sh", "--full").string
```

On Scala Native, a command whose name ends in `.sh` runs through `bash`, so this is only needed on the other platforms.

## Run a Shell One-Liner

To use shell syntax inline, pass the text to `sh -c` as one argument. The shell, not ZIO Process, interprets the globs and the pipe:

```scala
import zio._
import zio.process._

val count: ZIO[Any, CommandError, String] = Command("sh", "-c", "ls /tmp/*.log | wc -l").string
```

## Prefer ZIO Process for Pipes

When the commands are known in advance, a pipeline built with `Command#|` avoids the shell and its quoting rules. See [Pipe One Command into Another](./piping.md). Never build a `sh -c` string from untrusted input, because the shell interprets it. To pass such input safely, make it a separate argument, as in `Command("sh", "-c", "wc -l < \"$1\"", "sh", path)`, where the shell reads it as `$1`.
