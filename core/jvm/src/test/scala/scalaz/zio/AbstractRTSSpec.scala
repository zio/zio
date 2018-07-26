package scalaz.zio

import org.specs2.Specification

trait AbstractRTSSpec extends Specification with RTS {
  override def defaultHandler[E]: Throwable => IO[E, Unit] = {
    case Errors.UnhandledError(_)      => IO.unit[E]
    case Errors.TerminatedException(_) => IO.unit[E]
    case Errors.LostRace(_)            => IO.unit[E]
    case Errors.NothingRaced           => IO.unit[E]
    case e                             => IO sync Console.err.println(s"""[info] Discarding ${e.getClass.getName} ("${e.getMessage}")""")
  }
}
