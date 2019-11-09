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

package zio

import zio.internal.Executor

import scala.concurrent.Future
import com.github.ghik.silencer.silent

/**
 * A fiber is a lightweight thread of execution that never consumes more than a
 * whole thread (but may consume much less, depending on contention and
 * asynchronicity). Fibers are spawned by forking ZIO effects, which run
 * concurrently with the parent effect.
 *
 * Fibers can be joined, yielding their result to other fibers, or interrupted,
 * which terminates the fiber, safely releasing all resources.
 *
 * {{{
 * def parallel[A, B](io1: Task[A], io2: Task[B]): Task[(A, B)] =
 *   for {
 *     fiber1 <- io1.fork
 *     fiber2 <- io2.fork
 *     a      <- fiber1.join
 *     b      <- fiber2.join
 *   } yield (a, b)
 * }}}
 */
trait Fiber[+E, +A] { self =>

  /**
   * Zips this fiber and the specified fiber together, producing a tuple of their
   * output.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of that fiber
   * @return `Fiber[E1, (A, B)]` combined fiber
   */
  final def <*>[E1 >: E, B](that: => Fiber[E1, B]): Fiber[E1, (A, B)] =
    (self zipWith that)((a, b) => (a, b))

  /**
   * Same as `zip` but discards the output of the left hand side.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of the fiber
   * @return `Fiber[E1, B]` combined fiber
   */
  final def *>[E1 >: E, B](that: Fiber[E1, B]): Fiber[E1, B] =
    (self zipWith that)((_, b) => b)

  /**
   * Same as `zip` but discards the output of the right hand side.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of the fiber
   * @return `Fiber[E1, A]` combined fiber
   */
  final def <*[E1 >: E, B](that: Fiber[E1, B]): Fiber[E1, A] =
    (self zipWith that)((a, _) => a)

  /**
   * Maps the output of this fiber to the specified constant.
   *
   * @param b constant
   * @tparam B type of the fiber
   * @return `Fiber[E, B]` fiber mapped to constant
   */
  final def as[B](b: => B): Fiber[E, B] =
    self map (_ => b)

  /**
   * Awaits the fiber, which suspends the awaiting fiber until the result of the
   * fiber has been determined.
   *
   * @return `UIO[Exit[E, A]]`
   */
  def await: UIO[Exit[E, A]]

  @deprecated("use as", "1.0.0")
  final def const[B](b: => B): Fiber[E, B] = self as b

  /**
   * Inherits values from all [[FiberRef]] instances into current fiber.
   * This will resume immediately.
   *
   * @return `UIO[Unit]`
   */
  def inheritFiberRefs: UIO[Unit]

  /**
   * Interrupts the fiber from whichever fiber is calling this method. If the
   * fiber has already exited, the returned effect will resume immediately.
   * Otherwise, the effect will resume when the fiber exits.
   *
   * @return `UIO[Exit, E, A]]`
   */
  final def interrupt: UIO[Exit[E, A]] =
    ZIO.fiberId.flatMap(fiberId => self.interruptAs(fiberId))

  /**
   * Interrupts the fiber as if interrupted from the specified fiber. If the
   * fiber has already exited, the returned effect will resume immediately.
   * Otherwise, the effect will resume when the fiber exits.
   *
   * @return `UIO[Exit, E, A]]`
   */
  def interruptAs(fiberId: FiberId): UIO[Exit[E, A]]

  /**
   * Joins the fiber, which suspends the joining fiber until the result of the
   * fiber has been determined. Attempting to join a fiber that has errored will
   * result in a catchable error. Joining an interrupted fiber will result in an
   * "inner interruption" of this fiber, unlike interruption triggered by another
   * fiber, "inner interruption" can be catched and recovered.
   *
   * @return `IO[E, A]`
   */
  final def join: IO[E, A] = await.flatMap(IO.done) <* inheritFiberRefs

  /**
   * Maps over the value the Fiber computes.
   *
   * @param f mapping function
   * @tparam B result type of f
   * @return `Fiber[E, B]` mapped fiber
   */
  final def map[B](f: A => B): Fiber[E, B] =
    mapM(f andThen UIO.succeed)

  /**
   * Passes the success of this fiber to the specified callback, and continues
   * with the fiber that it returns.
   *
   * @param f The callback.
   * @tparam B The success value.
   * @return `Fiber[E, B]` The continued fiber.
   */
  final def mapFiber[E1 >: E, B](f: A => Fiber[E1, B]): UIO[Fiber[E1, B]] =
    self.await.map(_.fold(Fiber.halt(_), f))

