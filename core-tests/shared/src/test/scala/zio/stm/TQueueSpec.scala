/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.stm

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

object TQueueSpec extends ZIOBaseSpec {

  def spec = suite("TQueue")(
    suite("factories")(
      testM("make") {
        val capacity = 5
        val tq       = TQueue.make[Int](capacity).map(_.capacity)
        assertM(tq.commit)(equalTo(capacity))
      },
      testM("unbounded") {
        val tq = TQueue.unbounded[Int].map(_.capacity)
        assertM(tq.commit)(equalTo(Int.MaxValue))
      }
    ),
    suite("insertion and removal")(
      testM("offer & take") {
        val t = for {
          tq    <- TQueue.make[Int](5)
          _     <- tq.offer(1)
          _     <- tq.offer(2)
          _     <- tq.offer(3)
          one   <- tq.take
          two   <- tq.take
          three <- tq.take
        } yield List(one, two, three)
        assertM(t.commit)(hasSameElements(List(1, 2, 3)))
      },
      testM("takeUpTo") {
        val t = for {
          tq   <- TQueue.make[Int](5)
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans  <- tq.takeUpTo(3)
          size <- tq.size
        } yield (ans, size)
        for {
          z <- t.commit
        } yield assert(z._2)(equalTo(2)) &&
          assert(z._1)(hasSameElements(List(1, 2, 3)))
      },
      testM("offerAll & takeAll") {
        val t = for {
          tq  <- TQueue.make[Int](5)
          _   <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans <- tq.takeAll
        } yield ans
        assertM(t.commit)(hasSameElements(List(1, 2, 3, 4, 5)))
      },
      testM("takeUpTo") {
        val t = for {
          tq   <- TQueue.make[Int](5)
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans  <- tq.takeUpTo(3)
          size <- tq.size
        } yield (ans, size)
        for {
          z <- t.commit
        } yield assert(z._2)(equalTo(2)) &&
          assert(z._1)(hasSameElements(List(1, 2, 3)))
      },
      testM("takeUpTo larger than container") {
        val t = for {
          tq   <- TQueue.make[Int](5)
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans  <- tq.takeUpTo(7)
          size <- tq.size
        } yield (ans, size)
        for {
          z <- t.commit
        } yield assert(z._2)(equalTo(0)) &&
          assert(z._1)(hasSameElements(List(1, 2, 3, 4, 5)))
      },
      testM("poll value") {
        val t = for {
          tq  <- TQueue.make[Int](5)
          _   <- tq.offerAll(List(1, 2, 3))
          ans <- tq.poll
        } yield ans
        assertM(t.commit)(isSome(equalTo(1)))
      },
      testM("poll empty queue") {
        val t = for {
          tq  <- TQueue.make[Int](5)
          ans <- tq.poll
        } yield ans
        assertM(t.commit)(isNone)
      },
      testM("seek element") {
        val t = for {
          tq   <- TQueue.make[Int](5)
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans  <- tq.seek(_ == 3)
          size <- tq.size
        } yield (ans, size)
        for {
          z <- t.commit
        } yield assert(z._1)(equalTo(3)) &&
          assert(z._2)(equalTo(2))
      }
    ),
    suite("lookup")(
      testM("size") {
        val t = for {
          tq   <- TQueue.unbounded[Int]
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          size <- tq.size
        } yield size
        assertM(t.commit)(equalTo(5))
      },
      testM("peek the next value") {
        val t = for {
          tq   <- TQueue.unbounded[Int]
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          next <- tq.peek
          size <- tq.size
        } yield (next, size)
        for {
          z <- t.commit
        } yield assert(z._1)(equalTo(1)) &&
          assert(z._2)(equalTo(5))
      },
      testM("view the last value") {
        val t = for {
          tq   <- TQueue.unbounded[Int]
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          last <- tq.last
          size <- tq.size
        } yield (last, size)
        for {
          z <- t.commit
        } yield assert(z._1)(equalTo(5)) &&
          assert(z._2)(equalTo(5))
      },
      testM("check isEmpty") {
        val t = for {
          tq1 <- TQueue.unbounded[Int]
          tq2 <- TQueue.unbounded[Int]
          _   <- tq1.offerAll(List(1, 2, 3, 4, 5))
          qb1 <- tq1.isEmpty
          qb2 <- tq2.isEmpty
        } yield (qb1, qb2)
        for {
          z <- t.commit
        } yield assert(z._1)(equalTo(false)) &&
          assert(z._2)(equalTo(true))
      },
      testM("check isFull") {
        val t = for {
          tq1 <- TQueue.make[Int](5)
          tq2 <- TQueue.make[Int](5)
          _   <- tq1.offerAll(List(1, 2, 3, 4, 5))
          qb1 <- tq1.isFull
          qb2 <- tq2.isFull
        } yield (qb1, qb2)
        for {
          z <- t.commit
        } yield assert(z._1)(equalTo(true)) &&
          assert(z._2)(equalTo(false))
      },
      testM("get the longest queue") {
        val t = for {
          tq1 <- TQueue.make[Int](5)
          tq2 <- TQueue.make[Int](5)
          _   <- tq1.offerAll(List(1, 2, 3, 4, 5))
          _   <- tq2.offerAll(List(1, 2, 3))
          tq  <- tq1.longest(tq2)
          l   <- tq.takeAll
        } yield l
        assertM(t.commit)(hasSameElements(List(1, 2, 3, 4, 5)))
      }
    )
  )
}
