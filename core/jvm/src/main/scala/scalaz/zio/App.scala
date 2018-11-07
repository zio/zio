// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

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
 *   def run(args: List[String]): IO[Nothing, ExitStatus] =
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

  case class ExitStatus(code: Int) extends Serializable with Product

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program and has to return an `IO` with the errors fully handled.
   */
  def run(args: List[String]): IO[Nothing, ExitStatus]

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    val status = unsafeRun(
      for {
        fiber <- run(args0.toList).fork
        _ <- IO.sync(Runtime.getRuntime.addShutdownHook(new Thread {
              override def run() = unsafeRun(fiber.interrupt)
            }))
        result <- fiber.join
      } yield result
    )

    sys.exit(status.code)
  }

}
