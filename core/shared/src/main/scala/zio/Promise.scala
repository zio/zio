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
 * `get`) and automatically resume when the variable is set.
 *
 * Promises can be used for building primitive actions whose completions
 * require the coordinated action of multiple fibers, and for building
 * higher-level concurrent or asynchronous structures.
 * {{{
 * for {
 *   promise <- Promise.make[Nothing, Int]
 *   _       <- promise.complete(42).delay(1.second).fork
 *   value   <- promise.get // Resumes when forked fiber completes promise
 * } yield value
 * }}}
 */
class Promise[E, A] private (private val state: AtomicReference[State[E, A]]) extends AnyVal {

  /**
   * Retrieves the value of the promise, suspending the fiber running the action
   * until the result is available.
   */
  final def await: IO[E, A] =
    IO.effectAsyncInterrupt[Any, E, A](k => {
      var result = null.asInstanceOf[Either[Canceler, IO[E, A]]]
      var retry  = true

      while (retry) {
        val oldState = state.get

        val newState = oldState match {
          case Pending(joiners) =>
            result = Left(interruptJoiner(k))

            Pending(k :: joiners)
          case s @ Done(value) =>
            result = Right(value)

            s
        }

        retry = !state.compareAndSet(oldState, newState)
      }

      result
    })

  /**
   * Kills the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def die(e: Throwable): UIO[Boolean] = done(IO.die(e))

  /**
   * Completes the promise with the specified result. If the specified promise
   * has already been completed, the method will produce false.
   */
  final def done(io: IO[E, A]): UIO[Boolean] =
    IO.effectTotal {
      var action: () => Boolean = null.asInstanceOf[() => Boolean]
      var retry                 = true

      while (retry) {
        val oldState = state.get

        val newState = oldState match {
          case Pending(joiners) =>
            action = () => { joiners.foreach(_(io)); true }

            Done(io)

          case Done(_) =>
            action = Promise.ConstFalse

            oldState
        }

        retry = !state.compareAndSet(oldState, newState)
      }

      action()
    }

  /**
   * Fails the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def fail(e: E): UIO[Boolean] = done(IO.fail(e))

  /**
   * Halts the promise with the specified cause, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def halt(e: Cause[E]): UIO[Boolean] = done(IO.halt(e))

  /**
   * Completes the promise with interruption. This will interrupt all fibers
   * waiting on the value of the promise.
   */
  final def interrupt: UIO[Boolean] = done(IO.interrupt)

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
   * Completes immediately this promise and returns optionally it's result.
   */
  final def poll: UIO[Option[IO[E, A]]] =
    IO.effectTotal(state.get).flatMap {
      case Pending(_) => IO.succeed(None)
      case Done(io)   => IO.succeed(Some(io))
    }

  /**
   * Completes the promise with the specified value.
   */
  final def succeed(a: A): UIO[Boolean] = done(IO.succeed(a))

  private def interruptJoiner(joiner: IO[E, A] => Unit): Canceler = IO.effectTotal {
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

  private[zio] final def unsafeDone(io: IO[E, A]): Unit = {
    var retry: Boolean                  = true
    var joiners: List[IO[E, A] => Unit] = null

    while (retry) {
      val oldState = state.get

      val newState = oldState match {
        case Pending(js) =>
          joiners = js
          Done(io)
        case _ => oldState
      }

      retry = !state.compareAndSet(oldState, newState)
    }

    if (joiners ne null) joiners.reverse.foreach(_(io))
  }

}
object Promise {
  private val ConstFalse: () => Boolean = () => false

  private final def unsafeMake[E, A]: Promise[E, A] =
    new Promise[E, A](new AtomicReference[State[E, A]](new internal.Pending[E, A](Nil)))

  private[zio] object internal {
    sealed trait State[E, A]                                        extends Serializable with Product
    final case class Pending[E, A](joiners: List[IO[E, A] => Unit]) extends State[E, A]
    final case class Done[E, A](value: IO[E, A])                    extends State[E, A]
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
            p <- ref.modify { a: A =>
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
