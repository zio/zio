/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

package zio.internal.concurrent

import java.util.concurrent.atomic.AtomicLongFieldUpdater
import scala.annotation.tailrec

private[zio] sealed trait MailboxMessage

private[zio] object MailboxMessage {
  case object Yield extends MailboxMessage
  case object Checkpoint extends MailboxMessage
}

private[zio] final class Mailbox private (initialCapacity: Int) extends MutableConcurrentQueue[MailboxMessage] {
  import Mailbox._

  @volatile
  private var writeIndex: Long = 0L
  private var readIndex: Long  = 0L

  private val core: Array[AnyRef] = new Array[AnyRef](initialCapacity)
  private var overflow: List[AnyRef] = Nil

  private def writ: Long = writeIndex
  private def read: Long = readIndex

  def capacity(): Int = Int.MaxValue

  def size(): Int = {
    val w = writeIndex
    val r = readIndex
    val size = (w - r).toInt
    if (size < 0) 0 else size
  }

  def isEmpty(): Boolean = {
    val w = writeIndex
    val r = readIndex
    w <= r
  }

  def isFull(): Boolean = false

  @tailrec
  def offer(message: MailboxMessage): Boolean = {
    val w = writeIndex
    val r = readIndex
    val capacity = core.length
    val size = (w - r).toInt

    if (size < capacity) {
      val index = (w % capacity).toInt
      if (writeIndexUpdater.compareAndSet(this, w, w + 1)) {
        core(index) = message.asInstanceOf[AnyRef]
        true
      } else {
        offer(message)
      }
    } else {
      val newOverflow = message.asInstanceOf[AnyRef] :: overflow
      if (writeIndexUpdater.compareAndSet(this, w, w + 1)) {
        overflow = newOverflow
        true
      } else {
        offer(message)
      }
    }
  }

  def poll(default: MailboxMessage): MailboxMessage = {
    val message = poll()
    if (message eq null) default else message
  }

  @tailrec
  def poll(): MailboxMessage = {
    val w = writeIndex
    val r = readIndex

    if (r >= w) {
      null
    } else {
      val capacity = core.length
      val index = (r % capacity).toInt
      val message =
        if (r / capacity == 0) {
          val msg = core(index)
          core(index) = null
          msg
        } else {
          val msg = overflow.last
          overflow = overflow.init
          msg
        }

      if (readIndexUpdater.compareAndSet(this, r, r + 1)) {
        message.asInstanceOf[MailboxMessage]
      } else {
        // Another thread advanced readIndex; retry
        poll()
      }
    }
  }

  def unsafeOffer(message: MailboxMessage): Unit = {
    offer(message)
  }

  def unsafePoll(): MailboxMessage = {
    poll()
  }

  def unsafePeek(): MailboxMessage = {
    val r = readIndex
    val w = writeIndex
    if (r >= w) null
    else {
      val capacity = core.length
      val index = (r % capacity).toInt
      if (r / capacity == 0) core(index).asInstanceOf[MailboxMessage]
      else overflow.last.asInstanceOf[MailboxMessage]
    }
  }
}

private[zio] object Mailbox {
  def apply(): Mailbox = new Mailbox(4)

  private val writeIndexUpdater: AtomicLongFieldUpdater[Mailbox] =
    AtomicLongFieldUpdater.newUpdater(classOf[Mailbox], "writeIndex")

  private val readIndexUpdater: AtomicLongFieldUpdater[Mailbox] =
    AtomicLongFieldUpdater.newUpdater(classOf[Mailbox], "readIndex")
}