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

object TSetSpec extends ZIOBaseSpec {

  def spec = suite("TSet")(
    suite("factories")(
      testM("apply") {
        val tx = TSet.make(1, 2, 2, 3).flatMap[Any, Nothing, List[Int]](_.toList)
        assertM(tx.commit)(hasSameElements(List(1, 2, 3)))

      },
      testM("empty") {
        val tx = TSet.empty[Int].flatMap[Any, Nothing, List[Int]](_.toList)
        assertM(tx.commit)(isEmpty)
      },
      testM("fromIterable") {
        val tx = TSet.fromIterable(List(1, 2, 2, 3)).flatMap[Any, Nothing, List[Int]](_.toList)
        assertM(tx.commit)(hasSameElements(List(1, 2, 3)))
      }
    ),
    suite("lookups")(
      testM("contains existing element") {
        val tx = TSet.make(1, 2, 3, 4).flatMap[Any, Nothing, Boolean](_.contains(1))
        assertM(tx.commit)(isTrue)
      },
      testM("contains non-existing element") {
        val tx = TSet.make(1, 2, 3, 4).flatMap[Any, Nothing, Boolean](_.contains(0))
        assertM(tx.commit)(isFalse)
      },
      testM("collect all elements") {
        val tx = TSet.make(1, 2, 3, 4).flatMap[Any, Nothing, List[Int]](_.toList)
        assertM(tx.commit)(hasSameElements(List(1, 2, 3, 4)))
      },
      testM("cardinality") {
        val tx = TSet.make(1, 2, 3, 4).flatMap[Any, Nothing, Int](_.size)
        assertM(tx.commit)(equalTo(4))
      }
    ),
    suite("insertion and removal")(
      testM("add new element") {
        val tx =
          for {
            tset <- TSet.empty[Int]
            _    <- tset.put(1)
            res  <- tset.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(1)))
      },
      testM("add duplicate element") {
        val tx =
          for {
            tset <- TSet.make(1)
            _    <- tset.put(1)
            res  <- tset.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(1)))
      },
      testM("remove existing element") {
        val tx =
          for {
            tset <- TSet.make(1, 2)
            _    <- tset.delete(1)
            res  <- tset.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(2)))
      },
      testM("remove non-existing element") {
        val tx =
          for {
            tset <- TSet.make(1, 2)
            _    <- tset.delete(3)
            res  <- tset.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(1, 2)))
      }
    ),
    suite("transformations")(
      testM("retainIf") {
        val tx =
          for {
            tset <- TSet.make("a", "aa", "aaa")
            _    <- tset.retainIf(_ == "aa")
            a    <- tset.contains("a")
            aa   <- tset.contains("aa")
            aaa  <- tset.contains("aaa")
          } yield (a, aa, aaa)

        assertM(tx.commit)(equalTo((false, true, false)))
      },
      testM("removeIf") {
        val tx =
          for {
            tset <- TSet.make("a", "aa", "aaa")
            _    <- tset.removeIf(_ == "aa")
            a    <- tset.contains("a")
            aa   <- tset.contains("aa")
            aaa  <- tset.contains("aaa")
          } yield (a, aa, aaa)

        assertM(tx.commit)(equalTo((true, false, true)))
      },
      testM("transform") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            _    <- tset.transform(_ * 2)
            res  <- tset.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(2, 4, 6)))
      },
      testM("transform and shrink") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            _    <- tset.transform(_ => 1)
            res  <- tset.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(1)))
      },
      testM("transformM") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            _    <- tset.transformM(a => STM.succeed(a * 2))
            res  <- tset.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(2, 4, 6)))
      },
      testM("transformM and shrink") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            _    <- tset.transformM(_ => STM.succeed(1))
            res  <- tset.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(1)))
      }
    ),
    suite("folds")(
      testM("fold on non-empty set") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            res  <- tset.fold(0)(_ + _)
          } yield res

        assertM(tx.commit)(equalTo(6))
      },
      testM("fold on empty set") {
        val tx =
          for {
            tset <- TSet.empty[Int]
            res  <- tset.fold(0)(_ + _)
          } yield res

        assertM(tx.commit)(equalTo(0))
      },
      testM("foldM on non-empty set") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            res  <- tset.foldM(0)((acc, a) => STM.succeed(acc + a))
          } yield res

        assertM(tx.commit)(equalTo(6))
      },
      testM("foldM on empty set") {
        val tx =
          for {
            tset <- TSet.empty[Int]
            res  <- tset.foldM(0)((acc, a) => STM.succeed(acc + a))
          } yield res

        assertM(tx.commit)(equalTo(0))
      }
    ),
    suite("set operations")(
      testM("diff") {
        val tx =
          for {
            tset1 <- TSet.make(1, 2, 3)
            tset2 <- TSet.make(1, 4, 5)
            _     <- tset1.diff(tset2)
            res   <- tset1.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(2, 3)))
      },
      testM("intersect") {
        val tx =
          for {
            tset1 <- TSet.make(1, 2, 3)
            tset2 <- TSet.make(1, 4, 5)
            _     <- tset1.intersect(tset2)
            res   <- tset1.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(1)))
      },
      testM("union") {
        val tx =
          for {
            tset1 <- TSet.make(1, 2, 3)
            tset2 <- TSet.make(1, 4, 5)
            _     <- tset1.union(tset2)
            res   <- tset1.toList
          } yield res

        assertM(tx.commit)(hasSameElements(List(1, 2, 3, 4, 5)))
      }
    )
  )
}
