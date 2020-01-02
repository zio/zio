package zio.test

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._

private[test] object ExitUtils {

  def await(f: Future[Boolean])(implicit ec: ExecutionContext): Unit = {
    val passed = Await.result(f.map(identity), 60.seconds)
    if (passed) () else throw new AssertionError("tests failed")
  }

  def fail(): Unit = ()
}
