/*
 * Copyright 2018-2022 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm._
import zio.stream._

import java.io.{IOException, InputStream}

package object managed extends ZManagedCompatPlatformSpecific {

  type Managed[+E, +A]   = ZManaged[Any, E, A]         //Manage an `A`, may fail with `E`        , no requirements
  type TaskManaged[+A]   = ZManaged[Any, Throwable, A] //Manage an `A`, may fail with `Throwable`, no requirements
  type RManaged[-R, +A]  = ZManaged[R, Throwable, A]   //Manage an `A`, may fail with `Throwable`, requires an `R`
  type UManaged[+A]      = ZManaged[Any, Nothing, A]   //Manage an `A`, cannot fail              , no requirements
  type URManaged[-R, +A] = ZManaged[R, Nothing, A]     //Manage an `A`, cannot fail              , requires an `R`

  val Managed: ZManaged.type = ZManaged

  implicit final class ZManagedTSemaphoreSyntax(private val self: TSemaphore) extends AnyVal {

    /**
     * Returns a managed effect that describes acquiring a permit as the
     * `acquire` action and releasing it as the `release` action.
     */
    def withPermitManaged(implicit trace: ZTraceElement): ZManaged[Any, Nothing, Unit] =
      ZManaged.scoped(self.withPermitScoped)

    /**
     * Returns a managed effect that describes acquiring the specified number of
     * permits as the `acquire` action and releasing them as the `release`
     * action.
     */
    def withPermitsManaged(n: Long)(implicit trace: ZTraceElement): ZManaged[Any, Nothing, Unit] =
      ZManaged.scoped(self.withPermitsScoped(n))
  }

  implicit final class ZManagedZFiberRefSyntax[+EA, +EB, -A, +B](private val self: ZFiberRef[EA, EB, A, B]) {

    /**
     * Returns a managed effect that sets the value associated with the curent
     * fiber to the specified value as its `acquire` action and restores it to
     * its original value as its `release` action.
     */
    def locallyManaged(value: A)(implicit trace: ZTraceElement): ZManaged[Any, EA, Unit] =
      ZManaged.scoped(self.locallyScoped(value))
  }

  implicit final class ZManagedZIOSyntax[R, E, A](private val self: ZIO[R, E, A]) {

    /**
     * Forks the fiber in a [[ZManaged]]. Using the [[ZManaged]] value will
     * execute the effect in the fiber, while ensuring its interruption when the
     * effect supplied to [[ZManaged#use]] completes.
     */
    final def forkManaged(implicit trace: ZTraceElement): ZManaged[R, Nothing, Fiber.Runtime[E, A]] =
      self.toManaged.fork

    /**
     * Converts this ZIO to [[ZManaged]] with no release action. It will be
     * performed interruptibly.
     */
    final def toManaged(implicit trace: ZTraceElement): ZManaged[R, E, A] =
      ZManaged.fromZIO(self)

    /**
     * Converts this ZIO to [[Managed]]. This ZIO and the provided release
     * action will be performed uninterruptibly.
     */
    final def toManagedWith[R1 <: R](release: A => URIO[R1, Any])(implicit trace: ZTraceElement): ZManaged[R1, E, A] =
      ZManaged.acquireReleaseWith(self)(release)

    /**
     * Converts this ZIO to [[ZManaged]] with no release action. It will be
     * performed interruptibly.
     */
    @deprecated("use toManaged", "2.0.0")
    final def toManaged_(implicit trace: ZTraceElement): ZManaged[R, E, A] =
      self.toManaged
  }

  implicit final class ZManagedZIOAutoCloseableSyntax[R, E, A <: AutoCloseable](private val self: ZIO[R, E, A])
      extends AnyVal {

    /**
     * Converts this ZIO value to a ZManaged value. See
     * [[ZManaged.fromAutoCloseable]].
     */
    def toManagedAuto(implicit trace: ZTraceElement): ZManaged[R, E, A] =
      ZManaged.fromAutoCloseable(self)
  }

  implicit final class ZManagedZIOCompanionSyntax(private val self: ZIO.type) extends AnyVal {

    /**
     * Acquires a resource, uses the resource, and then releases the resource.
     * However, unlike `acquireReleaseWith`, the separation of these phases
     * allows the acquisition to be interruptible.
     *
     * Useful for concurrent data structures and other cases where the
     * 'deallocator' can tell if the allocation succeeded or not just by
     * inspecting internal / external state.
     */
    def reserve[R, E, A, B](reservation: => ZIO[R, E, Reservation[R, E, A]])(use: A => ZIO[R, E, B])(implicit
      trace: ZTraceElement
    ): ZIO[R, E, B] =
      ZManaged.fromReservationZIO(reservation).use(use)
  }

  implicit final class ZManagedZChannelSyntax[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    private val self: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends AnyVal {

    def runManaged(implicit
      ev1: Any <:< InElem,
      ev2: OutElem <:< Nothing,
      trace: ZTraceElement
    ): ZManaged[Env, OutErr, OutDone] =
      ZManaged.scoped[Env](self.runScoped)
  }

  implicit final class ZManagedZChannelCompanionSyntax(private val self: ZChannel.type) extends AnyVal {

    def managed[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone, A](m: => ZManaged[Env, OutErr, A])(
      use: A => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    )(implicit trace: ZTraceElement): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      ZChannel.scoped[Env](m.scoped)(use)

    def managedOut[R, E, A](
      m: => ZManaged[R, E, A]
    )(implicit trace: ZTraceElement): ZChannel[R, Any, Any, Any, E, A, Any] =
      ZChannel.scopedOut[R](m.scoped)

    def unwrapManaged[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      channel: => ZManaged[Env, OutErr, ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]]
    )(implicit
      trace: ZTraceElement
    ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      ZChannel.unwrapScoped[Env](channel.scoped)
  }

  implicit final class ZManagedZStreamSyntax[R, E, A](private val self: ZStream[R, E, A]) extends AnyVal {

    /**
     * Executes a pure fold over the stream of values. Returns a managed value
     * that represents the scope of the stream.
     */
    @deprecated("user runFoldManaged", "2.0.0")
    final def foldManaged[S](s: => S)(f: (S, A) => S)(implicit trace: ZTraceElement): ZManaged[R, E, S] =
      ZManaged.scoped[R](self.runFoldScoped(s)(f))

    /**
     * Executes an effectful fold over the stream of values. Returns a managed
     * value that represents the scope of the stream.
     */
    @deprecated("use runFoldScopedManaged", "2.0.0")
    final def foldManagedM[R1 <: R, E1 >: E, S](s: => S)(f: (S, A) => ZIO[R1, E1, S])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, S] =
      ZManaged.scoped[R1](self.runFoldScopedZIO[R1, E1, S](s)(f))

    /**
     * Executes an effectful fold over the stream of values. Returns a managed
     * value that represents the scope of the stream.
     */
    @deprecated("use runFoldManagedZIO", "2.0.0")
    final def foldManagedZIO[R1 <: R, E1 >: E, S](s: => S)(f: (S, A) => ZIO[R1, E1, S])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, S] =
      ZManaged.scoped[R1](self.runFoldScopedZIO[R1, E1, S](s)(f))

    /**
     * Executes a pure fold over the stream of values. Returns a managed value
     * that represents the scope of the stream. Stops the fold early when the
     * condition is not fulfilled.
     */
    @deprecated("use runFoldWhileManaged", "2.0.0")
    final def foldWhileManaged[S](s: => S)(cont: S => Boolean)(f: (S, A) => S)(implicit
      trace: ZTraceElement
    ): ZManaged[R, E, S] =
      ZManaged.scoped[R](self.runFoldWhileScoped(s)(cont)(f))

    /**
     * Executes an effectful fold over the stream of values. Returns a managed
     * value that represents the scope of the stream. Stops the fold early when
     * the condition is not fulfilled. Example:
     * {{{
     *   Stream(1)
     *     .forever                                // an infinite Stream of 1's
     *     .fold(0)(_ <= 4)((s, a) => UIO(s + a))  // Managed[Nothing, Int]
     *     .use(ZIO.succeed)                       // UIO[Int] == 5
     * }}}
     *
     * @param cont
     *   function which defines the early termination condition
     */
    @deprecated("use runFoldWhileManagedZIO", "2.0.0")
    final def foldWhileManagedM[R1 <: R, E1 >: E, S](
      s: => S
    )(cont: S => Boolean)(f: (S, A) => ZIO[R1, E1, S])(implicit trace: ZTraceElement): ZManaged[R1, E1, S] =
      ZManaged.scoped[R1](self.runFoldWhileScopedZIO[R1, E1, S](s)(cont)(f))

    /**
     * Executes an effectful fold over the stream of values. Returns a managed
     * value that represents the scope of the stream. Stops the fold early when
     * the condition is not fulfilled. Example:
     * {{{
     *   Stream(1)
     *     .forever                                // an infinite Stream of 1's
     *     .fold(0)(_ <= 4)((s, a) => UIO(s + a))  // Managed[Nothing, Int]
     *     .use(ZIO.succeed)                       // UIO[Int] == 5
     * }}}
     *
     * @param cont
     *   function which defines the early termination condition
     */
    @deprecated("use runFoldWhileManagedZIO", "2.0.0")
    final def foldWhileManagedZIO[R1 <: R, E1 >: E, S](
      s: => S
    )(cont: S => Boolean)(f: (S, A) => ZIO[R1, E1, S])(implicit trace: ZTraceElement): ZManaged[R1, E1, S] =
      ZManaged.scoped[R1](self.runFoldWhileScopedZIO[R1, E1, S](s)(cont)(f))

    /**
     * Like [[ZStream#runForeachChunk]], but returns a `ZManaged` so the
     * finalization order can be controlled.
     */
    @deprecated("use runForeachChunkManaged", "2.0.0")
    final def foreachChunkManaged[R1 <: R, E1 >: E](f: Chunk[A] => ZIO[R1, E1, Any])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runForeachChunkScoped[R1, E1](f))

    /**
     * Like [[ZStream#foreach]], but returns a `ZManaged` so the finalization
     * order can be controlled.
     */
    @deprecated("run runForeachManaged", "2.0.0")
    final def foreachManaged[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Any])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runForeachScoped[R1, E1](f))

    /**
     * Like [[ZStream#runForeachWhile]], but returns a `ZManaged` so the
     * finalization order can be controlled.
     */
    @deprecated("use runForeachWhileManaged", "2.0.0")
    final def foreachWhileManaged[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runForeachWhileScoped[R1, E1](f))

    /**
     * Like [[ZStream#intoHub]], but provides the result as a [[ZManaged]] to
     * allow for scope composition.
     */
    @deprecated("use runIntoHubManaged", "2.0.0")
    final def intoHubManaged[R1 <: R, E1 >: E](
      hub: => ZHub[R1, Nothing, Nothing, Any, Take[E1, A], Any]
    )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runIntoHubScoped(hub))

    /**
     * Like [[ZStream#into]], but provides the result as a [[ZManaged]] to allow
     * for scope composition.
     */
    @deprecated("use runIntoQueueManaged", "2.0.0")
    final def intoManaged[R1 <: R, E1 >: E](
      queue: => ZQueue[R1, Nothing, Nothing, Any, Take[E1, A], Any]
    )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runIntoQueueScoped(queue))

    /**
     * Like [[ZStream#ntoQueue]], but provides the result as a [[ZManaged]] to
     * allow for scope composition.
     */
    @deprecated("use runIntoQueueManaged", "2.0.0")
    final def intoQueueManaged[R1 <: R, E1 >: E](
      queue: => ZQueue[R1, Nothing, Nothing, Any, Take[E1, A], Any]
    )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runIntoQueueScoped(queue))

    /**
     * Executes a pure fold over the stream of values. Returns a managed value
     * that represents the scope of the stream.
     */
    final def runFoldManaged[S](s: => S)(f: (S, A) => S)(implicit trace: ZTraceElement): ZManaged[R, E, S] =
      ZManaged.scoped[R](self.runFoldScoped(s)(f))

    /**
     * Executes an effectful fold over the stream of values. Returns a managed
     * value that represents the scope of the stream.
     */
    @deprecated("use runFoldManagedZIO", "2.0.0")
    final def runFoldManagedM[R1 <: R, E1 >: E, S](s: => S)(f: (S, A) => ZIO[R1, E1, S])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, S] =
      ZManaged.scoped[R1](self.runFoldScopedZIO[R1, E1, S](s)(f))

    /**
     * Executes an effectful fold over the stream of values. Returns a managed
     * value that represents the scope of the stream.
     */
    final def runFoldManagedZIO[R1 <: R, E1 >: E, S](s: => S)(f: (S, A) => ZIO[R1, E1, S])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, S] =
      ZManaged.scoped[R1](self.runFoldScopedZIO[R1, E1, S](s)(f))

    /**
     * Executes an effectful fold over the stream of values. Returns a managed
     * value that represents the scope of the stream. Stops the fold early when
     * the condition is not fulfilled. Example:
     * {{{
     *   Stream(1)
     *     .forever                                // an infinite Stream of 1's
     *     .fold(0)(_ <= 4)((s, a) => UIO(s + a))  // Managed[Nothing, Int]
     *     .use(ZIO.succeed)                       // UIO[Int] == 5
     * }}}
     *
     * @param cont
     *   function which defines the early termination condition
     */
    @deprecated("use runFoldWhileManagedZIO", "2.0.0")
    final def runFoldWhileManagedM[R1 <: R, E1 >: E, S](
      s: => S
    )(cont: S => Boolean)(f: (S, A) => ZIO[R1, E1, S])(implicit trace: ZTraceElement): ZManaged[R1, E1, S] =
      ZManaged.scoped[R1](self.runFoldWhileScopedZIO[R1, E1, S](s)(cont)(f))

    /**
     * Executes an effectful fold over the stream of values. Returns a managed
     * value that represents the scope of the stream. Stops the fold early when
     * the condition is not fulfilled. Example:
     * {{{
     *   Stream(1)
     *     .fold(0)(_ <= 4)((s, a) => UIO(s + a))  // Managed[Nothing, Int]
     *     .use(ZIO.succeed)                       // UIO[Int] == 5
     * }}}
     *
     * @param cont
     *   function which defines the early termination condition
     */
    final def runFoldWhileManagedZIO[R1 <: R, E1 >: E, S](
      s: => S
    )(cont: S => Boolean)(f: (S, A) => ZIO[R1, E1, S])(implicit trace: ZTraceElement): ZManaged[R1, E1, S] =
      ZManaged.scoped[R1](self.runFoldWhileScopedZIO[R1, E1, S](s)(cont)(f))

    /**
     * Executes a pure fold over the stream of values. Returns a managed value
     * that represents the scope of the stream. Stops the fold early when the
     * condition is not fulfilled.
     */
    final def runFoldWhileManaged[S](s: => S)(cont: S => Boolean)(f: (S, A) => S)(implicit
      trace: ZTraceElement
    ): ZManaged[R, E, S] =
      ZManaged.scoped[R](self.runFoldWhileScoped(s)(cont)(f))

    /**
     * Like [[ZStream#runForeachChunk]], but returns a scoped `ZIO` so the
     * finalization order can be controlled.
     */
    final def runForeachChunkManaged[R1 <: R, E1 >: E](f: Chunk[A] => ZIO[R1, E1, Any])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runForeachChunkScoped(f))

    /**
     * Like [[ZStream#foreach]], but returns a `ZManaged` so the finalization
     * order can be controlled.
     */
    final def runForeachScoped[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Any])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runForeachScoped(f))

    /**
     * Like [[ZStream#runForeachWhile]], but returns a scoped `ZIO` so the
     * finalization order can be controlled.
     */
    final def runForeachWhileManaged[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runForeachWhileScoped(f))

    /**
     * Like [[ZStream#runInto]], but provides the result as a [[ZManaged]] to
     * allow for scope composition.
     */
    @deprecated("use runIntoQueueElementsManaged", "2.0.0")
    final def runIntoElementsManaged[R1 <: R, E1 >: E](
      queue: => ZQueue[R1, Nothing, Nothing, Any, Exit[Option[E1], A], Any]
    )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runIntoQueueElementsScoped(queue))

    /**
     * Like [[ZStream#runIntoHub]], but provides the result as a [[ZManaged]] to
     * allow for scope composition.
     */
    final def runIntoHubManaged[R1 <: R, E1 >: E](
      hub: => ZHub[R1, Nothing, Nothing, Any, Take[E1, A], Any]
    )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runIntoHubScoped(hub))

    /**
     * Like [[ZStream#runInto]], but provides the result as a [[ZManaged]] to
     * allow for scope composition.
     */
    @deprecated("use runIntoQueueManaged", "2.0.0")
    final def runIntoManaged[R1 <: R, E1 >: E](
      queue: => ZQueue[R1, Nothing, Nothing, Any, Take[E1, A], Any]
    )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runIntoQueueScoped(queue))

    /**
     * Like [[ZStream#runIntoQueue]], but provides the result as a [[ZManaged]]
     * to allow for scope composition.
     */
    final def runIntoQueueManaged[R1 <: R, E1 >: E](
      queue: => ZQueue[R1, Nothing, Nothing, Any, Take[E1, A], Any]
    )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runIntoQueueScoped(queue))

    /**
     * Like [[ZStream#runIntoQueue]], but provides the result as a [[ZManaged]]
     * to allow for scope composition.
     */
    final def runIntoQueueElementsManaged[R1 <: R, E1 >: E](
      queue: => ZQueue[R1, Nothing, Nothing, Any, Exit[Option[E1], A], Any]
    )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
      ZManaged.scoped[R1](self.runIntoQueueElementsScoped(queue))

    def runManaged[R1 <: R, E1 >: E, B](sink: => ZSink[R1, E1, A, Any, B])(implicit
      trace: ZTraceElement
    ): ZManaged[R1, E1, B] =
      ZManaged.scoped[R1](self.runScoped(sink))
  }

  implicit final class ZManagedZStreamCompanionSyntax(private val self: ZStream.type) extends AnyVal {

    /**
     * Creates a stream from a subscription to a hub in the context of a managed
     * effect. The managed effect describes subscribing to receive messages from
     * the hub while the stream describes taking messages from the hub.
     */
    def fromChunkHubManaged[R, E, O](
      hub: => ZHub[Nothing, R, Any, E, Nothing, Chunk[O]]
    )(implicit trace: ZTraceElement): ZManaged[Any, Nothing, ZStream[R, E, O]] =
      ZManaged.scoped(ZStream.fromChunkHubScoped(hub))

    /**
     * Creates a stream from a subscription to a hub in the context of a managed
     * effect. The managed effect describes subscribing to receive messages from
     * the hub while the stream describes taking messages from the hub.
     *
     * The hub will be shut down once the stream is closed.
     */
    def fromChunkHubManagedWithShutdown[R, E, O](
      hub: => ZHub[Nothing, R, Any, E, Nothing, Chunk[O]]
    )(implicit trace: ZTraceElement): ZManaged[Any, Nothing, ZStream[R, E, O]] =
      ZManaged.scoped(ZStream.fromChunkHubScopedWithShutdown(hub))

    /**
     * Creates a stream from a subscription to a hub in the context of a managed
     * effect. The managed effect describes subscribing to receive messages from
     * the hub while the stream describes taking messages from the hub.
     */
    def fromHubManaged[R, E, A](
      hub: => ZHub[Nothing, R, Any, E, Nothing, A],
      maxChunkSize: => Int = ZStream.DefaultChunkSize
    )(implicit trace: ZTraceElement): ZManaged[Any, Nothing, ZStream[R, E, A]] =
      ZManaged.scoped(ZStream.fromHubScoped(hub, maxChunkSize))

    /**
     * Creates a stream from a subscription to a hub in the context of a managed
     * effect. The managed effect describes subscribing to receive messages from
     * the hub while the stream describes taking messages from the hub.
     *
     * The hub will be shut down once the stream is closed.
     */
    def fromHubManagedWithShutdown[R, E, A](
      hub: => ZHub[Nothing, R, Any, E, Nothing, A],
      maxChunkSize: => Int = ZStream.DefaultChunkSize
    )(implicit trace: ZTraceElement): ZManaged[Any, Nothing, ZStream[R, E, A]] =
      ZManaged.scoped(ZStream.fromHubScopedWithShutdown(hub, maxChunkSize))

    /**
     * Creates a stream from a managed `java.io.InputStream` value.
     */
    def fromInputStreamManaged[R](
      is: => ZManaged[R, IOException, InputStream],
      chunkSize: => Int = ZStream.DefaultChunkSize
    )(implicit trace: ZTraceElement): ZStream[R, IOException, Byte] =
      ZStream.fromInputStreamScoped[R](is.scoped, chunkSize)

    /**
     * Creates a stream from a managed iterator
     */
    def fromIteratorManaged[R, A](
      iterator: => ZManaged[R, Throwable, Iterator[A]],
      maxChunkSize: => Int = ZStream.DefaultChunkSize
    )(implicit
      trace: ZTraceElement
    ): ZStream[R, Throwable, A] =
      ZStream.fromIteratorScoped[R, A](iterator.scoped, maxChunkSize)

    /**
     * Creates a stream from a managed iterator
     */
    def fromJavaIteratorManaged[R, A](iterator: => ZManaged[R, Throwable, java.util.Iterator[A]])(implicit
      trace: ZTraceElement
    ): ZStream[R, Throwable, A] =
      ZStream.fromJavaIteratorScoped[R, A](iterator.scoped)

    def managed[R, E, A](managed: => ZManaged[R, E, A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
      ZStream.scoped[R](managed.scoped)

    def unwrapScoped[R, E, A](fa: => ZManaged[R, E, ZStream[R, E, A]])(implicit
      trace: ZTraceElement
    ): ZStream[R, E, A] =
      ZStream.unwrapScoped[R](fa.scoped)
  }

  implicit final class ZManagedZSinkCompanionSyntax(private val self: ZSink.type) extends AnyVal {

    @deprecated("use unwrapManaged", "2.0.0")
    def managed[R, E, In, A, L <: In, Z](resource: => ZManaged[R, E, A])(
      fn: A => ZSink[R, E, In, L, Z]
    )(implicit trace: ZTraceElement): ZSink[R, E, In, In, Z] =
      unwrapManaged(resource.map(fn))

    /**
     * Creates a sink produced from a managed effect.
     */
    def unwrapManaged[R, E, In, L, Z](managed: => ZManaged[R, E, ZSink[R, E, In, L, Z]])(implicit
      trace: ZTraceElement
    ): ZSink[R, E, In, L, Z] =
      ZSink.unwrapScoped[R](managed.scoped)
  }
}
