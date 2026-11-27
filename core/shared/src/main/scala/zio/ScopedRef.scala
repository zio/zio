/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
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

/**
 * A [[ScopedRef]] is a reference whose value is associated with resources,
 * which must be released properly. You can both get the current value of any
 * [[ScopedRef]], as well as set it to a new value (which may require new
 * resources). The reference itself takes care of properly releasing resources
 * for the old value whenever a new value is obtained.
 *
 * The resources of the current value are released when the `Scope` that the
 * [[ScopedRef]] was created in closes.
 *
 * {{{
 * def connect(name: String): ZIO[Scope, Nothing, String] =
 *   ZIO.acquireRelease(ZIO.debug(s"open \$name").as(name))(_ => ZIO.debug(s"close \$name"))
 *
 * val program: ZIO[Scope, Nothing, String] =
 *   for {
 *     ref <- ScopedRef.fromAcquire(connect("a"))
 *     _   <- ref.set(connect("b"))
 *     v   <- ref.get
 *   } yield v
 * // prints "open a", "close a", "open b"; the result is "b"
 * // "close b" is printed when the Scope closes
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/resource/scopedref/ ScopedRef]]
 */
sealed trait ScopedRef[A] {

  /**
   * Sets the value of this reference to the specified resourcefully-created
   * value. Any resources associated with the old value will be released.
   *
   * This method will not return until the old resources are released and either
   * the reference is successfully changed to the new value or the attempt to
   * acquire a new value fails.
   *
   * The old resources are released before the acquisition of the new value
   * starts. When the acquisition fails, `set` fails with the error and the
   * reference keeps the old value, whose resources have already been released.
   * Concurrent calls run one after another. The acquisition runs
   * uninterruptibly, so interrupting `set` takes effect after it finishes; the
   * reference then keeps the old value, and the resources of the new value are
   * not released by the reference.
   */
  def set[R, E](acquire: ZIO[R with Scope, E, A])(implicit trace: Trace): ZIO[R, E, Unit]

  /**
   * The same as [[ScopedRef#set]], but performs the set asynchronously,
   * ignoring failures. It runs `set` in a daemon fiber and returns without
   * waiting for it.
   */
  def setAsync[R, E](acquire: ZIO[R with Scope, E, A])(implicit trace: Trace): ZIO[R, Nothing, Unit] =
    set[R, E](acquire).forkDaemon.unit

  /**
   * Retrieves the current value of the reference. It does not wait for a `set`
   * that is in progress: it returns the value from before that `set`.
   */
  def get(implicit trace: Trace): UIO[A]
}

/** Constructors for [[ScopedRef]] values. */
object ScopedRef {

  /**
   * Creates a new [[ScopedRef]] from the specified value. This method should
   * not be used for values whose creation require the acquisition of resources.
   * The value is evaluated when the effect runs.
   */
  def make[A](a: => A): ZIO[Scope, Nothing, ScopedRef[A]] = fromAcquire[Any, Nothing, A](ZIO.succeed(a))

  /**
   * Creates a new [[ScopedRef]] from an effect that resourcefully produces a
   * value. The acquisition runs uninterruptibly. Its resources are released
   * when the reference is set to another value, or when the enclosing `Scope`
   * closes. When the acquisition fails, its resources are released and the
   * effect fails with the error.
   */
  def fromAcquire[R, E, A](acquire: ZIO[R, E, A])(implicit trace: Trace): ZIO[R with Scope, E, ScopedRef[A]] =
    ZIO.uninterruptible {
      for {
        newScope <- Scope.make
        a <-
          acquire.provideSomeEnvironment[R](_.add[Scope](newScope)).onError(cause => newScope.close(Exit.fail(cause)))
        ref      <- Ref.Synchronized.make((newScope, a))
        scopedRef = Synch(ref)
        _        <- ZIO.addFinalizer(scopedRef.close)
      } yield scopedRef
    }

  private case class Synch[A](ref: Ref.Synchronized[(Scope.Closeable, A)]) extends ScopedRef[A] {
    final def close(implicit trace: Trace): UIO[Any] =
      ref.get.flatMap { case (scope, _) =>
        scope.close(Exit.unit)
      }

    final def get(implicit trace: Trace): UIO[A] = ref.get.map(_._2)

    final def set[R, E](acquire: ZIO[R with Scope, E, A])(implicit trace: Trace): ZIO[R, E, Unit] =
      ref.modifyZIO { case (oldScope, a) =>
        ZIO.uninterruptible {
          for {
            _        <- oldScope.close(Exit.unit)
            newScope <- Scope.make
            exit     <- newScope.extend[R](acquire).exit
            result <- exit match {
                        case Exit.Failure(cause) =>
                          newScope.close(Exit.unit).as(Exit.failCause(cause) -> (oldScope -> a))
                        case Exit.Success(a) => ZIO.succeed(ZIO.unit -> (newScope -> a))
                      }
          } yield result
        }
      }.flatten
  }
}
