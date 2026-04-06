package zio.zioapp.apps

import zio._

// Tests regression #9807 - on clean shutdown, stderr should NOT contain
// uncaught exception traces from the main thread
object CleanStderrApp extends ZIOAppDefault {
  val run = ZIO.succeed(println("APP_READY")) *> ZIO.never
}
