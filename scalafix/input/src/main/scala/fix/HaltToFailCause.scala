/*
rule = HaltToFailCause
*/
package fix

import zio.Exit.{Failure, Success}
import zio.{Cause, Exit, ZIO}
import zio.duration.durationInt

object HaltToFailCause {

  // Add code that needs fixing here.
  def halt(s : String): Int = ???

  object Halt {

    def zipWith[E, A, B, C](a: Exit[E, A], b: Exit[E, B])(
      f: (A, B) => C,
      g: (Cause[E], Cause[E]) => Cause[E]
    ): Exit[E, C] =
      (a, b) match {
        case (Success(a), Success(b)) => Exit.succeed(f(a, b))
        case (Failure(l), Failure(r)) => Exit.halt(g(l, r))
        case (e@Failure(_), _) => e
        case (_, e@Failure(_)) => e
      }

    val io = ZIO.succeed(true).timeoutHalt(10.millis)
  }

}
