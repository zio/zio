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

    val app = workflow(args0)

    val _ =
      runtime.unsafe.fork {
        ZIO.uninterruptibleMask { restore =>
          for {
            fiberId <- ZIO.fiberId
            exit0 <- restore(app).exitWith { exit0 =>
                       val exitCode = if (exit0.isSuccess) ExitCode.success else ExitCode.failure
                       interruptRootFibers(fiberId).as(exitCode)
                     }
          } yield exitUnsafe(exit0)
        }
      }
  }
}
