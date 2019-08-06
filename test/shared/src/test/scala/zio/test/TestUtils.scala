package zio.test

import scala.concurrent.{ ExecutionContext, Future }

object TestUtils {

  def label(f: Future[Boolean], s: String)(implicit ec: ExecutionContext): Future[(Boolean, String)] =
    f.map(p => (p, s)).recover { case _ => (false, s) }

  def report(ps: List[Future[(Boolean, String)]])(implicit ec: ExecutionContext): Unit = {
    val f = Future
      .sequence(ps)
      .map(results => (results.forall(_._1), results))
      .flatMap {
        case (passed, results) =>
          results.foreach {
            case (p, s) =>
              if (p)
                println(green("+") + " " + s)
              else
                println(red(" -" + " " + s))
          }
          if (!passed)
            Future(ExitUtils.fail()).map(_ => false)
          else
            Future.successful(true)
      }
    ExitUtils.await(f)
  }

  private def green(s: String): String =
    Console.GREEN + s + Console.RESET

  private def red(s: String): String =
    Console.RED + s + Console.RESET
}
