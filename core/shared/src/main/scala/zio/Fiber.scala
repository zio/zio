/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.{FiberRenderer, FiberScope}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException
import scala.concurrent.Future
import zio.internal.WeakConcurrentBag

/**
 * A `Fiber[E, A]` is a handle to a computation that runs concurrently with the
 * code that started it, and that ends with a failure of type `E` or a value of
 * type `A`. A fiber is a lightweight thread of execution that never consumes
 * more than a whole thread (but may consume much less, depending on contention
 * and asynchronicity).
 *
 * Forking an effect (`ZIO#fork` and its variants) starts it on a new
 * [[Fiber.Runtime]]. Combinators such as `zip`, `orElse` and `map`, and
 * constructors such as `Fiber.succeed` or `Fiber.fromFuture`, build a
 * [[Fiber.Synthetic]] fiber out of other fibers or values.
 *
 * `join` waits for the result and succeeds or fails like the fiber did; it
 * also merges the fiber's `FiberRef` values into the joining fiber. `await`
 * waits for the result as an [[Exit]] and never fails. `interrupt` stops the
 * fiber, runs its finalizers, and waits until it has ended. Prefer the
 * higher-level operators (`zipPar`, `race`, `foreachPar`, ...) when they fit:
 * they fork, join and interrupt fibers for you.
 *
 * {{{
 * val program: UIO[(Int, Boolean, Boolean)] =
 *   for {
 *     sumFiber   <- ZIO.succeed((1 to 10).sum).fork
 *     sum        <- sumFiber.join
 *     failing    <- ZIO.fail("invalid order").fork
 *     failedExit <- failing.await
 *     sleeping   <- ZIO.never.fork
 *     stopped    <- sleeping.interrupt
 *   } yield (sum, failedExit.isFailure, stopped.isInterrupted)
 * // succeeds with (55,true,true)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/fiber/ Fiber]]
 */
sealed abstract class Fiber[+E, +A] { self =>

  /**
   * Same as `zip` but discards the output of the left hand side.
   *
   * @param that
   *   fiber to be zipped
   * @tparam E1
   *   error type
   * @tparam B
   *   success type of that fiber
   * @return
   *   `Fiber[E1, B]` combined fiber
   */
  final def *>[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, B] =
    (self zipWith that)((_, b) => b)

  /**
   * Same as `zip` but discards the output of the right hand side.
   *
   * @param that
   *   fiber to be zipped
   * @tparam E1
   *   error type
   * @tparam B
   *   success type of that fiber
   * @return
   *   `Fiber[E1, A]` combined fiber
   */
  final def <*[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, A] =
    (self zipWith that)((a, _) => a)

  /**
   * Zips this fiber and the specified fiber together, producing a tuple of
   * their output.
   *
   * @param that
   *   fiber to be zipped
   * @tparam E1
   *   error type
   * @tparam B
   *   success type of that fiber
   * @return
   *   `Fiber[E1, (A, B)]` combined fiber
   */
  final def <*>[E1 >: E, B](that: => Fiber[E1, B])(implicit
    zippable: Zippable[A, B]
  ): Fiber.Synthetic[E1, zippable.Out] =
    (self zipWith that)((a, b) => zippable.zip(a, b))

  /**
   * A symbolic alias for `orElseEither`.
   */
  final def <+>[E1 >: E, B](that: => Fiber[E1, B])(implicit ev: CanFail[E]): Fiber.Synthetic[E1, Either[A, B]] =
    self.orElseEither(that)

  /**
   * A symbolic alias for `orElse`.
   */
  def <>[E1, A1 >: A](that: => Fiber[E1, A1])(implicit ev: CanFail[E]): Fiber.Synthetic[E1, A1] =
    self.orElse(that)

  /**
   * Maps the output of this fiber to the specified constant.
   *
   * @param b
   *   constant
   * @tparam B
   *   new success type of the fiber
   * @return
   *   `Fiber[E, B]` fiber mapped to constant
   */
  final def as[B](b: => B): Fiber.Synthetic[E, B] =
    self map (_ => b)

  /**
   * Awaits the fiber, which suspends the awaiting fiber until the result of the
   * fiber has been determined. The result is the fiber's [[Exit]]: a failure,
   * defect or interruption of the fiber comes back as a value, so the returned
   * effect never fails. Unlike `join`, it does not merge the fiber's `FiberRef`
   * values into the awaiting fiber.
   *
   * @return
   *   `UIO[Exit[E, A]]`
   */
  def await(implicit trace: Trace): UIO[Exit[E, A]]

