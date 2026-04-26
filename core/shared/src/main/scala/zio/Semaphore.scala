/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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
import scala.annotation.tailrec

private final case class WaitQueue(head: Promise[Unit, Nothing], ref: Ref[Option[Promise[Unit, Nothing]]]) {
  def add(promise: Promise[Unit, Nothing]): UIO[Unit] =
    ref.modify {
      case Some(last) =>
        (ZIO.unit, Some(promise))
      case None =>
        (head.succeed(()), Some(promise))
    }.flatten
}

private object WaitQueue {
  def make: UIO[WaitQueue] =
    for {
      head <- Promise.make[Unit, Nothing]
      ref  <- Ref.make[Option[Promise[Unit, Nothing]]](None)
    } yield WaitQueue(head, ref)
}

private final case class State(permits: Long, queue: Option[WaitQueue])

private object State {
  def apply(permits: Long): State =
    State(permits, None)
}

final class Semaphore private (state: AtomicReference[State]) extends Serializable {

  /**
   * Acquires a single permit.
   */
  def acquire: UIO[Unit] =
    acquireN(1)

  /**
   * Acquires the specified number of permits.
   */
  def acquireN(n: Long): UIO[Unit] = {
    if (n < 0)
      ZIO.dieMessage(s"Semaphore#acquireN: cannot acquire negative number of permits: $n")
    else if (n == 0)
      ZIO.unit
    else
      ZIO.async { cb =>
        loop(n, cb)
      }
  }

  /**
   * Releases a single permit.
   */
  def release: UIO[Unit] =
    releaseN(1)

  /**
   * Releases the specified number of permits.
   */
  def releaseN(n: Long): UIO[Unit] = {
    if (n < 0)
      ZIO.dieMessage(s"Semaphore#releaseN: cannot release negative number of permits: $n")
    else if (n == 0)
      ZIO.unit
    else
      releaseLoop(n)
  }

  /**
   * Executes the specified effect, acquiring a permit before the effect is
   * executed and releasing a permit after the effect is executed.
   */
  def withPermit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.acquireReleaseWith(acquire)(release)(_ => zio)

  /**
   * Executes the specified effect, acquiring the specified number of permits
   * before the effect is executed and releasing the specified number of
   * permits after the effect is executed.
   */
  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.acquireReleaseWith(acquireN(n))(releaseN(n))(_ => zio)

  /**
   * Attempts to acquire a single permit without waiting.
   */
  def tryAcquire: UIO[Boolean] =
    tryAcquireN(1)

  /**
   * Attempts to acquire the specified number of permits without waiting.
   */
  def tryAcquireN(n: Long): UIO[Boolean] = {
    if (n < 0)
      ZIO.dieMessage(s"Semaphore#tryAcquireN: cannot acquire negative number of permits: $n")
    else if (n == 0)
      ZIO.succeed(true)
    else
      ZIO.suspendSucceed {
        val currentState = state.get()
        if (currentState.permits >= n) {
          if (unsafeUpdateState(currentState, currentState.copy(permits = currentState.permits - n))) {
            ZIO.succeed(true)
          } else {
            tryAcquireN(n) // retry on CAS failure
          }
        } else {
          ZIO.succeed(false)
        }
      }
  }

  /**
   * Returns the number of available permits.
   */
  def available: UIO[Long] =
    ZIO.succeed(state.get().permits)

  @tailrec
  private def loop(n: Long, callback: Try[Nothing] => Unit): Option[UIO[Unit]] = {
    val currentState = state.get()

    if (currentState.permits >= n) {
      val newState = currentState.copy(permits = currentState.permits - n)
      if (unsafeUpdateState(currentState, newState)) {
        callback(ZIO.unit.succeed(()))
        None
      } else {
        loop(n, callback)
      }
    } else {
      val newState = currentState match {
        case State(permits, None) =>
          State(permits, Some(null)) // placeholder to avoid allocation until needed
        case other =>
          other
      }
      if (unsafeUpdateState(currentState, newState)) {
        Some(
          WaitQueue.make.flatMap { waitQueue =>
            val promise = waitQueue.head
            val update = state.update { s =>
              if (s.queue.isEmpty) s.copy(queue = Some(waitQueue))
              else s
            }
            update *> promise.await.onInterrupt(releaseN(n))
          }
        )
      } else {
        loop(n, callback)
      }
    }
  }

  @tailrec
  private def releaseLoop(n: Long): UIO[Unit] = {
    val currentState = state.get()

    val updatedPermits = currentState.permits + n

    currentState.queue match {
      case Some(waitQueue) =>
        val acquired = Math.min(n, updatedPermits)
        val remaining = n - acquired
        val newState = State(updatedPermits - acquired, if (acquired < n) currentState.queue else None)
        if (unsafeUpdateState(currentState, newState)) {
          waitQueue.release *> (if (remaining > 0) releaseLoop(remaining) else ZIO.unit)
        } else {
          releaseLoop(n)
        }
      case None =>
        val newState = State(updatedPermits)
        if (unsafeUpdateState(currentState, newState)) {
          ZIO.unit
        } else {
          releaseLoop(n)
        }
    }
  }

  @tailrec
  private def unsafeUpdateState(oldState: State, newState: State): Boolean =
    if (state.compareAndSet(oldState, newState)) {
      true
    } else {
      false
    }
}

object Semaphore extends Serializable {

  /**
   * Creates a new semaphore with the specified number of permits.
   */
  def make(permits: Long): UIO[Semaphore] = {
    if (permits < 0)
      ZIO.dieMessage(s"Semaphore.make: cannot create semaphore with negative permits: $permits")
    else
      ZIO.succeed {
        new Semaphore(new AtomicReference(State(permits)))
      }
  }
}