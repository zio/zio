package zio.examples.bank.effect
import zio.UIO

object SilentLogger extends Logger.Effect {
  override def info(msg: String): UIO[Unit] = UIO.unit

  override def error(msg: String): UIO[Unit] = UIO.unit
}
