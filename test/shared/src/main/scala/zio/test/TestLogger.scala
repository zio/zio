package zio.test

import zio.{ UIO, URIO, ZIO }
import zio.console.Console

trait TestLogger {
  def testLogger: TestLogger.Service
}

object TestLogger {
  trait Service {
    def logLine(line: String): UIO[Unit]
  }

  def fromConsole(console: Console): TestLogger = new TestLogger {
    override def testLogger: Service =
      (line: String) => console.console.putStrLn(line)
  }

  def fromConsoleM: URIO[Console, TestLogger] = ZIO.access[Console](fromConsole)

  def logLine(line: String): URIO[TestLogger, Unit] =
    ZIO.accessM(_.testLogger.logLine(line))
}
