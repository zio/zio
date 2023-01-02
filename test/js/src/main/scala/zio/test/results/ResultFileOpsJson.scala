package zio.test.results

import zio._

import java.io.IOException
import java.nio.file.Path

private[test] trait ResultFileOpsJson {
  // TODO Guarantee that file has been created by the time you are able to call this
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

private[test] object ResultFileOpsJson {
  val live: ZLayer[Any, Nothing, ResultFileOpsJson] =
    ZLayer.scoped(
      ZIO.acquireRelease(
        for {
          reentrantLockImposter <- Ref.Synchronized.make[Unit](())
          instance               = Live("target/test-reports-zio/output.json", reentrantLockImposter)
        } yield instance
      )(instance => ZIO.unit)
    )

}

private[test] case class Live(resultPath: String, lock: Ref.Synchronized[Unit]) extends ResultFileOpsJson {
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit] =
    ZIO.unit

}