  /**
   * Retrieves the immediate children of the fiber that are still running.
   * Fibers built with `zip`, `zipWith` or `orElse` return the children of
   * their left fiber only, `Fiber.collectAll` returns the children of all its
   * fibers, and fibers made from a value or a future have none.
   */
  def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]]

  /**
   * Folds over the runtime or synthetic fiber.
   */
  final def fold[Z](
    runtime: Fiber.Runtime[E, A] => Z,
    synthetic: Fiber.Synthetic[E, A] => Z
  ): Z =
    self match {
      case fiber: Fiber.Runtime[_, _]   => runtime(fiber.asInstanceOf[Fiber.Runtime[E, A]])
      case fiber: Fiber.Synthetic[_, _] => synthetic(fiber.asInstanceOf[Fiber.Synthetic[E, A]])
    }

  /**
   * The identity of the fiber. A fiber built from several fibers (`zip`,
   * `orElse`, `Fiber.collectAll`) has the combined identity of its fibers, and
   * a fiber made from a value or a future has `FiberId.None`.
   */
  def id: FiberId

  /**
   * Merges the fiber's current `FiberRef` values into the calling fiber, using
   * each `FiberRef`'s `join` function, as `join` does when the fiber succeeds.
   * It does not wait for the fiber to finish: on a running fiber it takes the
   * values the fiber has at that moment. The fiber's runtime flags are merged
   * too, except for its interruptibility.
   *
   * {{{
   * val program: UIO[Int] =
   *   ZIO.scoped {
   *     for {
   *       counter <- FiberRef.make(0)
   *       started <- Promise.make[Nothing, Unit]
   *       fiber   <- (counter.set(10) *> started.succeed(()) *> ZIO.never).fork
   *       _       <- started.await
   *       _       <- fiber.inheritAll
   *       value   <- counter.get
   *       _       <- fiber.interrupt
   *     } yield value
   *   }
   * // succeeds with 10
   * }}}
   *
   * @return
   *   `UIO[Unit]`
   */
  def inheritAll(implicit trace: Trace): UIO[Unit]

  /**
   * Interrupts the fiber from whichever fiber is calling this method. If the
   * fiber has already exited, the returned effect will resume immediately.
   * Otherwise, the effect will resume when the fiber exits, after its
   * finalizers have run. The result is the fiber's [[Exit]]: an interruption
   * that names the calling fiber, or the fiber's own result when it had
   * already ended.
   *
   * @return
   *   `UIO[Exit[E, A]]`
   */
  final def interrupt(implicit trace: Trace): UIO[Exit[E, A]] =
    ZIO.fiberIdWith(self.interruptAs)

  /**
   * Interrupts the fiber as if interrupted from the specified fiber. If the
   * fiber has already exited, the returned effect will resume immediately.
   * Otherwise, the effect will resume when the fiber exits. The interruption
   * in the resulting [[Exit]] names `fiberId`.
   *
   * @return
   *   `UIO[Exit[E, A]]`
   */
  def interruptAs(fiberId: FiberId)(implicit trace: Trace): UIO[Exit[E, A]] =
    self.interruptAsFork(fiberId) *> self.await

  /**
   * Sends an interruption to the fiber as if interrupted from the specified
   * fiber, and does not wait for the fiber to exit. Use `await` to wait for
   * it.
   *
   * On a fiber made with `Fiber.fromFuture` from a `Future` that is not a
   * `CancelableFuture`, there is nothing to cancel, so the returned effect
   * waits for the `Future` to complete.
   *
   * @return
   *   `UIO[Unit]`
   */
  def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit]

  /**
   * Interrupts the fiber from whichever fiber is calling this method, without
   * waiting for the fiber to exit: on a runtime fiber the returned effect
   * resumes as soon as the interruption has been sent. Use `await` to wait for
   * the fiber to exit.
   *
   * On a fiber made with `Fiber.fromFuture` from a `Future` that is not a
   * `CancelableFuture`, the returned effect waits for the `Future` to
   * complete.
   *
   * @return
   *   `UIO[Unit]`
   */
  final def interruptFork(implicit trace: Trace): UIO[Unit] = ZIO.fiberIdWith(fiberId => self.interruptAsFork(fiberId))

  /**
   * Joins the fiber, which suspends the joining fiber until the result of the
   * fiber has been determined. Attempting to join a fiber that has erred will
   * result in a catchable error. Joining an interrupted fiber will result in an
   * "inner interruption" of this fiber, unlike interruption triggered by
   * another fiber, "inner interruption" can be caught and recovered (with
   * `catchAllCause`, for example).
   *
   * When the fiber succeeds, `join` also merges the fiber's `FiberRef` values
   * into the joining fiber (see `inheritAll`); when it fails, they are not
   * merged.
   *
   * @return
   *   `IO[E, A]`
   */
  final def join(implicit trace: Trace): IO[E, A] =
    await.unexit <* inheritAll

  /**
   * Maps over the value the Fiber computes. The function runs each time the
   * result is read with `await`, `join` or `poll`.
   *
   * @param f
   *   mapping function
   * @tparam B
   *   result type of f
   * @return
   *   `Fiber[E, B]` mapped fiber
   */
  final def map[B](f: A => B): Fiber.Synthetic[E, B] =
    mapZIO(f andThen Exit.succeed)

  /**
   * Passes the success of this fiber to the specified callback, and continues
   * with the fiber that it returns. The returned effect waits for this fiber to
   * finish; when this fiber fails, it returns a fiber that has failed with the
   * same cause.
   *
   * @param f
   *   The callback.
   * @tparam B
   *   The success type of the continued fiber.
   * @return
   *   `Fiber[E, B]` The continued fiber.
   */
  final def mapFiber[E1 >: E, B](f: A => Fiber[E1, B])(implicit trace: Trace): UIO[Fiber[E1, B]] =
    self.await.map(_.foldExit(Fiber.failCause(_), f))

  /**
   * Effectually maps over the value the fiber computes. The effect runs each
   * time the result is read with `await`, `join` or `poll`, on the fiber that
   * reads it.
   */
  final def mapZIO[E1 >: E, B](f: A => IO[E1, B]): Fiber.Synthetic[E1, B] =
    new Fiber.Synthetic[E1, B] {
      final def await(implicit trace: Trace): UIO[Exit[E1, B]] =
        self.await.flatMap(_.foreach(f))

      final def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = self.children

      def id: FiberId = self.id
      final def inheritAll(implicit trace: Trace): UIO[Unit] =
        self.inheritAll
      final def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit] =
        self.interruptAsFork(id)
      final def poll(implicit trace: Trace): UIO[Option[Exit[E1, B]]] =
        self.poll.flatMap(_.fold[UIO[Option[Exit[E1, B]]]](Exit.none)(_.foreach(f).map(Some(_))))
    }

  /**
   * Returns a fiber that prefers `this` fiber, but falls back to the `that` one
   * when `this` one fails, dies or is interrupted. Awaiting the returned fiber
   * waits for both fibers, `this` one first, even when `this` one succeeds.
   * Interrupting the returned fiber sends the interruption to `this` fiber and
   * then to `that` one, and then waits for both; their finalizers can run
   * concurrently.
   *
   * {{{
   * val program: IO[String, Int] =
   *   for {
   *     primary  <- ZIO.fail("primary unavailable").as(1).fork
   *     fallback <- ZIO.succeed(2).fork
   *     result   <- primary.orElse(fallback).join
   *   } yield result
   * // succeeds with 2
   * }}}
   *
   * @param that
   *   fiber to fall back to
   * @tparam E1
   *   error type
   * @tparam A1
   *   success type of the returned fiber
   * @return
   *   `Fiber[E1, A1]`
   */
  def orElse[E1, A1 >: A](that: => Fiber[E1, A1])(implicit ev: CanFail[E]): Fiber.Synthetic[E1, A1] =
    new Fiber.Synthetic[E1, A1] {
      final def await(implicit trace: Trace): UIO[Exit[E1, A1]] =
        self.await.zipWith(that.await) {
          case (e1 @ Exit.Success(_), _) => e1
          case (_, e2)                   => e2

        }

      final def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = self.children
      final def id: FiberId                                                      = self.id <> that.id

      final def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit] =
        self.interruptAsFork(id) *> that.interruptAsFork(id)

      final def inheritAll(implicit trace: Trace): UIO[Unit] =
        that.inheritAll *> self.inheritAll

      final def poll(implicit trace: Trace): UIO[Option[Exit[E1, A1]]] =
        self.poll.zipWith(that.poll) {
          case (Some(e1 @ Exit.Success(_)), _) => Some(e1)
          case (Some(_), o2)                   => o2
          case _                               => None
        }
    }

  /**
   * Returns a fiber that prefers `this` fiber, but falls back to the `that` one
   * when `this` one fails, dies or is interrupted. The value of `this` fiber
   * comes back in a `Left`, the value of `that` one in a `Right`. As with
   * `orElse`, awaiting the returned fiber waits for both fibers, and
   * interrupting it interrupts both.
   *
   * @param that
   *   fiber to fall back to
   * @tparam E1
   *   error type
   * @tparam B
   *   success type of the other fiber
   * @return
   *   `Fiber[E1, Either[A, B]]`
   */
  final def orElseEither[E1, B](that: => Fiber[E1, B]): Fiber.Synthetic[E1, Either[A, B]] =
    (self map (Left(_))) orElse (that map (Right(_)))

  /**
   * Tentatively observes the fiber, but returns immediately if it is not
   * already done: the result is `Some(exit)` when the fiber has ended and
   * `None` while it is still running.
   *
   * {{{
   * val program: UIO[(Option[Exit[Nothing, String]], Option[Exit[Nothing, String]])] =
   *   for {
   *     fiber  <- ZIO.sleep(100.millis).as("report").fork
   *     before <- fiber.poll
   *     _      <- fiber.await
   *     after  <- fiber.poll
   *   } yield (before, after)
   * // succeeds with (None,Some(Success(report)))
   * }}}
   *
   * @return
   *   `UIO[Option[Exit[E, A]]]`
   */
  def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]]

  /**
   * Converts this fiber into a scoped [[zio.ZIO]]. The fiber is interrupted
   * when the scope is closed.
   */
  final def scoped(implicit trace: Trace): ZIO[Scope, Nothing, Fiber[E, A]] =
    ZIO.acquireRelease(ZIO.succeed(self))(_.interrupt)

  /**
   * Converts this fiber into a [[scala.concurrent.Future]] that completes with
   * the fiber's result. Cancelling the returned [[CancelableFuture]]
   * interrupts the fiber.
   *
   * @param ev
   *   implicit witness that E is a subtype of Throwable
   * @return
   *   `UIO[CancelableFuture[A]]`
   */
  final def toFuture(implicit ev: E IsSubtypeOfError Throwable, trace: Trace): UIO[CancelableFuture[A]] =
    self toFutureWith ev

  /**
   * Converts this fiber into a [[scala.concurrent.Future]], translating any
   * errors to [[java.lang.Throwable]] with the specified conversion function,
   * using [[Cause.squashTraceWith]]. Cancelling the returned
   * [[CancelableFuture]] interrupts the fiber.
   *
   * @param f
   *   function that converts the error into a Throwable
   * @return
   *   `UIO[CancelableFuture[A]]`
   */
  final def toFutureWith(f: E => Throwable)(implicit trace: Trace): UIO[CancelableFuture[A]] =
    ZIO.suspendSucceed {
      val p: scala.concurrent.Promise[A] = scala.concurrent.Promise[A]()

      def failure(cause: Cause[E]): UIO[p.type] = Exit.succeed(p.failure(cause.squashTraceWith(f)))
      def success(value: A): UIO[p.type]        = Exit.succeed(p.success(value))

      val completeFuture =
        self.await.flatMap(_.foldExitZIO[Any, Nothing, p.type](failure, success))

      for {
        runtime <- ZIO.runtime[Any]
        _       <- completeFuture.forkDaemon // Cannot afford to NOT complete the promise, no matter what, so we fork daemon
      } yield new CancelableFuture[A](p.future) {
        def cancel(): Future[Exit[Throwable, A]] =
          runtime.unsafe.runToFuture[Nothing, Exit[Throwable, A]](self.interrupt.map(_.mapErrorExit(f)))(
            trace,
            Unsafe.unsafe
          )
      }
    }.uninterruptible

  /**
   * Maps the output of this fiber to `()`.
   *
   * @return
   *   `Fiber[E, Unit]` fiber mapped to `()`
   */
  final def unit: Fiber.Synthetic[E, Unit] = as(())

  /**
   * Named alias for `<*>`: zips this fiber and the specified fiber together,
   * producing a tuple of their output. It behaves like `zipWith`.
   *
   * {{{
   * val program: UIO[(Int, String)] =
   *   for {
   *     count <- ZIO.succeed(3).fork
   *     label <- ZIO.succeed("items").fork
   *     both  <- count.zip(label).join
   *   } yield both
   * // succeeds with (3,items)
   * }}}
   *
   * @param that
   *   fiber to be zipped
   * @tparam E1
   *   error type
   * @tparam B
   *   success type of that fiber
   * @return
   *   `Fiber[E1, (A, B)]` combined fiber
   */
  final def zip[E1 >: E, B](that: => Fiber[E1, B])(implicit
    zippable: Zippable[A, B]
  ): Fiber.Synthetic[E1, zippable.Out] =
    self <*> that

  /**
   * Named alias for `<*`.
   *
   * @param that
   *   fiber to be zipped
   * @tparam E1
   *   error type
   * @tparam B
   *   type of the fiber
   * @return
   *   `Fiber[E1, A]` combined fiber
   */
  final def zipLeft[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, A] =
    self <* that

  /**
   * Named alias for `*>`.
   *
   * @param that
   *   fiber to be zipped
   * @tparam E1
   *   error type
   * @tparam B
   *   type of the fiber
   * @return
   *   `Fiber[E1, B]` combined fiber
   */
  final def zipRight[E1 >: E, B](that: Fiber[E1, B]): Fiber.Synthetic[E1, B] =
    self *> that

  /**
   * Zips this fiber with the specified fiber, combining their results using the
   * specified combiner function.
   *
   * Awaiting the returned fiber waits for this fiber and then for that one,
   * even when this one has already failed. When both fail, the causes are
   * combined in parallel (`&&`). Interrupting the returned fiber sends the
   * interruption to this fiber and then to that one, and then waits for both;
   * their finalizers can run concurrently. `join` and `inheritAll` merge the
   * `FiberRef` values of that fiber first and of this fiber second, so for a
   * `FiberRef` made with the default `join` function the value of this fiber
   * wins.
   *
   * {{{
   * val program: UIO[String] =
   *   for {
   *     quantity <- ZIO.succeed(2).fork
   *     item     <- ZIO.succeed("apple").fork
   *     order    <- quantity.zipWith(item)((n, name) => s"\$n x \$name").join
   *   } yield order
   * // succeeds with 2 x apple
   * }}}
   *
   * @param that
   *   fiber to be zipped
   * @param f
   *   function to combine the results of both fibers
   * @tparam E1
   *   error type
   * @tparam B
   *   type of that fiber
   * @tparam C
   *   type of the resulting fiber
   * @return
   *   `Fiber[E1, C]` combined fiber
   */
  final def zipWith[E1 >: E, B, C](that: => Fiber[E1, B])(f: (A, B) => C): Fiber.Synthetic[E1, C] =
    new Fiber.Synthetic[E1, C] {
      final def await(implicit trace: Trace): UIO[Exit[E1, C]] =
        self.await.zipWith(that.await)(_.zipWith(_)(f, _ && _))

      final def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = self.children

      final def id: FiberId = self.id <> that.id

      final def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit] =
        self.interruptAsFork(id) *> that.interruptAsFork(id)

      final def inheritAll(implicit trace: Trace): UIO[Unit] = that.inheritAll *> self.inheritAll

      final def poll(implicit trace: Trace): UIO[Option[Exit[E1, C]]] =
        self.poll.zipWith(that.poll) {
          case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(f, _ && _))
          case _                    => None
        }
    }
}

