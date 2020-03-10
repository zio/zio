/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import zio.internal.Sync

import java.util.concurrent.atomic.AtomicLong

/**
 * A scope is a value that allows adding finalizers up until the point where
 * the scope is closed.
 */
final class Scope private (private val id: Long, @volatile private var finalizers: UIO[Any]) { self =>

  /**
   * Creates a child scope that is bound to this scope.
   */
  def child: UIO[Option[Scope]] = UIO {
    Sync(self) {
      if (finalizers eq null) None
      else {
        val child = Scope.unsafeMake()

        self.finalizers = self.finalizers *> child.close

        Some(child.scope)
      }
    }
  }

  /**
   * Determines if the scope is closed at the instant the effect executes.
   */
  def closed: UIO[Boolean] = UIO(finalizers eq null)

  /**
   * Adds a finalizer to the scope. If successful, this ensures that when the
   * scope exits, the finalizer will be run.
   */
  def ensure(finalizer: UIO[Any]): UIO[Boolean] = UIO {
    Sync(self) {
      finalizers match {
        case null => false
        case _ =>
          finalizers = finalizers *> finalizer

          true
      }
    }
  }

  /**
   * Adds a finalizer to the scope, or executes the specified fallback effect,
   * if the scope is already closed.
   */
  def ensureOrElse[A, B](finalizer: UIO[A], orElse: UIO[B]): UIO[Either[A, B]] =
    for {
      promise <- Promise.make[Nothing, Either[A, B]]
      added   <- ensure(finalizer.flatMap(a => promise.succeed(Left(a))))
      _       <- if (!added) orElse.flatMap(b => promise.succeed(Right(b))) else ZIO.unit
      value   <- promise.await
    } yield value

  /**
   * Attempts to migrate the finalizers of this scope to the specified scope.
   */
  def migrateToWith(that: Scope)(f: (UIO[Any], UIO[Any]) => UIO[Any]): UIO[Boolean] = UIO {
    val (lock1, lock2) = if (self.id < that.id) (self, that) else (that, self)

    Sync(lock1) {
      Sync(lock2) {
        if (that.finalizers eq null) false
        else if (self.finalizers eq null) true
        else {
          that.finalizers = f(self.finalizers, that.finalizers)

          true
        }
      }
    }
  }

  /**
   * Determines if the scope is open at the instant the effect executes.
   */
  def open: UIO[Boolean] = UIO(finalizers ne null)
}
object Scope {
  private val counter: AtomicLong = new AtomicLong()

  /**
   * A tuple that contains a scope, together with an effect that closes the scope.
   */
  final case class Open(close: UIO[Boolean], scope: Scope)

  /**
   * An effect that makes a new scope, together with an effect that can close
   * the scope.
   */
  val make: UIO[Open] = UIO(unsafeMake())

  private def unsafeMake(): Open = {
    val scope = new Scope(counter.getAndIncrement(), ZIO.unit)

    val close = UIO.effectSuspendTotal {
      Sync(scope) {
        scope.finalizers match {
          case null => UIO(false)

          case finalizers =>
            scope.finalizers = null

            finalizers as true
        }
      }
    }

    Open(close, scope)
  }
}
