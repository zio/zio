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

import java.util.Map

import zio.internal.Sync

/**
 * A `ZScope[K, A]` is a value that allows adding finalizers identified by `K`
 * up until the point where the scope is closed by a value of type `A`.
 */
final class ZScope[-K, +A] private (
  @volatile private var isClosed: Boolean,
  private val weakKeys: Boolean,
  private val finalizers: Map[Any, Any => UIO[Any]]
) { self =>

  /**
   * Creates a child scope that will be automatically closed when this scope is
   * closed, but may also be closed independently.
   */
  def child(k: K): UIO[Option[ZScope[K, A]]] = UIO {
    Sync(finalizers) {
      if (isClosed) None
      else {
        val child = ZScope.unsafeMake[K, A](weakKeys)

        self.finalizers.put(k, coerce(child.close(_)))

        Some(child.scope)
      }
    }
  }

  /**
   * Determines if the scope is closed at the instant the effect executes.
   */
  def closed: UIO[Boolean] = UIO(isClosed)

  /**
   * Unensures a finalizer runs when the scope is closed.
   */
  def deny(k: K): UIO[Boolean] = UIO {
    Sync(finalizers) {
      if (isClosed) false
      else finalizers.remove(k) ne null
    }
  }

  /**
   * Determines if the scope is empty at the instant the effect executes.
   */
  def empty: UIO[Boolean] = UIO(finalizers.size() == 0)

  /**
   * Adds a finalizer to the scope, using the finalizer itself as the key for
   * the finalizer. If successful, this ensures that when the scope exits, the
   * finalizer will be run.
   */
  def ensure[A1 >: A](finalizer: A1 => UIO[Any])(implicit ev: (A1 => UIO[Any]) <:< K): UIO[Boolean] =
    ensure(ev(finalizer), finalizer)

  /**
   * Adds a finalizer to the scope. If successful, this ensures that when the
   * scope exits, the finalizer will be run, assuming the key has not been
   * garbage collected.
   */
  def ensure(key: K, finalizer: A => UIO[Any]): UIO[Boolean] = UIO {
    Sync(finalizers) {
      if (isClosed) false
      else {
        if (finalizers.containsKey(key)) false
        else {
          finalizers.put(key, coerce(finalizer))

          true
        }
      }
    }
  }

  /**
   * Attempts to migrate the finalizers of the specified scope to this scope.
   */
  def drainFrom[K1 <: K, A1 >: A](that: ZScope[K1, A1]): UIO[Boolean] = UIO {
    if (self eq that) true
    else {
      val (lock1, lock2) =
        if (self comesBefore that) (self.finalizers, that.finalizers) else (that.finalizers, self.finalizers)

      Sync(lock1) {
        Sync(lock2) {
          if (self.isClosed) false
          else if (that.isClosed) true
          else {
            self.finalizers.putAll(that.finalizers)
            that.finalizers.clear()
            that.isClosed = true

            true
          }
        }
      }
    }
  }

  /**
   * Determines if the scope is open at the moment the effect is executed.
   */
  def open: UIO[Boolean] = UIO(!isClosed)

  private[zio] def unsafeClosed(): Boolean = isClosed

  private[zio] def unsafeEmpty(): Boolean = finalizers.size() == 0

  private[zio] def unsafeOpen(): Boolean = !isClosed

  private def comesBefore(that: ZScope[_, _]): Boolean =
    self.finalizers.hashCode < that.finalizers.hashCode

  private def coerce(f: A => UIO[Any]): Any => UIO[Any] = f.asInstanceOf[Any => UIO[Any]]
}
object ZScope {

  /**
   * A tuple that contains a scope, together with an effect that closes the scope.
   */
  trait Open[K, A] {

    /**
     * Closes the scope by providing finalizers with the value they need.
     */
    def close(a: A): UIO[Boolean]

    /**
     * The scope, which is initially open.
     */
    def scope: ZScope[K, A]

    private[zio] def unsafeClose(a: A): UIO[Any]
  }

  /**
   * An effect that makes a new scope, together with an effect that can close
   * the scope. The scope is backed by a weak map, meaning that if the keys are
   * garbage collected, the finalizers will be garbage collected too.
   */
  def weakKeys[K, A]: UIO[Open[K, A]] = UIO(unsafeMake(true))

  /**
   * An effect that makes a new scope, together with an effect that can close
   * the scope. The scope is backed by a strong map, and finalizers will never
   * be garbage collected so long as a reference is held to the scope.
   */
  def strongKeys[K, A]: UIO[Open[K, A]] = UIO(unsafeMake(false))

  private def unsafeMake[K, A](weakKeys: Boolean): Open[K, A] = {
    val map =
      if (weakKeys) internal.Platform.newWeakHashMap[Any, Any => UIO[Any]]()
      else new java.util.HashMap[Any, Any => UIO[Any]]()

    val scope0 = new ZScope[K, A](false, weakKeys, map)

    def unsafeClose0(a: A): UIO[Any] =
      Sync(scope0.finalizers) {
        val finalizers = scope0.finalizers

        if (scope0.isClosed) null
        else {
          val iterator = finalizers.entrySet.iterator()

          val effect = if (iterator.hasNext()) {
            var effect = iterator.next().getValue()(a)

            while (iterator.hasNext()) {
              val next = iterator.next().getValue()

              effect = effect *> next(a)
            }

            finalizers.clear()

            effect
          } else ZIO.unit

          scope0.isClosed = true

          effect
        }
      }

    new Open[K, A] {
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
