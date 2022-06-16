package zio

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait ZIOAppPlatformSpecific { self: ZIOApp =>

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    implicit val trace = Tracer.newTrace

    val newLayer =
      Scope.default +!+ ZLayer.succeed(ZIOAppArgs(Chunk.fromIterable(args0))) >>>
        bootstrap +!+ ZLayer.environment[ZIOAppArgs with Scope]

    Unsafe.unsafeCompat { implicit u =>
      runtime.unsafeRunAsync {
        (for {
          runtime <- ZIO.runtime[Environment with ZIOAppArgs with Scope]
          _       <- installSignalHandlers(runtime)
          _       <- runtime.run(run).tapErrorCause(ZIO.logErrorCause(_)).exitCode.tap(exit)
        } yield ()).provideLayer(newLayer)
      }
    }
  }
}
