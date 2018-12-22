// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import scalaz.zio.duration.Duration

/**
 * The entry point for a purely-functional application on the JVM.
 *
 * {{{
 * import java.io.IOException
 * import scalaz.zio.{App, IO}
 * import scalaz.zio.console._
 *
 * object MyApp extends App {
 *
 *  final def run(args: List[String]): IO[Nothing, ExitStatus] =
 *     myAppLogic.attempt.map(_.fold(_ => 1, _ => 0)).map(ExitStatus.ExitNow(_))
 *
 *   def myAppLogic: IO[IOException, Unit] =
 *     for {
 *       _ <- putStrLn("Hello! What is your name?")
 *       n <- getStrLn
 *       _ <- putStrLn("Hello, " + n + ", good to meet you!")
 *     } yield ()
 * }
 * }}}
 */
trait App extends RTS {
  sealed abstract class ExitStatus extends Serializable with Product
  object ExitStatus extends Serializable {
    case class ExitNow(code: Int)                         extends ExitStatus
    case class ExitWhenDone(code: Int, timeout: Duration) extends ExitStatus
    case object DoNotExit                                 extends ExitStatus
  }

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program and has to return an `IO` with the errors fully handled.
   */
  def run(args: List[String]): IO[Nothing, ExitStatus]

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit =
    unsafeRun(
      for {
        fiber <- run(args0.toList).fork
        _ <- IO.sync(Runtime.getRuntime.addShutdownHook(new Thread {
              override def run() = {
                val _ = unsafeRun(fiber.interrupt)
              }
            }))
        result <- fiber.join
      } yield result
    ) match {
      case ExitStatus.ExitNow(code) =>
        sys.exit(code)
      case ExitStatus.ExitWhenDone(code, _) =>
        // TODO: Shutdown
        sys.exit(code)
      case ExitStatus.DoNotExit =>
    }

}
