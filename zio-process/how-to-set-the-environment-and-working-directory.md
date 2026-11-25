# Set the Environment and Working Directory

> This guide shows how to start a program with its own environment variables and in a chosen directory. Both settings are methods on `Command` that return a new command, so the original value is never changed.

## Set Environment Variables

To pass variables to the program, give `Command#env` a `Map`:

```scala
import zio.process._

val withJavaHome: Command =
  Command("java", "-version").env(Map("JAVA_HOME" -> "/usr/lib/jvm/default"))
```

On the JVM and Scala Native, these variables are added to the environment that the current program inherits. Calling `Command#env` again replaces the earlier map, so put all variables into one call.

On Scala.js, the map replaces the whole environment of the child process. A command that never calls `Command#env` runs with an empty environment, with no `PATH`, so set every variable it needs on that platform.

## Set the Working Directory

To start the program in another directory, use `Command#workingDirectory`:

```scala
import zio._
import zio.process._

import java.io.File

val listRoot: ZIO[Any, CommandError, Chunk[String]] =
  Command("ls").workingDirectory(new File("/")).lines
```

If the directory does not exist, the command fails with `CommandError.WorkingDirectoryMissing` before the program starts. On Scala.js, the `File` type is a `String` path, so the same call takes `"/"`.

## Apply Both to a Pipeline

On a pipeline, `Command#env` and `Command#workingDirectory` apply to every command in it. See [Pipe One Command into Another](./piping.md).
