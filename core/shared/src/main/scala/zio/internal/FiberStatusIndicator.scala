/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

object FiberStatusIndicator {
  def apply(
    status: Status,
    messages: Boolean,
    interrupting: Boolean,
    interruptible: Boolean,
    asyncs: Int
  ): FiberStatusIndicator =
    (status + (asyncs << AsyncsShift) + ((if (interrupting) 1 else 0) << InterruptingShift)
      + ((if (interruptible) 1 else 0) << InterruptibleShift)
      + ((if (messages) 1 else 0) << MessagesShift)).asInstanceOf[FiberStatusIndicator]

  def initial: FiberStatusIndicator = FiberStatusIndicator(Status.Running, false, false, true, 0)

  type Status <: Int
  object Status {
    final val Running   = 0.asInstanceOf[Status]
    final val Suspended = 1.asInstanceOf[Status]
    final val Done      = 2.asInstanceOf[Status]
  }

  final val StatusShift = 0
  final val StatusSize  = 2
  final val StatusMask  = 3 << StatusShift
  final val StatusMaskN = ~StatusMask

  final val MessagesShift = StatusSize
  final val MessagesSize  = 1
  final val MessagesMask  = 1 << MessagesShift
  final val MessagesMaskN = ~MessagesMask

  final val InterruptingShift = MessagesShift + MessagesSize
  final val InterruptingSize  = 1
  final val InterruptingMask  = 1 << InterruptingShift
  final val InterruptingMaskN = ~InterruptingMask

  final val InterruptibleShift = InterruptingShift + InterruptingSize
  final val InterruptibleSize  = 1
  final val InterruptibleMask  = 1 << InterruptibleShift
  final val InterruptibleMaskN = ~InterruptibleMask

  final val AsyncsShift = InterruptibleShift + InterruptibleSize
  final val AsyncsSize  = 32 - (StatusSize + MessagesSize + InterruptingSize + InterruptibleSize)
  final val AsyncsMask  = 0x7ffffff << AsyncsShift
  final val AsyncsMaskN = ~AsyncsMask

  def getStatus(flags: FiberStatusIndicator): Status = ((flags & StatusMask) >> StatusShift).asInstanceOf[Status]

  def getAsyncs(flags: FiberStatusIndicator): Int = (flags & AsyncsMask) >> AsyncsShift

  def getInterruptible(flags: FiberStatusIndicator): Boolean = (flags & InterruptibleMask) != 0

  def getInterrupting(flags: FiberStatusIndicator): Boolean = (flags & InterruptingMask) != 0

  def getMessages(flags: FiberStatusIndicator): Boolean = (flags & MessagesMask) != 0

  def withAsyncs(flags: FiberStatusIndicator, asyncs: Int): FiberStatusIndicator =
    ((asyncs << AsyncsShift) | (flags & AsyncsMaskN)).asInstanceOf[FiberStatusIndicator]

  def withInterruptible(flags: FiberStatusIndicator, interruptible: Boolean): FiberStatusIndicator =
    (((if (interruptible) 1 else 0) << InterruptibleShift) | (flags & InterruptibleMaskN))
      .asInstanceOf[FiberStatusIndicator]

  def withInterrupting(flags: FiberStatusIndicator, interrupting: Boolean): FiberStatusIndicator =
    (((if (interrupting) 1 else 0) << InterruptingShift) | (flags & InterruptingMaskN))
      .asInstanceOf[FiberStatusIndicator]

  def withMessages(flags: FiberStatusIndicator, messages: Boolean): FiberStatusIndicator =
    (((if (messages) 1 else 0) << MessagesShift) | (flags & MessagesMaskN)).asInstanceOf[FiberStatusIndicator]

  def withStatus(flags: FiberStatusIndicator, status: Status): FiberStatusIndicator =
    ((status << StatusShift) | (flags & StatusMaskN)).asInstanceOf[FiberStatusIndicator]
}
