/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import com.github.ghik.silencer.silent

import zio.console.Console
import zio.internal.stacktracer.ZTraceElement
import zio.internal.{ Executor, FiberRenderer }

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
sealed trait Fiber[+E, +A] { self =>

  /**
   * Zips this fiber and the specified fiber together, producing a tuple of their
   * output.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of that fiber
   * @return `Fiber[E1, (A, B)]` combined fiber
   */
  final def <*>[E1 >: E, B](that: => Fiber[E1, B]): Fiber.Synthetic[E1, (A, B)] =
    (self zipWith that)((a, b) => (a, b))

  /**
   * Same as `zip` but discards the output of the left hand side.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of the fiber
   * @return `Fiber[E1, B]` combined fiber
   */
  final def *>[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, B] =
    (self zipWith that)((_, b) => b)

  /**
   * Same as `zip` but discards the output of the right hand side.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of the fiber
   * @return `Fiber[E1, A]` combined fiber
   */
  final def <*[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, A] =
    (self zipWith that)((a, _) => a)

  /**
   * Maps the output of this fiber to the specified constant.
   *
   * @param b constant
   * @tparam B type of the fiber
   * @return `Fiber[E, B]` fiber mapped to constant
   */
  final def as[B](b: => B): Fiber.Synthetic[E, B] =
    self map (_ => b)

  /**
   * Awaits the fiber, which suspends the awaiting fiber until the result of the
   * fiber has been determined.
   *
   * @return `UIO[Exit[E, A]]`
   */
  def await: UIO[Exit[E, A]]

  /**
   * Children of the fiber.
   */
  def children: UIO[Iterable[Fiber.Runtime[Any, Any]]]

  /**
   * Descendants of the fiber (children and their children, recursively).
   */
  def descendants: UIO[Iterable[Fiber[Any, Any]]] =
    children.flatMap(children => ZIO.foreach(children)(_.descendants).map(collected => children ++ collected.flatten))

  /**
   * Disowns the fiber from the fiber calling this method.
   */
  final def disown: UIO[Boolean] = ZIO.disown(self)

  /**
   * Folds over the runtime or synthetic fiber.
   */
  final def fold[Z](
    runtime: Fiber.Runtime[E, A] => Z,
    synthetic: Fiber.Synthetic[E, A] => Z
  ): Z =
    self match {
      case fiber: Fiber.Runtime[E, A]   => runtime(fiber)
      case fiber: Fiber.Synthetic[E, A] => synthetic(fiber)
    }

  /**
   * Gets the value of the fiber ref for this fiber, or the initial value of
   * the fiber ref, if the fiber is not storing the ref.
   */
  def getRef[A](ref: FiberRef[A]): UIO[A]

  /**
   * Inherits values from all [[FiberRef]] instances into current fiber.
   * This will resume immediately.
   *
   * @return `UIO[Unit]`
   */
  def inheritRefs: UIO[Unit]

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
  def interruptAs(fiberId: Fiber.Id): UIO[Exit[E, A]]

  /**
   * Interrupts the fiber from whichever fiber is calling this method. The
   * interruption will happen in a separate daemon fiber, and the returned
   * effect will always resume immediately without waiting.
   *
   * @return `UIO[Unit]`
   */
  final def interruptFork: UIO[Unit] = interrupt.forkDaemon.unit

  /**
   * Joins the fiber, which suspends the joining fiber until the result of the
   * fiber has been determined. Attempting to join a fiber that has errored will
   * result in a catchable error. Joining an interrupted fiber will result in an
   * "inner interruption" of this fiber, unlike interruption triggered by another
   * fiber, "inner interruption" can be caught and recovered.
   *
   * @return `IO[E, A]`
   */
  final def join: IO[E, A] = await.flatMap(IO.done(_)) <* inheritRefs