/**
 * Constructors and operators for [[Fiber]] values: fibers that are already
 * done (`succeed`, `fail`, `done`, `never`), fibers backed by a `Future`
 * (`fromFuture`), operations on collections of fibers (`collectAll`,
 * `joinAll`, `awaitAll`, `interruptAll`), and fiber dumps (`dumpAll`,
 * `roots`).
 */
object Fiber extends FiberPlatformSpecific {

  /**
   * A fiber that runs an effect on the ZIO runtime, created by forking an
   * effect (`ZIO#fork` and its variants) or by running one with a `Runtime`.
   * Runtime fibers have an identity, a status, a trace and their own
   * `FiberRef` values.
   */
  sealed abstract class Runtime[+E, +A] extends Fiber.Internal[E, A] { self =>

    private[zio] def shouldYieldBeforeFork(): Boolean

    /**
     * The location the fiber was forked from.
     */
    def location: Trace

    /**
     * Generates a fiber dump: the fiber's identity, its current status and its
     * trace, which `Fiber.Dump#prettyPrint` renders as text.
     */
    final def dump(implicit trace: Trace): UIO[Fiber.Dump] =
      for {
        status <- self.status
        trace  <- self.trace
      } yield Fiber.Dump(self.id, status, trace)

    /**
     * Returns the fiber's `FiberRef` values. Read from another fiber while this
     * one runs, the values are a snapshot that may already be out of date.
     */
    def fiberRefs(implicit trace: Trace): UIO[FiberRefs]

