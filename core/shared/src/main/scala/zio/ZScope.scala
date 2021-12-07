/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

import zio.internal.{Platform, Sync}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.{Comparator, Map}

/**
 * A `ZScope[A]` is a value that allows adding finalizers identified by a key.
 * Scopes are closed with a value of type `A`, which is provided to all the
 * finalizers when the scope is released.
 *
 * For safety reasons, this interface has no method to close a scope. Rather, an
 * open scope may be required with `ZScope.make`, which returns a function that
 * can close a scope. This allows scopes to be safely passed around without fear
 * they will be accidentally closed.
 */
sealed abstract class ZScope[+A] { self =>

  /**
   * Determines if the scope is closed at the instant the effect executes.
   * Returns an effect that will succeed with `true` if the scope is closed, and
   * `false` otherwise.
   */
  def isClosed(implicit trace: ZTraceElement): UIO[Boolean]

  /**
   * Prevents a previously added finalizer from being executed when the scope is
   * closed. The returned effect will succeed with `true` if the finalizer will
   * not be run by this scope, and `false` otherwise.
   */
  def deny(key: => ZScope.Key)(implicit trace: ZTraceElement): UIO[Boolean] = UIO(unsafeDeny(key))

  /**
   * Determines if the scope is empty (has no finalizers) at the instant the
   * effect executes. The returned effect will succeed with `true` if the scope
   * is empty, and `false` otherwise.
   */
  def isEmpty(implicit trace: ZTraceElement): UIO[Boolean]

  /**
   * Adds a finalizer to the scope. If successful, this ensures that when the
   * scope exits, the finalizer will be run, assuming the key has not been
   * garbage collected.
   *
   * The returned effect will succeed with `Right` with a key if the finalizer
   * was added to the scope or `Left` with the value the scope was closed with
   * if the scope is already closed.
   */
  def ensure(finalizer: A => UIO[Any], mode: ZScope.Mode = ZScope.Mode.Strong)(implicit
    trace: ZTraceElement
  ): UIO[Either[A, ZScope.Key]]

  /**
   * Extends the specified scope so that it will not be closed until this scope
   * is closed. Note that extending a scope into the global scope will result in
   * the scope *never* being closed!
   *
   * Scope extension does not result in changes to the scope contract: open
   * scopes must *always* be closed.
   */
  final def extend(that: ZScope[Any])(implicit trace: ZTraceElement): UIO[Boolean] = UIO(unsafeExtend(that))

  /**
   * Determines if the scope is open at the moment the effect is executed.
   * Returns an effect that will succeed with `true` if the scope is open, and
   * `false` otherwise.
   */
  def isOpen(implicit trace: ZTraceElement): UIO[Boolean] = isClosed.map(!_)

  /**
   * Determines if the scope has been released at the moment the effect is
   * executed executed. A scope can be closed yet unreleased, if it has been
   * extended by another scope which is not yet released.
   */
  def isReleased(implicit trace: ZTraceElement): UIO[Boolean]

  private[zio] def unsafeDeny(key: ZScope.Key): Boolean
  private[zio] def unsafeEnsure(finalizer: A => UIO[Any], mode: ZScope.Mode)(implicit
    trace: ZTraceElement
  ): Either[A, ZScope.Key]
  private[zio] def unsafeExtend(that: ZScope[Any])(implicit trace: ZTraceElement): Boolean
}
object ZScope {
  sealed abstract class Mode
  object Mode {
    case object Weak   extends Mode
    case object Strong extends Mode
  }

  /**
   * Represents a key in a scope, which is associated with a single finalizer.
   */
  sealed abstract class Key {

    /**
     * Attempts to remove the finalizer associated with this key from the scope.
     * The returned effect will succeed with a boolean, which indicates whether
     * the attempt was successful. A value of `true` indicates the finalizer
     * will not be executed, while a value of `false` indicates the finalizer
     * was already executed.
     *
     * @return
     */
    def remove: UIO[Boolean]
  }
  object Key {
    def apply(remove0: UIO[Boolean]): Key = new Key { def remove = remove0 }
  }

  /**
   * The global scope, which is entirely stateless. Finalizers added to the
   * global scope will never be executed (nor kept in memory).
   */
  object global extends ZScope[Nothing] {
    private def unsafeEnsureResult(implicit trace: ZTraceElement) = Right(Key(UIO(true)))
    private def ensureResult(implicit trace: ZTraceElement)       = UIO(unsafeEnsureResult)

    def isClosed(implicit trace: ZTraceElement): UIO[Boolean] = UIO(false)

