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

import scala.collection.JavaConverters._
import scala.concurrent.Future

import com.github.ghik.silencer.silent

import zio.internal.Executor
import zio.internal.stacktracer.ZTraceElement

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

  /**
   * Children of the fiber.
   */
  def children: UIO[Iterable[Fiber[Any, Any]]]

  /**
   * Generates a fiber dump, if the fiber is not synthetic.
   */
  final def dump: UIO[Option[Fiber.Dump]] =
    for {
      name   <- self.getRef(Fiber.fiberName)
      id     <- self.id
      status <- self.status
      trace  <- self.trace
    } yield for {
      id    <- id
      trace <- trace
    } yield Fiber.Dump(id, name, status, trace)

  /**
   * Gets the value of the fiber ref for this fiber, or the initial value of
   * the fiber ref, if the fiber is not storing the ref.
   */
  def getRef[A](ref: FiberRef[A]): UIO[A]

  /**
   * The identity of the fiber, if it is a single runtime fiber. Fibers created
   * from values or composite fibers do not have identities.
   */
  def id: UIO[Option[Fiber.Id]]

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
   * Joins the fiber, which suspends the joining fiber until the result of the
   * fiber has been determined. Attempting to join a fiber that has errored will
   * result in a catchable error. Joining an interrupted fiber will result in an
   * "inner interruption" of this fiber, unlike interruption triggered by another
   * fiber, "inner interruption" can be caught and recovered.
   *
   * @return `IO[E, A]`
   */
  final def join: IO[E, A] = await.flatMap(IO.done) <* inheritRefs

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
      final def children: UIO[Iterable[Fiber[Any, Any]]] = self.children
      final def getRef[A](ref: FiberRef[A]): UIO[A]      = self.getRef(ref)
      final def id: UIO[Option[Fiber.Id]]                = self.id
      final def inheritRefs: UIO[Unit] =
        self.inheritRefs
      final def interruptAs(id: Fiber.Id): UIO[Exit[E1, B]] =
        self.interruptAs(id).flatMap(_.foreach(f))
      final def poll: UIO[Option[Exit[E1, B]]] =
        self.poll.flatMap(_.fold[UIO[Option[Exit[E1, B]]]](UIO.succeed(None))(_.foreach(f).map(Some(_))))
      final def status: UIO[Fiber.Status] = self.status
      def trace: UIO[Option[ZTrace]]      = self.trace
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
  def orElse[E1 >: E, A1 >: A](that: => Fiber[E1, A1]): Fiber[E1, A1] =
    new Fiber[E1, A1] {
      final def await: UIO[Exit[E1, A1]] =
        self.await.zipWith(that.await) {
          case (Exit.Failure(_), e2) => e2
          case (e1, _)               => e1
        }

      final def children: UIO[Iterable[Fiber[Any, Any]]] = (self.children zipWith that.children)(_ ++ _)

      final def getRef[A](ref: FiberRef[A]): UIO[A] =
        for {
          first  <- self.getRef(ref)
          second <- self.getRef(ref)
        } yield if (first == ref.initial) second else first

      final def id: UIO[Option[Fiber.Id]] = UIO.none

      final def interruptAs(id: Fiber.Id): UIO[Exit[E1, A1]] =
        self.interruptAs(id) *> that.interruptAs(id)

      final def inheritRefs: UIO[Unit] =
        that.inheritRefs *> self.inheritRefs

      final def poll: UIO[Option[Exit[E1, A1]]] =
        self.poll.zipWith(that.poll)(_ orElse _)

      final def status: UIO[Fiber.Status] = (self.status zipWith that.status)(_ <> _)

      final def trace: UIO[Option[ZTrace]] = UIO.none
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
   * The status of the fiber.
   */
  def status: UIO[Fiber.Status]

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
   * any errors to [[java.lang.Throwable]] with the specified conversion function,
   * using [[Cause.squashWithTrace]]
   *
   * @param f function to the error into a Throwable
   * @return `UIO[Future[A]]`
   */
  final def toFutureWith(f: E => Throwable): UIO[CancelableFuture[E, A]] =
    UIO.effectTotal {
      val p: concurrent.Promise[A] = scala.concurrent.Promise[A]()

      def failure(cause: Cause[E]): UIO[p.type] = UIO(p.failure(cause.squashWithTrace(f)))
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
   * The trace of the fiber. Currently only single runtime fibers have traces.
   */
  def trace: UIO[Option[ZTrace]]

  /**
   * Maps the output of this fiber to `()`.
   *
   * @return `Fiber[E, Unit]` fiber mapped to `()`
   */
  final def unit: Fiber[E, Unit] = as(())

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
      final def await: UIO[Exit[E1, C]] =
        self.await.flatMap(IO.done).zipWithPar(that.await.flatMap(IO.done))(f).run

      final def children: UIO[Iterable[Fiber[Any, Any]]] = (self.children zipWith that.children)(_ ++ _)

      final def getRef[A](ref: FiberRef[A]): UIO[A] =
        (self.getRef(ref) zipWith that.getRef(ref))(ref.combine(_, _))

      final def id: UIO[Option[Fiber.Id]] = UIO.none

      final def interruptAs(id: Fiber.Id): UIO[Exit[E1, C]] =
        (self interruptAs id).zipWith(that interruptAs id)(_.zipWith(_)(f, _ && _))

      final def inheritRefs: UIO[Unit] = that.inheritRefs *> self.inheritRefs

      final def poll: UIO[Option[Exit[E1, C]]] =
        self.poll.zipWith(that.poll) {
          case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(f, _ && _))
          case _                    => None
        }

      final def status: UIO[Fiber.Status] = (self.status zipWith that.status)(_ <> _)

      final def trace: UIO[Option[ZTrace]] = UIO.none
    }
}