    /**
     * The identity of the fiber.
     */
    override def id: FiberId.Runtime

    /**
     * Returns the fiber's current runtime flags, such as whether it is
     * interruptible.
     */
    def runtimeFlags(implicit trace: Trace): UIO[RuntimeFlags]

    /**
     * The status of the fiber: [[Fiber.Status.Done]] once it has ended,
     * [[Fiber.Status.Running]] while it executes on a thread, and
     * [[Fiber.Status.Suspended]] while it waits, for example on `await`, a
     * `Promise` or `ZIO.sleep`.
     *
     * {{{
     * val program: UIO[(Boolean, Boolean)] =
     *   for {
     *     fiber     <- ZIO.never.fork
     *     waiting   <- fiber.status.repeatUntil(_.isSuspended)
     *     _         <- fiber.interrupt
     *     finished  <- fiber.status
     *   } yield (waiting.isSuspended, finished.isDone)
     * // succeeds with (true,true)
     * }}}
     */
    def status(implicit trace: Trace): UIO[Fiber.Status]

    /**
     * The trace of the fiber: the locations of the effects that the fiber is
     * running, most recent first.
     */
    def trace(implicit trace: Trace): UIO[StackTrace]

    /**
     * Operations on the fiber that run outside of ZIO effects, and need an
     * implicit [[Unsafe]] value.
     */
    def unsafe: UnsafeAPI

    /**
     * The operations of `Fiber.Runtime#unsafe`. They take effect directly
     * instead of describing an effect. Operations that change the fiber are
     * sent to it as messages, and the fiber applies them when it processes its
     * messages.
     */
    trait UnsafeAPI {

      /**
       * Registers a callback that receives the fiber's [[Exit]] when the fiber
       * ends. When the fiber has already ended, the callback runs at once on
       * the calling thread.
       */
      def addObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit

      /**
       * Removes the value of the specified `FiberRef` from the fiber, which
       * then reads the `FiberRef`'s initial value.
       */
      def deleteFiberRef(ref: FiberRef[_])(implicit unsafe: Unsafe): Unit

      /**
       * Returns the fiber's `FiberRef` values. Called from another thread
       * while the fiber runs, the values may already be out of date.
       */
      def getFiberRefs()(implicit unsafe: Unsafe): FiberRefs

      /**
       * Removes a callback that was registered with `addObserver`. Callbacks
       * are compared by reference.
       */
      def removeObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit

