# Concepts

> These pages explain how ZIO Process models external programs, so that the how-to guides and the reference read as the obvious consequences of a few ideas. Nothing here is required to run a first command, but each page answers a question that comes up as soon as a program does more than print one line.

[Commands and Processes](./commands-and-processes.md) explains the difference between describing a program and running it, and who owns a running process. [Streams and Redirects](./streams-and-redirects.md) describes the three standard streams and where each one can be connected. [Errors and Exit Codes](./errors-and-exit-codes.md) explains why a non-zero exit code is not automatically a failure and which failures ZIO Process reports. [Platform Support](../platform-support.md) lists what differs between the JVM, Scala Native and Scala.js.
