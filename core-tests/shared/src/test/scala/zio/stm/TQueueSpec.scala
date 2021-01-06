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

package zio.stm

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

object TQueueSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("TQueue")(
    suite("factories")(
      testM("bounded") {
        val capacity = 5
        val tq       = TQueue.bounded[Int](capacity).map(_.capacity)
        assertM(tq.commit)(equalTo(capacity))
      },
      testM("unbounded") {
        val tq = TQueue.unbounded[Int].map(_.capacity)
        assertM(tq.commit)(equalTo(Int.MaxValue))
      }
    ),
    suite("insertion and removal")(
      testM("offer & take") {
        val tx = for {
          tq    <- TQueue.bounded[Int](5)
          _     <- tq.offer(1)
          _     <- tq.offer(2)
          _     <- tq.offer(3)
          one   <- tq.take
          two   <- tq.take
          three <- tq.take
        } yield List(one, two, three)
        assertM(tx.commit)(equalTo(List(1, 2, 3)))
      },
      testM("takeUpTo") {
        val tx = for {
          tq   <- TQueue.bounded[Int](5)
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans  <- tq.takeUpTo(3)
          size <- tq.size
        } yield (ans, size)
        assertM(tx.commit)(equalTo((List(1, 2, 3), 2)))
      },
      testM("offerAll & takeAll") {
        val tx = for {
          tq  <- TQueue.bounded[Int](5)
          _   <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans <- tq.takeAll
        } yield ans
        assertM(tx.commit)(equalTo(List(1, 2, 3, 4, 5)))
      },
      testM("offerAll respects capacity") {
        val tx = for {
          tq        <- TQueue.bounded[Int](3)
          remaining <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans       <- tq.takeAll
        } yield (ans, remaining)
        assertM(tx.commit)(equalTo((List(1, 2, 3), List(4, 5))))
      },
      testM("takeUpTo") {
        val tx = for {
          tq   <- TQueue.bounded[Int](5)
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans  <- tq.takeUpTo(3)
          size <- tq.size
        } yield (ans, size)
        assertM(tx.commit)(equalTo((List(1, 2, 3), 2)))
      },
      testM("takeUpTo larger than container") {
        val tx = for {
          tq   <- TQueue.bounded[Int](5)
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans  <- tq.takeUpTo(7)
          size <- tq.size
        } yield (ans, size)
        assertM(tx.commit)(equalTo((List(1, 2, 3, 4, 5), 0)))
      },
      testM("poll value") {
        val tx = for {
          tq  <- TQueue.bounded[Int](5)
          _   <- tq.offerAll(List(1, 2, 3))
          ans <- tq.poll
        } yield ans
        assertM(tx.commit)(isSome(equalTo(1)))
      },
      testM("poll empty queue") {
        val tx = for {
          tq  <- TQueue.bounded[Int](5)
          ans <- tq.poll
        } yield ans
        assertM(tx.commit)(isNone)
      },
      testM("seek element") {
        val tx = for {
          tq   <- TQueue.bounded[Int](5)
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          ans  <- tq.seek(_ == 3)
          size <- tq.size
        } yield (ans, size)
        assertM(tx.commit)(equalTo((3, 2)))
      }
    ),
    suite("lookup")(
      testM("size") {
        val tx = for {
          tq   <- TQueue.unbounded[Int]
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          size <- tq.size
        } yield size
        assertM(tx.commit)(equalTo(5))
      },
      testM("peek the next value") {
        val tx = for {
          tq   <- TQueue.unbounded[Int]
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          next <- tq.peek
          size <- tq.size
        } yield (next, size)
        assertM(tx.commit)(equalTo((1, 5)))
      },
      testM("peekOption value") {
        val tx = for {
          tq   <- TQueue.unbounded[Int]
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          next <- tq.peekOption
          size <- tq.size
        } yield (next, size)
        assertM(tx.commit)(equalTo((Some(1), 5)))
      },
      testM("peekOption empty queue") {
        val tx = for {
          tq   <- TQueue.bounded[Int](5)
          next <- tq.peekOption
        } yield next
        assertM(tx.commit)(isNone)
      },
      testM("view the last value") {
        val tx = for {
          tq   <- TQueue.unbounded[Int]
          _    <- tq.offerAll(List(1, 2, 3, 4, 5))
          last <- tq.last
          size <- tq.size
        } yield (last, size)
        assertM(tx.commit)(equalTo((5, 5)))
      },
      testM("check isEmpty") {
        val tx = for {
          tq1 <- TQueue.unbounded[Int]
          tq2 <- TQueue.unbounded[Int]
          _   <- tq1.offerAll(List(1, 2, 3, 4, 5))
          qb1 <- tq1.isEmpty
          qb2 <- tq2.isEmpty
        } yield (qb1, qb2)
        assertM(tx.commit)(equalTo((false, true)))
      },
      testM("check isFull") {
        val tx = for {
          tq1 <- TQueue.bounded[Int](5)
          tq2 <- TQueue.bounded[Int](5)
          _   <- tq1.offerAll(List(1, 2, 3, 4, 5))
          qb1 <- tq1.isFull
          qb2 <- tq2.isFull
        } yield (qb1, qb2)
        assertM(tx.commit)(equalTo((true, false)))
      }
    )
  )
}
