/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
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

import zio.Exit._
import zio.ZIO.{FiberRefDelete, FiberRefLocally, FiberRefModify, FiberRefWith}
import zio.Supervisor.FiberRefTrackingSupervisor
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.{AtomicReference, LongAdder}
import scala.collection.immutable.SortedSet

/**
 * A `Supervisor[A]` is allowed to supervise the launching and termination of
 * fibers, producing some visible value of type `A` from the supervision.
 */
abstract class Supervisor[+A] { self =>

  /**
   * Returns an effect that succeeds with the value produced by this supervisor.
   * This value may change over time, reflecting what the supervisor produces as
   * it supervises fibers.
   */
  def value(implicit trace: Trace): UIO[A]

  /**
   * Maps this supervisor to another one, which has the same effect, but whose
   * value has been transformed by the specified function.
   */
  def map[B](f: A => B): Supervisor[B] =
    new Supervisor.ProxySupervisor(trace => value(trace).map(f)(trace), self)

  /**
   * Returns a new supervisor that performs the function of this supervisor, and
   * the function of the specified supervisor, producing a tuple of the outputs
   * produced by both supervisors.
   */
  final def ++[B](that0: Supervisor[B]): Supervisor[(A, B)] =
    new Supervisor[(A, B)] {
      lazy val that = that0

      def value(implicit trace: Trace) = self.value zip that.value

      def unsafeOnStart[R, E, A](
        environment: ZEnvironment[R],
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      ): Unit =
        try self.unsafeOnStart(environment, effect, parent, fiber)
        finally that.unsafeOnStart(environment, effect, parent, fiber)

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = {
        self.unsafeOnEnd(value, fiber)
        that.unsafeOnEnd(value, fiber)
      }

      override def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit = {
        self.unsafeOnEffect(fiber, effect)
        that.unsafeOnEffect(fiber, effect)
      }

      override def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
        self.unsafeOnSuspend(fiber)
        that.unsafeOnSuspend(fiber)
      }

      override def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
        self.unsafeOnResume(fiber)
        that.unsafeOnResume(fiber)
      }
    }

  def unsafeOnStart[R, E, A](
    environment: ZEnvironment[R],
    effect: ZIO[R, E, A],
    parent: Option[Fiber.Runtime[Any, Any]],
    fiber: Fiber.Runtime[E, A]
  ): Unit

  def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit

  def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit = ()

  def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit = ()

  def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit = ()
}
object Supervisor {
  import zio.internal._

  /**
   * Creates a new supervisor that tracks children in a set.
   *
   * @param weak
   *   Whether or not to track the children in a weak set, if possible
   *   (platform-dependent).
   */
  def track(weak: Boolean)(implicit trace: Trace): UIO[Supervisor[Chunk[Fiber.Runtime[Any, Any]]]] =
    ZIO.succeed(unsafeTrack(weak))

  def fromZIO[A](value: UIO[A]): Supervisor[A] = new ConstSupervisor(_ => value)

  /**
   * Creates a new supervisor that tracks children in a set.
   */
  def fibersIn(
    ref: AtomicReference[SortedSet[Fiber.Runtime[Any, Any]]]
  )(implicit trace: Trace): UIO[Supervisor[SortedSet[Fiber.Runtime[Any, Any]]]] =
    ZIO.succeed {

      new Supervisor[SortedSet[Fiber.Runtime[Any, Any]]] {
        def value(implicit trace: Trace): UIO[SortedSet[Fiber.Runtime[Any, Any]]] =
          ZIO.succeed(ref.get)

        def unsafeOnStart[R, E, A](
          environment: ZEnvironment[R],
          effect: ZIO[R, E, A],
          parent: Option[Fiber.Runtime[Any, Any]],
          fiber: Fiber.Runtime[E, A]
        ): Unit = {
          var loop = true
          while (loop) {
            val set = ref.get
            loop = !ref.compareAndSet(set, set + fiber)
          }
        }

        def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = {
          var loop = true
          while (loop) {
            val set = ref.get
            loop = !ref.compareAndSet(set, set - fiber)
          }
        }
      }
    }

  /**
   * A supervisor that doesn't do anything in response to supervision events.
   */
  val none: Supervisor[Unit] = new ConstSupervisor(_ => ZIO.unit)

  /**
   * Creates a [[Supervisor]] and a [[FiberRef[A]].
   * The supervisor ensures that `link` function is called with the current value of the [[FiberRef]] at the beginning of every async boundary,
   * and is called with `initialValue` just before an async boundary.
   * The [[FiberRef]] returned, will call `link` on every change applied to it, as well as at the beginning and the end of the effect passed to `FiberRef.locally`.
   */
  def trackFiberRef[A](initialValue: A)(link: A => Unit): (Supervisor[Unit], FiberRef[A]) = {
    val fiberRef = FiberRef.unsafeMake(initialValue)
    (new FiberRefTrackingSupervisor[A](fiberRef, initialValue, link), new TrackingFiberRef[A](fiberRef, link))
  }

