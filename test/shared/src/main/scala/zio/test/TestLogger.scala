package zio.test

import zio.{Console, Has, UIO, URIO, ZIO, ZLayer}

trait TestLogger extends Serializable {
  def logLine(line: String): UIO[Unit]
}

object TestLogger {

  def fromConsole: ZLayer[Has[Console], Nothing, Has[TestLogger]] =
    ZIO
      .service[Console]
      .map { console =>
        new TestLogger {
          def logLine(line: String): UIO[Unit] = console.printLine(line)
        }
      }
      .toLayer

  def logLine(line: String): URIO[Has[TestLogger], Unit] =
    ZIO.accessM(_.get.logLine(line))
}
