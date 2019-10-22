/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

import zio.test.Assertion._
import zio.test._
import zio.ZIOBaseSpec

object TMapSpec
    extends ZIOBaseSpec(
      suite("TMap")(
        suite("lookups")(
          testM("get existing element") {
            val tx = TMap(List("a" -> 1, "b" -> 2)).flatMap(_.get("a"))
            assertM(tx.commit, isSome(equalTo(1)))
          },
          testM("get non-existing element") {
            val tx = TMap.empty[String, Int].flatMap(_.get("a"))
            assertM(tx.commit, isNone)
          },
          testM("getOrElse existing element") {
            val tx = TMap(List("a" -> 1, "b" -> 2)).flatMap(_.getOrElse("a", 10))
            assertM(tx.commit, equalTo(1))
          },
          testM("getOrElse non-existing element") {
            val tx = TMap.empty[String, Int].flatMap(_.getOrElse("a", 10))
            assertM(tx.commit, equalTo(10))
          },
          testM("contains existing element") {
            val tx = TMap(List("a" -> 1, "b" -> 2)).flatMap(_.contains("a"))
            assertM(tx.commit, isTrue)
          },
          testM("contains non-existing element") {
            val tx = TMap.empty[String, Int].flatMap(_.contains("a"))
            assertM(tx.commit, isFalse)
          }
        ),
        suite("insertion and removal")(
          testM("add new element") {
            val tx =
              for {
                tmap <- TMap.empty[String, Int]
                _    <- tmap.put("a", 1)
                e    <- tmap.get("a")
              } yield e

            assertM(tx.commit, isSome(equalTo(1)))
          },
          testM("overwrite existing element") {
            val tx =
              for {
                tmap <- TMap(List("a" -> 1, "b" -> 2))
                _    <- tmap.put("a", 10)
                e    <- tmap.get("a")
              } yield e

            assertM(tx.commit, isSome(equalTo(10)))
          },
          testM("remove existing element") {
            val tx =
              for {
                tmap <- TMap(List("a" -> 1, "b" -> 2))
                _    <- tmap.delete("a")
                e    <- tmap.get("a")
              } yield e

            assertM(tx.commit, isNone)
          },
          testM("remove non-existing element") {
            val tx =
              for {
                tmap <- TMap.empty[String, Int]
                _    <- tmap.delete("a")
                e    <- tmap.get("a")
              } yield e

            assertM(tx.commit, isNone)
          }
        ),
        suite("transformations")(
          testM("retainIf") {
            val tx =
              for {
                tmap <- TMap(List("a" -> 1, "aa" -> 2, "aaa" -> 3))
                _    <- tmap.retainIf(_._1 == "aa")
                a    <- tmap.contains("a")
                aa   <- tmap.contains("aa")
                aaa  <- tmap.contains("aaa")
              } yield (a, aa, aaa)

            assertM(tx.commit, equalTo((false, true, false)))
          },
          testM("removeIf") {
            val tx =
              for {
                tmap <- TMap(List("a" -> 1, "aa" -> 2, "aaa" -> 3))
                _    <- tmap.removeIf(_._1 == "aa")
                a    <- tmap.contains("a")
                aa   <- tmap.contains("aa")
                aaa  <- tmap.contains("aaa")
              } yield (a, aa, aaa)

            assertM(tx.commit, equalTo((true, false, true)))
          },
          testM("map") {
            def valuesOf(tmap: TMap[String, Int]): STM[Nothing, List[Int]] =
              tmap.fold(List.empty[Int])((acc, kv) => kv._2 :: acc).map(_.reverse)

            val tx =
              for {
                tmap1 <- TMap(List("a" -> 1, "aa" -> 2, "aaa" -> 3))
                tmap2 <- tmap1.map(kv => (kv._1, kv._2 * 2))
                res1  <- valuesOf(tmap1)
                res2  <- valuesOf(tmap2)
              } yield (res1, res2)

            assertM(tx.commit, equalTo((List(1, 2, 3), List(2, 4, 6))))
          }
        ),
        suite("folds")(
          testM("fold on non-empty map") {
            val tx =
              for {
                tmap <- TMap(List("a" -> 1, "b" -> 2, "c" -> 3))
                res  <- tmap.fold(0)((acc, kv) => acc + kv._2)
              } yield res

            assertM(tx.commit, equalTo(6))
          },
          testM("fold on empty map") {
            val tx =
              for {
                tmap <- TMap.empty[String, Int]
                res  <- tmap.fold(0)((acc, kv) => acc + kv._2)
              } yield res

            assertM(tx.commit, equalTo(0))
          }
        )
      )
    )
