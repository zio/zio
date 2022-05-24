/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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
package zio.internal

import zio.Fiber.Status
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

sealed abstract class FiberState[+E, +A] extends Serializable with Product {
  def suppressed: Cause[Nothing]
  def status: Fiber.Status
  def isInterrupting: Boolean = status.isInterrupting
  def interruptors: Set[FiberId]
  def interruptorsCause: Cause[Nothing] =
    interruptors.foldLeft[Cause[Nothing]](Cause.empty) { case (acc, interruptor) =>
      acc ++ Cause.interrupt(interruptor)
    }
}
object FiberState extends Serializable {
  sealed abstract class CancelerState

  object CancelerState {
    case object Empty                                              extends CancelerState
    case object Pending                                            extends CancelerState
    final case class Registered(asyncCanceler: ZIO[Any, Any, Any]) extends CancelerState
  }

  final case class Executing[E, A](
    status: Fiber.Status,
    observers: List[Callback[Nothing, Exit[E, A]]],
    suppressed: Cause[Nothing],
    interruptors: Set[FiberId],
    asyncCanceler: CancelerState,
    mailbox: UIO[Any]
  ) extends FiberState[E, A]
  final case class Done[E, A](value: Exit[E, A]) extends FiberState[E, A] {
    def suppressed: Cause[Nothing] = Cause.empty
    def status: Fiber.Status       = Status.Done
    def interruptors: Set[FiberId] = Set.empty
  }

  def initial[E, A]: Executing[E, A] =
    Executing[E, A](
      Status.Running(false),
      Nil,
      Cause.empty,
      Set.empty[FiberId],
      CancelerState.Empty,
      null.asInstanceOf[UIO[Any]]
    )
}
