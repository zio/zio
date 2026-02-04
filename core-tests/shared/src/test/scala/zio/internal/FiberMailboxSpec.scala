/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import zio._
import zio.test._

object FiberMailboxSpec extends ZIOSpecDefault {

  def spec = suite("FiberMailboxSpec")(
    test("single producer, single consumer (sequential)") {
      val mailbox = new FiberMailbox()
      val msg1    = FiberMessage.resumeUnit
      val msg2    = FiberMessage.resumeUnit

      mailbox.offer(msg1)
      mailbox.offer(msg2)

      assertTrue(mailbox.poll() == msg1) &&
      assertTrue(mailbox.poll() == msg2) &&
      assertTrue(mailbox.poll() == null)
    },
    test("cross chunk boundary (overflow 32 items)") {
      val mailbox = new FiberMailbox()
      val n       = 100
      for (_ <- 1 to n) {
        mailbox.offer(FiberMessage.resumeUnit)
      }

      var success = true
      for (_ <- 1 to n) {
        if (mailbox.poll() == null) success = false
      }

      assertTrue(success) && assertTrue(mailbox.poll() == null)
    },
    test("concurrent multiple producers, single consumer") {
      val mailbox             = new FiberMailbox()
      val numProducers        = 4
      val messagesPerProducer = 1000
      val totalMessages       = numProducers * messagesPerProducer

      for {
        _ <- ZIO.foreachParDiscard(1 to numProducers) { _ =>
               ZIO.succeed {
                 var i = 0
                 while (i < messagesPerProducer) {
                   mailbox.offer(FiberMessage.resumeUnit)
                   i += 1
                 }
               }
             }
      } yield {
        var received = 0
        while (mailbox.poll() != null) {
          received += 1
        }
        assertTrue(received == totalMessages)
      }
    }
  )
}