      /**
       * Returns `Some(exit)` when the fiber has ended and `None` while it is
       * still running, without waiting.
       */
      def poll(implicit unsafe: Unsafe): Option[Exit[E, A]]

      /**
       * Sends an interruption with the specified cause to the fiber, and
       * returns without waiting for the fiber to exit.
       */
      def interrupt(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit = ()
    }

    /**
     * Adds a weakly-held reference to the specified fiber inside the children
     * set.
     *
     * '''NOTE''': This method must be invoked by the fiber itself.
     */
    private[zio] def addChild(child: Fiber.Runtime[_, _]): Unit
    private[zio] def addChildren(children: Iterable[Fiber.Runtime[_, _]]): Unit

    /**
     * Deletes the specified fiber ref.
     *
     * '''NOTE''': This method must be invoked by the fiber itself.
     */
    private[zio] def deleteFiberRef(ref: FiberRef[_]): Unit

    /**
     * Generates a full stack trace from the reified stack.
     *
     * '''NOTE''': This method must be invoked by the fiber itself.
     */
    private[zio] def generateStackTrace(): StackTrace

    /**
     * Retrieves the current executor that effects are executed on.
     *
     * '''NOTE''': This method is safe to invoke on any fiber, but if not
     * invoked on this fiber, then values derived from the fiber's state
     * (including the log annotations and log level) may not be up-to-date.
     */
    private[zio] def getCurrentExecutor(): Executor

    /**
     * Retrieves the state of the fiber ref, or else its initial value.
     *
     * '''NOTE''': This method is safe to invoke on any fiber, but if not
     * invoked on this fiber, then values derived from the fiber's state
     * (including the log annotations and log level) may not be up-to-date.
     */
    private[zio] def getFiberRef[A](fiberRef: FiberRef[A]): A

    /**
     * Retrieves the state of the fiber ref, or `null` if it hasn't been
     * modified.
     *
     * '''NOTE''': This method is safe to invoke on any fiber, but if not
     * invoked on this fiber, then values derived from the fiber's state
     * (including the log annotations and log level) may not be up-to-date.
     */
    private[zio] def getFiberRefOrNull[A](fiberRef: FiberRef[A]): A

    /**
     * Retrieves all fiber refs of the fiber.
     *
     * '''NOTE''': This method is safe to invoke on any fiber, but if not
     * invoked on this fiber, then values derived from the fiber's state
     * (including the log annotations and log level) may not be up-to-date.
     */
    private[zio] def getFiberRefs(): FiberRefs = getFiberRefs(true)

    /**
     * Retrieves all fiber refs of the fiber. If `updateRuntimeFlagsWithin` is
     * set to `false`, the FiberRefs will ''not'' be updated with the Fiber's
     * current runtime flags. This can help performance for cases that we don't
     * need to extract the runtime flags from the FiberRefs.
     *
     * '''NOTE''': This method is safe to invoke on any fiber, but if not
     * invoked on this fiber, then values derived from the fiber's state
     * (including the log annotations and log level) may not be up-to-date.
     */
    private[zio] def getFiberRefs(updateRuntimeFlagsWithin: Boolean): FiberRefs

    /**
     * Retrieves the executor that this effect is currently executing on.
     *
     * '''NOTE''': This method must be invoked by the fiber itself.
     */
    private[zio] def getRunningExecutor(): Option[Executor]

    /**
     * Boolean flag which indicates whether the fiber contains children that are
     * currently running
     *
     * '''NOTE''': This method is safe to invoke from any fiber
     */
    private[zio] def hasChildrenAlive(implicit trace: Trace): UIO[Boolean]

    private[zio] def isAlive(): Boolean

    /**
     * Determines if the specified throwable is fatal: returns `true` for a
     * `java.lang.VirtualMachineError` and `false` otherwise, whatever the
     * fiber's state. Kept only for binary compatibility.
     */
    @deprecated("IsFatal is deprecated, kept only for binary compatability.", "2.1.21")
    private[zio] final def isFatal(t: Throwable): Boolean = !nonFatal(t)

    /**
     * Logs using the current set of loggers.
     *
     * '''NOTE''': This method is safe to invoke on any fiber, but if not
     * invoked on this fiber, then values derived from the fiber's state
     * (including the log annotations and log level) may not be up-to-date.
     */
    private[zio] def log(
      message: () => String,
      cause: Cause[Any],
      overrideLogLevel: Option[LogLevel],
      trace: Trace
    ): Unit

    private[zio] def scope: FiberScope

    /**
     * Sets the fiber ref to the specified value.
     *
     * '''NOTE''': This method must be invoked by the fiber itself.
     */
    private[zio] def setFiberRef[A](fiberRef: FiberRef[A], value: A): Unit

    /**
     * Resets the fiber ref to its initial value.
     *
     * '''NOTE''': This method must be invoked by the fiber itself.
     */
    private[zio] def resetFiberRef(fiberRef: FiberRef[?]): Unit

    /**
     * Wholesale replaces all fiber refs of this fiber.
     *
     * '''NOTE''': This method must be invoked by the fiber itself.
     */
    private[zio] def setFiberRefs(fiberRefs: FiberRefs): Unit

    /**
     * Adds a message to add a child to this fiber.
     */
    private[zio] def tellAddChild(child: Fiber.Runtime[_, _]): Unit
    private[zio] def tellAddChildren(children: Iterable[Fiber.Runtime[_, _]]): Unit

    /**
     * Adds a message to interrupt this fiber.
     */
    private[zio] def tellInterrupt(cause: Cause[Nothing]): Unit

    /**
     * Transfers all children of this fiber that are currently running to the
     * specified fiber scope
     *
     * '''NOTE''': This method must be invoked by the fiber itself after it has
     * evaluated the effects but prior to exiting
     */
    private[zio] def transferChildren(scope: FiberScope): Unit

  }

  private[zio] object Runtime {

    implicit val fiberOrdering: Ordering[Fiber.Runtime[?, ?]] = { (x, y) =>
      val byTime = x.id.startTimeMillis.compare(y.id.startTimeMillis)
      if (byTime == 0) x.id.id.compare(y.id.id)
      else byTime
    }

    abstract class Internal[+E, +A] extends Runtime[E, A]
  }

