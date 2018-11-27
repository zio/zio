// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.ExecutionContext

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
   * Observes the fiber, which suspends the observing fiber until the result of the
   * fiber has been determined.
   */
  def observe: IO[Nothing, ExitResult[E, A]]

  /**
   * Tentatively observes the fiber, but returns immediately if it is not already done.
   */
  def poll: IO[Nothing, Option[ExitResult[E, A]]]

  /**
   * Joins the fiber, which suspends the joining fiber until the result of the
   * fiber has been determined. Attempting to join a fiber that has errored will
   * result in a catchable error, _if_ that error does not result from interruption.
   */
  final def join: IO[E, A] = observe.flatMap(IO.done)

  /**
   * Interrupts the fiber with no specified reason. If the fiber has already
   * terminated, either successfully or with error, this will resume
   * immediately. Otherwise, it will resume when the fiber has been
   * successfully interrupted or has produced its result.
   */
  def interrupt: IO[Nothing, Unit]

  /**
   * Zips this fiber with the specified fiber, combining their results using
   * the specified combiner function. Both joins and interruptions are performed
   * in sequential order from left to right.
   */
  final def zipWith[E1 >: E, B, C](that: => Fiber[E1, B])(f: (A, B) => C): Fiber[E1, C] =
    new Fiber[E1, C] {
      def observe: IO[Nothing, ExitResult[E1, C]] =
        self.observe.seqWith(that.observe)(_.zipWith(_)(f, _ && _))

      def poll: IO[Nothing, Option[ExitResult[E1, C]]] =
        self.poll.seqWith(that.poll) {
          case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(f, _ && _))
          case _                    => None
        }

      def interrupt: IO[Nothing, Unit] = self.interrupt *> that.interrupt
    }

  /**
   * Zips this fiber and the specified fiber togther, producing a tuple of their
   * output.
   */
  final def zip[E1 >: E, B](that: => Fiber[E1, B]): Fiber[E1, (A, B)] =
    zipWith(that)((a, b) => (a, b))

  /**
   * Same as `zip` but discards the output of the left hand side.
   */
  final def *>[E1 >: E, B](that: Fiber[E1, B]): Fiber[E1, B] =
    zip(that).map(_._2)

  /**
   * Same as `zip` but discards the output of the right hand side.
   */
  final def <*[E1 >: E, B](that: Fiber[E1, B]): Fiber[E1, A] =
    zip(that).map(_._1)

  /**
   * Maps over the value the Fiber computes.
   */
  final def map[B](f: A => B): Fiber[E, B] =
    new Fiber[E, B] {
      def observe: IO[Nothing, ExitResult[E, B]]      = self.observe.map(_.map(f))
      def poll: IO[Nothing, Option[ExitResult[E, B]]] = self.poll.map(_.map(_.map(f)))
      def interrupt: IO[Nothing, Unit]                = self.interrupt
    }

  /**
   * Maps the output of this fiber to the specified constant.
   */
  final def const[B](b: => B): Fiber[E, B] =
    map(_ => b)

  /**
   * Maps the output of this fiber to `()`.
   */
  final def void: Fiber[E, Unit] = const(())
}

object Fiber {
  final case class Descriptor(
    id: FiberId,
    interrupted: Boolean,
    executor: ExecutionContext,
    supervisor: ExitResult.Cause[Nothing] => IO[Nothing, Unit]
  )

  final val unit: Fiber[Nothing, Unit] = Fiber.point(())

  final val never: Fiber[Nothing, Nothing] =
    new Fiber[Nothing, Nothing] {
      def observe: IO[Nothing, ExitResult[Nothing, Nothing]]      = IO.never
      def poll: IO[Nothing, Option[ExitResult[Nothing, Nothing]]] = IO.point(None)
      def interrupt: IO[Nothing, Unit]                            = IO.never
    }

  final def done[E, A](exit: => ExitResult[E, A]): Fiber[E, A] =
    new Fiber[E, A] {
      def observe: IO[Nothing, ExitResult[E, A]]      = IO.point(exit)
      def poll: IO[Nothing, Option[ExitResult[E, A]]] = IO.point(Some(exit))
      def interrupt: IO[Nothing, Unit]                = IO.unit
    }

  final def point[E, A](a: => A): Fiber[E, A] =
    done(ExitResult.succeeded(a))

  final def interruptAll(fs: Iterable[Fiber[_, _]]): IO[Nothing, Unit] =
    fs.foldLeft(IO.unit)((io, f) => io *> f.interrupt)

  final def joinAll(fs: Iterable[Fiber[_, _]]): IO[Nothing, Unit] =
    fs.foldLeft(IO.unit)((io, f) => io *> f.observe.void)
}