object Fiber {

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
    children: UIO[Iterable[Fiber[Any, Any]]],
    executor: Executor
  )

  final case class Dump(fiberId: Fiber.Id, fiberName: Option[String], status: Status, trace: ZTrace)
      extends Serializable {
    import zio.Fiber.Status._

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
    def prettyPrintM: UIO[String] = UIO {
      val time = System.currentTimeMillis()

      val millis  = (time - fiberId.startTimeMillis)
      val seconds = millis / 1000L
      val minutes = seconds / 60L
      val hours   = minutes / 60L

      val name = fiberName.fold("")(name => "\"" + name + "\" ")
      val lifeMsg = (if (hours == 0) "" else s"${hours}h") +
        (if (hours == 0 && minutes == 0) "" else s"${minutes}m") +
        (if (hours == 0 && minutes == 0 && seconds == 0) "" else s"${seconds}s") +
        (s"${millis}ms")
      val waitMsg = status match {
        case Done    => ""
        case Running => ""
        case Suspended(_, _, blockingOn, _) =>
          if (blockingOn.nonEmpty)
            "waiting on " + blockingOn.map(id => s"#${id.seqNumber}").mkString(", ")
          else ""
      }
      val statMsg = status match {
        case Done    => "Done"
        case Running => "Running"
        case Suspended(interruptible, epoch, _, asyncTrace) =>
          val in = if (interruptible) "interruptible" else "uninterruptible"
          val ep = s"${epoch} asyncs"
          val as = asyncTrace.map(_.prettyPrint).mkString(" ")
          s"Suspended(${in}, ${ep}, ${as})"
      }

      s"""
         |${name}#${fiberId.seqNumber} (${lifeMsg}) ${waitMsg}
         |   Status: ${statMsg}
         |${trace.prettyPrint}
         |""".stripMargin
    }
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

    /**
     * Combines the two statuses into one in an associative way.
     */
    final def <>(that: Status): Status = (self, that) match {
      case (Done, Done)                                           => Done
      case (Suspended(i1, e1, l1, a1), Suspended(i2, e2, l2, a2)) => Suspended(i1 && i2, e1 max e2, l1 ++ l2, a1 ++ a2)
      case (Suspended(_, _, _, _), _)                             => self
      case (_, Suspended(_, _, _, _))                             => that
      case _                                                      => Running
    }
  }
  object Status {
    case object Done    extends Status
    case object Running extends Status
    final case class Suspended(
      interruptible: Boolean,
      epoch: Long,
      blockingOn: List[Fiber.Id],
      asyncTrace: List[ZTraceElement]
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
  def collectAll[E, A](fibers: Iterable[Fiber[E, A]]): Fiber[E, List[A]] =
    new Fiber[E, List[A]] {
      def await: UIO[Exit[E, List[A]]] =
        IO.foreachPar(fibers)(_.await.flatMap(IO.done)).run
      def children: UIO[Iterable[Fiber[Any, Any]]] =
        UIO.foreach(fibers)(_.children).map(_.foldRight(Iterable.empty[Fiber[Any, Any]])(_ ++ _))
      def getRef[A](ref: FiberRef[A]): UIO[A] =
        UIO.foreach(fibers)(_.getRef(ref)).map(_.foldRight(ref.initial)(ref.combine))
      def id: UIO[Option[Fiber.Id]] =
        UIO.none
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
      def status: UIO[Fiber.Status] =
        UIO.foreach(fibers)(_.status).map(_.foldRight[Status](Status.Done)(_ <> _))
      def trace: UIO[Option[ZTrace]] =
        UIO.none
    }

  /**
   * A fiber that is done with the specified [[zio.Exit]] value.
   *
   * @param exit [[zio.Exit]] value
   * @tparam E error type
   * @tparam A type of the fiber
   * @return `Fiber[E, A]`
   */
  def done[E, A](exit: => Exit[E, A]): Fiber[E, A] =
    new Fiber[E, A] {
      final def await: UIO[Exit[E, A]]                     = IO.succeed(exit)
      final def children: UIO[Iterable[Fiber[Any, Any]]]   = UIO(Nil)
      final def getRef[A](ref: FiberRef[A]): UIO[A]        = UIO(ref.initial)
      final def id: UIO[Option[Fiber.Id]]                  = UIO.none
      final def interruptAs(id: Fiber.Id): UIO[Exit[E, A]] = IO.succeed(exit)
      final def inheritRefs: UIO[Unit]                     = IO.unit
      final def poll: UIO[Option[Exit[E, A]]]              = IO.succeed(Some(exit))
      final def status: UIO[Fiber.Status]                  = UIO(Fiber.Status.Done)
      final def trace: UIO[Option[ZTrace]]                 = UIO.none
    }

  /**
   * Collects a complete dump of all fibers. This could potentially be quite large.
   *
   * TODO: Switch to "streaming lazy" version.
   */
  val dump: UIO[Iterable[Dump]] = UIO.effectSuspendTotal {
    import internal.FiberContext

    def loop(fibers: Iterable[FiberContext[_, _]], acc: UIO[Vector[Dump]]): UIO[Vector[Dump]] =
      ZIO
        .collectAll(fibers.toIterable.map { context =>
          (context.children zip context.dump).map {
            case (children, dump) => dump.map(dump => (children, dump))
          }
        })
        .flatMap { (collected: List[Option[(Iterable[Fiber[Any, Any]], Dump)]]) =>
          val collected1 = collected.collect { case Some(a) => a }
          val children   = collected1.map(_._1).flatten
          val dumps      = collected1.map(_._2)
          val acc2       = acc.map(_ ++ dumps.toVector)

          if (children.isEmpty) acc2 else loop(children.asInstanceOf[Iterable[FiberContext[Any, Any]]], acc2)
        }

    loop(_rootFibers.asScala: @silent("JavaConverters"), UIO(Vector()))
  }

  /**
   * A fiber that has already failed with the specified value.
   *
   * @param e failure value
   * @tparam E error type
   * @return `Fiber[E, Nothing]` failed fiber
   */
  def fail[E](e: E): Fiber[E, Nothing] = done(Exit.fail(e))

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
  def fromEffect[E, A](io: IO[E, A]): UIO[Fiber[E, A]] =
    io.run.map(done(_))

  /**
   * Returns a `Fiber` that is backed by the specified `Future`.
   *
   * @param thunk `Future[A]` backing the `Fiber`
   * @tparam A type of the `Fiber`
   * @return `Fiber[Throwable, A]`
   */
  def fromFuture[A](thunk: => Future[A]): Fiber[Throwable, A] =
    new Fiber[Throwable, A] {
      lazy val ftr: Future[A] = thunk

      def await: UIO[Exit[Throwable, A]] = Task.fromFuture(_ => ftr).run

      def children: UIO[Iterable[Fiber[Any, Any]]] = UIO(Nil)

      def getRef[A](ref: FiberRef[A]): UIO[A] = UIO(ref.initial)

      def id: UIO[Option[Fiber.Id]] = UIO.none

      def interruptAs(id: Fiber.Id): UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)

      def inheritRefs: UIO[Unit] = IO.unit

      def poll: UIO[Option[Exit[Throwable, A]]] = IO.effectTotal(ftr.value.map(Exit.fromTry))

      def status: UIO[Fiber.Status] = UIO {
        if (thunk.isCompleted) Status.Done else Status.Running
      }

      def trace: UIO[Option[ZTrace]] = UIO.none
    }

  /**
   * Creates a `Fiber` that is halted with the specified cause.
   */
  def halt[E](cause: Cause[E]): Fiber[E, Nothing] = done(Exit.halt(cause))

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
  def interruptAs(id: Fiber.Id): Fiber[Nothing, Nothing] = done(Exit.interrupt(id))

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
  val never: Fiber[Nothing, Nothing] =
    new Fiber[Nothing, Nothing] {
      def await: UIO[Exit[Nothing, Nothing]]                     = IO.never
      def children: UIO[Iterable[Fiber[Any, Any]]]               = UIO(Nil)
      def getRef[A](ref: FiberRef[A]): UIO[A]                    = UIO(ref.initial)
      def id: UIO[Option[Fiber.Id]]                              = UIO.none
      def interruptAs(id: Fiber.Id): UIO[Exit[Nothing, Nothing]] = IO.never
      def inheritRefs: UIO[Unit]                                 = IO.unit
      def poll: UIO[Option[Exit[Nothing, Nothing]]]              = IO.succeed(None)
      def status: UIO[Fiber.Status]                              = UIO(Status.Suspended(false, 0, Nil, Nil))
      def trace: UIO[Option[ZTrace]]                             = UIO.none
    }

  /**
   * The root fibers.
   */
  val roots: UIO[Set[Fiber[Any, Any]]] = UIO {
    _rootFibers.asScala.toSet: @silent("JavaConverters")
  }

  /**
   * Returns a fiber that has already succeeded with the specified value.
   *
   * @param a success value
   * @tparam E error type
   * @tparam A type of the fiber
   * @return `Fiber[E, A]` succeeded fiber
   */
  def succeed[A](a: A): Fiber[Nothing, A] = done(Exit.succeed(a))

  /**
   * A fiber that has already succeeded with unit.
   */
  val unit: Fiber[Nothing, Unit] = Fiber.succeed(())

  /**
   * Retrieves the fiber currently executing on this thread, if any. This will
   * always be `None` unless called from within an executing effect.
   */
  def unsafeCurrentFiber(): Option[Fiber[Any, Any]] =
    Option(_currentFiber.get)

  private[zio] def newFiberId(): Fiber.Id = Fiber.Id(System.currentTimeMillis(), _fiberCounter.getAndIncrement())

  private[zio] def track[E, A](context: internal.FiberContext[E, A]): Unit = {
    Fiber._rootFibers.add(context)

    context.onDone(_ => { val _ = Fiber._rootFibers.remove(context) })
  }

  private[zio] val _currentFiber: ThreadLocal[internal.FiberContext[_, _]] =
    new ThreadLocal[internal.FiberContext[_, _]]()

  private val _rootFibers: java.util.Set[internal.FiberContext[_, _]] =
    internal.Platform.newConcurrentSet[internal.FiberContext[_, _]]()

  private[zio] val _fiberCounter = new java.util.concurrent.atomic.AtomicLong(0)
}
