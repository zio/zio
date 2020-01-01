package zio

import sun.misc.{Signal, SignalHandler}
import zio.console._

object Signals {
  def fiberDump: SignalHandler =
    Signal.handle(new Signal("USR1"), (sig: Signal) => {
      for {
      dumps <- Fiber.dump
      _ <- URIO.foreach(dumps)(_.prettyPrintM.flatMap(console.putStrLn))
    } yield ()
    })
}

