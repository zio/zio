package zio.test

import zio.{Console, UIO, URIO, ZIO, ZLayer, Has}

trait TestLogger extends Serializable {
  def logLine(line: String): UIO[Unit]
}

object TestLogger {

  def fromConsole: ZLayer[Has[Console], Nothing, Has[TestLogger]] =
    ZLayer.fromService { (console: Console) =>
      new TestLogger {
        def logLine(line: String): UIO[Unit] = console.putStrLn(line)
      }
    }

  def logLine(line: String): URIO[Has[TestLogger], Unit] =
    ZIO.accessM(_.get.logLine(line))
}
