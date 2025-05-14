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
 * Promises can be used for building primitive actions whose completions require
 * the coordinated action of multiple fibers, and for building higher-level
 * concurrent or asynchronous structures.
 * {{{
 * for {
 *   promise <- Promise.make[Nothing, Int]
 *   _       <- promise.succeed(42).delay(1.second).fork
 *   value   <- promise.await // Resumes when forked fiber completes promise
 * } yield value
 * }}}
 */
final class Promise[E, A] private (blockingOn: FiberId) extends Serializable {
  import Promise.internal._

  /**
   * Retrieves the value of the promise, suspending the fiber running the action
   * until the result is available.
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
   * Kills the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  def die(e: Throwable)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.die(e)(trace, Unsafe))

  /**
   * Exits the promise with the specified exit, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  def done(e: Exit[E, A])(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.completeWith(e)(Unsafe))

  /**
   * Completes the promise with the result of the specified effect. If the
   * promise has already been completed, the method will produce false.
   *
   * Note that [[Promise.completeWith]] will be much faster, so consider using
   * that if you do not need to memoize the result of the specified effect.
   */
  def complete(io: IO[E, A])(implicit trace: Trace): UIO[Boolean] = io.intoPromise(this)

  /**
   * Completes the promise with the specified effect. If the promise has already
   * been completed, the method will produce false.
   *
   * Note that since the promise is completed with an effect, the effect will be
   * evaluated each time the value of the promise is retrieved through
   * combinators such as `await`, potentially producing different results if the
   * effect produces different results on subsequent evaluations. In this case
   * te meaning of the "exactly once" guarantee of `Promise` is that the promise
   * can be completed with exactly one effect. For a version that completes the
   * promise with the result of an effect see [[Promise.complete]].
   */
  def completeWith(io: IO[E, A])(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.completeWith(io)(Unsafe))

  /**
   * Fails the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  def fail(e: E)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.fail(e)(trace, Unsafe))

  /**
   * Fails the promise with the specified cause, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  def failCause(e: Cause[E])(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.failCause(e)(trace, Unsafe))

  /**
   * Completes the promise with interruption. This will interrupt all fibers
   * waiting on the value of the promise as by the fiber calling this method.
   */
  def interrupt(implicit trace: Trace): UIO[Boolean] =
    ZIO.fiberIdWith(id => interruptAs(id))

  /**
   * Completes the promise with interruption. This will interrupt all fibers
   * waiting on the value of the promise as by the specified fiber.
   */
  def interruptAs(fiberId: FiberId)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.interruptAs(fiberId)(trace, Unsafe))

  /**
   * Checks for completion of this Promise. Produces true if this promise has
   * already been completed with a value or an error and false otherwise.
   */
  def isDone(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.isDone(Unsafe))

  /**
   * Checks for completion of this Promise. Returns the result effect if this
   * promise has already been completed or a `None` otherwise.
   */
  def poll(implicit trace: Trace): UIO[Option[IO[E, A]]] =
    ZIO.succeed(unsafe.poll(Unsafe))

  /**
   * Fails the promise with the specified cause, which will be propagated to all
   * fibers waiting on the value of the promise. No new stack trace is attached
   * to the cause.
   */
  def refailCause(e: Cause[E])(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(unsafe.refailCause(e)(trace, Unsafe))

  /**
   * Completes the promise with the specified value.
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
    def completeWith(io: IO[E, A])(implicit unsafe: Unsafe): Boolean
    def die(e: Throwable)(implicit trace: Trace, unsafe: Unsafe): Boolean
    def done(io: IO[E, A])(implicit unsafe: Unsafe): Unit
    def fail(e: E)(implicit trace: Trace, unsafe: Unsafe): Boolean
    def failCause(e: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Boolean
    def interruptAs(fiberId: FiberId)(implicit trace: Trace, unsafe: Unsafe): Boolean
    def isDone(implicit unsafe: Unsafe): Boolean
    def poll(implicit unsafe: Unsafe): Option[IO[E, A]]
    def refailCause(e: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Boolean
    def succeed(a: A)(implicit trace: Trace, unsafe: Unsafe): Boolean
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
       * order.
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
   * Makes a new promise to be completed by the fiber creating the promise.
   */
  def make[E, A](implicit trace: Trace): UIO[Promise[E, A]] =
    ZIO.fiberIdWith(id => Exit.succeed(unsafe.make(id)(Unsafe)))

  /**
   * Makes a new promise to be completed by the fiber with the specified id.
   */
  def makeAs[E, A](fiberId: => FiberId)(implicit trace: Trace): UIO[Promise[E, A]] =
    ZIO.succeed(unsafe.make(fiberId)(Unsafe))

  object unsafe {
    def make[E, A](fiberId: FiberId)(implicit unsafe: Unsafe): Promise[E, A] = new Promise[E, A](fiberId)
  }
}
