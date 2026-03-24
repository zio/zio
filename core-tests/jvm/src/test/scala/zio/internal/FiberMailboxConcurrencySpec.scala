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

package zio.internal

import zio.ZIOBaseSpec
import zio.test._

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrent stress tests for FiberMailbox. Uses plain Java threads to exercise
 * the MPSC contract independently of ZIO's own fiber runtime.
 */
object FiberMailboxConcurrencySpec extends ZIOBaseSpec {

  private val msg = FiberMessage.resumeUnit

  private def runMpsc(nWriters: Int, msgsPerWriter: Int): (Boolean, Int) = {
    val mailbox = new FiberMailbox {}
    val start   = new CountDownLatch(1)
    val done    = new CountDownLatch(nWriters)

    (0 until nWriters).foreach { _ =>
      val t = new Thread(() => {
        start.await()
        var i = 0
        while (i < msgsPerWriter) { mailbox.add(msg); i += 1 }
        done.countDown()
      })
      t.setDaemon(true)
      t.start()
    }

    start.countDown()
    val finished = done.await(10, TimeUnit.SECONDS)

    var count = 0
    while (mailbox.poll() ne null) count += 1
    (finished, count)
  }

  def spec = suite("FiberMailboxConcurrencySpec")(
    test("no messages lost with 4 concurrent writers (1000 messages each)") {
      val (finished, count) = runMpsc(nWriters = 4, msgsPerWriter = 1000)
      assertTrue(finished) && assertTrue(count == 4000)
    },
    test("no messages lost with 8 concurrent writers (500 messages each)") {
      val (finished, count) = runMpsc(nWriters = 8, msgsPerWriter = 500)
      assertTrue(finished) && assertTrue(count == 4000)
    },
    test("concurrent writers do not produce duplicate messages") {
      val mailbox       = new FiberMailbox {}
      val nWriters      = 4
      val msgsPerWriter = 500
      val start         = new CountDownLatch(1)
      val done          = new CountDownLatch(nWriters)
      val written       = new AtomicInteger(0)

      (0 until nWriters).foreach { _ =>
        val t = new Thread(() => {
          start.await()
          var i = 0
          while (i < msgsPerWriter) { mailbox.add(msg); written.incrementAndGet(); i += 1 }
          done.countDown()
        })
        t.setDaemon(true)
        t.start()
      }

      start.countDown()
      val finished = done.await(10, TimeUnit.SECONDS)

      var polled = 0
      while (mailbox.poll() ne null) polled += 1

      assertTrue(finished) && assertTrue(polled == written.get())
    },
    test("consumer running concurrently with producers receives all messages") {
      // Mirrors the FiberRuntime drain loop: a single consumer polls while
      // multiple producers are still writing.
      val mailbox       = new FiberMailbox {}
      val nWriters      = 4
      val msgsPerWriter = 1000
      val total         = nWriters * msgsPerWriter
      val start         = new CountDownLatch(1)
      val writersDone   = new CountDownLatch(nWriters)
      val polled        = new AtomicInteger(0)
      val consumerDone  = new CountDownLatch(1)

      (0 until nWriters).foreach { _ =>
        val t = new Thread(() => {
          start.await()
          var i = 0
          while (i < msgsPerWriter) { mailbox.add(msg); i += 1 }
          writersDone.countDown()
        })
        t.setDaemon(true)
        t.start()
      }

      // Single consumer: spin-poll until all messages are accounted for.
      val consumer = new Thread(() => {
        start.await()
        while (polled.get() < total) {
          val m = mailbox.poll()
          if (m ne null) polled.incrementAndGet()
        }
        consumerDone.countDown()
      })
      consumer.setDaemon(true)
      consumer.start()

      start.countDown()
      val writersFinished  = writersDone.await(10, TimeUnit.SECONDS)
      val consumerFinished = consumerDone.await(10, TimeUnit.SECONDS)

      assertTrue(writersFinished) &&
      assertTrue(consumerFinished) &&
      assertTrue(polled.get() == total)
    },
    test("isEmpty is true after all producers finish and mailbox is fully drained") {
      // Verifies that isEmpty is consistent with the drained state — the same
      // invariant FiberRuntime relies on at the isEmpty + CAS check in drainQueueOnCurrentThread.
      val mailbox       = new FiberMailbox {}
      val nWriters      = 4
      val msgsPerWriter = 500
      val start         = new CountDownLatch(1)
      val done          = new CountDownLatch(nWriters)

      (0 until nWriters).foreach { _ =>
        val t = new Thread(() => {
          start.await()
          var i = 0
          while (i < msgsPerWriter) { mailbox.add(msg); i += 1 }
          done.countDown()
        })
        t.setDaemon(true)
        t.start()
      }

      start.countDown()
      val finished = done.await(10, TimeUnit.SECONDS)
      while (mailbox.poll() ne null) ()

      assertTrue(finished) && assertTrue(mailbox.isEmpty)
    }
  )
}