    def isEmpty(implicit trace: ZTraceElement): UIO[Boolean] = UIO(false)

    def ensure(finalizer: Nothing => UIO[Any], mode: ZScope.Mode = ZScope.Mode.Strong)(implicit
      trace: ZTraceElement
    ): UIO[Either[Nothing, Key]] =
      ensureResult

    def isReleased(implicit trace: ZTraceElement): UIO[Boolean] = UIO(false)

    private[zio] def unsafeDeny(key: Key): Boolean = true
    private[zio] def unsafeEnsure(finalizer: Nothing => UIO[Any], mode: ZScope.Mode)(implicit
      trace: ZTraceElement
    ): Either[Nothing, Key] =
      unsafeEnsureResult
    private[zio] def unsafeExtend(that: ZScope[Any])(implicit trace: ZTraceElement): Boolean = that match {
      case local: Local[_] => local.unsafeAddRef()
      case _               => true
    }
  }

  /**
   * A tuple that contains an open scope, together with a function that closes
   * the scope.
   */
  final case class Open[A](close: A => UIO[Boolean], scope: Local[A])

  /**
   * An effect that makes a new open scope, which provides not just the scope,
   * but also a way to close the scope.
   */
  def make[A](implicit trace: ZTraceElement): UIO[Open[A]] = UIO(unsafeMake())

  private[ZScope] final case class OrderedFinalizer(order: Int, finalizer: Any => UIO[Any])

  private[zio] def unsafeMake[A]()(implicit trace: ZTraceElement): Open[A] = {
    val nullA: A = null.asInstanceOf[A]

    val exitValue = new AtomicReference(nullA)

    val scope0 =
      new Local[A](new AtomicInteger(Int.MinValue), exitValue, new AtomicInteger(1))

    Open[A](
      (a: A) =>
        UIO.suspendSucceed {
          val result = scope0.unsafeClose(a)

          if (result eq null) UIO(false) else result as true
        },
      scope0
    )
  }