  /**
   * Effectually maps over the value the fiber computes.
   */
  final def mapM[E1 >: E, B](f: A => IO[E1, B]): Fiber[E1, B] =
    new Fiber[E1, B] {
      final def await: UIO[Exit[E1, B]] =
        self.await.flatMap(_.foreach(f))
      final def inheritFiberRefs: UIO[Unit] =
        self.inheritFiberRefs
      final def interruptAs(id: FiberId): UIO[Exit[E1, B]] =
        self.interruptAs(id).flatMap(_.foreach(f))
      final def poll: UIO[Option[Exit[E1, B]]] =
        self.poll.flatMap(_.fold[UIO[Option[Exit[E1, B]]]](UIO.succeed(None))(_.foreach(f).map(Some(_))))
    }

  /**
   * Returns a fiber that prefers `this` fiber, but falls back to the
   * `that` one when `this` one fails. Interrupting the returned fiber
   * will interrupt both fibers, sequentially, from left to right.
   *
   * @param that fiber to fall back to
   * @tparam E1 error type
   * @tparam A1 type of the other fiber
   * @return `Fiber[E1, A1]`
   */
  def orElse[E1 >: E, A1 >: A](that: Fiber[E1, A1]): Fiber[E1, A1] =
    new Fiber[E1, A1] {
      final def await: UIO[Exit[E1, A1]] =
        self.await.zipWith(that.await) {
          case (Exit.Failure(_), e2) => e2
          case (e1, _)               => e1
        }

      final def poll: UIO[Option[Exit[E1, A1]]] =
        self.poll.zipWith(that.poll)(_ orElse _)

      final def interruptAs(id: FiberId): UIO[Exit[E1, A1]] =
        self.interruptAs(id) *> that.interruptAs(id)

      final def inheritFiberRefs: UIO[Unit] =
        that.inheritFiberRefs *> self.inheritFiberRefs
    }

  /**
   * Returns a fiber that prefers `this` fiber, but falls back to the
   * `that` one when `this` one fails. Interrupting the returned fiber
   * will interrupt both fibers, sequentially, from left to right.
   *
   * @param that fiber to fall back to
   * @tparam E1 error type
   * @tparam B type of the other fiber
   * @return `Fiber[E1, B]`
   */
  def orElseEither[E1 >: E, B](that: Fiber[E1, B]): Fiber[E1, Either[A, B]] =
    (self map (Left(_))) orElse (that map (Right(_)))

  /**
   * Tentatively observes the fiber, but returns immediately if it is not already done.
   *
   * @return `UIO[Option[Exit, E, A]]]`
   */
  def poll: UIO[Option[Exit[E, A]]]

  /**
   * Converts this fiber into a [[scala.concurrent.Future]].
   *
   * @param ev implicit witness that E is a subtype of Throwable
   * @return `UIO[Future[A]]`
   */
  final def toFuture(implicit ev: E <:< Throwable): UIO[CancelableFuture[E, A]] =
    self toFutureWith ev

  /**
   * Converts this fiber into a [[scala.concurrent.Future]], translating
   * any errors to [[java.lang.Throwable]] with the specified conversion function.
   *
   * @param f function to the error into a Throwable
   * @return `UIO[Future[A]]`
   */
  final def toFutureWith(f: E => Throwable): UIO[CancelableFuture[E, A]] =
    UIO.effectTotal {
      val p: concurrent.Promise[A] = scala.concurrent.Promise[A]()

      def failure(cause: Cause[E]): UIO[p.type] = UIO(p.failure(cause.squashWith(f)))
      def success(value: A): UIO[p.type]        = UIO(p.success(value))

      ZIO.runtime[Any].map { runtime =>
        new CancelableFuture[E, A](p.future) {
          def cancel: Future[Exit[E, A]] = runtime.unsafeRunToFuture(interrupt)
        }
      } <* self.await
        .flatMap[Any, Nothing, p.type](_.foldM[Any, Nothing, p.type](failure, success))
        .fork
    }.flatten

  /**
   * Converts this fiber into a [[zio.ZManaged]]. Fiber is interrupted on release.
   *
   * @return `ZManaged[Any, Nothing, Fiber[E, A]]`
   */
  final def toManaged: ZManaged[Any, Nothing, Fiber[E, A]] =
    ZManaged.make(UIO.succeed(self))(_.interrupt)

  /**
   * Maps the output of this fiber to `()`.
   *
   * @return `Fiber[E, Unit]` fiber mapped to `()`
   */
  final def unit: Fiber[E, Unit] = as(())

  /**
   * Maps the output of this fiber to `()`.
   */
  @deprecated("use unit", "1.0.0")
  final def void: Fiber[E, Unit] = unit

  /**
   * Named alias for `<*>`.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of that fiber
   * @return `Fiber[E1, (A, B)]` combined fiber
   */
  final def zip[E1 >: E, B](that: => Fiber[E1, B]): Fiber[E1, (A, B)] =
    self <*> that

