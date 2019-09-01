package zio.test

import scala.concurrent.{ ExecutionContext, Future }

private[test] object TestPlatform {

  def await(f: Future[Boolean])(implicit ec: ExecutionContext): Unit =
    f.foreach(_ => ())

  def fail(): Unit = System.exit(-1)

  val isJS = true

  val isJVM = false
}
