// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

/**
 * A fiber is a lightweight thread of execution that never consumes more than a
 * whole thread (but may consume much less, depending on contention). Fibers are
 * spawned by forking `IO` actions, which, conceptually at least, runs them
 * concurrently with the parent `IO` action.
 *
 * Fibers can be joined, yielding their result other fibers, or interrupted,
 * which terminates the fiber with a runtime error.
 *
 * Fork-Join Identity: fork >=> join = id
 *
 * {{{
 * for {
 *   fiber1 <- io1.fork
 *   fiber2 <- io2.fork
 *   _      <- fiber1.interrupt(e)
 *   a      <- fiber2.join
 * } yield a
 * }}}
 */
trait Fiber[+E, +A] { self =>

  /**
   * Joins the fiber, with suspends the joining fiber until the result of the
   * fiber has been determined. Attempting to join a fiber that has been or is
   * killed before producing its result will result in a catchable error.
   */
  def join: IO[E, A]

  /**
   * Interrupts the fiber with no specified reason. If the fiber has already
   * terminated, either successfully or with error, this will resume
   * immediately. Otherwise, it will resume when the fiber has been
   * successfully interrupted or has produced its result.
   */
  def interrupt: IO[Nothing, Unit] = interrupt0(Nil)

  /**
   * Interrupts the fiber with the specified error(s). If the fiber has already
   * terminated, either successfully or with error, this will resume
   * immediately. Otherwise, it will resume when the fiber has been
   * successfully interrupted or has produced its result.
   */
  def interrupt(t: Throwable, ts: Throwable*): IO[Nothing, Unit] = interrupt0(t :: ts.toList)

  /**
   * Interrupts the fiber with a list of error(s).
   */
  def interrupt0(ts: List[Throwable]): IO[Nothing, Unit]

  /**
   * Add an exit handler for when the fiber terminates and receive information
   * on whether the fiber terminated normally, with unhandled error, or with
   * exception.
   *
   * The specified action will be invoked after the fiber has finished running
   * (including all finalizers). If the specified action throws an exception,
   * it will be reported to the parent fiber's unhandled error handler.
   */
  def onComplete(f: ExitResult[E, A] => IO[Nothing, Unit]): IO[Nothing, Unit]

  /**
   * Zips this fiber with the specified fiber, combining their results using
   * the specified combiner function. Both joins and interruptions are performed
   * in sequential order from left to right.
   */
  final def zipWith[E1 >: E, B, C](that: => Fiber[E1, B])(f: (A, B) => C): Fiber[E1, C] =
    new Fiber[E1, C] {
      def join: IO[E1, C] =
        self.join.zipWith(that.join)(f)

      def interrupt0(ts: List[Throwable]): IO[Nothing, Unit] =
        self.interrupt0(ts) *> that.interrupt0(ts)

      def onComplete(fc: ExitResult[E1, C] => IO[Nothing, Unit]): IO[Nothing, Unit] =
        self.onComplete { ra: ExitResult[E, A] =>
          that.onComplete { rb: ExitResult[E1, B] =>
            (ra, rb) match {
              case (ExitResult.Completed(a), ExitResult.Completed(b)) => fc(ExitResult.Completed(f(a, b)))
              case _ =>
                (ra match {
                  case ExitResult.Failed(e, ts)  => fc(ExitResult.Failed(e, ts))
                  case ExitResult.Terminated(ts) => fc(ExitResult.Terminated(ts))
                  case _                         => IO.unit
                }) *> (rb match {
                  case ExitResult.Failed(e, ts)  => fc(ExitResult.Failed(e, ts))
                  case ExitResult.Terminated(ts) => fc(ExitResult.Terminated(ts))
                  case _                         => IO.unit
                })
            }
          }
        }
    }

  /**
   * Maps over the value the Fiber computes.
   */
  final def map[B](f: A => B): Fiber[E, B] =
    new Fiber[E, B] {
      def join: IO[E, B] = self.join.map(f)

      def interrupt0(ts: List[Throwable]): IO[Nothing, Unit] = self.interrupt0(ts)

      def onComplete(fb: ExitResult[E, B] => IO[Nothing, Unit]): IO[Nothing, Unit] =
        self.onComplete { r: ExitResult[E, A] =>
          fb(r.map(f))
        }
    }
}

object Fiber {
  final def point[E, A](a: => A): Fiber[E, A] =
    new Fiber[E, A] {
      def join: IO[E, A]                                       = IO.point(a)
      def interrupt0(ts: List[Throwable]): IO[Nothing, Unit]   = IO.unit
      def onComplete(f: ExitResult[E, A] => IO[Nothing, Unit]) = f(ExitResult.Completed(a))
    }

  final def interruptAll(fs: Iterable[Fiber[_, _]]): IO[Nothing, Unit] =
    fs.foldLeft(IO.unit)((io, f) => io *> f.interrupt)

  final def joinAll(fs: Iterable[Fiber[_, _]]): IO[Nothing, Unit] =
    fs.foldLeft(IO.unit)((io, f) => io *> f.join.attempt.void)
}
