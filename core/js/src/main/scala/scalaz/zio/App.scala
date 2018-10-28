package scalaz.zio

import scala.concurrent.duration._

trait App extends RTS {

  sealed trait ExitStatus
  object ExitStatus {
    case class ExitNow(code: Int)                         extends ExitStatus
    case class ExitWhenDone(code: Int, timeout: Duration) extends ExitStatus
    case object DoNotExit                                 extends ExitStatus
  }

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program and has to return an `IO` with the errors fully handled.
   */
  def run(args: List[String]): IO[Nothing, ExitStatus]

  def errorInfo: Int => Unit = code => println(s"Exited with following error code: $code")

  private val exitCallback: Callback[Nothing, ExitStatus] = {
    case ExitResult.Completed(exitResult) =>
      exitResult match {
        case ExitStatus.ExitNow(code) =>
          println(s"Program exiting: $code")
        case ExitStatus.ExitWhenDone(code, timeout) =>
          unsafeShutdownAndWait[Int](timeout)(errorInfo, code)
        case ExitStatus.DoNotExit =>
      }

    case ExitResult.Failed(error, defects) =>
      println("Exiting with value: " + error + s"following exceptions where thrown: ${defects.mkString("\n")}")

    case ExitResult.Terminated(causes) =>
      println(s"Program was terminated, the following exceptions where thrown: ${causes.mkString("\n")}")
  }

  final def main(args0: Array[String]): Unit =
    unsafeRunAsync(run(args0.toList))(exitCallback)

}
