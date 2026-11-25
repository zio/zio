# Platform Support

> ZIO Process is one codebase built for three platforms: the JVM, Scala Native, and Scala.js. Most code runs the same way on all three, but some features, error cases, and runtime requirements differ. This page explains how to add the library for each platform and lists the differences to check before you rely on a feature.

## Supported Platforms

Each platform runs programs in its own way. The JVM uses the Java process API, Scala Native uses its own native runtime, and Scala.js runs on Node.js and uses its `child_process` module.

| Platform     | Runtime                           | Scala Versions   |
| ------------ | --------------------------------- | ---------------- |
| JVM          | JDK, 9 or later for some features | 2.12, 2.13 and 3 |
| Scala Native | Native binary                     | 2.12, 2.13 and 3 |
| Scala.js     | Node.js                           | 2.12, 2.13 and 3 |

The project builds and runs its JVM tests on JDK 17, 21 and 25. Programs that call `Process#killTree`, `Process#killTreeForcibly`, `Command#string` or `ProcessStream#string` need JDK 9 or later.

## Adding the Dependency

The [Installation](./index.md#installation) section of the introduction has the dependency blocks for the JVM and for Scala.js. Scala Native artifacts are not on Maven Central yet, because the build skips publishing the Native project, so there is no dependency line to add for Native. Scala Native support is in the code and in the tests, but a release that includes the Native artifact is needed before you can add it to a build. Check the release notes before you plan to use it.

## Behavior Differences

The table below lists the differences that matter most in practice. "Not tested" means the project's test suite does not check that feature on that platform, so verify it in your own program.

| Feature                         | JVM                    | Scala Native        | Scala.js                                |
| ------------------------------- | ---------------------- | ------------------- | --------------------------------------- |
| `Process#killTree`              | Available              | Not available       | Not available                           |
| `Process#killTreeForcibly`      | Available              | Not available       | Not available                           |
| `Process#pid`                   | Available              | Available (parsed)  | Available                               |
| `Process#isAlive`               | Reports the real state | Not tested          | `true` even after exit                  |
| `Process#killForcibly`          | Sends SIGKILL on Unix  | Not tested          | Sends the same signal as `Process#kill` |
| `CommandError.ProgramNotFound`  | Raised                 | Not tested          | Raised                                  |
| `CommandError.PermissionDenied` | Raised                 | Not tested          | Raised                                  |
| `Command#env` with no map set   | Inherits the parent    | Inherits the parent | Empty, with no `PATH`                   |
| `Command#env` with a map        | Adds to the parent     | Adds to the parent  | Replaces the whole environment          |
| `Command#redirectErrorStream`   | Honored                | Honored             | Ignored                                 |
| `File` type                     | `java.io.File`         | `java.io.File`      | `String` path                           |
| `.sh` commands                  | Run as given           | Run through `bash`  | Run as given                            |
| Streaming standard input        | Tested                 | Not tested          | Not tested                              |

### JVM

The JVM is the most complete platform. It is the only one with `Process#killTree`, the only one where streaming standard input is tested, and the one the examples in this documentation are compiled against. It uses the standard Java `ProcessBuilder`, so a program's arguments reach the program unchanged.

### Scala Native

On Scala Native, a command whose name ends in `.sh` runs through `bash`, so a shell script runs without needing to be executable. The tests skip `CommandError.ProgramNotFound` and `CommandError.PermissionDenied` on this platform, so these errors may not be raised here. Check the exit code or the output as well.

Streaming standard input is not tested on Scala Native, so verify it in your own program before you rely on it. The same applies to `ProcessInput.fromFile`, whose test is skipped on Native.

### Scala.js

On Scala.js, the library runs on Node.js. It cannot run in a browser, because it uses the Node.js `child_process` module. The `File` type is a `String` path, so a path that works on the JVM is written as a plain string.

Several features behave differently on Scala.js. `Process#isAlive` stays `true` after the program has exited, so do not use it to detect that a program has finished. `Process#killForcibly` sends the same signal as `Process#kill`, so a program that ignores the terminate signal keeps running after either call. A command that does not call `Command#env` also runs with an empty environment, with no `PATH`. `Command#redirectErrorStream` has no effect.

On Scala.js, a working directory is a plain path string:

```scala
Command("ls", "-l").workingDirectory("/")
```

On the JVM the same call takes `new File("/")`, as shown in [Set the Environment and Working Directory](./how-to-set-the-environment-and-working-directory.md).
