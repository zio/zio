/*
 * Copyright 2018-2024 John A. De Goes and ZIO Contributors
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

import scala.annotation.tailrec

/**
 * A `Promise[E, A]` is a concurrency-safe variable that can be set exactly
 * once, with the ability to block on its value. Promises can be used for
 * synchronizing computations, implementing semaphores and mutexes, and
 * building higher-level concurrent primitives.
 */
trait Promise[E, A] extends Serializable { self =>

  /**
   * Completes the promise with the specified value.
   *
   * @param a
   *   the value to complete the promise with
   * @return
   *   a `UIO[Unit]` that succeeds with unit when the promise has been
   *   successfully completed
   */
  def succeed(a: A): UIO[Boolean]

  /**
   * Completes the promise with the specified effect.
   *
   * @param io
   *   the effect to complete the promise with
   * @return
   *   a `UIO[Unit]` that succeeds with unit when the promise has been
   *   successfully completed
   */
  def done(io: IO[E, A]): UIO[Boolean]

  /**
   * Completes the promise with the specified error.
   *
   * @param e
   *   the error to complete the promise with
   * @return
   *   a `UIO[Unit]` that succeeds with unit when the promise has been
   *   successfully completed
   */
  def fail(e: E): UIO[Boolean]

  /**
   * Completes the promise with the specified cause.
   *
   * @param cause
   *   the cause to complete the promise with
   * @return
   *   a `UIO[Unit]` that succeeds with unit when the promise has been
   *   successfully completed
   */
  def failCause(cause: Cause[E]): UIO[Boolean]

  /**
   * Returns a `UIO` that will succeed with `Some(a)` when the promise is
   * successfully completed with `a`, or `None` if the promise is completed
   * with a failure.
   */
  def either: UIO[Either[E, A]]

  /**
   * Returns a `UIO` that will succeed with `Some(a)` when the promise is
   * successfully completed with `a`, or `None` if the promise is completed
   * with a failure. If the promise is completed with an error, the resulting
   * `UIO` will succeed with `None`, but the error will not be reported as a
   * failure. This method is useful when the error type is `Nothing`.
   */
  def option: UIO[Option[A]]

  /**
   * Interrupts the promise with a `FiberId.None`.
   *
   * @return
   *   a `UIO[Unit]` that succeeds with unit when the promise has been
   *   successfully interrupted
   */
  def interrupt: UIO[Boolean]

  /**
   * Retrieves whether the promise has been completed.
   */
  def isDone: UIO[Boolean]

  /**
   * Retrieves the value of the promise, suspending the fiber until the result
   * is available.
   */
  def await: IO[E, A]

  /**
   * Makes this promise complete with the same result as `that` promise.
   * Any fibers waiting on this promise will be transferred to `that`.
   * If `that` is already completed, this promise is completed immediately.
   */
  def become(that: Promise[E, A]): UIO[Unit]

  /**
   * Returns a `Fiber` that will await the result of this promise.
   */
  def toFiber: UIO[Fiber[E, A]]
}

object Promise {

  /**
   * Creates a new promise that is not completed.
   */
  def make[E, A]: UIO[Promise[E, A]] =
    makeAs(FiberId.None)

  /**
   * Creates a new promise that is not completed, with the specified `FiberId`
   * used for reporting purposes when the promise is interrupted.
   */
  def makeAs[E, A](id: => FiberId): UIO[Promise[E, A]] =
    ZIO
      .succeed {
        new unsafe.UnsafePromise[E, A](id)
      }
      .refailException

  /**
   * Creates a new promise that is already completed with the specified value.
   */
  def succeed[A](a: A): UIO[Promise[Nothing, A]] =
    make.map { promise =>
      unsafe.UnsafePromise.done(promise, IO.succeed(a))
      promise
    }

  /**
   * Creates a new promise that is already completed with the specified error.
   */
  def fail[E](e: E): UIO[Promise[E, Nothing]] =
    make.map { promise =>
      unsafe.UnsafePromise.done(promise, IO.fail(e))
      promise
    }

  /**
   * Creates a new promise that is already completed with the specified cause.
   */
  def failCause[E](cause: Cause[E]): UIO[Promise[E, Nothing]] =
    make.map { promise =>
      unsafe.UnsafePromise.done(promise, IO.failCause(cause))
      promise
    }

  /**
   * Creates a new promise that is already interrupted.
   */
  def interrupt: UIO[Promise[Nothing, Nothing]] =
    make.map { promise =>
      unsafe.UnsafePromise.done(promise, IO.interrupt)
      promise
    }

  /**
   * Awaits on both promises, combining their results into a tuple.
   */
  def both[E, A, B](left: Promise[E, A], right: Promise[E, B]): UIO[Promise[E, (A, B)]] =
    make.map { promise =>
      left.await.zipWith(right.await)(_ -> _).pipeTo(promise)
      promise
    }

