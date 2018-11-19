package scalaz.zio

import scala.concurrent.duration._
import org.specs2.Specification
import org.specs2.specification.core.Fragments
import scalaz.zio.ExitResult.Cause

trait AbstractRTSSpec extends Specification with RTS {
  override def defaultHandler: Cause[Any] => IO[Nothing, Unit] = _ => IO.unit

  lazy val ShutdownRTS =
    step {
      println("Shutting down RTS...")
      unsafeShutdownAndWait(Duration.Zero)
    }

  override def map(fs: => Fragments) =
    fs.append(ShutdownRTS)
}
