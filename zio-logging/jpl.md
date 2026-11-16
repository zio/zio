# Java Platform/System Logger

> [`Java Platform/System Logger`](https://openjdk.org/jeps/264) is a logging API that was introduced in Java 9.

In order to use this logging backend, we need to add the following line to our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "2.5.4"
```

To replace the default ZIO loggers with the `JPL` logger, combine `Runtime.removeDefaultLoggers` with `JPL.jpl` in a logger layer:

```scala
import zio.Runtime
import zio.logging.backend.JPL

val logger = Runtime.removeDefaultLoggers >>> JPL.jpl
```

Default `JPL` logger setup:
* logger name (by default) is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.JplSimpleApp.run(JplSimpleApp.scala:17)` will have `zio.logging.example.JplSimpleApp` as logger name
    * NOTE: custom logger name may be set by `zio.logging.loggerName` aspect
* all annotations (logger name annotation is excluded) are placed at the beginning of log message
* cause is logged as throwable

See also [LogFormat and LogAppender](formatting-log-records.md#logformat-and-logappender)

To set a custom logger name for a single effect, apply the `zio.logging.loggerName` aspect:

```scala
import zio.ZIO

ZIO.logInfo("Starting user operation") @@ zio.logging.loggerName("zio.logging.example.UserOperation")
```

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### Java Platform/System Logger Name and Annotations

The following application runs two user operations concurrently. Each operation carries a `user` annotation, and both share a trace ID. The `zio.logging.loggerName` aspect sets the logger name for the operations:

```scala
import zio.logging.LogAnnotation
import zio.logging.backend.JPL
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, ZIOAspect, ZLayer, _ }

import java.util.UUID

object JplSimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers >>> JPL.jpl

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _       <- ZIO.logInfo("Start")
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting user operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping user operation")
        } @@ ZIOAspect.annotated("user", uId.toString)
      } @@ LogAnnotation.TraceId(traceId) @@ zio.logging.loggerName("zio.logging.example.UserOperation")
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Expected Console Output:
```
Oct 28, 2022 1:47:01 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: Start
Oct 28, 2022 1:47:01 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: user=59c114fd-676d-4df9-a5a0-b8e132468fbf trace_id=7d3e3b84-dd3b-44ff-915a-04fb2d135e28 Starting user operation
Oct 28, 2022 1:47:01 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: user=e1ebf0cc-2f61-484f-afcd-de7e20ec7829 trace_id=7d3e3b84-dd3b-44ff-915a-04fb2d135e28 Starting user operation
Oct 28, 2022 1:47:02 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: user=e1ebf0cc-2f61-484f-afcd-de7e20ec7829 trace_id=7d3e3b84-dd3b-44ff-915a-04fb2d135e28 Stopping user operation
Oct 28, 2022 1:47:02 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: user=59c114fd-676d-4df9-a5a0-b8e132468fbf trace_id=7d3e3b84-dd3b-44ff-915a-04fb2d135e28 Stopping user operation
Oct 28, 2022 1:47:02 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: Done
```

## Feature Changes

The following changes affect the logger name annotation of this backend.

### Version 2.2.0

The deprecated log annotation with key `jpl_logger_name` was removed.
The common log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`) is now the only supported annotation for the logger name.