  /**
   * Maps over the value the Fiber computes.
   *
   * @param f mapping function
   * @tparam B result type of f
   * @return `Fiber[E, B]` mapped fiber
   */
  final def map[B](f: A => B): Fiber.Synthetic[E, B] =
    mapM(f andThen UIO.succeedNow)

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
  final def mapM[E1 >: E, B](f: A => IO[E1, B]): Fiber.Synthetic[E1, B] =
    new Fiber.Synthetic[E1, B] {
      final def await: UIO[Exit[E1, B]] =
        self.await.flatMap(_.foreach(f))
      final def children: UIO[Iterable[Fiber.Runtime[Any, Any]]] = self.children
      final def getRef[A](ref: FiberRef[A]): UIO[A]              = self.getRef(ref)
      final def inheritRefs: UIO[Unit] =
        self.inheritRefs
      final def interruptAs(id: Fiber.Id): UIO[Exit[E1, B]] =
        self.interruptAs(id).flatMap(_.foreach(f))
      final def poll: UIO[Option[Exit[E1, B]]] =
        self.poll.flatMap(_.fold[UIO[Option[Exit[E1, B]]]](UIO.succeedNow(None))(_.foreach(f).map(Some(_))))
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
  def orElse[E1 >: E, A1 >: A](that: => Fiber[E1, A1]): Fiber.Synthetic[E1, A1] =
    new Fiber.Synthetic[E1, A1] {
      final def await: UIO[Exit[E1, A1]] =
        self.await.zipWith(that.await) {
          case (Exit.Failure(_), e2) => e2
          case (e1, _)               => e1
        }

      final def children: UIO[Iterable[Fiber.Runtime[Any, Any]]] = (self.children zipWith that.children)(_ ++ _)

      final def getRef[A](ref: FiberRef[A]): UIO[A] =
        for {
          first  <- self.getRef(ref)
          second <- self.getRef(ref)
        } yield if (first == ref.initial) second else first

      final def interruptAs(id: Fiber.Id): UIO[Exit[E1, A1]] =
        self.interruptAs(id) *> that.interruptAs(id)

      final def inheritRefs: UIO[Unit] =
        that.inheritRefs *> self.inheritRefs

      final def poll: UIO[Option[Exit[E1, A1]]] =
        self.poll.zipWith(that.poll)(_ orElse _)
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
  final def orElseEither[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, Either[A, B]] =
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
  final def toFuture(implicit ev: E <:< Throwable): UIO[CancelableFuture[A]] =
    self toFutureWith ev

  /**
   * Converts this fiber into a [[scala.concurrent.Future]], translating
   * any errors to [[java.lang.Throwable]] with the specified conversion function,
   * using [[Cause.squashTraceWith]]
   *
   * @param f function to the error into a Throwable
   * @return `UIO[Future[A]]`
   */
  final def toFutureWith(f: E => Throwable): UIO[CancelableFuture[A]] =
    UIO.effectSuspendTotal {
      val p: concurrent.Promise[A] = scala.concurrent.Promise[A]()

      def failure(cause: Cause[E]): UIO[p.type] = UIO(p.failure(cause.squashTraceWith(f)))
      def success(value: A): UIO[p.type]        = UIO(p.success(value))

      val completeFuture =
        self.await.flatMap(_.foldM[Any, Nothing, p.type](failure, success))

      for {
        runtime <- ZIO.runtime[Any]
        _       <- completeFuture.forkDaemon // Cannot afford to NOT complete the promise, no matter what, so we fork daemon
      } yield new CancelableFuture[A](p.future) {
        def cancel(): Future[Exit[Throwable, A]] =
          runtime.unsafeRunToFuture[Nothing, Exit[Throwable, A]](self.interrupt.map(_.mapError(f)))
      }
    }.uninterruptible

  /**
   * Converts this fiber into a [[zio.ZManaged]]. Fiber is interrupted on release.
   *
   * @return `ZManaged[Any, Nothing, Fiber[E, A]]`
   */
  final def toManaged: ZManaged[Any, Nothing, Fiber[E, A]] =
    ZManaged.make(UIO.succeedNow(self))(_.interrupt)

  /**
   * Maps the output of this fiber to `()`.
   *
   * @return `Fiber[E, Unit]` fiber mapped to `()`
   */
  final def unit: Fiber.Synthetic[E, Unit] = as(())

  /**
   * Named alias for `<*>`.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of that fiber
   * @return `Fiber[E1, (A, B)]` combined fiber
   */
  final def zip[E1 >: E, B](that: => Fiber[E1, B]): Fiber.Synthetic[E1, (A, B)] =
    self <*> that

  /**
   * Named alias for `<*`.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of the fiber
   * @return `Fiber[E1, A]` combined fiber
   */
  final def zipLeft[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, A] =
    self <* that

  /**
   * Named alias for `*>`.
   *
   * @param that fiber to be zipped
   * @tparam E1 error type
   * @tparam B type of the fiber
   * @return `Fiber[E1, B]` combined fiber
   */
  final def zipRight[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, B] =
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
  final def zipWith[E1 >: E, B, C](that: => Fiber[E1, B])(f: (A, B) => C): Fiber.Synthetic[E1, C] =
    new Fiber.Synthetic[E1, C] {
      final def await: UIO[Exit[E1, C]] =
        self.await.flatMap(IO.done(_)).zipWithPar(that.await.flatMap(IO.done(_)))(f).run

      final def children: UIO[Iterable[Fiber.Runtime[Any, Any]]] = (self.children zipWith that.children)(_ ++ _)

      final def getRef[A](ref: FiberRef[A]): UIO[A] =
        (self.getRef(ref) zipWith that.getRef(ref))(ref.combine(_, _))

      final def interruptAs(id: Fiber.Id): UIO[Exit[E1, C]] =
        (self interruptAs id).zipWith(that interruptAs id)(_.zipWith(_)(f, _ && _))

      final def inheritRefs: UIO[Unit] = that.inheritRefs *> self.inheritRefs

      final def poll: UIO[Option[Exit[E1, C]]] =
        self.poll.zipWith(that.poll) {
          case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(f, _ && _))
          case _                    => None
        }
    }
}

object Fiber extends FiberPlatformSpecific {

