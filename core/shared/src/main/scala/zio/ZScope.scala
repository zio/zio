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
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }

import zio.internal.Sync

/**
 * A `ZScope[K, A]` is a value that allows adding finalizers identified by `K`
 * up until the point where the scope is closed by a value of type `A`.
 */
sealed trait ZScope[-K, +A] { self =>

  /**
   * Determines if the scope is closed at the instant the effect executes.
   * Returns an effect that will succeed with `true` if the scope is closed,
   * and `false` otherwise.
   */
  def closed: UIO[Boolean]

  /**
   * Prevents a previously added finalizer from being executed when the scope 
   * is closed. The returned effect will succeed with `true` if the finalizer 
   * will not be run by this scope, and `false` otherwise.
   */
  def deny(k: K): UIO[Boolean]

  /**
   * Determines if the scope is empty (has no finalizers) at the instant the
   * effect executes. The returned effect will succeed with `true` if the scope
   * is empty, and `false` otherwise.
   */
  def empty: UIO[Boolean]

  /**
   * Adds a finalizer to the scope, using the finalizer itself as the key for
   * the finalizer. If successful, this ensures that when the scope exits, the
   * finalizer will be run.
   *
   * The returned effect will succeed with `true` if the finalizer was added
   * to the scope, and `false` if the scope was already closed.
   */
  final def ensure[A1 >: A](finalizer: A1 => UIO[Any])(implicit ev: (A1 => UIO[Any]) <:< K): UIO[Boolean] =
    ensure(ev(finalizer), finalizer)

  /**
   * Adds a finalizer to the scope. If successful, this ensures that when the
   * scope exits, the finalizer will be run, assuming the key has not been
   * garbage collected.
   *
   * The returned effect will succeed with `true` if the finalizer was added
   * to the scope, and `false` if the scope was already closed.
   */
  def ensure(key: K, finalizer: A => UIO[Any]): UIO[Boolean]

  /**
   * Extends the specified scope so that it will not be closed until this
   * scope is closed. Note that extending a scope into the global scope
   * will result in the scope never being closed!
   *
   * Scope extension does not result in changes to the scope contract: open
   * scopes must always be closed.
   */
  def extend(that: ZScope[Any, Nothing]): UIO[Boolean] = UIO.effectSuspendTotal {
    (self, that) match {
      case (ZScope.global, ZScope.global) => UIO(true)

      case (ZScope.global, child : ZScope.Local[Any, Nothing]) =>
        Sync(child.finalizers) {
          if (child.unsafeClosed()) UIO(false)
          else {
            child.unsafeAddRef()
            UIO(true)
          }
        }

      case (_ : ZScope.Local[a, b], ZScope.global) => UIO(true)

      case (parent : ZScope.Local[a, b], child : ZScope.Local[Any, Nothing]) =>
        Sync(child.finalizers) {
          Sync(parent.finalizers) {
            if (child.unsafeAddRef()) {
              val newKey = new {}.asInstanceOf[a]

              if (parent.unsafeEnsure(newKey, _ => Sync(child.finalizers)(child.unsafeRelease()))) UIO(true)
              else {
                val effect = child.unsafeRelease()

                if (effect eq null) UIO(false) else effect as false
              }
            } else UIO(false)
          }
        }
    }
  }

  /**
   * Determines if the scope is open at the moment the effect is executed.
   * Returns an effect that will succeed with `true` if the scope is open,
   * and `false` otherwise.
   */
  def open: UIO[Boolean] = closed.map(!_)
}
object ZScope {

  /**
   * The global scope, which is entirely stateless. Finalizers added to the
   * global scope will never be executed (nor kept in memory).
   */
  object global extends ZScope[Any, Nothing] {
    def closed: UIO[Boolean] = UIO(false)

    def deny(k: Any): UIO[Boolean] = UIO(true)

    def empty: UIO[Boolean] = UIO(false)

    def ensure(key: Any, finalizer: Nothing => UIO[Any]): UIO[Boolean] = UIO(true)
  }

  /**
   * A tuple that contains an open scope, together with a function that closes 
   * the scope.
   */
  final case class Open[K, A](close: A => UIO[Boolean], scope: Local[K, A])

