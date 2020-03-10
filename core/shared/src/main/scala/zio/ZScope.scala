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

import java.util.ArrayList

import zio.internal.Sync

/**
 * A `ZScope[A]` is a value that allows adding finalizers up until the point where
 * the scope is closed by a value of type `A`.
 */
final class ZScope[A] private (
  @volatile private var isClosed: Boolean,
  private val finalizers: ArrayList[A => UIO[Any]]
) { self =>

  /**
   * Creates a child scope that will be automatically closed when this scope is
   * closed, but may also be closed independently.
   */
  def child: UIO[Option[ZScope[A]]] = UIO {
    Sync(finalizers) {
      if (isClosed) None
      else {
        val child = ZScope.unsafeMake[A]()

        self.finalizers.add(child.close(_))

        Some(child.scope)
      }
    }
  }

  /**
   * Determines if the scope is closed at the instant the effect executes.
   */
  def closed: UIO[Boolean] = UIO(isClosed)

  /**
   * Determines if the scope is empty at the instant the effect executes.
   */
  def empty: UIO[Boolean] = UIO(finalizers.size() == 0)

  /**
   * Adds a finalizer to the scope. If successful, this ensures that when the
   * scope exits, the finalizer will be run.
   */
  def ensure(finalizer: A => UIO[Any]): UIO[Boolean] = UIO {
    Sync(finalizers) {
      if (isClosed) false
      else {
        finalizers.add(finalizer)

        true
      }
    }
  }

  /**
   * Attempts to migrate the finalizers of the specified scope to this scope.
   */
  def drainFrom(that: ZScope[A]): UIO[Boolean] = UIO {
    if (self eq that) true
    else {
      val (lock1, lock2) =
        if (self comesBefore that) (self.finalizers, that.finalizers) else (that.finalizers, self.finalizers)

      Sync(lock1) {
        Sync(lock2) {
          if (self.isClosed) false
          else if (that.isClosed) true
          else {
            self.finalizers.addAll(that.finalizers)
            that.finalizers.clear()
            that.isClosed = true

            true
          }
        }
      }
    }
  }

  /**
   * Attempts to migrate the finalizers of this scope to the specified scope.
   */
  def drainTo(that: ZScope[A]): UIO[Boolean] = that.drainFrom(self)

  /**
   * Determines if the scope is open at the moment the effect is executed.
   */
  def open: UIO[Boolean] = UIO(!isClosed)

  private[zio] def unsafeClosed(): Boolean = isClosed

  private[zio] def unsafeEmpty(): Boolean = finalizers.size() == 0

  private[zio] def unsafeOpen(): Boolean = !isClosed

  private def comesBefore(that: ZScope[A]): Boolean =
    self.finalizers.hashCode < that.finalizers.hashCode
}
object ZScope {

  /**
   * A tuple that contains a scope, together with an effect that closes the scope.
   */
  trait Open[A] {
    def close(a: A): UIO[Boolean]

    def scope: ZScope[A]

    private[zio] def unsafeClose(a: A): UIO[Any]
  }

  /**
   * An effect that makes a new scope, together with an effect that can close
   * the scope.
   */
  def make[A]: UIO[Open[A]] = UIO(unsafeMake())

  private def unsafeMake[A](): Open[A] = {
    val scope0 = new ZScope[A](false, new ArrayList[A => UIO[Any]]())

    def unsafeClose0(a: A): UIO[Any] =
      Sync(scope0.finalizers) {
        val finalizers = scope0.finalizers

        if (scope0.isClosed) null
        else {
          val iterator = finalizers.iterator()

          val effect = if (iterator.hasNext()) {
            var effect = iterator.next()(a)

            while (iterator.hasNext()) {
              val next = iterator.next()

              effect = effect *> next(a)
            }

            finalizers.clear()

            effect
          } else ZIO.unit

          scope0.isClosed = true

          effect
        }
      }

    new Open[A] {
      def close(a: A) = UIO.effectSuspendTotal {
        val result = unsafeClose0(a)

        if (result eq null) UIO(false)
        else result as true
      }

      def scope = scope0

      def unsafeClose(a: A): UIO[Any] = unsafeClose0(a)
    }
  }
}
