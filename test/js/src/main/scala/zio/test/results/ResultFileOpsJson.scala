package zio.test.results

import zio._

import java.io.IOException

private[test] trait ResultFileOps {
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

private[test] object ResultFileOps {
  def live(jsonResultPath: String): ZLayer[Any, Nothing, ResultFileOps] =
    ZLayer.succeed(
      Json()
    )

  private[test] case class Json() extends ResultFileOps {
    def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit] =
      ZIO.unit
  }

}