  /**
   * A runtime fiber that is executing an effect. Runtime fibers ave an
   * identity and a trace.
   */
  sealed trait Runtime[+E, +A] extends Fiber[E, A] { self =>

    /**
     * Generates a fiber dump.
     */
    final def dumpWith(withTrace: Boolean): UIO[Fiber.Dump] =
      for {
        name       <- self.getRef(Fiber.fiberName)
        id         <- self.id
        status     <- self.status
        trace      <- if (withTrace) self.trace.map(Some(_)) else UIO(None)
        ch         <- self.children
        childDumps <- ZIO.foreach(ch)(_.dumpWith(withTrace))
      } yield Fiber.Dump(id, name, status, childDumps, trace)

    /**
     * Generates a fiber dump with optionally excluded stacktraces.
     */
    final def dump: UIO[Fiber.Dump] = dumpWith(true)

    /**
     * The identity of the fiber.
     */
    def id: UIO[Fiber.Id]

    /**
     * The status of the fiber.
     */
    def status: UIO[Fiber.Status]

    /**
     * The trace of the fiber.
     */
    def trace: UIO[ZTrace]
  }

  private[zio] object Runtime {
    trait Internal[+E, +A] extends Runtime[E, A]
  }

  /**
   * A synthetic fiber that is created from a pure value or that combines
   * existing fibers.
   */
  sealed trait Synthetic[+E, +A] extends Fiber[E, A] {}

  private[zio] object Synthetic {
    trait Internal[+E, +A] extends Synthetic[E, A]
  }

  /**
   * A record containing information about a [[Fiber]].
   *
   * @param id            The fiber's unique identifier
   * @param interruptors  The set of fibers attempting to interrupt the fiber or its ancestors.
   * @param executor      The [[zio.internal.Executor]] executing this fiber
   * @param children      The fiber's forked children.
   */
  final case class Descriptor(
    id: Fiber.Id,
    status: Status,
    interruptors: Set[Fiber.Id],
    interruptStatus: InterruptStatus,
    children: UIO[Iterable[Fiber.Runtime[Any, Any]]],
    executor: Executor
  )

  final case class Dump(
    fiberId: Fiber.Id,
    fiberName: Option[String],
    status: Status,
    children: Iterable[Dump],
    trace: Option[ZTrace]
  ) extends Serializable {

    /**
     * {{{
     * "Fiber Name" #432 (16m2s) waiting on fiber #283
     *    Status: Suspended (interruptible, 12 asyncs, ...)
     *     at ...
     *     at ...
     *     at ...
     *     at ...
     * }}}
     */
    def prettyPrintM: UIO[String] = FiberRenderer.prettyPrintM(this)
  }

