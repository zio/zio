// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scalaz.zio.internal.Executor

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
   * Awaits the fiber, which suspends the awaiting fiber until the result of the
   * fiber has been determined.
   */
  <<<<<<< HEAD
  def observe: IO[Nothing, ExitResult[E, A]]
  =======
  def await: IO[Nothing, Exit[E, A]]

  /**
   * Tentatively observes the fiber, but returns immediately if it is not already done.
   */
  def poll: IO[Nothing, Option[Exit[E, A]]]

  /**
   * Joins the fiber, which suspends the joining fiber until the result of the
   * fiber has been determined. Attempting to join a fiber that has errored will
   * result in a catchable error, _if_ that error does not result from interruption.
   */
  final def join: IO[E, A] = await.flatMap(IO.done)

  /**
   * Interrupts the fiber with no specified reason. If the fiber has already
   * terminated, either successfully or with error, this will resume
   * immediately. Otherwise, it will resume when the fiber completes.
   */
  def interrupt: IO[Nothing, Exit[E, A]]

  /**
   * Zips this fiber with the specified fiber, combining their results using
   * the specified combiner function. Both joins and interruptions are performed
   * in sequential order from left to right.
   */
  final def zipWith[E1 >: E, B, C](that: => Fiber[E1, B])(f: (A, B) => C): Fiber[E1, C] =
    new Fiber[E1, C] {
      def await: IO[Nothing, Exit[E1, C]] =
        self.await.zipWith(that.await)(_.zipWith(_)(f, _ && _))

      <<<<<<< HEAD
      final def poll: IO[Unit, ExitResult[E1, C]] =
        self.poll.seqWith(that.poll) {
          case (ra, rb) =>
            ra.zipWith(rb)(f, _ && _)
            =======
            def poll: IO[Nothing, Option[Exit[E1, C]]] =
              self.poll.zipWith(that.poll) {
                case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(f, _ && _))
                case _                    => None

              }

            def interrupt: IO[Nothing, Exit[E1, C]] = self.interrupt.zipWith(that.interrupt)(_.zipWith(_)(f, _ && _))
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
          def await: IO[Nothing, Exit[E, B]]        = self.await.map(_.map(f))
          def poll: IO[Nothing, Option[Exit[E, B]]] = self.poll.map(_.map(_.map(f)))
          def interrupt: IO[Nothing, Exit[E, B]]    = self.interrupt.map(_.map(f))
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
      supervisor: Exit.Cause[Nothing] => IO[Nothing, _],
      executor: Executor
    )

    final val unit: Fiber[Nothing, Unit] = Fiber.succeedLazy(())

    final val never: Fiber[Nothing, Nothing] =
      new Fiber[Nothing, Nothing] {
        def await: IO[Nothing, Exit[Nothing, Nothing]]        = IO.never
        def poll: IO[Nothing, Option[Exit[Nothing, Nothing]]] = IO.succeed(None)
        def interrupt: IO[Nothing, Exit[Nothing, Nothing]]    = IO.never
      }

    final def done[E, A](exit: => Exit[E, A]): Fiber[E, A] =
      new Fiber[E, A] {
        def await: IO[Nothing, Exit[E, A]]        = IO.succeedLazy(exit)
        def poll: IO[Nothing, Option[Exit[E, A]]] = IO.succeedLazy(Some(exit))
        def interrupt: IO[Nothing, Exit[E, A]]    = IO.succeedLazy(exit)
      }

    final def succeedLazy[E, A](a: => A): Fiber[E, A] =
      done(Exit.succeed(a))

    final def interruptAll(fs: Iterable[Fiber[_, _]]): IO[Nothing, Unit] =
      fs.foldLeft(IO.unit)((io, f) => io <* f.interrupt)

    final def joinAll(fs: Iterable[Fiber[_, _]]): IO[Nothing, Unit] =
      fs.foldLeft(IO.unit)((io, f) => io *> f.await.void)
  }
}