  /**
   * Named alias for `<*`.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of the fiber
   * @return `Fiber[E1, A]` combined fiber
   */
  final def zipLeft[E1 >: E, B](that: Fiber[E1, B]): Fiber[E1, A] =
    self <* that

  /**
   * Named alias for `*>`.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of the fiber
   * @return `Fiber[E1, B]` combined fiber
   */
  final def zipRight[E1 >: E, B](that: Fiber[E1, B]): Fiber[E1, B] =
    self *> that

  /**
   * Zips this fiber with the specified fiber, combining their results using
   * the specified combiner function. Both joins and interruptions are performed
   * in sequential order from left to right.
   *
   * @param that fiber to be zipped
   * @param f function to combine the results of both fibers
   * @tparam E1 error type
   * @tparam B type of that fiber
   * @tparam C type of the resulting fiber
   * @return `Fiber[E1, C]` combined fiber
   */
  final def zipWith[E1 >: E, B, C](that: => Fiber[E1, B])(f: (A, B) => C): Fiber[E1, C] =
    new Fiber[E1, C] {
      def await: UIO[Exit[E1, C]] =
        self.await.flatMap(IO.done).zipWithPar(that.await.flatMap(IO.done))(f).run

      def poll: UIO[Option[Exit[E1, C]]] =
        self.poll.zipWith(that.poll) {
          case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(f, _ && _))
          case _                    => None
        }

      def interruptAs(id: FiberId): UIO[Exit[E1, C]] =
        (self interruptAs id).zipWith(that interruptAs id)(_.zipWith(_)(f, _ && _))

      def inheritFiberRefs: UIO[Unit] = that.inheritFiberRefs *> self.inheritFiberRefs
    }
}

object Fiber {

  /**
   * A record containing information about a [[Fiber]].
   *
   * @param id            The fiber's unique identifier
   * @param interruptors  The set of fibers attempting to interrupt the fiber or its ancestors.
   * @param executor      The [[zio.internal.Executor]] executing this fiber
   * @param children      The fiber's forked children. This will only be populated if the fiber is supervised (via [[ZIO#supervised]]).
   */
  final case class Descriptor(
    id: FiberId,
    status: Status,
    interruptors: Set[FiberId],
    interruptStatus: InterruptStatus,
    children: Set[Fiber[Any, Any]],
    executor: Executor
  )

  sealed trait Status extends Serializable with Product
  object Status {
    case object Done                                                extends Status
    case object Running                                             extends Status
    final case class Suspended(interruptible: Boolean, epoch: Long) extends Status
  }

  /**
   * Awaits on all fibers to be completed, successfully or not.
   *
   * @param fs `Iterable` of fibers to be awaited
   * @return `UIO[Unit]`
   */
  final def awaitAll(fs: Iterable[Fiber[Any, Any]]): UIO[Unit] =
    fs.foldLeft[Fiber[Any, Any]](Fiber.unit)(_ *> _).await.unit

  /**
   * Collects all fibers into a single fiber producing an in-order list of the
   * results.
   */
  final def collectAll[E, A](fibers: Iterable[Fiber[E, A]]): Fiber[E, List[A]] =
    fibers.foldRight[Fiber[E, List[A]]](Fiber.succeed(Nil)) {
      case (fiber, acc) => fiber.zipWith(acc)(_ :: _)
    }

  /**
   * A fiber that is done with the specified [[zio.Exit]] value.
   *
   * @param exit [[zio.Exit]] value
   * @tparam E error type
   * @tparam A type of the fiber
   * @return `Fiber[E, A]`
   */
  final def done[E, A](exit: => Exit[E, A]): Fiber[E, A] =
    new Fiber[E, A] {
      final def await: UIO[Exit[E, A]]                    = IO.succeed(exit)
      final def poll: UIO[Option[Exit[E, A]]]             = IO.succeed(Some(exit))
      final def interruptAs(id: FiberId): UIO[Exit[E, A]] = IO.succeed(exit)
      final def inheritFiberRefs: UIO[Unit]               = IO.unit
    }

  /**
   * A fiber that has already failed with the specified value.
   *
   * @param e failure value
   * @tparam E error type
   * @return `Fiber[E, Nothing]` failed fiber
   */
  final def fail[E](e: E): Fiber[E, Nothing] = done(Exit.fail(e))

  /**
   * Lifts an [[zio.IO]] into a `Fiber`.
   *
   * @param io `IO[E, A]` to turn into a `Fiber`
   * @tparam E error type
   * @tparam A type of the fiber
   * @return `UIO[Fiber[E, A]]`
   */
  final def fromEffect[E, A](io: IO[E, A]): UIO[Fiber[E, A]] =
    io.run.map(done(_))