  /**
   * An effect that makes a new open scope, which provides not just the scope,
   * but also a way to close the scope.
   *
   * The scope is backed by a weak map or a strong map, depending on the value 
   * of the `weakKeys` parameter. For weka maps, if the keys are collected, 
   * then the finalizers will be garbage collected too, without being run.
   */
  def make[K, A](weakKeys: Boolean): UIO[Open[K, A]] = UIO(unsafeMake(weakKeys))

  private[zio] def unsafeMake[K, A](weakKeys: Boolean): Open[K, A] = {
    val nullA: A = null.asInstanceOf[A]

    val exitValue = new AtomicReference(nullA)

    val opened = new AtomicInteger(1)

    val finalizers =
      if (weakKeys) internal.Platform.newWeakHashMap[Any, Any => UIO[Any]]()
      else new java.util.HashMap[Any, Any => UIO[Any]]()

    val scope0 = new Local[K, A](exitValue, opened, finalizers)

    Open[K, A](
      (a: A) =>
        UIO.effectSuspendTotal {
          Sync(finalizers) {
            val result = scope0.unsafeClose(a)

            if (result eq null) UIO(false)
            else result as true
          }
        },
      scope0
    )
  }

  final class Local[K, A](
    /**
     * The value that a scope is closed with (or `null`).
     */
    private[zio] val exitValue: AtomicReference[A],
    /**
     * The number of references to the scope, which defaults to 1.
     */
    private[zio] val opened: AtomicInteger,
    /**
     * The finalizers attached to the scope.
     */
    private[zio] val finalizers: Map[Any, Any => UIO[Any]]
  ) extends ZScope[K, A] { self =>

    /**
     * Determines if the scope is closed at the instant the effect executes.
     */
    def closed: UIO[Boolean] = UIO(unsafeClosed())

    /**
     * Unensures a finalizer runs when the scope is closed.
     */
    def deny(k: K): UIO[Boolean] = UIO(unsafeDeny(k))

    /**
     * Determines if the scope is empty at the instant the effect executes.
     */
    def empty: UIO[Boolean] = UIO(finalizers.size() == 0)

    /**
     * Adds a finalizer to the scope. If successful, this ensures that when the
     * scope exits, the finalizer will be run, assuming the key has not been
     * garbage collected.
     */
    def ensure(key: K, finalizer: A => UIO[Any]): UIO[Boolean] = UIO(unsafeEnsure(key, finalizer))

    private[zio] def unsafeClosed(): Boolean = Sync(finalizers)(opened.get() <= 0)

    private[zio] def unsafeClose(a0: A): UIO[Any] =
      Sync(finalizers) {
        exitValue.compareAndSet(null.asInstanceOf[A], a0)

        unsafeRelease()
      }

    private[zio] def unsafeDeny(k: K): Boolean =
      Sync(finalizers) {
        if (unsafeClosed()) false
        else finalizers.remove(k) ne null
      }

    private[zio] def unsafeEnsure(key: K, finalizer: A => UIO[Any]): Boolean =
      Sync(finalizers) {
        def coerce(f: A => UIO[Any]): Any => UIO[Any] = f.asInstanceOf[Any => UIO[Any]]

        if (unsafeClosed()) false
        else {
          if (finalizers.containsKey(key)) false
          else {
            finalizers.put(key, coerce(finalizer))

            true
          }
        }
      }

    private[zio] def unsafeAddRef(): Boolean =
      Sync(finalizers) {
        if (unsafeClosed()) false
        else {
          opened.incrementAndGet()
          true
        }
      }

    private[zio] def unsafeEmpty(): Boolean =
      Sync(finalizers) {
        finalizers.size() == 0
      }

    private[zio] def unsafeRelease(): UIO[Any] =
      Sync(finalizers) {
        if (opened.decrementAndGet() == 0) {
          val iterator = finalizers.entrySet.iterator()

          val effect = if (iterator.hasNext()) {
            val a = exitValue.get()

            val value = iterator.next().getValue()

            var effect = if (value eq null) IO.unit else value(a)

            while (iterator.hasNext()) {
              val value = iterator.next().getValue()

              val next = if (value eq null) IO.unit else value(a)

              effect = effect *> next
            }

            finalizers.clear()

            effect
          } else ZIO.unit

          effect
        } else null
      }
  }
}
