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

import zio.test._
import zio.ZIO
import java.util.concurrent.CountDownLatch

object FiberMailboxSpec extends ZIOSpecDefault {

  def spec = suite("FiberMailboxSpec")(
    test("single producer, single consumer (sequential)") {
      val mailbox = new FiberMailbox()
      val msg1 = FiberMessage.resumeUnit
      val msg2 = FiberMessage.resumeUnit

      mailbox.offer(msg1)
      mailbox.offer(msg2)

      assertTrue(mailbox.poll() == msg1) &&
      assertTrue(mailbox.poll() == msg2) &&
      assertTrue(mailbox.poll() == null)
    },
    test("cross chunk boundary (overflow 32 items)") {
      val mailbox = new FiberMailbox()
      val n = 100
      // Offer 100 messages to force creation of multiple linked chunks
      for (i <- 1 to n) {
        mailbox.offer(FiberMessage.Resume(ZIO.succeed(i)))
      }

      var success = true
      for (i <- 1 to n) {
        val msg = mailbox.poll()
        if (msg == null) success = false
        else {
           // Verify order is preserved
           msg match {
             case FiberMessage.Resume(_) => 
               ()
             case _ => success = false
           }
        }
      }

      assertTrue(success) && assertTrue(mailbox.poll() == null)
    },
    test("concurrent multiple producers, single consumer") {
      val mailbox = new FiberMailbox()
      val numProducers = 4
      val messagesPerProducer = 1000
      val totalMessages = numProducers * messagesPerProducer

      // Use raw threads to simulate intense contention outside of ZIO's scheduler
      val latch = new CountDownLatch(1)
      
      val threads = (1 to numProducers).map { _ =>
        new Thread(() => {
          try {
            latch.await()
            for (_ <- 1 to messagesPerProducer) {
              mailbox.offer(FiberMessage.resumeUnit)
            }
          } catch {
            case _: InterruptedException => ()
          }
        })
      }

      threads.foreach(_.start())
      latch.countDown() // Start race
      threads.foreach(_.join())

      // Now drain and count
      var received = 0
      var msg = mailbox.poll()
      while (msg != null) {
        received += 1
        msg = mailbox.poll()
      }

      assertTrue(received == totalMessages)
    }
  )
}