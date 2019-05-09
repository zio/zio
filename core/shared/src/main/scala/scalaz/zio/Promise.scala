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

package scalaz.zio

import java.util.concurrent.atomic.AtomicReference
import scalaz.zio.internal.Executor
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
   * Checks for completion of this Promise. Produces true if this promise has
   * already been completed with a value or an error and false otherwise.
   */
  final def isDone: UIO[Boolean] =
    BIO.effectTotal(state.get() match {
      case Done(_)    => true
      case Pending(_) => false
    })

  /**
   * Retrieves the value of the promise, suspending the fiber running the action
   * until the result is available.
   */
  final def await: BIO[E, A] =
    BIO.effectAsyncInterrupt[Any, E, A](k => {
      var result = null.asInstanceOf[Either[Canceler, BIO[E, A]]]
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
   * Completes immediately this promise and returns optionally it's result.
   */
  final def poll: UIO[Option[BIO[E, A]]] =
    BIO.effectTotal(state.get).flatMap {
      case Pending(_) => BIO.succeed(None)
      case Done(io)   => BIO.succeed(Some(io))
    }

  /**
   * Completes the promise with the specified value.
   */
  final def succeed(a: A): UIO[Boolean] = done(BIO.succeed(a))

  /**
   * Fails the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def fail(e: E): UIO[Boolean] = done(BIO.fail(e))

  /**
   * Completes the promise with interruption. This will interrupt all fibers
   * waiting on the value of the promise.
   */
  final def interrupt: UIO[Boolean] = done(BIO.interrupt)

  /**
   * Completes the promise with the specified result. If the specified promise
   * has already been completed, the method will produce false.
   */
  final def done(io: BIO[E, A]): UIO[Boolean] =
    BIO
      .flatten(BIO.effectTotal {
        var action: UIO[Boolean] = null.asInstanceOf[UIO[Boolean]]
        var retry                = true

        while (retry) {
          val oldState = state.get

          val newState = oldState match {
            case Pending(joiners) =>
              action =
                BIO.forkAll_(joiners.map(k => BIO.effectTotal[Unit](k(io)))) *>
                  BIO.succeed[Boolean](true)

              Done(io)

            case Done(_) =>
              action = BIO.succeed[Boolean](false)

              oldState
          }

          retry = !state.compareAndSet(oldState, newState)
        }

        action
      })
      .uninterruptible

  private[zio] final def unsafeDone(io: BIO[E, A], exec: Executor): Unit = {
    var retry: Boolean                   = true
    var joiners: List[BIO[E, A] => Unit] = null

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

    if (joiners ne null) joiners.reverse.foreach(k => exec.submit(() => k(io)))
  }

  private def interruptJoiner(joiner: BIO[E, A] => Unit): Canceler = BIO.effectTotal {
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
}
object Promise {

  /**
   * Makes a new promise.
   */
  final def make[E, A]: UIO[Promise[E, A]] = BIO.effectTotal[Promise[E, A]](unsafeMake[E, A])

  private final def unsafeMake[E, A]: Promise[E, A] =
    new Promise[E, A](new AtomicReference[State[E, A]](new internal.Pending[E, A](Nil)))

  /**
   * Acquires a resource and performs a state change atomically, and then
   * guarantees that if the resource is acquired (and the state changed), a
   * release action will be called.
   */
  final def bracket[E, A, B, C](
    ref: Ref[A]
  )(
    acquire: (Promise[E, B], A) => (UIO[C], A)
  )(release: (C, Promise[E, B]) => UIO[_]): BIO[E, B] =
    for {
      pRef <- Ref.make[Option[(C, Promise[E, B])]](None)
      b <- (for {
            p <- ref.modify { a: A =>
                  val p = Promise.unsafeMake[E, B]

                  val (io, a2) = acquire(p, a)

                  ((p, io), a2)
                }.flatMap {
                  case (p, io) => io.flatMap(c => pRef.set(Some((c, p))) *> BIO.succeed(p))
                }.uninterruptible
            b <- p.await
          } yield b).ensuring(pRef.get.flatMap(_.map(t => release(t._1, t._2)).getOrElse(BIO.unit)))
    } yield b

  private[zio] object internal {
    sealed trait State[E, A]                                         extends Serializable with Product
    final case class Pending[E, A](joiners: List[BIO[E, A] => Unit]) extends State[E, A]
    final case class Done[E, A](value: BIO[E, A])                    extends State[E, A]
  }
}