  /**
   * The identity of a Fiber, described by the time it began life, and a
   * monotonically increasing sequence number generated from an atomic counter.
   */
  final case class Id(startTimeMillis: Long, seqNumber: Long) extends Serializable
  object Id {

    /**
     * A sentinel value to indicate a fiber without identity.
     */
    final val None = Id(0L, 0L)
  }

  sealed trait Status extends Serializable with Product { self =>
    import Status._

    final def toFinishing: Status = self match {
      case Done                            => Done
      case Finishing(interrupting)         => Finishing(interrupting)
      case Running(interrupting)           => Running(interrupting)
      case Suspended(previous, _, _, _, _) => previous.toFinishing
    }

    final def withInterrupting(b: Boolean): Status = self match {
      case Done                         => Done
      case Finishing(_)                 => Finishing(b)
      case Running(_)                   => Running(b)
      case v @ Suspended(_, _, _, _, _) => v.copy(previous = v.previous.withInterrupting(b))
    }
  }
  object Status {
    case object Done                                  extends Status
    final case class Finishing(interrupting: Boolean) extends Status
    final case class Running(interrupting: Boolean)   extends Status
    final case class Suspended(
      previous: Status,
      interruptible: Boolean,
      epoch: Long,
      blockingOn: List[Fiber.Id],
      asyncTrace: Option[ZTraceElement]
    ) extends Status
  }

  /**
   * Awaits on all fibers to be completed, successfully or not.
   *
   * @param fs `Iterable` of fibers to be awaited
   * @return `UIO[Unit]`
   */
  def awaitAll(fs: Iterable[Fiber[Any, Any]]): UIO[Unit] =
    collectAll(fs).await.unit

  /**
   * Collects all fibers into a single fiber producing an in-order list of the
   * results.
   */
  def collectAll[E, A](fibers: Iterable[Fiber[E, A]]): Fiber.Synthetic[E, List[A]] =
    new Fiber.Synthetic[E, List[A]] {
      def await: UIO[Exit[E, List[A]]] =
        IO.foreachPar(fibers)(_.await.flatMap(IO.done(_))).run
      def children: UIO[Iterable[Fiber.Runtime[Any, Any]]] =
        UIO.foreach(fibers)(_.children).map(_.foldRight(Iterable.empty[Fiber.Runtime[Any, Any]])(_ ++ _))
      def getRef[A](ref: FiberRef[A]): UIO[A] =
        UIO.foreach(fibers)(_.getRef(ref)).map(_.foldRight(ref.initial)(ref.combine))
      def inheritRefs: UIO[Unit] =
        UIO.foreach_(fibers)(_.inheritRefs)
      def interruptAs(fiberId: Fiber.Id): UIO[Exit[E, List[A]]] =
        UIO
          .foreach(fibers)(_.interruptAs(fiberId))
          .map(_.foldRight[Exit[E, List[A]]](Exit.succeed(Nil))(_.zipWith(_)(_ :: _, _ && _)))
      def poll: UIO[Option[Exit[E, List[A]]]] =
        UIO
          .foreach(fibers)(_.poll)
          .map(_.foldRight[Option[Exit[E, List[A]]]](Some(Exit.succeed(Nil))) {
            case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(_ :: _, _ && _))
            case _                    => None
          })
    }

  /**
   * A fiber that is done with the specified [[zio.Exit]] value.
   *
   * @param exit [[zio.Exit]] value
   * @tparam E error type
   * @tparam A type of the fiber
   * @return `Fiber[E, A]`
   */
  def done[E, A](exit: => Exit[E, A]): Fiber.Synthetic[E, A] =
    new Fiber.Synthetic[E, A] {
      final def await: UIO[Exit[E, A]]                           = IO.succeedNow(exit)
      final def children: UIO[Iterable[Fiber.Runtime[Any, Any]]] = UIO(Nil)
      final def getRef[A](ref: FiberRef[A]): UIO[A]              = UIO(ref.initial)
      final def interruptAs(id: Fiber.Id): UIO[Exit[E, A]]       = IO.succeedNow(exit)
      final def inheritRefs: UIO[Unit]                           = IO.unit
      final def poll: UIO[Option[Exit[E, A]]]                    = IO.succeedNow(Some(exit))
    }