  /**
   * A synthetic fiber is not a separate computation: it is created from a pure
   * value or an [[Exit]] (`Fiber.succeed`, `Fiber.done`), from a future
   * (`Fiber.fromFuture`), or by combining existing fibers (`zip`, `orElse`,
   * `map`, `Fiber.collectAll`).
   */
  sealed abstract class Synthetic[+E, +A] extends Fiber.Internal[E, A] {}

  private[zio] object Synthetic {
    abstract class Internal[+E, +A] extends Synthetic[E, A]
  }

  private[zio] abstract class Internal[+E, +A] extends Fiber[E, A]

  /**
   * A record containing information about a [[Fiber]], as returned by
   * `ZIO.descriptor` for the fiber that runs it.
   *
   * @param id
   *   The fiber's unique identifier
   * @param status
   *   The fiber's status, with its runtime flags and its current trace
   * @param interrupters
   *   The set of fibers that have tried to interrupt the fiber. It is empty
   *   when nothing has interrupted it; a forked child starts with an empty
   *   set.
   * @param executor
   *   The [[Executor]] executing this fiber
   * @param isLocked
   *   Whether the fiber is locked to its executor, as inside
   *   `ZIO#onExecutor`
   */
  final case class Descriptor(
    id: FiberId.Runtime,
    status: Status.Running,
    interrupters: Set[FiberId],
    executor: Executor,
    isLocked: Boolean
  ) {

    /**
     * Whether the fiber is in an interruptible or an uninterruptible region,
     * read from its runtime flags.
     */
    def interruptStatus: InterruptStatus =
      InterruptStatus.fromBoolean(RuntimeFlags.interruption(status.runtimeFlags))
  }

  /**
   * A snapshot of a runtime fiber, made by `Fiber.Runtime#dump`, `Fiber.dumpAll`
   * and `Fiber.dumpAllWith`: the fiber's identity, its status and its trace.
   */
  final case class Dump(fiberId: FiberId.Runtime, status: Status, trace: StackTrace) extends Product with Serializable {
    self =>

    /**
     * Renders the dump as text: the fiber's thread name, the time since it
     * started, what it waits on (when it is suspended and `blockingOn` is not
     * `FiberId.None`), its status and its trace.
     *
     * {{{
     * val program: UIO[String] =
     *   for {
     *     worker  <- ZIO.never.fork
     *     waiter  <- worker.await.fork
     *     _       <- waiter.status.repeatUntil(_.isSuspended)
     *     dump    <- waiter.dump
     *     text    <- dump.prettyPrint
     *     _       <- worker.interrupt
     *   } yield text
     * // succeeds with a text like:
     * // "zio-fiber-1690346806" (12ms) waiting on #1043239547
     * //   Status: Suspended((Interruption, CooperativeYielding, FiberRoots), ...)
     * //   at ...
     * }}}
     */
    def prettyPrint(implicit trace: Trace): UIO[String] =
      FiberRenderer.prettyPrint(self)
  }

  /**
   * The status of a runtime fiber, as returned by `Fiber.Runtime#status`:
   * [[Fiber.Status.Done]], [[Fiber.Status.Running]] or
   * [[Fiber.Status.Suspended]].
   *
   * {{{
   * def describe(status: Fiber.Status): String =
   *   status match {
   *     case Fiber.Status.Done                           => "done"
   *     case Fiber.Status.Running(_, _)                  => "running"
   *     case Fiber.Status.Suspended(_, _, FiberId.None)  => "suspended"
   *     case Fiber.Status.Suspended(_, _, blockingOn)    => s"waiting on \$blockingOn"
   *   }
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/fiber/fiberstatus/ Fiber.Status]]
   */
  sealed trait Status { self =>

    /** Returns `true` when the fiber has ended. */
    def isDone: Boolean = self eq Status.Done

    /** Returns `true` when the fiber is executing on a thread. */
    def isRunning: Boolean = self.isInstanceOf[Status.Running]

    /** Returns `true` when the fiber is waiting, for example on `await`. */
    def isSuspended: Boolean = self.isInstanceOf[Status.Suspended]
  }

  /**
   * The cases of [[Fiber.Status]].
   */
  object Status {

    /**
     * The status of a fiber that has not ended yet: [[Fiber.Status.Running]]
     * or [[Fiber.Status.Suspended]].
     */
    sealed trait Unfinished extends Status {

      /** The fiber's runtime flags, such as whether it is interruptible. */
      def runtimeFlags: RuntimeFlags

      /** The location of the last effect the fiber ran. */
      def trace: Trace
    }

    /**
     * The status of a fiber that has ended, whether it succeeded, failed or
     * was interrupted.
     */
    case object Done extends Status {

      /** Always `Trace.empty`. */
      def trace: Trace = Trace.empty

      override def toString(): String = "Done"
    }

    /**
     * The status of a fiber that is executing on a thread.
     */
    final case class Running(runtimeFlags: RuntimeFlags, trace: Trace) extends Unfinished {
      override def toString(): String = {
        val currentLocation =
          if ((trace eq Trace.empty) || (trace eq null)) "<trace unavailable>"
          else trace

        s"Running(${RuntimeFlags.render(runtimeFlags)}, ${currentLocation})"
      }
    }
    /**
     * The status of a fiber that is waiting and not executing on any thread.
     * `blockingOn` is the identity that the operation the fiber waits in
     * records: the awaited fiber for `Fiber#await`, the fiber that created the
     * promise for `Promise#await`, and `FiberId.None` for `ZIO.sleep` and
     * `ZIO.never`.
     */
    final case class Suspended(
      runtimeFlags: RuntimeFlags,
      trace: Trace,
      blockingOn: FiberId
    ) extends Unfinished {
      override def toString(): String = {
        val currentLocation =
          if ((trace eq Trace.empty) || (trace eq null)) "<trace unavailable>"
          else trace

        s"Suspended(${RuntimeFlags.render(runtimeFlags)}, ${currentLocation}, ${blockingOn})"
      }
    }
  }

