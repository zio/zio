package scalaz.zio

import org.specs2.Specification

trait AbstractRTSSpec extends Specification with RTS {
  override def defaultHandler: List[Throwable] => IO[Nothing, Unit] =
    IO.traverse(_) {
      case Errors.UnhandledError(_, _) => IO.unit
      case Errors.TerminatedFiber(_)   => IO.unit
      case e                           => IO sync Console.err.println(s"""[info] Discarding ${e.getClass.getName} ("${e.getMessage}")""")
    } *> IO.unit
}