  /**
   * Collects a complete dump of all fibers. This could potentially be quite large.
   *
   * TODO: Switch to "streaming lazy" version.
   */
  @silent("JavaConverters")
  val dumpAll: UIO[Iterable[Dump]] =
    UIO.effectSuspendTotal {
      dump(internal.Sync(rootFibers)(rootFibers.asScala.toList): _*)
    }

  /**
   * Collects a complete dump of the specified fibers and all children of the
   * fibers.
   */
  def dump(fibers: Fiber.Runtime[_, _]*): UIO[Iterable[Dump]] =
    ZIO.foreach(fibers)(f => f.dump)

  /**
   * Collects a complete dump of the specified fibers and all children of the
   * fibers and renders it as a string.
   */
  def dumpStr(fibers: Fiber.Runtime[_, _]*): UIO[String] =
    FiberRenderer.dumpStr(fibers, true)

  /**
   * Collects a complete dump of all fibers and all children of the
   * fibers and renders it as a string.
   */
  lazy val dumpAllStr: UIO[String] =
    dumpStr(internal.Sync(rootFibers)(rootFibers.asScala.toList): _*)

  /**
   * A fiber that has already failed with the specified value.
   *
   * @param e failure value
   * @tparam E error type
   * @return `Fiber[E, Nothing]` failed fiber
   */
  def fail[E](e: E): Fiber.Synthetic[E, Nothing] = done(Exit.fail(e))

  /**
   * A `FiberRef` that stores the name of the fiber, which defaults to `None`.
   */
  val fiberName: FiberRef[Option[String]] = new FiberRef(None, (old, _) => old)

  /**
   * Lifts an [[zio.IO]] into a `Fiber`.
   *
   * @param io `IO[E, A]` to turn into a `Fiber`
   * @tparam E error type
   * @tparam A type of the fiber
   * @return `UIO[Fiber[E, A]]`
   */
  def fromEffect[E, A](io: IO[E, A]): UIO[Fiber.Synthetic[E, A]] =
    io.run.map(done(_))

  /**
   * Returns a `Fiber` that is backed by the specified `Future`.
   *
   * @param thunk `Future[A]` backing the `Fiber`
   * @tparam A type of the `Fiber`
   * @return `Fiber[Throwable, A]`
   */
  def fromFuture[A](thunk: => Future[A]): Fiber.Synthetic[Throwable, A] =
    new Fiber.Synthetic[Throwable, A] {
      lazy val ftr: Future[A] = thunk

      def await: UIO[Exit[Throwable, A]] = Task.fromFuture(_ => ftr).run

      def children: UIO[Iterable[Fiber.Runtime[Any, Any]]] = UIO(Nil)

      def getRef[A](ref: FiberRef[A]): UIO[A] = UIO(ref.initial)

      def interruptAs(id: Fiber.Id): UIO[Exit[Throwable, A]] =
        UIO.effectSuspendTotal {
          ftr match {
            case c: CancelableFuture[A] => ZIO.fromFuture(implicit ec => c.cancel()).orDie
            case _                      => join.fold(Exit.fail, Exit.succeed)
          }
        }

      def inheritRefs: UIO[Unit] = IO.unit

      def poll: UIO[Option[Exit[Throwable, A]]] = IO.effectTotal(ftr.value.map(Exit.fromTry))
    }

  /**
   * Creates a `Fiber` that is halted with the specified cause.
   */
  def halt[E](cause: Cause[E]): Fiber.Synthetic[E, Nothing] = done(Exit.halt(cause))

  /**
   * Interrupts all fibers, awaiting their interruption.
   *
   * @param fs `Iterable` of fibers to be interrupted
   * @return `UIO[Unit]`
   */
  def interruptAll(fs: Iterable[Fiber[Any, Any]]): UIO[Unit] =
    ZIO.fiberId.flatMap(interruptAllAs(_)(fs))

  /**
   * Interrupts all fibers as by the specified fiber, awaiting their interruption.
   *
   * @param fiberId The identity of the fiber to interrupt as.
   * @param fs `Iterable` of fibers to be interrupted
   * @return `UIO[Unit]`
   */
  def interruptAllAs(fiberId: Fiber.Id)(fs: Iterable[Fiber[Any, Any]]): UIO[Unit] =
    fs.foldLeft(IO.unit)((io, f) => io <* f.interruptAs(fiberId))

