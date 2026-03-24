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

object FiberMailboxSpec extends ZIOBaseSpec {

  private val msg  = FiberMessage.resumeUnit
  private val msg2 = FiberMessage.Stateful(_ => ())
  private val msg3 = FiberMessage.Stateful(_ => ())

  def spec = suite("FiberMailboxSpec")(
    suite("isEmpty")(
      test("new mailbox is empty") {
        val m = new FiberMailbox {}
        assertTrue(m.isEmpty)
      },
      test("not empty after add") {
        val m = new FiberMailbox {}
        m.add(msg)
        assertTrue(!m.isEmpty)
      },
      test("empty again after add then poll") {
        val m = new FiberMailbox {}
        m.add(msg)
        m.poll()
        assertTrue(m.isEmpty)
      }
    ),
    suite("poll")(
      test("poll on empty returns null") {
        val m = new FiberMailbox {}
        assertTrue(m.poll() eq null)
      },
      test("poll returns the added message") {
        val m = new FiberMailbox {}
        m.add(msg)
        assertTrue(m.poll() eq msg)
      },
      test("poll after drain returns null") {
        val m = new FiberMailbox {}
        m.add(msg)
        m.poll()
        assertTrue(m.poll() eq null)
      }
    ),
    suite("FIFO ordering")(
      test("two messages are returned in insertion order") {
        val m = new FiberMailbox {}
        m.add(msg)
        m.add(msg2)
        assertTrue((m.poll() eq msg) && (m.poll() eq msg2))
      },
      test("three messages are returned in insertion order") {
        val m = new FiberMailbox {}
        m.add(msg)
        m.add(msg2)
        m.add(msg3)
        assertTrue((m.poll() eq msg) && (m.poll() eq msg2) && (m.poll() eq msg3) && (m.poll() eq null))
      },
      test("interleaved add/poll preserves order") {
        val m = new FiberMailbox {}
        m.add(msg)
        val r1 = m.poll()
        m.add(msg2)
        m.add(msg3)
        val r2 = m.poll()
        val r3 = m.poll()
        assertTrue((r1 eq msg) && (r2 eq msg2) && (r3 eq msg3))
      }
    ),
    suite("state transitions")(
      test("mailbox is empty after all messages drained from CLQ path") {
        val m = new FiberMailbox {}
        m.add(msg)
        m.add(msg2)
        m.poll()
        m.poll()
        assertTrue(m.isEmpty)
      },
      test("can add and poll after CLQ promotion") {
        val m = new FiberMailbox {}
        m.add(msg)
        m.add(msg2)
        m.poll()
        m.poll()
        m.add(msg3)
        assertTrue(m.poll() eq msg3)
      }
    ),
    suite("post-drain behaviour")(
      test("mailbox is usable after CLQ is fully drained") {
        val m = new FiberMailbox {}
        m.add(msg)
        m.add(msg2) // triggers CLQ promotion
        m.poll()    // drains msg
        m.poll()    // drains msg2
        m.add(msg3)
        assertTrue(m.poll() eq msg3)
      },
      test("isEmpty is true after CLQ is fully drained") {
        val m = new FiberMailbox {}
        m.add(msg)
        m.add(msg2)
        m.poll()
        m.poll()
        assertTrue(m.isEmpty)
      },
      test("mailbox works correctly through multiple burst/drain cycles") {
        val m = new FiberMailbox {}
        // cycle 1
        m.add(msg); m.add(msg2); m.poll(); m.poll()
        // cycle 2
        m.add(msg3); m.add(msg); m.poll(); m.poll()
        // single after second drain
        m.add(msg2)
        assertTrue(m.poll() eq msg2) && assertTrue(m.isEmpty)
      },
      test("CLQ is reused across burst cycles") {
        val m = new FiberMailbox {}
        // five burst/drain cycles — CLQ allocated once on first burst, reused after
        var correct = true
        var i       = 0
        while (i < 5) {
          m.add(msg); m.add(msg2)
          correct = correct && (m.poll() eq msg) && (m.poll() eq msg2) && m.isEmpty
          i += 1
        }
        assertTrue(correct)
      }
    )
  )
}