  /**
   * Awaits on both promises, returning the result of the one that completes
   * first.
   */
  def either[E, A, B](left: Promise[E, A], right: Promise[E, B]): UIO[Promise[E, Either[A, B]]] =
    make.map { promise =>
      left.await.map(Left(_)).race(right.await.map(Right(_))).pipeTo(promise)
      promise
    }

  /**
   * Awaits on both promises, returning the result of the one that completes
   * first, or the failure if both fail.
   */
  def race[E, A](left: Promise[E, A], right: Promise[E, A]): UIO[Promise[E, A]] =
    make.map { promise =>
      left.await.race(right.await).pipeTo(promise)
      promise
    }

  private[zio] object unsafe {

    sealed private[zio] trait State[E, A] extends Serializable

    object State {
      final case class Pending[E, A](waiters: Chunk[(Either[Cause[E], A]) => Unit]) extends State[E, A]
      final case class Linking[E, A](promise: Promise[E, A])                         extends State[E, A]
      final case class Done[E, A](exit: Exit[E, A])                                  extends State[E, A]
    }

    final class UnsafePromise[E, A](id: => FiberId) extends Promise[E, A] {
      @volatile private[this] var state: State[E, A] = State.Pending(Chunk.empty)

      override def succeed(a: A): UIO[Boolean] =
        done(IO.succeed(a))

      override def done(io: IO[E, A]): UIO[Boolean] =
        ZIO.uninterruptible {
          ZIO
            .succeed {
              val oldState = state
              oldState match {
                case State.Pending(waiters) =>
                  val exit = io.unsafeRunSync()
                  state = State.Done(exit)
                  waiters.foreach(cb => cb(exit.fold(Left(_), Right(_))))
                  true
                case State.Linking(that) =>
                  that.done(io)
                  true
                case State.Done(_) =>
                  false
              }
            }
            .refailException
        }

      override def fail(e: E): UIO[Boolean] =
        done(IO.fail(e))

      override def failCause(cause: Cause[E]): UIO[Boolean] =
        done(IO.failCause(cause))

      override def either: UIO[Either[E, A]] =
        await.either

      override def option: UIO[Option[A]] =
        await.flip.map(_.toOption).refailException

      override def interrupt: UIO[Boolean] =
        failCause(Cause.interrupt(FiberId.None))

      override def isDone: UIO[Boolean] =
        ZIO.succeed {
          state match {
            case State.Done(_)       => true
            case State.Linking(that) => unsafe.UnsafePromise.isDone(that)
            case State.Pending(_)    => false
          }
        }

      override def await: IO[E, A] =
        ZIO.asyncInterrupt { cb =>
          val updateState: State[E, A] => Boolean = {
            case State.Pending(waiters) =>
              state = State.Pending(waiters :+ cb)
              false
            case State.Linking(that) =>
              that.await.unsafeRunAsync(cb)
              true
            case State.Done(exit) =>
              cb(exit.fold(Left(_), Right(_)))
              true
          }

          if (updateState(state)) {
            Left(ZIO.unit)
          } else {
            Left {
              ZIO.succeed {
                @tailrec
                def loop(): Unit =
                  state match {
                    case State.Pending(waiters) =>
                      val index = waiters.indexWhere(_ eq cb)
                      if (index >= 0) {
                        val newWaiters = waiters.take(index) ++ waiters.drop(index + 1)
                        state = State.Pending(newWaiters)
                      }
                    case State.Linking(that) =>
                      that.await.unsafeRunAsyncInterrupt { _ =>
                        ZIO.unit
                      }
                    case State.Done(_) =>
                      ()
                  }
              }.refailException
            }
          }
        }

      override def become(that: Promise[E, A]): UIO[Unit] =
        ZIO.uninterruptible {
          ZIO.succeed {
            val oldState = this.synchronized {
              state match {
                case State.Pending(waiters) if waiters.nonEmpty =>
                  state = State.Linking(that)
                  Some(waiters)
                case State.Pending(waiters) if waiters.isEmpty =>
                  state = State.Linking(that)
                  None
                case State.Done(exit) =>
                  that.done(exit)
                  None
                case State.Linking(existing) =>
                  if (existing eq that) None
                  else {
                    state = State.Linking(that)
                    None
                  }
              }
            }

            oldState match {
              case Some(waiters) =>
                waiters.foreach { cb =>
                  that.await.unsafeRunAsync(cb)
                }
              case None =>
                ()
            }
          }.refailException
        }

      override def toFiber: UIO[Fiber[E, A]] =
        Fiber.fromEffect(await)
    }

    private[zio] def isDone[E, A](promise: Promise[E, A]): Boolean =
      promise match {
        case p: UnsafePromise[E, A] =>
          p.state match {
            case State.Done(_)       => true
            case State.Linking(that) => isDone(that)
            case State.Pending(_)    => false
          }
        case _ => false
      }

    private[zio] def done[E, A](promise: Promise[E, A], io: IO[E, A]): Unit =
      promise match {
        case p: UnsafePromise[E, A] =>
          p.done(io).unsafeRunSync()
        case _ =>
          io.unsafeRunAsync { exit =>
            promise.done(exit).unsafeRunSync()
          }
      }
  }
}