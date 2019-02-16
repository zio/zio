/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scalaz.zio

import scalaz.zio.internal.Executor

import scala.concurrent.Future

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
  def await: UIO[Exit[E, A]]

  /**
   * Tentatively observes the fiber, but returns immediately if it is not already done.
   */
  def poll: UIO[Option[Exit[E, A]]]

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
  def interrupt: UIO[Exit[E, A]]

  /**
   * Returns a fiber that prefers the left hand side, but falls back to the
   * right hand side when the left fails.
   * */
  def orElse[E1 >: E, A1 >: A](that: Fiber[E1, A1]): Fiber[E1, A1] =
    new Fiber[E1, A1] {
      def await: IO[Nothing, Exit[E1, A1]] =
        self.await.zipWith(that.await) {
          case (Exit.Failure(_), e2) => e2
          case (e1, _)               => e1
        }

      def poll: IO[Nothing, Option[Exit[E1, A1]]] =
        self.poll.zipWith(that.poll)(_ orElse _)

      def interrupt: IO[Nothing, Exit[E1, A1]] =
        self.interrupt *> that.interrupt
    }

  /**
   * Zips this fiber with the specified fiber, combining their results using
   * the specified combiner function. Both joins and interruptions are performed
   * in sequential order from left to right.
   */
  final def zipWith[E1 >: E, B, C](that: => Fiber[E1, B])(f: (A, B) => C): Fiber[E1, C] =
    new Fiber[E1, C] {
      def await: UIO[Exit[E1, C]] =
        self.await.zipWith(that.await)(_.zipWith(_)(f, _ && _))

      def poll: UIO[Option[Exit[E1, C]]] =
        self.poll.zipWith(that.poll) {
          case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(f, _ && _))
          case _                    => None
        }

      def interrupt: UIO[Exit[E1, C]] = self.interrupt.zipWith(that.interrupt)(_.zipWith(_)(f, _ && _))
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
      def await: UIO[Exit[E, B]]        = self.await.map(_.map(f))
      def poll: UIO[Option[Exit[E, B]]] = self.poll.map(_.map(_.map(f)))
      def interrupt: UIO[Exit[E, B]]    = self.interrupt.map(_.map(f))
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
    executor: Executor
  )

  final val unit: Fiber[Nothing, Unit] = Fiber.succeedLazy(())

  final val never: Fiber[Nothing, Nothing] =
    new Fiber[Nothing, Nothing] {
      def await: UIO[Exit[Nothing, Nothing]]        = IO.never
      def poll: UIO[Option[Exit[Nothing, Nothing]]] = IO.succeed(None)
      def interrupt: UIO[Exit[Nothing, Nothing]]    = IO.never
    }

  final def done[E, A](exit: => Exit[E, A]): Fiber[E, A] =
    new Fiber[E, A] {
      def await: UIO[Exit[E, A]]        = IO.succeedLazy(exit)
      def poll: UIO[Option[Exit[E, A]]] = IO.succeedLazy(Some(exit))
      def interrupt: UIO[Exit[E, A]]    = IO.succeedLazy(exit)
    }

  final def fail[E](e: E): Fiber[E, Nothing] = done(Exit.fail(e))

  final def lift[E, A](io: IO[E, A]): IO[Nothing, Fiber[E, A]] =
    io.run.map(done(_))

  final def interrupt: Fiber[Nothing, Nothing] = done(Exit.interrupt)

  final def succeed[E, A](a: A): Fiber[E, A] = done(Exit.succeed(a))

  final def succeedLazy[E, A](a: => A): Fiber[E, A] = done(Exit.succeed(a))

  final def interruptAll(fs: Iterable[Fiber[_, _]]): UIO[Unit] =
    fs.foldLeft(IO.unit)((io, f) => io <* f.interrupt)

  final def joinAll(fs: Iterable[Fiber[_, _]]): UIO[Unit] =
    fs.foldLeft(IO.unit)((io, f) => io *> f.await.void)

  def fromFuture[A](ftr: Future[A]): Fiber[Throwable, A] =
    new Fiber[Throwable, A] {

      def await: UIO[Exit[Throwable, A]] = Task.fromFuture(_ => ftr).run

      def poll: UIO[Option[Exit[Throwable, A]]] = IO.sync(ftr.value.map(Exit.fromTry))

      def interrupt: UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)
    }
}
