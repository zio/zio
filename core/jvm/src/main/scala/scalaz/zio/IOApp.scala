// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration

import Errors._

/**
 * The entry point for a purely-functional application on the JVM.
 *
 * {{{
 * import java.io.IOException
 * import scalaz.zio.{IO, IOApp, Void}
 * import scalaz.zio.console._
 *
 * object MyApp extends IOApp {
 *
 *   def run(args: List[String]): IO[Void, ExitStatus] =
 *     myAppLogic.attempt.map(_.fold(_ => 1)(_ => 0)).map(ExitStatus.ExitNow(_))
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
trait IOApp extends RTS {

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
  def run(args: List[String]): IO[Void, ExitStatus]

  /**
   * Thread for interrupting the main function
   */
  class InterruptThread(io: IOApp) extends Runnable {

    def run: Unit = {
       io.mainFiber match {
         case Some(fiber) => 
           try {
             io.unsafeRun(fiber.interrupt(TerminatedException("interrupted")))
           } catch {
             case _: Throwable => //we just do nothing here
           }
         case None =>
       }
    }
  }

  /**
   * The main fiber reference
   */
   var mainFiber = None: Option[ Fiber[Void, ExitStatus] ];

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread (new InterruptThread(this)))

    val fiber = unsafeRun(run(args0.toList).fork)
    mainFiber = Some(fiber)

    unsafeRun(fiber.join) match {
      case ExitStatus.ExitNow(code) =>
        sys.exit(code)
      case ExitStatus.ExitWhenDone(code, timeout) =>
        unsafeShutdownAndWait(timeout)
        sys.exit(code)
      case ExitStatus.DoNotExit =>
    }
  }

}
