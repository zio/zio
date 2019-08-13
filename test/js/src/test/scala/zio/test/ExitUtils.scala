package zio.test

import scala.concurrent.{ ExecutionContext, Future }

object ExitUtils {

  def fail(): Unit = System.exit(-1)

  def await(f: Future[Boolean])(implicit ec: ExecutionContext): Unit =
    f.foreach(_ => ())
}
