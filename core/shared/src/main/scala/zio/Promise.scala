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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicReference

/**
 * A promise represents an asynchronous variable, of [[zio.ZIO]] type, that can
 * be set exactly once, with the ability for an arbitrary number of fibers to
 * suspend (by calling `await`) and automatically resume when the variable is
 * set.
 *
 * Any fiber can complete a promise, with a value (`succeed`), an error
 * (`fail`), a defect (`die`), an interruption (`interrupt`), an `Exit` (`done`)
 * or an effect (`complete`, `completeWith`). The first completion wins: the
 * completion methods return `true` when they complete the promise, and `false`
 * when it was already completed, in which case they leave it unchanged.
 *
 * Promises can be used for building primitive actions whose completions require
 * the coordinated action of multiple fibers, and for building higher-level
 * concurrent or asynchronous structures.
 *
 * {{{
 * val program: UIO[Int] =
 *   for {
 *     promise <- Promise.make[Nothing, Int]
 *     _       <- promise.succeed(42).delay(1.second).fork
 *     value   <- promise.await // resumes when the forked fiber completes it
 *   } yield value
 * // 42
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/concurrency/promise/ Promise]]
 */
final class Promise[E, A] private (blockingOn: FiberId) extends Serializable {
  import Promise.internal._

  /**
   * Retrieves the value of the promise, suspending the fiber running the action
   * until the result is available. It then ends as the promise was completed:
   * it succeeds, fails, dies or is interrupted. When the promise was completed
   * with `completeWith`, each `await` runs the stored effect.
   *
   * The wait can be interrupted. Interrupting a waiting fiber does not complete
   * the promise.
   */
  def await(implicit trace: Trace): IO[E, A] =
    ZIO.suspendSucceed {
      state.get match {
        case Done(value) => value
        case pending =>
          ZIO.asyncInterrupt[Any, E, A](
            k => {
              @annotation.tailrec
              def loop(current: State[E, A]): Unit =
                current match {
                  case pending: Pending[?, ?] =>
                    if (state.compareAndSet(pending, pending.add(k))) ()
                    else loop(state.get)
                  case Done(value) => k(value)
                }
              loop(pending)

              Left(ZIO.succeed(state.updateAndGet {
                case pending: Pending[?, ?] => pending.remove(k)
                case completed              => completed
              }))
            },
            blockingOn
          )
      }
    }

