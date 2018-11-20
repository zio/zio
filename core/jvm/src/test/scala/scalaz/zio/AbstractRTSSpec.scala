package scalaz.zio

import org.specs2.Specification
import org.specs2.specification.AroundEach
import org.specs2.execute.{ AsResult, Failure, Result, Skipped }
import scalaz.zio.ExitResult.Cause

trait AbstractRTSSpec extends Specification with RTS with AroundEach {
  override def defaultHandler: Cause[Any] => IO[Nothing, Unit] = _ => IO.unit

  def around[R: AsResult](r: => R): Result =
    AsResult.safely(r) match {
      case Skipped(m, e) if m contains "TIMEOUT" => Failure(m, e)
      case other                                 => other
    }
}