  /**
   * Returns a `Fiber` that is backed by the specified `Future`.
   *
   * @param thunk `Future[A]` backing the `Fiber`
   * @tparam A type of the `Fiber`
   * @return `Fiber[Throwable, A]`
   */
  final def fromFuture[A](thunk: => Future[A]): Fiber[Throwable, A] =
    new Fiber[Throwable, A] {
      lazy val ftr: Future[A] = thunk

      final def await: UIO[Exit[Throwable, A]] = Task.fromFuture(_ => ftr).run

      final def poll: UIO[Option[Exit[Throwable, A]]] = IO.effectTotal(ftr.value.map(Exit.fromTry))

      final def interruptAs(id: FiberId): UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)

      final def inheritFiberRefs: UIO[Unit] = IO.unit
    }

  /**
   * Creates a `Fiber` that is halted with the specified cause.
   */
  final def halt[E](cause: Cause[E]): Fiber[E, Nothing] = done(Exit.halt(cause))

  /**
   * Interrupts all fibers, awaiting their interruption.
   *
   * @param fs `Iterable` of fibers to be interrupted
   * @return `UIO[Unit]`
   */
  final def interruptAll(fs: Iterable[Fiber[Any, Any]]): UIO[Unit] =
    ZIO.fiberId.flatMap(interruptAllAs(_)(fs))

  /**
   * Interrupts all fibers as by the specified fiber, awaiting their interruption.
   *
   * @param fiberId The identity of the fiber to interrupt as.
   * @param fs `Iterable` of fibers to be interrupted
   * @return `UIO[Unit]`
   */
  final def interruptAllAs(fiberId: FiberId)(fs: Iterable[Fiber[Any, Any]]): UIO[Unit] =
    fs.foldLeft(IO.unit)((io, f) => io <* f.interruptAs(fiberId))

  /**
   * A fiber that is already interrupted.
   *
   * @return `Fiber[Nothing, Nothing]` interrupted fiber
   */
  final def interruptAs(id: FiberId): Fiber[Nothing, Nothing] = done(Exit.interrupt(id))

  /**
   * Joins all fibers, awaiting their _successful_ completion.
   * Attempting to join a fiber that has errored will result in
   * a catchable error, _if_ that error does not result from interruption.
   *
   * @param fs `Iterable` of fibers to be joined
   * @return `UIO[Unit]`
   */
  final def joinAll[E](fs: Iterable[Fiber[E, Any]]): IO[E, Unit] =
    fs.foldLeft[Fiber[E, Any]](Fiber.unit)(_ *> _).join.unit.refailWithTrace

  /**
   * A fiber that never fails or succeeds.
   */
  final val never: Fiber[Nothing, Nothing] =
    new Fiber[Nothing, Nothing] {
      final def await: UIO[Exit[Nothing, Nothing]]                    = IO.never
      final def poll: UIO[Option[Exit[Nothing, Nothing]]]             = IO.succeed(None)
      final def interruptAs(id: FiberId): UIO[Exit[Nothing, Nothing]] = IO.never
      final def inheritFiberRefs: UIO[Unit]                           = IO.unit
    }

  /**
   * The root fibers.
   */
  final val roots: UIO[Set[Fiber[_, _]]] = UIO {
    import scala.collection.JavaConverters._

    _rootFibers.asScala.asInstanceOf[Set[Fiber[_, _]]]: @silent("JavaConverters")
  }

  /**
   * Returns a fiber that has already succeeded with the specified value.
   *
   * @param a success value
   * @tparam E error type
   * @tparam A type of the fiber
   * @return `Fiber[E, A]` succeeded fiber
   */
  final def succeed[A](a: A): Fiber[Nothing, A] = done(Exit.succeed(a))

  @deprecated("use succeed", "1.0.0")
  final def succeedLazy[E, A](a: => A): Fiber[E, A] =
    succeed(a)

  /**
   * A fiber that has already succeeded with unit.
   */
  final val unit: Fiber[Nothing, Unit] = Fiber.succeed(())

  /**
   * Retrieves the fiber currently executing on this thread, if any. This will
   * always be `None` unless called from within an executing effect.
   */
  final def unsafeCurrentFiber(): Option[Fiber[Any, Any]] =
    Option(_currentFiber.get)

  private[zio] val _currentFiber: ThreadLocal[internal.FiberContext[_, _]] =
    new ThreadLocal[internal.FiberContext[_, _]]()

  private[zio] val _rootFibers: java.util.Set[internal.FiberContext[_, _]] =
    internal.Platform.newConcurrentSet[internal.FiberContext[_, _]]()
}
