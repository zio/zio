package zio.test

import scala.concurrent.{ ExecutionContext, Future }

private[test] object ExitUtils {

  def fail(): Unit = ()

  def await(f: Future[Boolean])(implicit ec: ExecutionContext): Unit =
    f.foreach(_ => ())
}
