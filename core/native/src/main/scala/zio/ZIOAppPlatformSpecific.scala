package zio

trait ZIOAppPlatformSpecific { self: ZIOApp =>

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit =
    runtime.unsafeRunAsync {
      invoke(Chunk.fromIterable(args0))
        .provide(runtime.environment)
        .tapErrorCause(ZIO.logErrorCause(_))
        .exitCode
        .flatMap(exit)
    }
}