  final case class Local[A](
    // A counter for finalizers, which is used for ordering purposes.
    private[zio] val finalizerCount: AtomicInteger,
    // The value that a scope is closed with (or `null`).
    private[zio] val exitValue: AtomicReference[A],
    // The number of references to the scope, which defaults to 1.
    private[zio] val references: AtomicInteger
  ) extends ZScope[A] { self =>

    // The weak finalizers attached to the scope.
    private[this] var _weakFinalizers: Map[Key, OrderedFinalizer] = null.asInstanceOf[Map[Key, OrderedFinalizer]]
    // The strong finalizers attached to the scope.
    private[this] var _strongFinalizers: Map[Key, OrderedFinalizer] = null.asInstanceOf[Map[Key, OrderedFinalizer]]

    def weakFinalizers: Map[Key, OrderedFinalizer] =
      if (_weakFinalizers eq null) {
        _weakFinalizers = Platform.newWeakHashMap[Key, OrderedFinalizer]()
        _weakFinalizers
      } else {
        _weakFinalizers
      }

    def strongFinalizers: Map[Key, OrderedFinalizer] =
      if (_strongFinalizers eq null) {
        _strongFinalizers = new java.util.HashMap[Key, OrderedFinalizer]()
        _strongFinalizers
      } else {
        _strongFinalizers
      }

    def isClosed(implicit trace: ZTraceElement): UIO[Boolean] = UIO(unsafeIsClosed())

    def isEmpty(implicit trace: ZTraceElement): UIO[Boolean] = UIO(unsafeIsEmpty())

    def ensure(finalizer: A => UIO[Any], mode: ZScope.Mode = ZScope.Mode.Strong)(implicit
      trace: ZTraceElement
    ): UIO[Either[A, Key]] =
      UIO(unsafeEnsure(finalizer, mode))

    def release(implicit trace: ZTraceElement): UIO[Boolean] = UIO.suspendSucceed {
      val result = unsafeRelease()

      if (result eq null) UIO(false) else result as true
    }

    def child(implicit trace: ZTraceElement): UIO[Either[A, ZScope.Open[A]]] = UIO(unsafeChild())

    def isReleased(implicit trace: ZTraceElement): UIO[Boolean] = UIO(unsafeIsReleased())

    private[this] def finalizers(mode: ZScope.Mode): Map[Key, OrderedFinalizer] =
      if (mode == ZScope.Mode.Weak) weakFinalizers else strongFinalizers

    private[zio] def unsafeIsClosed(): Boolean = Sync(self)(exitValue.get() != null)

    private[zio] def unsafeClose(a0: A)(implicit trace: ZTraceElement): UIO[Any] =
      Sync(self) {
        exitValue.compareAndSet(null.asInstanceOf[A], a0)

        unsafeRelease()
      }

    private[zio] def unsafeDeny(key: Key): Boolean =
      Sync(self) {
        if (unsafeIsClosed()) false
        else
          ((_weakFinalizers ne null) && (_weakFinalizers.remove(key) ne null)) ||
          ((_strongFinalizers ne null) && (_strongFinalizers.remove(key) ne null))
      }

    private[zio] def unsafeEnsure(finalizer: A => UIO[Any], mode: ZScope.Mode)(implicit
      trace: ZTraceElement
    ): Either[A, Key] =
      Sync(self) {
        def coerce(f: A => UIO[Any]): Any => UIO[Any] = f.asInstanceOf[Any => UIO[Any]]

        if (unsafeIsClosed()) Left(exitValue.get())
        else {
          lazy val key: Key = Key(deny(key))

          finalizers(mode).put(key, OrderedFinalizer(finalizerCount.incrementAndGet(), coerce(finalizer)))

          Right(key)
        }
      }

    private[zio] def unsafeAddRef(): Boolean =
      Sync(self) {
        if (unsafeIsClosed()) false
        else {
          references.incrementAndGet()
          true
        }
      }

    private[zio] def unsafeIsEmpty(): Boolean =
      Sync(self) {
        ((_weakFinalizers eq null) || _weakFinalizers.isEmpty()) &&
        ((_strongFinalizers eq null) || _strongFinalizers.isEmpty())
      }

    private[zio] def unsafeChild()(implicit trace: ZTraceElement): Either[A, ZScope.Open[A]] =
      Sync(self) {
        val childScope = unsafeMake[A]()
        unsafeEnsure(a => childScope.close(a), Mode.Strong) match {
          case Left(a) =>
            Left(a)
          case Right(key) =>
            childScope.scope.unsafeEnsure(_ => UIO(unsafeDeny(key)), Mode.Strong)
            Right(childScope)
        }
      }

    private[zio] def unsafeExtend(that: ZScope[Any])(implicit trace: ZTraceElement): Boolean =
      if (self eq that) true
      else
        that match {
          case ZScope.global => true

          case child: ZScope.Local[_] =>
            Sync(child) {
              Sync(self) {
                if (!self.unsafeIsClosed() && !child.unsafeIsClosed()) {
                  // If parent and child scopes are both open:
                  child.unsafeAddRef()

                  self.unsafeEnsure(_ => child.release, ZScope.Mode.Strong)

                  true
                } else false
              }
            }
        }

    private[zio] def unsafeRelease()(implicit trace: ZTraceElement): UIO[Unit] =
      Sync(self) {
        if (references.decrementAndGet() == 0) {
          val weakFinalizersSize   = if (_weakFinalizers eq null) 0 else _weakFinalizers.size()
          val strongFinalizersSize = if (_strongFinalizers eq null) 0 else _strongFinalizers.size()
          val totalSize            = weakFinalizersSize + strongFinalizersSize

          if (totalSize == 0) null
          else {
            val array = Array.ofDim[OrderedFinalizer](totalSize)

            var i = 0

            if (weakFinalizersSize != 0) {
              val iterator = weakFinalizers.entrySet().iterator()

              while (iterator.hasNext()) {
                array(i) = iterator.next().getValue()
                i = i + 1
              }

              weakFinalizers.clear()
            }

            if (strongFinalizersSize != 0) {
              val iterator = strongFinalizers.entrySet().iterator()

              while (iterator.hasNext()) {
                array(i) = iterator.next().getValue()
                i = i + 1
              }

              strongFinalizers.clear()
            }

            val comparator: Comparator[OrderedFinalizer] = (l: OrderedFinalizer, r: OrderedFinalizer) =>
              if (l eq null) -1 else if (r eq null) 1 else l.order - r.order

            java.util.Arrays.sort(array, comparator)

            val a = exitValue.get()

            array
              .foldLeft[UIO[Cause[Nothing]]](noCauseEffect) { case (acc, o) =>
                if (o ne null) acc.zipWith(o.finalizer(a).cause)(_ ++ _)
                else acc
              }
              .uncause[Nothing]
          }
        } else null
      }

    private[zio] def unsafeIsReleased(): Boolean = references.get() <= 0
  }

  private val noCause: Cause[Nothing]            = Cause.empty
  private val noCauseEffect: UIO[Cause[Nothing]] = UIO.succeedNow(noCause)
}