  /**
   * Awaits on all fibers to be completed, successfully or not. The fibers are
   * awaited one after the other, in the order of the collection; the returned
   * effect never fails.
   *
   * @param fs
   *   `Iterable` of fibers to be awaited
   * @return
   *   `UIO[Unit]`
   */
  def awaitAll(fs: Iterable[Fiber[Any, Any]])(implicit trace: Trace): UIO[Unit] =
    collectAll(fs).await.unit

  /**
   * Collects all fibers into a single fiber producing an in-order collection of
   * the results, of the same collection type as the input. Awaiting it waits
   * for every fiber; when several fail, their causes are combined in parallel
   * (`&&`).
   */
  def collectAll[E, A, Collection[+Element] <: Iterable[Element]](
    fibers: Collection[Fiber[E, A]]
  )(implicit bf: BuildFrom[Collection[Fiber[E, A]], A, Collection[A]]): Fiber.Synthetic[E, Collection[A]] =
    new Fiber.Synthetic[E, Collection[A]] {
      def await(implicit trace: Trace): UIO[Exit[E, Collection[A]]] =
        ZIO
          .foreach[Any, Nothing, Fiber[E, A], Exit[E, A], Iterable](fibers)(_.await)
          .map(Exit.collectAllPar(_).getOrElse(Exit.succeed(Iterable.empty)).mapExit(bf.fromSpecific(fibers)))
      final def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
        ZIO.foreachPar(Chunk.fromIterable(fibers))(_.children).map(_.flatten)

      final def id: FiberId = fibers.foldLeft(FiberId.None: FiberId)(_ <> _.id)

      def inheritAll(implicit trace: Trace): UIO[Unit] =
        ZIO.foreachDiscard(fibers)(_.inheritAll)
      def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
        ZIO
          .foreachDiscard(fibers)(_.interruptAsFork(fiberId))
      def poll(implicit trace: Trace): UIO[Option[Exit[E, Collection[A]]]] =
        ZIO
          .foreach[Any, Nothing, Fiber[E, A], Option[Exit[E, A]], Iterable](fibers)(_.poll)
          .map(_.foldRight[Option[Exit[E, List[A]]]](Some(Exit.succeed(Nil))) {
            case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)(_ :: _, _ && _))
            case _                    => None
          })
          .map(_.map(_.mapExit(bf.fromSpecific(fibers))))
    }

  /**
   * Collects all fibers into a single fiber discarding their results.
   */
  def collectAllDiscard[E, A](fibers: Iterable[Fiber[E, A]]): Fiber.Synthetic[E, Unit] =
    new Fiber.Synthetic[E, Unit] {
      def await(implicit trace: Trace): UIO[Exit[E, Unit]] =
        ZIO
          .foreach[Any, Nothing, Fiber[E, A], Exit[E, A], Iterable](fibers)(_.await)
          .map(_.foldLeft[Exit[E, Unit]](Exit.unit)(_ <& _))
      final def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
        ZIO.foreachPar(Chunk.fromIterable(fibers))(_.children).map(_.flatten)

      final def id: FiberId = fibers.foldLeft(FiberId.None: FiberId)(_ <> _.id)

      def inheritAll(implicit trace: Trace): UIO[Unit] =
        ZIO.foreachDiscard(fibers)(_.inheritAll)
      def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
        ZIO
          .foreachDiscard(fibers)(_.interruptAsFork(fiberId))
      def poll(implicit trace: Trace): UIO[Option[Exit[E, Unit]]] =
        ZIO
          .foreach[Any, Nothing, Fiber[E, A], Option[Exit[E, A]], Iterable](fibers)(_.poll)
          .map(_.foldRight[Option[Exit[E, Unit]]](Some(Exit.succeed(Nil))) {
            case (Some(ra), Some(rb)) => Some(ra.zipWith(rb)((_, b) => b, _ && _))
            case _                    => None
          })
    }

  /**
   * A fiber that is done with the specified [[zio.Exit]] value.
   *
   * @param exit
   *   [[zio.Exit]] value
   * @tparam E
   *   error type
   * @tparam A
   *   type of the fiber
   * @return
   *   `Fiber[E, A]`
   */
  def done[E, A](exit: => Exit[E, A]): Fiber.Synthetic[E, A] =
    new Fiber.Synthetic[E, A] {
      final def await(implicit trace: Trace): UIO[Exit[E, A]]                    = ZIO.succeed(exit)
      final def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = ZIO.succeed(Chunk.empty)
      final def id: FiberId                                                      = FiberId.None
      final def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit]   = ZIO.unit
      final def inheritAll(implicit trace: Trace): UIO[Unit]                     = ZIO.unit
      final def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]]             = ZIO.succeed(Some(exit))
    }

  /**
   * Dumps all fibers to the standard error of the console: each root fiber
   * (see `roots`) and its descendants, rendered with `Fiber.Dump#prettyPrint`.
   *
   * {{{
   * val program: ZIO[Any, java.io.IOException, Unit] =
   *   for {
   *     worker <- ZIO.never.fork
   *     _      <- Fiber.dumpAll
   *     _      <- worker.interrupt
   *   } yield ()
   * }}}
   */
  def dumpAll(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    dumpAllWith { dump =>
      dump.prettyPrint.flatMap(Console.printError(_))
    }

  /**
   * Dumps all fibers to the specified callback: each root fiber (see `roots`)
   * and then its descendants, one [[Fiber.Dump]] at a time.
   */
  def dumpAllWith[R, E](f: Dump => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] = {
    def process(fiber: Fiber.Runtime[_, _]): ZIO[R, E, Unit] =
      fiber.dump.flatMap(f) *> fiber.children.flatMap(chunk => ZIO.foreachDiscard(chunk)(process(_)))

    Fiber.roots.flatMap(_.mapZIODiscard(process(_)))
  }

  /**
   * A fiber that has already failed with the specified value.
   *
   * @param e
   *   failure value
   * @tparam E
   *   error type
   * @return
   *   `Fiber[E, Nothing]` failed fiber
   */
  def fail[E](e: E): Fiber.Synthetic[E, Nothing] = done(Exit.fail(e))

  /**
   * Creates a `Fiber` that has already failed with the specified cause.
   */
  def failCause[E](cause: Cause[E]): Fiber.Synthetic[E, Nothing] =
    done(Exit.failCause(cause))

  /**
   * Returns a `Fiber` that is backed by the specified `Future`. Interrupting
   * the fiber cancels a [[CancelableFuture]]; any other `Future` cannot be
   * cancelled, so the interruption waits for it to complete, and the fiber
   * then ends with the `Future`'s result.
   *
   * @param thunk
   *   `Future[A]` backing the `Fiber`
   * @tparam A
   *   type of the `Fiber`
   * @return
   *   `Fiber[Throwable, A]`
   */
  def fromFuture[A](thunk: => Future[A])(implicit trace: Trace): Fiber.Synthetic[Throwable, A] =
    new Fiber.Synthetic[Throwable, A] {
      lazy val ftr: Future[A] = thunk

      final def await(implicit trace: Trace): UIO[Exit[Throwable, A]] =
        ZIO.suspend(ZIO.fromFutureNow(ftr)(trace, Unsafe.unsafe)).exit

      final def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = ZIO.succeed(Chunk.empty)

      final def id: FiberId = FiberId.None

      final def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit] =
        ZIO.suspendSucceed {
          ftr match {
            case c: CancelableFuture[A] => ZIO.attempt(c.cancel()).ignore
            case _                      => join.ignore
          }
        }

      final def inheritAll(implicit trace: Trace): UIO[Unit] = ZIO.unit

      final def poll(implicit trace: Trace): UIO[Option[Exit[Throwable, A]]] =
        ZIO.succeed(ftr.value.map(Exit.fromTry))
    }

  /**
   * Lifts an [[zio.ZIO]] into a `Fiber`. The returned effect runs `io` to
   * completion first, on the current fiber, and then returns a fiber that is
   * already done with its result.
   *
   * @param io
   *   `IO[E, A]` to turn into a `Fiber`
   * @tparam E
   *   error type
   * @tparam A
   *   type of the fiber
   * @return
   *   `UIO[Fiber[E, A]]`
   */
  def fromZIO[E, A](io: IO[E, A])(implicit trace: Trace): UIO[Fiber.Synthetic[E, A]] =
    io.exit.map(done(_))

  /**
   * Interrupts all fibers, awaiting their interruption. The interruption is
   * sent to every fiber first, and then each fiber is awaited, so the fibers
   * wind down concurrently.
   *
   * @param fs
   *   `Iterable` of fibers to be interrupted
   * @return
   *   `UIO[Unit]`
   */
  def interruptAll(fs: Iterable[Fiber[Any, Any]])(implicit trace: Trace): UIO[Unit] =
    ZIO.fiberIdWith(interruptAllAs(_)(fs))

  /**
   * Interrupts all fibers as by the specified fiber, awaiting their
   * interruption.
   *
   * @param fiberId
   *   The identity of the fiber to interrupt as.
   * @param fs
   *   `Iterable` of fibers to be interrupted
   * @return
   *   `UIO[Unit]`
   */
  def interruptAllAs(fiberId: FiberId)(fs: Iterable[Fiber[Any, Any]])(implicit trace: Trace): UIO[Unit] =
    ZIO.foreachDiscard(fs)(_.interruptAsFork(fiberId)) *> ZIO.foreachDiscard(fs)(_.await)

  /**
   * A fiber that is already interrupted, with an interruption that names the
   * fiber `id`.
   *
   * @return
   *   `Fiber[Nothing, Nothing]` interrupted fiber
   */
  def interruptAs(id: FiberId): Fiber.Synthetic[Nothing, Nothing] =
    done(Exit.interrupt(id))

  /**
   * Joins all fibers: awaits every fiber, then succeeds when all of them
   * succeeded, and merges their `FiberRef` values into the joining fiber. When
   * some fibers fail, it fails with their causes combined in parallel (`&&`).
   * A fiber that was interrupted makes the result an "inner interruption",
   * which `catchAllCause` can recover from but `catchAll` cannot.
   *
   * @param fs
   *   `Iterable` of fibers to be joined
   * @return
   *   `IO[E, Unit]`
   */
  def joinAll[E](fs: Iterable[Fiber[E, Any]])(implicit trace: Trace): IO[E, Unit] =
    collectAll(fs).join.unit

  /**
   * A fiber that never fails or succeeds. Interrupting it has no effect, so
   * `await`, `join` and `interrupt` on it never complete.
   */
  val never: Fiber.Synthetic[Nothing, Nothing] =
    new Fiber.Synthetic[Nothing, Nothing] {
      final def await(implicit trace: Trace): UIO[Exit[Nothing, Nothing]]        = ZIO.never
      final def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = ZIO.succeed(Chunk.empty)
      final def id: FiberId                                                      = FiberId.None
      final def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit]   = ZIO.unit
      final def inheritAll(implicit trace: Trace): UIO[Unit]                     = ZIO.unit
      final def poll(implicit trace: Trace): UIO[Option[Exit[Nothing, Nothing]]] = ZIO.succeed(None)
    }

  /**
   * Returns a chunk containing all root fibers: the fibers that have no
   * parent, such as the main fiber and fibers started with `forkDaemon`,
   * recorded while the `FiberRoots` runtime flag is on (as it is by default).
   * Fibers that have ended are removed periodically, so the chunk may still
   * hold some of them. Due to concurrency, the returned chunk is only weakly
   * consistent.
   */
  def roots(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
    ZIO.succeed(Chunk.fromIterator(_roots.iterator))

  /**
   * Returns a fiber that has already succeeded with the specified value.
   *
   * @param a
   *   success value
   * @tparam A
   *   type of the fiber
   * @return
   *   `Fiber[Nothing, A]` succeeded fiber
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
   * always be `None` unless called from within an executing effect and this
   * feature is enabled using [[Runtime.enableCurrentFiber]].
   */
  def currentFiber()(implicit unsafe: Unsafe): Option[Fiber[Any, Any]] =
    Option(_currentFiber.get)

  private[zio] val _currentFiber: ThreadLocal[Fiber.Runtime[_, _]] =
    new ThreadLocal[Fiber.Runtime[_, _]]()

  private[zio] val _roots: WeakConcurrentBag[Fiber.Runtime[_, _]] =
    WeakConcurrentBag[Fiber.Runtime[_, _]](10000, _.isAlive())
      .withAutoGc(5.seconds)
}
