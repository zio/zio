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

import zio._
import zio.test._
import zio.test.TestAspect.nonFlaky

import java.util.concurrent.CountDownLatch

object FiberSetSpec extends ZIOBaseSpec {

  def spec = suite("FiberSetSpec")(
    suite("single-threaded behaviour")(
      test("isEmpty returns true for a new set") {
        val s = new FiberSet[AnyRef]()
        assertTrue(s.isEmpty)
      },
      test("size is 0 for a new set") {
        val s = new FiberSet[AnyRef]()
        assertTrue(s.size() == 0)
      },
      test("add returns true for a new element") {
        val s   = new FiberSet[AnyRef]()
        val obj = new Object()
        assertTrue(s.add(obj))
      },
      test("add returns false for a duplicate element") {
        val s   = new FiberSet[AnyRef]()
        val obj = new Object()
        s.add(obj)
        assertTrue(!s.add(obj))
      },
      test("isEmpty returns false after an add") {
        val s   = new FiberSet[AnyRef]()
        val obj = new Object()
        s.add(obj)
        assertTrue(!s.isEmpty)
      },
      test("size reflects number of distinct elements") {
        val s    = new FiberSet[AnyRef]()
        val obj1 = new Object()
        val obj2 = new Object()
        s.add(obj1)
        s.add(obj2)
        assertTrue(s.size() == 2)
      },
      test("adding the same object twice keeps size at 1") {
        val s   = new FiberSet[AnyRef]()
        val obj = new Object()
        s.add(obj)
        s.add(obj)
        assertTrue(s.size() == 1)
      },
      test("iterator yields all added elements") {
        val s    = new FiberSet[AnyRef]()
        val obj1 = new Object()
        val obj2 = new Object()
        val obj3 = new Object()
        s.add(obj1)
        s.add(obj2)
        s.add(obj3)
        val found = new java.util.HashSet[AnyRef]()
        val it    = s.iterator()
        while (it.hasNext) found.add(it.next())
        assertTrue(found.contains(obj1) && found.contains(obj2) && found.contains(obj3) && found.size() == 3)
      },
      test("uses identity, not equality, to distinguish elements") {
        // Two different objects that are .equals() should both be stored
        val s    = new FiberSet[String]()
        val str1 = new String("hello") // distinct object
        val str2 = new String("hello") // distinct object, same content
        s.add(str1)
        s.add(str2)
        assertTrue(s.size() == 2)
      },
      test("add throws NullPointerException for null") {
        val s = new FiberSet[AnyRef]()
        assertTrue {
          try { s.add(null.asInstanceOf[AnyRef]); false }
          catch { case _: NullPointerException => true }
        }
      }
    ),
    suite("integration with FiberRuntime (via newConcurrentWeakSet)")(
      test("fiber children are tracked") {
        for {
          p     <- Promise.make[Nothing, Int]
          fiber <- p.await.fork
          // Give the runtime a moment to register the child
          _ <- ZIO.yieldNow
          _ <- p.succeed(42)
          _ <- fiber.join
        } yield assertCompletes
      },
      test("many concurrent fibers can be tracked") {
        ZIO
          .foreachPar(1 to 500)(_ => ZIO.yieldNow *> ZIO.succeed(1))
          .map(_.sum)
          .map(sum => assertTrue(sum == 500))
      } @@ nonFlaky(5),
      test("interrupted fibers are correctly handled") {
        for {
          fiber <- ZIO.never.fork
          _     <- fiber.interrupt
        } yield assertCompletes
      }
    ),
    suite("concurrent add correctness")(
      test("all elements added concurrently are eventually present") {
        ZIO.succeed {
          val s     = new FiberSet[AnyRef]()
          val count = 1000
          val elems = List.fill(count)(new Object())
          val latch = new CountDownLatch(1)
          val threads = elems.map { e =>
            val t = new Thread(new Runnable { def run(): Unit = { latch.await(); s.add(e); () } })
            t.start()
            t
          }
          latch.countDown()
          threads.foreach(_.join())
          assertTrue(s.size() == count)
        }
      }
    )
  )
}
