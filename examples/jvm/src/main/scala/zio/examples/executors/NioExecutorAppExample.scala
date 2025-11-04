package zio.examples.executors

import zio._
import zio.internal.NIOExecutor
import zio.logging.{consoleLogger, ConsoleLoggerConfig, LogFilter, LogFormat}

/**
 * This example app shows how to construct an application that uses the NIOExecutor
 * and NIOClock classes instead of the default scheduler and clock.
 */
object NioExecutorAppExample extends ZIOAppDefault {

  val nioExecutorLayer: ZLayer[Any, Config.Error, NIOExecutor] = NIOExecutor.live
  val clockLayer: ZLayer[NIOExecutor, Nothing, Clock]          = NIOClock.live

  private val traceLogger: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> consoleLogger(
      ConsoleLoggerConfig(
        format = LogFormat.default,
        filter = LogFilter.LogLevelByNameConfig(
          rootLevel = LogLevel.Trace,
          mappings = Map.empty[String, LogLevel]
        )
      )
    )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    nioExecutorLayer.flatMap { executorEnv =>
      val configLayer     = Runtime.setExecutor(executorEnv.get)
      val finalClockLayer = ZLayer.succeedEnvironment(executorEnv) >>> clockLayer

      // The layers are composed to include the custom logger and OpLog.
      configLayer >+> finalClockLayer >+> traceLogger >+> Runtime.enableOpLog
    }

  def run: ZIO[Any, Throwable, Unit] =
    for {
      _     <- ZIO.logInfo("Application starting on NIOExecutor...")
      _     <- ZIO.logTrace("Forking a fiber...")
      fiber <- ZIO.logDebug("This is a non-blocking forked fiber.").fork
      _     <- ZIO.logInfo("Submitting a blocking task...")
      _ <- ZIO.blocking {
             ZIO.logInfo("This is a blocking task...");
             Thread.sleep(2000);
             ZIO.logInfo("Blocking task done.");
           }
      _ <- ZIO.logInfo("Blocking task finished.")
      _ <- ZIO.logInfo("The application will run for 5 seconds and then shut down.")
      _ <- ZIO.sleep(5.seconds)
      _ <- ZIO.logInfo("Application finished after 5 seconds.")
    } yield ()
}
