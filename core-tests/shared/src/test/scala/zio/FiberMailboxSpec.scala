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

import zio.internal.{FiberMailbox, FiberMessage}
import zio.test._
import zio.test.TestAspect.nonFlaky

object FiberMailboxSpec extends ZIOBaseSpec {

  def spec = suite("FiberMailboxSpec")(
    suite("single-threaded behaviour")(
      test("isEmpty returns true for a new mailbox") {
        val m = new FiberMailbox()
        assertTrue(m.isEmpty)
      },
      test("isEmpty returns false after an offer") {
        val m = new FiberMailbox()
        m.offer(FiberMessage.resumeUnit)
        assertTrue(!m.isEmpty)
      },
      test("poll returns null from an empty mailbox") {
        val m = new FiberMailbox()
        assertTrue(m.poll() eq null)
      },
      test("offer then poll round-trips a message") {
        val m   = new FiberMailbox()
        val msg = FiberMessage.resumeUnit
        m.offer(msg)
        val got = m.poll()
        assertTrue((got eq msg) && m.isEmpty && (m.poll() eq null))
      },
      test("multiple offer/poll cycles work correctly") {
        val m    = new FiberMailbox()
        val msg1 = FiberMessage.resumeUnit
        val msg2 = FiberMessage.Resume(ZIO.unit)
        m.offer(msg1)
        val got1 = m.poll()
        m.offer(msg2)
        val got2 = m.poll()
        assertTrue(
          (got1 eq msg1) &&
            (got2 eq msg2) &&
            m.isEmpty
        )
      },
      test("two concurrent messages are both delivered (hot slot + overflow)") {
        // Simulate what happens when two writers call offer() simultaneously:
        // the second falls through to the overflow queue.
        val m    = new FiberMailbox()
        val msg1 = FiberMessage.resumeUnit
        val msg2 = FiberMessage.Resume(ZIO.unit)
        // Manually fill the hot slot so the second offer() goes to overflow
        m.offer(msg1) // lands in hot slot
        m.offer(msg2) // hot slot occupied → overflow
        assertTrue(!m.isEmpty)
        val got1 = m.poll() // from hot slot
        val got2 = m.poll() // from overflow
        assertTrue(
          (got1 eq msg1) &&
            (got2 eq msg2) &&
            m.isEmpty
        )
      },
      test("three messages are all delivered") {
        val m    = new FiberMailbox()
        val msg1 = FiberMessage.resumeUnit
        val msg2 = FiberMessage.Resume(ZIO.unit)
        val msg3 = FiberMessage.Resume(ZIO.unit)
        m.offer(msg1)
        m.offer(msg2)
        m.offer(msg3)
        val got1 = m.poll()
        val got2 = m.poll()
        val got3 = m.poll()
        assertTrue(
          (got1 eq msg1) &&
            (got2 eq msg2) &&
            (got3 eq msg3) &&
            m.isEmpty &&
            (m.poll() eq null)
        )
      }
    ),
    suite("integration with FiberRuntime")(
      test("fiber can be interrupted via inbox") {
        for {
          fiber <- ZIO.never.fork
          _     <- fiber.interrupt
        } yield assertCompletes
      },
      test("fiber resume via inbox works") {
        for {
          p     <- Promise.make[Nothing, Int]
          fiber <- p.await.fork
          _     <- p.succeed(42)
          v     <- fiber.join
        } yield assertTrue(v == 42)
      },
      test("many fibers can run concurrently with many messages") {
        ZIO
          .foreachPar(1 to 1000)(_ => ZIO.yieldNow *> ZIO.succeed(1))
          .map(_.sum)
          .map(sum => assertTrue(sum == 1000))
      } @@ nonFlaky(10)
    )
  )
}
