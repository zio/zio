// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.util.control.NonFatal
import scala.concurrent.duration.Duration
import scala.scalajs.js.timers._

/**
 * This trait provides a high-performance implementation of a runtime system for
 * the `IO` monad on the JVM.
 */
trait RTS extends CommonRTS {

  def unsafeShutdownAndWait[A](timeout: Duration)(f: A => Unit, a: A): Unit = {
    setTimeout(timeout.toNanos.toDouble) { f(a) }
    ()
  }

  def awaitTermination(timeout: Duration) = {
    setTimeout(timeout.toNanos.toDouble) { () }
    ()
  }

  final def submit[A](block: => A): Unit = {
    setTimeout(0.0) {
      block
      ()
    }
    ()
  }

  final def schedule[E, A](block: => A, duration: Duration): Async[E, Unit] =
    if (duration == Duration.Zero) {
      submit(block)
      Async.later[E, Unit]
    } else {
      val future = setTimeout(duration.toNanos.toDouble) {
        submit(block)
      }
      Async.maybeLater { () =>
        clearTimeout(future)
      }
    }

  /**
   * Utility function to avoid catching truly fatal exceptions. Do not allocate
   * memory here since this would defeat the point of checking for OOME.
   */
  def nonFatal(t: Throwable): Boolean = NonFatal(t)

  /**
   * Effectfully interprets an `IO`, blocking if necessary to obtain the result.
   */
  final def unsafeRunSync[E, A](io: IO[E, A]): ExitResult[E, A] =
    throw new Exception(
      "unsafeRunSync method is unsupported at the moment in ScalaJS, the issue with blocking in Javascript is currently not solved"
    )

  def getMap[A]: java.util.Map[A, java.lang.Boolean] =
    new java.util.HashMap[A, java.lang.Boolean]()

}
