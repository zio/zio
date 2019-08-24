package zio.examples.bank.effect

import zio.UIO

trait Logger {

  val log: Logger.Effect

}

object Logger {

  trait Effect {

    def info(msg: String): UIO[Unit]

    def error(msg: String): UIO[Unit]

  }

}

object SimpleLogger extends Logger.Effect {

  override def info(msg: String): UIO[Unit] = UIO.effectTotal(println(s"INFO: $msg"))

  override def error(msg: String): UIO[Unit] = UIO.effectTotal(println(s"ERROR: $msg"))

}
