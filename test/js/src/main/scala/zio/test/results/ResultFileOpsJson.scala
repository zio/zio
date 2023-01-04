package zio.test.results

import zio._

import java.io.IOException

private[test] trait ResultFileOpsJson {
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

private[test] object ResultFileOpsJson {
  val live: ZLayer[Any, Nothing, ResultFileOpsJson] =
    ZLayer.succeed(
      Live()
    )

  private[test] case class Live() extends ResultFileOpsJson {
    def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit] =
      ZIO.unit
  }

}