  /**
   * Kills the promise with the specified defect, which will be propagated to
   * all fibers waiting on the value of the promise. Returns `false` when the
   * promise was already completed.
   */
  def die(e: Throwable)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.die(e)(trace, Unsafe))

  /**
   * Exits the promise with the specified exit, which will be propagated to all
   * fibers waiting on the value of the promise. Returns `false` when the
   * promise was already completed.
   */
  def done(e: Exit[E, A])(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.completeWith(e)(Unsafe))

  /**
   * Completes the promise with the result of the specified effect. If the
   * promise has already been completed, the method will produce false.
   *
   * `complete` runs `io` once, in the calling fiber, and stores its `Exit`, so
   * every waiter sees the same result. It runs `io` even when the promise is
   * already completed. When the calling fiber is interrupted while `io` runs,
   * the promise is completed with that interruption.
   *
   * `completeWith` does not run the effect: it stores it, and every `await`
   * runs it. Use it when you do not need to memoize the result of the
   * specified effect.
   */
  def complete(io: IO[E, A])(implicit trace: Trace): UIO[Boolean] = io.intoPromise(this)

  /**
   * Completes the promise with the specified effect. If the promise has already
   * been completed, the method will produce false.
   *
   * Note that since the promise is completed with an effect, the effect will be
   * evaluated each time the value of the promise is retrieved through
   * combinators such as `await`, potentially producing different results if the
   * effect produces different results on subsequent evaluations. In this
   * case the meaning of the "exactly once" guarantee of `Promise` is that the
   * promise can be completed with exactly one effect. For a version that
   * completes the promise with the result of an effect see
   * [[Promise.complete]].
   */
  def completeWith(io: IO[E, A])(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.completeWith(io)(Unsafe))

  /**
   * Fails the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise. Returns `false` when the
   * promise was already completed.
   */
  def fail(e: E)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.fail(e)(trace, Unsafe))

  /**
   * Fails the promise with the specified cause, which will be propagated to all
   * fibers waiting on the value of the promise. The stack trace of each waiting
   * fiber is attached to the cause it receives. Returns `false` when the
   * promise was already completed.
   */
  def failCause(e: Cause[E])(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.failCause(e)(trace, Unsafe))

  /**
   * Completes the promise with interruption by the fiber calling this method.
   * `await`, in the fibers waiting now and in the ones that await later, then
   * fails with an interruption cause that holds the id of that fiber. A
   * waiting fiber that does not handle the cause (with `catchAllCause`, for
   * example) ends as interrupted. Returns `false` when the promise was already
   * completed.
   */
  def interrupt(implicit trace: Trace): UIO[Boolean] =
    ZIO.fiberIdWith(id => interruptAs(id))

  /**
   * Completes the promise with interruption by the specified fiber. `await`,
   * in the fibers waiting now and in the ones that await later, then fails
   * with an interruption cause that holds `fiberId`. Returns `false` when the
   * promise was already completed.
   */
  def interruptAs(fiberId: FiberId)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.interruptAs(fiberId)(trace, Unsafe))

  /**
   * Checks for completion of this Promise. Produces true if this promise has
   * already been completed, in any way, and false otherwise.
   */
  def isDone(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.isDone(Unsafe))

  /**
   * Checks for completion of this Promise. Returns the result effect, in a
   * `Some`, if this promise has already been completed or a `None` otherwise.
   * It does not wait.
   */
  def poll(implicit trace: Trace): UIO[Option[IO[E, A]]] =
    ZIO.succeed(unsafe.poll(Unsafe))

  /**
   * Fails the promise with the specified cause, which will be propagated to all
   * fibers waiting on the value of the promise. No new stack trace is attached
   * to the cause. Returns `false` when the promise was already completed.
   */
  def refailCause(e: Cause[E])(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.refailCause(e)(trace, Unsafe))

  /**
   * Completes the promise with the specified value. Returns `false`, and
   * leaves the promise unchanged, when it was already completed.
   */
  def succeed(a: A)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.succeed(a)(trace, Unsafe))

  /**
   * Internally, you can use this method instead of calling
   * `myPromise.succeed(())`
   *
   * It avoids the `Exit` allocation
   */
  private[zio] def succeedUnit(implicit ev0: A =:= Unit, trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.succeedUnit(ev0, trace, Unsafe))

  private[zio] trait UnsafeAPI extends Serializable {
    /**
     * Completes the promise with the specified effect, which each waiter runs.
     * Returns `false` when the promise was already completed.
     */
    def completeWith(io: IO[E, A])(implicit unsafe: Unsafe): Boolean
    /**
     * Completes the promise with the specified defect. Returns `false` when
     * the promise was already completed.
     */
    def die(e: Throwable)(implicit trace: Trace, unsafe: Unsafe): Boolean
    /**
     * Completes the promise with the specified effect, as `completeWith` does,
     * without reporting whether it completed the promise.
     */
    def done(io: IO[E, A])(implicit unsafe: Unsafe): Unit
    /**
     * Completes the promise with the specified error. Returns `false` when the
     * promise was already completed.
     */
    def fail(e: E)(implicit trace: Trace, unsafe: Unsafe): Boolean
    /**
     * Completes the promise with the specified cause, to which each waiter
     * attaches its stack trace. Returns `false` when the promise was already
     * completed.
     */
    def failCause(e: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Boolean
    /**
     * Completes the promise with interruption by the specified fiber. Returns
     * `false` when the promise was already completed.
     */
    def interruptAs(fiberId: FiberId)(implicit trace: Trace, unsafe: Unsafe): Boolean
    /**
     * Returns `true` when the promise has been completed.
     */
    def isDone(implicit unsafe: Unsafe): Boolean
    /**
     * Returns the effect the promise was completed with, or `None` when it is
     * not completed yet.
     */
    def poll(implicit unsafe: Unsafe): Option[IO[E, A]]
    /**
     * Completes the promise with the specified cause, without attaching a new
     * stack trace. Returns `false` when the promise was already completed.
     */
    def refailCause(e: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Boolean
    /**
     * Completes the promise with the specified value. Returns `false` when the
     * promise was already completed.
     */
    def succeed(a: A)(implicit trace: Trace, unsafe: Unsafe): Boolean
    /**
     * Completes the promise with `()`, reusing a shared `Exit` value. Returns
     * `false` when the promise was already completed.
     */
    def succeedUnit(implicit ev0: A =:= Unit, trace: Trace, unsafe: Unsafe): Boolean
  }

  @deprecated("Kept for binary compatibility only. Do not use", "2.1.16")
  private[zio] def state: AtomicReference[Promise.internal.State[E, A]] =
    unsafe.asInstanceOf[AtomicReference[Promise.internal.State[E, A]]]
  private[zio] val unsafe: UnsafeAPI = new AtomicReference(Promise.internal.State.empty[E, A]) with UnsafeAPI { state =>
    def completeWith(io: IO[E, A])(implicit unsafe: Unsafe): Boolean = {
      @annotation.tailrec
      def loop(): Boolean =
        state.get match {
          case pending: Pending[?, ?] =>
            if (state.compareAndSet(pending, Done(io))) {
              pending.complete(io)
              true
            } else {
              loop()
            }
          case _ => false
        }
      loop()
    }

    def die(e: Throwable)(implicit trace: Trace, unsafe: Unsafe): Boolean =
      completeWith(ZIO.die(e))

    def done(io: IO[E, A])(implicit unsafe: Unsafe): Unit = completeWith(io)

    def fail(e: E)(implicit trace: Trace, unsafe: Unsafe): Boolean =
      completeWith(ZIO.fail(e))

    def failCause(e: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Boolean =
      completeWith(ZIO.failCause(e))

    def interruptAs(fiberId: FiberId)(implicit trace: Trace, unsafe: Unsafe): Boolean =
      completeWith(ZIO.interruptAs(fiberId))

    def isDone(implicit unsafe: Unsafe): Boolean =
      state.get().isInstanceOf[Done[?, ?]]

    def poll(implicit unsafe: Unsafe): Option[IO[E, A]] =
      state.get() match {
        case Done(value) => Some(value)
        case _           => None
      }

    def refailCause(e: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Boolean =
      completeWith(Exit.failCause(e))

    def succeed(a: A)(implicit trace: Trace, unsafe: Unsafe): Boolean =
      completeWith(Exit.succeed(a))

    override def succeedUnit(implicit ev0: A =:= Unit, trace: Trace, unsafe: Unsafe): Boolean =
      completeWith(Exit.unit.asInstanceOf[IO[E, A]])
  }

}
/**
 * Constructors for [[Promise]].
 */
object Promise {
  private[zio] object internal {
    sealed abstract class State[E, A]            extends Serializable
    final case class Done[E, A](value: IO[E, A]) extends State[E, A]
    sealed abstract class Pending[E, A] extends State[E, A] { self =>
      def complete(io: IO[E, A]): Unit
      def add(waiter: IO[E, A] => Any): Pending[E, A]
      def remove(waiter: IO[E, A] => Any): Pending[E, A]
      def size: Int
    }
    private case object Empty extends Pending[Nothing, Nothing] { self =>
      override def complete(io: IO[Nothing, Nothing]): Unit = ()
      def size                                              = 0
      def add(waiter: IO[Nothing, Nothing] => Any): Pending[Nothing, Nothing] =
        new Link[Nothing, Nothing](waiter, self) {
          override def size = 1
        }
      def remove(waiter: IO[Nothing, Nothing] => Any): Pending[Nothing, Nothing] = self
    }
    private sealed abstract class Link[E, A](final val waiter: IO[E, A] => Any, final val ws: Pending[E, A])
        extends Pending[E, A] {
      self =>
      final def add(waiter: IO[E, A] => Any): Pending[E, A] = new Link(waiter, self) {
        override val size = self.size + 1
      }
      final def complete(io: IO[E, A]): Unit =
        if (size == 1) waiter(io)
        else {
          var current: Pending[E, A] = self
          while (current ne Empty) {
            current match {
              case link: Link[?, ?] =>
                link.waiter(io)
                current = link.ws
              case _ => // Empty
                current = Empty.asInstanceOf[Pending[E, A]]
            }
          }
        }

      final def remove(waiter: IO[E, A] => Any): Pending[E, A] =
        if (size == 1) if (waiter eq self.waiter) ws else self
        else {
          val arr                = Link.materialize(self, size)
          var i                  = size - 1
          var acc: Pending[E, A] = Empty.asInstanceOf[Pending[E, A]]

          while (i >= 0) {
            if (arr(i) ne waiter) {
              acc = acc.add(arr(i))
            }
            i -= 1
          }
          acc
        }
    }

    private object Link {

      /**
       * Materializes the pending state into an array of waiters in reverse
       * order of the linked list, which is the order in which they were added
       * (oldest first).
       */
      def materialize[E, A](pending: Pending[E, A], size: Int): Array[IO[E, A] => Any] = {
        val array   = new Array[IO[E, A] => Any](size)
        var current = pending
        var i       = size - 1

        while (i >= 0) {
          current match {
            case link: Link[?, ?] =>
              array(i) = link.waiter
              current = link.ws
            case _ => () // Empty
          }
          i -= 1
        }
        array
      }
    }

    object State {
      def empty[E, A]: State[E, A] = Empty.asInstanceOf[State[E, A]]
    }
  }

  /**
   * Makes a new promise. Any fiber can complete it. The id of the fiber that
   * creates the promise is recorded as the fiber that the waiting fibers are
   * blocked on, as their `Fiber.Status.Suspended` shows.
   */
  def make[E, A](implicit trace: Trace): UIO[Promise[E, A]] =
    ZIO.fiberIdWith(id => Exit.succeed(unsafe.make(id)(Unsafe)))

  /**
   * Makes a new promise, recording `fiberId` as the fiber that the waiting
   * fibers are blocked on, as their `Fiber.Status.Suspended` shows. Any fiber
   * can complete the promise.
   */
  def makeAs[E, A](fiberId: => FiberId)(implicit trace: Trace): UIO[Promise[E, A]] =
    ZIO.succeed(unsafe.make(fiberId)(Unsafe))

  /**
   * Constructors that create a `Promise` outside of a ZIO effect. They need an
   * implicit `Unsafe` value.
   */
  object unsafe {
    /**
     * Makes a new promise outside of a ZIO effect, recording `fiberId` as the
     * fiber that the waiting fibers are blocked on.
     */
    def make[E, A](fiberId: FiberId)(implicit unsafe: Unsafe): Promise[E, A] = new Promise[E, A](fiberId)
  }
}
