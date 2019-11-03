package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.{ Schedule, ZIO }
import zio.clock.Clock

object TestUtils {

  def label(f: Future[Boolean], s: String)(implicit ec: ExecutionContext): Future[(Boolean, String)] =
    f.map { p =>
      if (p)
        (p, succeed(s))
      else
        (p, fail(s))
    }.recover { case _ => (false, fail(s)) }

  def nonFlaky[R, E](test: ZIO[R, E, Boolean]): ZIO[R with Clock, E, Boolean] =
    test.repeat(Schedule.recurs(100) *> Schedule.identity[Boolean])

  def scope(fs: List[Future[(Boolean, String)]], s: String)(
    implicit ec: ExecutionContext
  ): List[Future[(Boolean, String)]] = {
    val p      = Future.sequence(fs).map(_.forall(_._1))
    val offset = fs.map(_.map { case (p, s) => (p, "  " + s) })
    p.map(p => if (p) (p, succeed(s)) else (p, fail(s))) :: offset
  }

  def report(ps: List[Future[(Boolean, String)]])(implicit ec: ExecutionContext): Unit = {
    val f = Future
      .sequence(ps)
      .map(results => (results.forall(_._1), results))
      .flatMap {
        case (passed, results) =>
          results.foreach(result => println(result._2))
          if (!passed)
            Future(ExitUtils.fail()).map(_ => false)
          else
            Future.successful(true)
      }
    ExitUtils.await(f)
  }

  private def succeed(s: String): String =
    green("+") + " " + s

  private def fail(s: String): String =
    red("-" + " " + s)

  private def green(s: String): String =
    Console.GREEN + s + Console.RESET

  private def red(s: String): String =
    Console.RED + s + Console.RESET
}
