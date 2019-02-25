// Copyright (C) 2018 John A. De Goes. All rights reserved.

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
   * Retrieves the value of the promise, suspending the fiber running the action
   * until the result is available.
   */
  final def await: IO[E, A] =
    IO.asyncInterrupt[E, A](k => {
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
   * Completes immediately this promise and returns optionally it's result.
   */
  final def poll: IO[Nothing, Option[IO[E, A]]] =
    IO.sync(state.get).flatMap {
      case Pending(_) => IO.succeed(None)
      case Done(io)   => IO.succeed(Some(io))
    }

  /**
   * Completes the promise with the specified value.
   */
  final def succeed(a: A): IO[Nothing, Boolean] = done(IO.succeed(a))

  /**
   * Fails the promise with the specified error, which will be propagated to all
   * fibers waiting on the value of the promise.
   */
  final def fail(e: E): IO[Nothing, Boolean] = done(IO.fail(e))

  /**
   * Completes the promise with interruption. This will interrupt all fibers
   * waiting on the value of the promise.
   */
  final def interrupt: IO[Nothing, Boolean] = done(IO.interrupt)

  /**
   * Completes the promise with the specified result. If the specified promise
   * has already been completed, the method will produce false.
   */
  final def done(io: IO[E, A]): IO[Nothing, Boolean] =
    IO.flatten(IO.sync {
        var action: IO[Nothing, Boolean] = null.asInstanceOf[IO[Nothing, Boolean]]
        var retry                        = true

        while (retry) {
          val oldState = state.get

          val newState = oldState match {
            case Pending(joiners) =>
              action =
                IO.forkAll_(joiners.map(k => IO.sync[Unit](k(io)))) *>
                  IO.succeed[Boolean](true)

              Done(io)

            case Done(_) =>
              action = IO.succeed[Boolean](false)

              oldState
          }

          retry = !state.compareAndSet(oldState, newState)
        }

        action
      })
      .uninterruptible

  private[zio] final def unsafeDone(io: IO[E, A], exec: Executor): Unit = {
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

    if (joiners ne null) joiners.reverse.foreach(k => exec.submit(() => k(io)))
  }

  private def interruptJoiner(joiner: IO[E, A] => Unit): Canceler = IO.sync {
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
  )(release: (C, Promise[E, B]) => IO[Nothing, _]): IO[E, B] =
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

  private[zio] object internal {
    sealed abstract class State[E, A]                               extends Serializable with Product
    final case class Pending[E, A](joiners: List[IO[E, A] => Unit]) extends State[E, A]
    final case class Done[E, A](value: IO[E, A])                    extends State[E, A]
  }
}
