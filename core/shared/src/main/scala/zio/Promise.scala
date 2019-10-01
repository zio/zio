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

import java.util.concurrent.atomic.AtomicReference
import Promise.internal._

/**
 * A promise represents an asynchronous variable that can be set exactly once,
 * with the ability for an arbitrary number of fibers to suspend (by calling
 * `await`) and automatically resume when the variable is set.
 *
 * Promises can be used for building primitive actions whose completions
 * require the coordinated action of multiple fibers, and for building
 * higher-level concurrent or asynchronous structures.
 * {{{
 * for {
 *   promise <- Promise.make[Nothing, Int]
 *   _       <- promise.succeed(42).delay(1.second).fork
 *   value   <- promise.await // Resumes when forked fiber completes promise
 * } yield value
 * }}}
 */
class Promise[E, A] private (private val state: AtomicReference[State[E, A]]) extends AnyVal with Serializable {

  /**
   * Retrieves the value of the promise, suspending the fiber running the action
   * until the result is available.
   */
  final def await: IO[E, A] =
    IO.effectAsyncInterrupt[E, A](k => {
      var result   = null.asInstanceOf[Either[Canceler[Any], Exit[E, A]]]
      var retry    = true
      val wrappedK = (e: Exit[E, A]) => k(IO.done(e))

      while (retry) {
        val oldState = state.get

        val newState = oldState match {
          case Pending(joiners) =>
            result = Left(interruptJoiner(wrappedK))

            Pending(wrappedK :: joiners)
          case s @ Done(value) =>
            result = Right(value)

            s
        }

        retry = !state.compareAndSet(oldState, newState)
      }

      result match {
        case Right(value) => Right(IO.done(value))
        case Left(value)  => Left(value)
      }
    })

  /**
   * Kills the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def die(e: Throwable): UIO[Boolean] = complete(IO.die(e))

  /**
   * Exits the promise with the specified exit, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def done(e: Exit[E, A]): UIO[Boolean] = IO.effectTotal {
    var action: () => Boolean = null.asInstanceOf[() => Boolean]
    var retry                 = true

    while (retry) {
      val oldState = state.get

      val newState = oldState match {
        case Pending(joiners) =>
          action = () => { joiners.foreach(_(e)); true }

          Done(e)

        case Done(_) =>
          action = Promise.ConstFalse

          oldState
      }

      retry = !state.compareAndSet(oldState, newState)
    }

    action()
  }

  /**
   * Alias for [[Promise.complete]]
   */
  @deprecated("use Promise.complete", "1.0.0")
  final def done(io: IO[E, A]): UIO[Boolean] = complete(io)

  /**
   * Completes the promise with the specified result. If the specified promise
   * has already been completed, the method will produce false.
   */
  final def complete[R](io: ZIO[R, E, A]): URIO[R, Boolean] =
    io.run.flatMap(done)

  /**
   * Fails the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def fail(e: E): UIO[Boolean] = done(Exit.fail(e))

  /**
   * Halts the promise with the specified cause, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def halt(e: Cause[E]): UIO[Boolean] = done(Exit.halt(e))

  /**
   * Completes the promise with interruption. This will interrupt all fibers
   * waiting on the value of the promise.
   */
  final def interrupt: UIO[Boolean] = complete(IO.interrupt)

  /**
   * Checks for completion of this Promise. Produces true if this promise has
   * already been completed with a value or an error and false otherwise.
   */
  final def isDone: UIO[Boolean] =
    IO.effectTotal(state.get() match {
      case Done(_)    => true
      case Pending(_) => false
    })

  /**
   * Completes immediately and returns optionally the result of this promise.
   */
  final def poll: UIO[Option[IO[E, A]]] =
    IO.effectTotal(state.get).flatMap {
      case Pending(_) => IO.succeed(None)
      case Done(io)   => IO.succeed(Some(IO.done(io)))
    }

  /**
   * Completes the promise with the specified value.
   */
  final def succeed(a: A): UIO[Boolean] = complete(IO.succeed(a))

  private def interruptJoiner(joiner: Exit[E, A] => Unit): Canceler[Any] = IO.effectTotal {
    var retry = true

    while (retry) {
      val oldState = state.get

      val newState = oldState match {
        case Pending(joiners) =>
          Pending(joiners.filter(j => !j.eq(joiner)))

        case Done(_) =>
          oldState
      }

      retry = !state.compareAndSet(oldState, newState)
    }
  }

  private[zio] final def unsafeDone(e: Exit[E, A]): Unit = {
    var retry: Boolean                    = true
    var joiners: List[Exit[E, A] => Unit] = null

    while (retry) {
      val oldState = state.get

      val newState = oldState match {
        case Pending(js) =>
          joiners = js
          Done(e)
        case _ => oldState
      }

      retry = !state.compareAndSet(oldState, newState)
    }

    if (joiners ne null) joiners.reverse.foreach(_(e))
  }

}
object Promise {
  private val ConstFalse: () => Boolean = () => false

  private final def unsafeMake[E, A]: Promise[E, A] =
    new Promise[E, A](new AtomicReference[State[E, A]](new internal.Pending[E, A](Nil)))

  private[zio] object internal {
    sealed trait State[E, A]                                          extends Serializable with Product
    final case class Pending[E, A](joiners: List[Exit[E, A] => Unit]) extends State[E, A]
    final case class Done[E, A](value: Exit[E, A])                    extends State[E, A]
  }

  /**
   * Acquires a resource and performs a state change atomically, and then
   * guarantees that if the resource is acquired (and the state changed), a
   * release action will be called.
   */
  final def bracket[E, A, B, C](
    ref: Ref[A]
  )(
    acquire: (Promise[E, B], A) => (UIO[C], A)
  )(release: (C, Promise[E, B]) => UIO[_]): IO[E, B] =
    for {
      pRef <- Ref.make[Option[(C, Promise[E, B])]](None)
      b <- (for {
            p <- ref.modify { (a: A) =>
                  val p = Promise.unsafeMake[E, B]

                  val (io, a2) = acquire(p, a)

                  ((p, io), a2)
                }.flatMap {
                  case (p, io) => io.flatMap(c => pRef.set(Some((c, p))) *> IO.succeed(p))
                }.uninterruptible
            b <- p.await
          } yield b).ensuring(pRef.get.flatMap(_.map(t => release(t._1, t._2)).getOrElse(IO.unit)))
    } yield b

  /**
   * Makes a new promise.
   */
  final def make[E, A]: UIO[Promise[E, A]] = IO.effectTotal[Promise[E, A]](unsafeMake[E, A])
}