  private class ConstSupervisor[A](value0: Trace => UIO[A]) extends Supervisor[A] {
    def value(implicit trace: Trace): UIO[A] = value0(trace)

    def unsafeOnStart[R, E, A](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
    ): Unit = ()

    def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = ()
  }

  private[zio] def unsafeTrack(weak: Boolean): Supervisor[Chunk[Fiber.Runtime[Any, Any]]] = {
    val set: java.util.Set[Fiber.Runtime[Any, Any]] =
      if (weak) Platform.newWeakSet[Fiber.Runtime[Any, Any]]()
      else new java.util.HashSet[Fiber.Runtime[Any, Any]]()

    new Supervisor[Chunk[Fiber.Runtime[Any, Any]]] {
      def value(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[Any, Any]]] =
        ZIO.succeed(
          Sync(set)(Chunk.fromArray(set.toArray[Fiber.Runtime[Any, Any]](Array[Fiber.Runtime[Any, Any]]())))
        )

      def unsafeOnStart[R, E, A](
        environment: ZEnvironment[R],
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      ): Unit = {
        Sync(set)(set.add(fiber))
        ()
      }

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = {
        Sync(set)(set.remove(fiber))
        ()
      }
    }
  }

  private class ProxySupervisor[A](value0: Trace => UIO[A], underlying: Supervisor[Any]) extends Supervisor[A] {
    def value(implicit trace: Trace): UIO[A] = value0(trace)

    def unsafeOnStart[R, E, A](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
    ): Unit = underlying.unsafeOnStart(environment, effect, parent, fiber)

    def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit =
      underlying.unsafeOnEnd(value, fiber)

    override def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit =
      underlying.unsafeOnEffect(fiber, effect)

    override def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit =
      underlying.unsafeOnSuspend(fiber)

    override def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit =
      underlying.unsafeOnResume(fiber)
  }

  private class FiberRefTrackingSupervisor[A](fiberRef: FiberRef.Runtime[A], initialValue: A, link: A => Unit)
      extends Supervisor[Unit] {
    private val runtime = Runtime.default

    override def value(implicit trace: ZTraceElement): UIO[Unit] = UIO.unit

    override private[zio] def unsafeOnStart[R, E, A1](
      environment: R,
      effect: ZIO[R, E, A1],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A1]
    ): Unit = ()

    override private[zio] def unsafeOnEnd[R, E, A1](value: Exit[E, A1], fiber: Fiber.Runtime[E, A1]): Unit = ()

    override private[zio] def unsafeOnSuspend[E, A1](fiber: Fiber.Runtime[E, A1]): Unit =
      reset()

    override private[zio] def unsafeOnResume[E, A1](fiber: Fiber.Runtime[E, A1]): Unit = {
      val a = getFiberRefValue(fiber)
      link(a)
    }

    private def getFiberRefValue[E, A1](fiber: Fiber.Runtime[E, A1]): A = {
      implicit val trace = ZTraceElement.empty
      runtime.unsafeRun(fiber.getRef(fiberRef))
    }

    private def reset() =
      link(initialValue)
  }

  private class TrackingFiberRef[A](fiberRef: FiberRef.Runtime[A], link: A => Unit)
      extends Derived[Nothing, Nothing, A, A] {

    override type S = A

    override def getEither(s: A): Either[Nothing, A] = Right(s)

    override def setEither(a: A): Either[Nothing, A] = Right(a)

    override val value: FiberRef.Runtime[A] = fiberRef

    override def locally[R, EC >: Nothing, C](
      value: A
    )(use: ZIO[R, EC, C])(implicit trace: ZTraceElement): ZIO[R, EC, C] =
      fiberRef.get.flatMap { before =>
        fiberRef.locally(value) {
          (linkM(value) *> use)
            .ensuring(linkM(before))
        }
      }

    override def locallyManaged(value: A)(implicit trace: ZTraceElement): ZManaged[Any, Nothing, Unit] =
      fiberRef.get.toManaged.flatMap { before =>
        linkM(value).toManaged *>
          fiberRef
            .locallyManaged(value)
            .ensuring(linkM(before))
      }

    override def set(value: A)(implicit trace: ZTraceElement): IO[Nothing, Unit] =
      fiberRef.set(value) <* linkM(value)

    private def linkM(a: A) = UIO(link(a))(ZTraceElement.empty)
  }
}
