# Testing

> ZIO 2 test library has test logger implementation for testing:

ZIO 2 test library has test logger implementation for testing:

```scala
libraryDependencies += "dev.zio" %% "zio-test" % ZioVersion % Test
```

Test logger layer:

```scala
zio.test.ZTestLogger.default
```

You can find the source code of examples [here](https://github.com/zio/zio-logging/tree/master/examples/src/test/scala/zio/logging/example)

Test example:

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

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
