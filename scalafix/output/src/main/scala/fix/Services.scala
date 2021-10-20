package fix

import zio.{Has, URIO, ZIO}
import zio._
import zio.{ Console, Random, System }
import zio.Console.{ printLine, readLine }

object Services {
  val random: URIO[Has[Random], Random] = ZIO.service[Random]
  val console: URIO[Has[Console], Console] = ZIO.service[Console]
  val live: ZIO[Has[System], Nothing, Unit] = ZIO.unit
  val live2: ZIO[Has[System], Nothing, Unit] = ZIO.unit

  val effect = readLine *> printLine("hi")
}