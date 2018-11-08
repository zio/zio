// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz.zio

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
  final def get: IO[E, A] =
    IO.async0[E, A](k => {
      var result: Async[E, A] = null.asInstanceOf[Async[E, A]]
      var retry               = true

      while (retry) {
        val oldState = state.get

        val newState = oldState match {
          case Pending(joiners) =>
            result = Async.maybeLater[E, A](interruptJoiner(k))

            Pending(k :: joiners)
          case s @ Done(value) =>
            result = Async.now[E, A](value)

            s
        }

        retry = !state.compareAndSet(oldState, newState)
      }

      result
    })

  /**
   * Retrieves immediately the ExitResult of this promise if done
   * and fails immediately with Unit otherwise
   */
  final def poll: IO[Unit, ExitResult[E, A]] =
    IO.sync(state.get).flatMap {
      case Pending(_)       => IO.fail(())
      case Done(exitResult) => IO.now(exitResult)
    }

  /**
   * Completes the promise with the specified value.
   */
  final def complete(a: A): IO[Nothing, Boolean] = done(ExitResult.succeeded[A](a))

  /**
   * Fails the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def error(e: E): IO[Nothing, Boolean] = done(ExitResult.checked(e))

  /**
   * Interrupts the promise with no specified reason. This will interrupt
   * all fibers waiting on the value of the promise.
   */
  final def interrupt: IO[Nothing, Boolean] = done(ExitResult.interrupted)

  /**
   * Completes the promise with the specified result. If the specified promise
   * has already been completed, the method will produce false.
   */
  final def done(r: ExitResult[E, A]): IO[Nothing, Boolean] =
    IO.flatten(IO.sync {
      var action: IO[Nothing, Boolean] = null.asInstanceOf[IO[Nothing, Boolean]]
      var retry                        = true

      while (retry) {
        val oldState = state.get

        val newState = oldState match {
          case Pending(joiners) =>
            action =
              IO.forkAll(joiners.map(k => IO.sync[Unit](k(r)))) *>
                IO.now[Boolean](true)

            Done(r)

          case Done(_) =>
            action = IO.now[Boolean](false)

            oldState
        }

        retry = !state.compareAndSet(oldState, newState)
      }

      action
    })

  private def interruptJoiner(joiner: Callback[E, A]): Canceler = { () =>
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
  final def make[E, A]: IO[Nothing, Promise[E, A]] = IO.sync[Promise[E, A]](unsafeMake[E, A])

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
    acquire: (Promise[E, B], A) => (IO[Nothing, C], A)
  )(release: (C, Promise[E, B]) => IO[Nothing, Unit]): IO[E, B] =
    for {
      pRef <- Ref[Option[(C, Promise[E, B])]](None)
      b <- (for {
            p <- ref.modify { a: A =>
                  val p = Promise.unsafeMake[E, B]

                  val (io, a2) = acquire(p, a)

                  ((p, io), a2)
                }.flatMap {
                  case (p, io) => io.flatMap(c => pRef.set(Some((c, p))) *> IO.now(p))
                }.uninterruptibly
            b <- p.get
          } yield b).ensuring(pRef.get.flatMap(_.fold(IO.unit)(t => release(t._1, t._2))))
    } yield b

  private[zio] object internal {
    sealed abstract class State[E, A]                             extends Serializable with Product
    final case class Pending[E, A](joiners: List[Callback[E, A]]) extends State[E, A]
    final case class Done[E, A](value: ExitResult[E, A])          extends State[E, A]
  }
}
