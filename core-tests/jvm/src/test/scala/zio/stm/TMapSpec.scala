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
                map1 <- TMap.empty[String, Int]
                map2 <- map1.put("a", 1)
                e    <- map2.get("a")
              } yield e

            assertM(tx.commit, isSome(equalTo(1)))
          },
          testM("overwrite existing element") {
            val tx =
              for {
                map1 <- TMap(List("a" -> 1, "b" -> 2))
                map2 <- map1.put("a", 10)
                e    <- map2.get("a")
              } yield e

            assertM(tx.commit, isSome(equalTo(10)))
          },
          testM("remove existing element") {
            val tx =
              for {
                map1 <- TMap(List("a" -> 1, "b" -> 2))
                map2 <- map1.delete("a")
                e    <- map2.get("a")
              } yield e

            assertM(tx.commit, isNone)
          },
          testM("remove non-existing element") {
            val tx =
              for {
                map1 <- TMap.empty[String, Int]
                map2 <- map1.delete("a")
                e    <- map2.get("a")
              } yield e

            assertM(tx.commit, isNone)
          }
        ),
        suite("filtering")(
          testM("filter") {
            val tx =
              for {
                map1 <- TMap(List("a" -> 1, "aa" -> 2, "aaa" -> 3))
                map2 <- map1.filter(_._1 == "aa")
                a    <- map2.contains("a")
                aa   <- map2.contains("aa")
                aaa  <- map2.contains("aaa")
              } yield (a, aa, aaa)

            assertM(tx.commit, equalTo((false, true, false)))
          },
          testM("filterNot") {
            val tx =
              for {
                map1 <- TMap(List("a" -> 1, "aa" -> 2, "aaa" -> 3))
                map2 <- map1.filterNot(_._1 == "aa")
                a    <- map2.contains("a")
                aa   <- map2.contains("aa")
                aaa  <- map2.contains("aaa")
              } yield (a, aa, aaa)

            assertM(tx.commit, equalTo((true, false, true)))
          }
        )
      )
    )
