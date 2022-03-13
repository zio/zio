/*
 * Copyright 2018-2022 John A. De Goes and the ZIO Contributors
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

package object managed {

  type Managed[+E, +A]   = ZManaged[Any, E, A]         //Manage an `A`, may fail with `E`        , no requirements
  type TaskManaged[+A]   = ZManaged[Any, Throwable, A] //Manage an `A`, may fail with `Throwable`, no requirements
  type RManaged[-R, +A]  = ZManaged[R, Throwable, A]   //Manage an `A`, may fail with `Throwable`, requires an `R`
  type UManaged[+A]      = ZManaged[Any, Nothing, A]   //Manage an `A`, cannot fail              , no requirements
  type URManaged[-R, +A] = ZManaged[R, Nothing, A]     //Manage an `A`, cannot fail              , requires an `R`

  implicit final class ZIOSyntax0[R, E, A](private val self: ZIO[Scope with R, E, A]) {
    def toManaged0: ZManaged[R, E, A] =
      ???
  }

  implicit final class ZManagedZIOSyntax[R, E, A](private val self: ZIO[R, E, A]) {

    /**
     * Forks the fiber in a [[ZManaged]]. Using the [[ZManaged]] value will
     * execute the effect in the fiber, while ensuring its interruption when the
     * effect supplied to [[ZManaged#use]] completes.
     */
    final def forkManaged(implicit trace: ZTraceElement): ZManaged[R, Nothing, Fiber.Runtime[E, A]] =
      self.toManaged.fork

    /**
     * Converts this ZIO to [[ZManaged]] with no release action. It will be
     * performed interruptibly.
     */
    final def toManaged(implicit trace: ZTraceElement): ZManaged[R, E, A] =
      ZManaged.fromZIO(self)

    /**
     * Converts this ZIO to [[Managed]]. This ZIO and the provided release
     * action will be performed uninterruptibly.
     */
    final def toManagedWith[R1 <: R](release: A => URIO[R1, Any])(implicit trace: ZTraceElement): ZManaged[R1, E, A] =
      ZManaged.acquireReleaseWith(self)(release)

    /**
     * Converts this ZIO to [[ZManaged]] with no release action. It will be
     * performed interruptibly.
     */
    @deprecated("use toManaged", "2.0.0")
    final def toManaged_(implicit trace: ZTraceElement): ZManaged[R, E, A] =
      self.toManaged
  }

  implicit final class ZManagedZIOAutoCloseableSyntax[R, E, A <: AutoCloseable](private val self: ZIO[R, E, A])
      extends AnyVal {

    /**
     * Converts this ZIO value to a ZManaged value. See
     * [[ZManaged.fromAutoCloseable]].
     */
    def toManagedAuto(implicit trace: ZTraceElement): ZManaged[R, E, A] =
      ZManaged.fromAutoCloseable(self)
  }

  implicit final class ZManagedZIOCompanionSyntax(private val self: ZIO.type) extends AnyVal {

    /**
     * Acquires a resource, uses the resource, and then releases the resource.
     * However, unlike `acquireReleaseWith`, the separation of these phases
     * allows the acquisition to be interruptible.
     *
     * Useful for concurrent data structures and other cases where the
     * 'deallocator' can tell if the allocation succeeded or not just by
     * inspecting internal / external state.
     */
    def reserve[R, E, A, B](reservation: => ZIO[R, E, Reservation[R, E, A]])(use: A => ZIO[R, E, B])(implicit
      trace: ZTraceElement
    ): ZIO[R, E, B] =
      ZManaged.fromReservationZIO(reservation).use(use)
  }
}
