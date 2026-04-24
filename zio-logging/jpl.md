# Java Platform/System Logger

> [`Java Platform/System Logger`](https://openjdk.org/jeps/264) is logging API which was introduced in Java 9.

[`Java Platform/System Logger`](https://openjdk.org/jeps/264) is logging API which was introduced in Java 9.

In order to use this logging backend, we need to add the following line in our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "2.5.3"
```

Logger layer:

[//]: # (TODO: make snippet type-checked using mdoc)

```scala

val logger = Runtime.removeDefaultLoggers >>> JPL.jpl
```

Default `JPL` logger setup:
* logger name (by default)  is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.JplSimpleApp.run(JplSimpleApp.scala:17)` will have `zio.logging.example.JplSimpleApp` as logger name
    * NOTE: custom logger name may be set by `zio.logging.loggerName` aspect
* all annotations (logger name annotation is excluded) are placed at the beginning of log message
* cause is logged as throwable

See also [LogFormat and LogAppender](formatting-log-records.md#logformat-and-logappender)

Custom logger name set by aspect:

```scala
ZIO.logInfo("Starting user operation") @@ zio.logging.loggerName("zio.logging.example.UserOperation")
```

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### Java Platform/System logger name and annotations

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

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

## Feature changes

### Version 2.2.0

Deprecated log annotation with key `jpl_logger_name` (`JPL.loggerNameAnnotationKey`) removed,
only common log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`) for logger name is supported now.
