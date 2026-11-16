# Testing

> ZIO Test provides a test logger that records log output, so tests can assert on it.
> Add `zio-test` to the test scope of your build:

```scala
libraryDependencies += "dev.zio" %% "zio-test" % ZioVersion % Test
```

The test logger layer is `ZTestLogger.default`. It replaces the default loggers and records each log entry,
which `ZTestLogger.logOutput` returns. To install it with the default loggers removed:

```scala
import zio.Runtime
import zio.test.ZTestLogger

val testLayer = Runtime.removeDefaultLoggers >>> ZTestLogger.default
```

You can find the source code of examples [here](https://github.com/zio/zio-logging/tree/master/examples/core/shared/src/test/scala/zio/logging/example)

A complete spec checks the message, level, trace id and annotations of each log entry.
Each `ZIO.logInfo` call produces one entry, `ZIOAspect.annotated` adds a key-value annotation, and `LogAnnotation.TraceId`
sets the trace id for the wrapped effect:

```scala
import zio.logging.{ LogAnnotation, logContext }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, LogLevel, Runtime, ZIO, ZIOAspect, _ }

import java.util.UUID

object LoggingSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] = suite("LoggingSpec")(
    test("start stop log output") {
      val users = Chunk.fill(2)(UUID.randomUUID())
      for {
        traceId      <- ZIO.succeed(UUID.randomUUID())
        _            <- ZIO.foreach(users) { uId =>
                          {
                            ZIO.logInfo("Starting operation") *> ZIO.sleep(500.millis) *> ZIO.logInfo("Stopping operation")
                          } @@ ZIOAspect.annotated("user", uId.toString)
                        } @@ LogAnnotation.TraceId(traceId)
        _            <- ZIO.logInfo("Done")
        loggerOutput <- ZTestLogger.logOutput
      } yield assertTrue(loggerOutput.size == 5) && assertTrue(
        loggerOutput.forall(_.logLevel == LogLevel.Info)
      ) && assert(loggerOutput.map(_.message()))(
        equalTo(
          Chunk(
            "Starting operation",
            "Stopping operation",
            "Starting operation",
            "Stopping operation",
            "Done"
          )
        )
      ) && assert(loggerOutput.map(_.context.get(logContext).flatMap(_.asMap.get(LogAnnotation.TraceId.name))))(
        equalTo(
          Chunk.fill(4)(Some(traceId.toString)) :+ None
        )
      ) && assert(loggerOutput.map(_.annotations.get("user")))(
        equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))) :+ None)
      )
    }
  ).provideLayer(
    Runtime.removeDefaultLoggers >>> ZTestLogger.default
  ) @@ TestAspect.withLiveClock
}
```
