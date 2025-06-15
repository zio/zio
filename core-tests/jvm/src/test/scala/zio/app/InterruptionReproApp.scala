package zio.app

import zio._

/**
 * A minimal ZIOApp designed specifically to reproduce the bug
 * where an external interruption causes a successful application
 * to return a failure exit code.
 */
object InterruptionReproApp extends ZIOAppDefault {
  override def run =
    Console.printLine("InterruptionReproApp started successfully.") *>
      ZIO.never
} 