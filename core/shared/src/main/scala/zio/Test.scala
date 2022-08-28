package zio

object Test extends ZIOAppDefault {

  val effect =
    for {
      _    <- Console.printLine("Please enter your name: ")
      name <- Console.printLine("stupid forking error!")
      _    <- Console.printLine(s"Hello, $name!")
    } yield ()
  def run =
    for {
      opLogger <- Supervisor.opLogger((_, op) => op.trace.toString())
      _ <- ZIO.logLevel(LogLevel.Trace) {
             effect.withRuntimeFlags(RuntimeFlags.enable(RuntimeFlag.OpSupervision)).supervised(opLogger)
           }
    } yield ()
}