  /**
   * A fiber that is already interrupted.
   *
   * @return `Fiber[Nothing, Nothing]` interrupted fiber
   */
  def interruptAs(id: Fiber.Id): Fiber.Synthetic[Nothing, Nothing] =
    done(Exit.interrupt(id))

  /**
   * Joins all fibers, awaiting their _successful_ completion.
   * Attempting to join a fiber that has errored will result in
   * a catchable error, _if_ that error does not result from interruption.
   *
   * @param fs `Iterable` of fibers to be joined
   * @return `UIO[Unit]`
   */
  def joinAll[E](fs: Iterable[Fiber[E, Any]]): IO[E, Unit] =
    collectAll(fs).join.unit.refailWithTrace

  /**
   * A fiber that never fails or succeeds.
   */
  val never: Fiber.Synthetic[Nothing, Nothing] =
    new Fiber.Synthetic[Nothing, Nothing] {
      def await: UIO[Exit[Nothing, Nothing]]                     = IO.never
      def children: UIO[Iterable[Fiber.Runtime[Any, Any]]]       = UIO(Nil)
      def getRef[A](ref: FiberRef[A]): UIO[A]                    = UIO(ref.initial)
      def interruptAs(id: Fiber.Id): UIO[Exit[Nothing, Nothing]] = IO.never
      def inheritRefs: UIO[Unit]                                 = IO.unit
      def poll: UIO[Option[Exit[Nothing, Nothing]]]              = IO.succeedNow(None)
    }

  /**
   * Collects a complete dump of the specified fibers and all children of the
   * fibers and renders it to the console.
   */
  def putDumpStr(label: String, fibers: Fiber.Runtime[_, _]*): URIO[Console, Unit] =
    dumpStr(fibers: _*).flatMap(str => console.putStrLn(s"$label: ${str}"))

  /**
   * The root fibers.
   */
  val roots: UIO[Set[Fiber[Any, Any]]] = UIO {
    internal.Sync(rootFibers)(rootFibers.asScala.toSet[Fiber[Any, Any]].filterNot(_ eq null): @silent("JavaConverters"))
  }

  /**
   * Returns a fiber that has already succeeded with the specified value.
   *
   * @param a success value
   * @tparam E error type
   * @tparam A type of the fiber
   * @return `Fiber[E, A]` succeeded fiber
   */
  def succeed[A](a: A): Fiber.Synthetic[Nothing, A] =
    done(Exit.succeed(a))

  /**
   * A fiber that has already succeeded with unit.
   */
  val unit: Fiber.Synthetic[Nothing, Unit] =
    Fiber.succeed(())

  /**
   * Retrieves the fiber currently executing on this thread, if any. This will
   * always be `None` unless called from within an executing effect.
   */
  def unsafeCurrentFiber(): Option[Fiber[Any, Any]] =
    Option(_currentFiber.get)

  private[zio] def newFiberId(): Fiber.Id = Fiber.Id(System.currentTimeMillis(), _fiberCounter.getAndIncrement())

  private[zio] def untrack[E, A](context: internal.FiberContext[E, A]): Boolean =
    if (context ne null) Fiber.rootFibers.remove(context)
    else false

  private[zio] def track[E, A](context: internal.FiberContext[E, A]): Unit =
    if (context ne null) {
      Fiber.rootFibers.add(context)

      // On the JVM, rely on garbage collection of the weak set to clean things up:
      if (!internal.Platform.isJVM) context.onDone(_ => Fiber.rootFibers.remove(context))
    }

  private[zio] val _currentFiber: ThreadLocal[internal.FiberContext[_, _]] =
    new ThreadLocal[internal.FiberContext[_, _]]()

  private type RootFibers = java.util.Set[internal.FiberContext[_, _]]

  private val rootFibers: RootFibers =
    internal.Platform.newConcurrentWeakSet[internal.FiberContext[_, _]]()

  private[zio] val _fiberCounter = new java.util.concurrent.atomic.AtomicLong(0)
}
