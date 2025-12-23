package zio

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import scala.annotation.nowarn

private[zio] trait ZIOAppPlatformSpecific { self: ZIOApp =>

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    implicit val trace: Trace   = Trace.empty
    implicit val unsafe: Unsafe = Unsafe

    val newLayer =
      ZLayer.succeed(ZIOAppArgs(Chunk.fromIterable(args0))) >>>
        bootstrap +!+ ZLayer.environment[ZIOAppArgs]

    val workflow =
      (for {
        runtime <- ZIO.runtime[Environment with ZIOAppArgs]
        _       <- installSignalHandlers(runtime)
        result <- runtime.run(ZIO.scoped[Environment with ZIOAppArgs](run)).tapErrorCause { c =>
                    // Don't log an interruption error if we're shutting down
                    if (shuttingDown.get() && c.isInterruptedOnly) Exit.unit
                    else ZIO.logErrorCause(c)
                  }
      } yield result).provideLayer(newLayer.tapErrorCause(ZIO.logErrorCause(_)))

    val _ =
      runtime.unsafe.fork {
        ZIO.uninterruptibleMask { restore =>
          for {
            fiberId <- ZIO.fiberId
            code <- restore(workflow).exitWith { exit0 =>
                      val exitCode = if (exit0.isSuccess) ExitCode.success else ExitCode.failure
                      interruptRootFibers(fiberId).as(exitCode)
                    }
          } yield exitUnsafe(code)(Unsafe)
        }
      }
  }
}
