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

import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, ZIOBaseSpec }

object TArraySpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("TArraySpec")(
    suite("apply")(
      testM("happy-path") {
        val res = for {
          tArray <- makeTArray(1)(42)
          value  <- tArray(0)
        } yield value
        assertM(res.commit)(equalTo(42))
      },
      testM("dies with ArrayIndexOutOfBounds when index is out of bounds") {
        for {
          tArray <- makeTArray(1)(42).commit
          result <- tArray(-1).commit.run
        } yield assert(result)(dies(isArrayIndexOutOfBoundsException))
      }
    ),
    suite("collectFirst")(
      testM("finds and transforms correctly") {
        for {
          tArray <- makeStairWithHoles(n).commit
          result <- tArray.collectFirst {
                     case Some(i) if i > 2 => i.toString
                   }.commit
        } yield assert(result)(isSome(equalTo("4")))
      },
      testM("succeeds for empty") {
        for {
          tArray <- makeTArray[Option[Int]](0)(None).commit
          result <- tArray.collectFirst {
                     case any => any
                   }.commit
        } yield assert(result)(isNone)
      },
      testM("fails to find absent") {
        for {
          tArray <- makeStairWithHoles(n).commit
          result <- tArray.collectFirst {
                     case Some(i) if i > n => i.toString
                   }.commit
        } yield assert(result)(isNone)
      } @@ zioTag(errors),
      testM("is atomic") {
        for {
          tArray <- makeStairWithHoles(N).commit
          findFiber <- tArray.collectFirst {
                        case Some(i) if (i % largePrime) == 0 => i.toString
                      }.commit.fork
          _      <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => Some(1))).commit
          result <- findFiber.join
        } yield assert(result)(isSome(equalTo(largePrime.toString)) || isNone)
      }
    ),
    suite("collectFirstM")(
      testM("finds and transforms correctly") {
        for {
          tArray <- makeStairWithHoles(n).commit
          result <- tArray.collectFirstM {
                     case Some(i) if i > 2 => STM.succeed(i.toString)
                   }.commit
        } yield assert(result)(isSome(equalTo("4")))
      },
      testM("succeeds for empty") {
        for {
          tArray <- makeTArray[Option[Int]](0)(None).commit
          result <- tArray.collectFirstM {
                     case any => STM.succeed(any)
                   }.commit
        } yield assert(result)(isNone)
      },
      testM("fails to find absent") {
        for {
          tArray <- makeStairWithHoles(n).commit
          result <- tArray.collectFirstM {
                     case Some(i) if i > n => STM.succeed(i.toString)
                   }.commit
        } yield assert(result)(isNone)
      } @@ zioTag(errors),
      testM("is atomic") {
        for {
          tArray <- makeStairWithHoles(N).commit
          findFiber <- tArray.collectFirstM {
                        case Some(i) if (i % largePrime) == 0 => STM.succeed(i.toString)
                      }.commit.fork
          _      <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => Some(1))).commit
          result <- findFiber.join
        } yield assert(result)(isSome(equalTo(largePrime.toString)) || isNone)
      },
      testM("fails on errors before result found") {
        for {
          tArray <- makeStairWithHoles(n).commit
          result <- tArray.collectFirstM {
                     case Some(i) if i > 2 => STM.succeed(i.toString)
                     case _                => STM.fail(boom)
                   }.commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors),
      testM("succeeds on errors after result found") {
        for {
          tArray <- makeStairWithHoles(n).commit
          result <- tArray.collectFirstM {
                     case Some(i) if i > 2 => STM.succeed(i.toString)
                     case Some(7)          => STM.fail(boom)
                   }.commit
        } yield assert(result)(isSome(equalTo("4")))
      } @@ zioTag(errors)
    ),
    suite("contains")(
      testM("true when in the array") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.contains(3).commit
        } yield assert(result)(isTrue)
      },
      testM("false when not in the array") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.contains(n + 1).commit
        } yield assert(result)(isFalse)
      },
      testM("false for empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.contains(0).commit
        } yield assert(result)(isFalse)
      }
    ),
    suite("count")(
      testM("computes correct sum") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.count(_ % 2 == 0).commit
        } yield assert(result)(equalTo(5))
      },
      testM("zero for absent") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.count(_ > n).commit
        } yield assert(result)(equalTo(0))
      },
      testM("zero for empty") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.count(_ => true).commit
        } yield assert(result)(equalTo(0))
      }
    ),
    suite("countM")(
      testM("computes correct sum") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.countM(i => STM.succeed(i % 2 == 0)).commit
        } yield assert(result)(equalTo(5))
      },
      testM("zero for absent") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.countM(i => STM.succeed(i > n)).commit
        } yield assert(result)(equalTo(0))
      },
      testM("zero for empty") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.countM(_ => STM.succeed(true)).commit
        } yield assert(result)(equalTo(0))
      }
    ),
    suite("exists")(
      testM("detects satisfaction") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.exists(_ % 2 == 0).commit
        } yield assert(result)(isTrue)
      },
      testM("detects lack of satisfaction") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.exists(_ % 11 == 0).commit
        } yield assert(result)(isFalse)
      },
      testM("false for empty") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.exists(_ => true).commit
        } yield assert(result)(isFalse)
      }
    ),
    suite("existsM")(
      testM("detects satisfaction") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.existsM(i => STM.succeed(i % 2 == 0)).commit
        } yield assert(result)(isTrue)
      },
      testM("detects lack of satisfaction") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.existsM(i => STM.succeed(i % 11 == 0)).commit
        } yield assert(result)(isFalse)
      },
      testM("false for empty") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.existsM(_ => STM.succeed(true)).commit
        } yield assert(result)(isFalse)
      },
      testM("fails for errors before witness") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.existsM(i => if (i == 4) STM.fail(boom) else STM.succeed(i == 5)).commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors),
      testM("fails for errors after witness") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.existsM(i => if (i == 6) STM.fail(boom) else STM.succeed(i == 5)).commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors)
    ),
    suite("find")(
      testM("finds correctly") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.find(_ % 5 == 0).commit
        } yield assert(result)(isSome(equalTo(5)))
      },
      testM("succeeds for empty") {
        for {
          tArray <- makeTArray(0)(0).commit
          result <- tArray.find(_ => true).commit
        } yield assert(result)(isNone)
      },
      testM("fails to find absent") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.find(_ > n).commit
        } yield assert(result)(isNone)
      } @@ zioTag(errors),
      testM("is atomic") {
        for {
          tArray    <- makeStair(N).commit
          findFiber <- tArray.find(_ % largePrime == 0).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => 1)).commit
          result    <- findFiber.join
        } yield assert(result)(isSome(equalTo(largePrime)) || isNone)
      }
    ),
    suite("findLast")(
      testM("finds correctly") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findLast(_ % 5 == 0).commit
        } yield assert(result)(isSome(equalTo(10)))
      },
      testM("succeeds for empty") {
        for {
          tArray <- makeTArray(0)(0).commit
          result <- tArray.findLast(_ => true).commit
        } yield assert(result)(isNone)
      },
      testM("fails to find absent") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findLast(_ > n).commit
        } yield assert(result)(isNone)
      } @@ zioTag(errors),
      testM("is atomic") {
        for {
          tArray    <- makeStair(N).commit
          findFiber <- tArray.findLast(_ % largePrime == 0).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => 1)).commit
          result    <- findFiber.join
        } yield assert(result)(isSome(equalTo(largePrime * 4)) || isNone)
      }
    ),
    suite("findLastM")(
      testM("finds correctly") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findLastM(i => STM.succeed(i % 5 == 0)).commit
        } yield assert(result)(isSome(equalTo(10)))
      },
      testM("succeeds for empty") {
        for {
          tArray <- makeTArray(0)(0).commit
          result <- tArray.findLastM(_ => STM.succeed(true)).commit
        } yield assert(result)(isNone)
      },
      testM("fails to find absent") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findLastM(i => STM.succeed(i > n)).commit
        } yield assert(result)(isNone)
      } @@ zioTag(errors),
      testM("is atomic") {
        for {
          tArray    <- makeStair(N).commit
          findFiber <- tArray.findLastM(i => STM.succeed(i % largePrime == 0)).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => 1)).commit
          result    <- findFiber.join
        } yield assert(result)(isSome(equalTo(largePrime * 4)) || isNone)
      },
      testM("succeeds on errors before result found") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findLastM(i => if (i == 4) STM.fail(boom) else STM.succeed(i % 7 == 0)).commit
        } yield assert(result)(isSome(equalTo(7)))
      },
      testM("fails on errors after result found") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findLastM(i => if (i == 8) STM.fail(boom) else STM.succeed(i % 7 == 0)).commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors)
    ),
    suite("findM")(
      testM("finds correctly") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findM(i => STM.succeed(i % 5 == 0)).commit
        } yield assert(result)(isSome(equalTo(5)))
      },
      testM("succeeds for empty") {
        for {
          tArray <- makeTArray(0)(0).commit
          result <- tArray.findM(_ => STM.succeed(true)).commit
        } yield assert(result)(isNone)
      },
      testM("fails to find absent") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findM(i => STM.succeed(i > n)).commit
        } yield assert(result)(isNone)
      } @@ zioTag(errors),
      testM("is atomic") {
        for {
          tArray    <- makeStair(N).commit
          findFiber <- tArray.findM(i => STM.succeed(i % largePrime == 0)).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => 1)).commit
          result    <- findFiber.join
        } yield assert(result)(isSome(equalTo(largePrime)) || isNone)
      },
      testM("fails on errors before result found") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findM(i => if (i == 4) STM.fail(boom) else STM.succeed(i % 5 == 0)).commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors),
      testM("succeeds on errors after result found") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.findM(i => if (i == 6) STM.fail(boom) else STM.succeed(i % 5 == 0)).commit
        } yield assert(result)(isSome(equalTo(5)))
      }
    ),
    suite("firstOption")(
      testM("retrieves the first item") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.firstOption.commit
        } yield assert(result)(isSome(equalTo(1)))
      },
      testM("is none for an empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.firstOption.commit
        } yield assert(result)(isNone)
      }
    ),
    suite("fold")(
      testM("is atomic") {
        for {
          tArray    <- makeTArray(N)(0).commit
          sum1Fiber <- tArray.fold(0)(_ + _).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ + 1)).commit
          sum1      <- sum1Fiber.join
        } yield assert(sum1)(equalTo(0) || equalTo(N))
      }
    ),
    suite("foldM")(
      testM("is atomic") {
        for {
          tArray    <- makeTArray(N)(0).commit
          sum1Fiber <- tArray.foldM(0)((z, a) => STM.succeed(z + a)).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ + 1)).commit
          sum1      <- sum1Fiber.join
        } yield assert(sum1)(equalTo(0) || equalTo(N))
      },
      testM("returns effect failure") {
        def failInTheMiddle(acc: Int, a: Int): STM[Exception, Int] =
          if (acc == N / 2) STM.fail(boom) else STM.succeed(acc + a)

        for {
          tArray <- makeTArray(N)(1).commit
          res    <- tArray.foldM(0)(failInTheMiddle).commit.either
        } yield assert(res)(isLeft(equalTo(boom)))
      } @@ zioTag(errors)
    ),
    suite("forall")(
      testM("detects satisfaction") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.forall(_ < n + 1).commit
        } yield assert(result)(isTrue)
      },
      testM("detects lack of satisfaction") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.forall(_ < n - 1).commit
        } yield assert(result)(isFalse)
      },
      testM("true for empty") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.forall(_ => false).commit
        } yield assert(result)(isTrue)
      }
    ),
    suite("forallM")(
      testM("detects satisfaction") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.forallM(i => STM.succeed(i < n + 1)).commit
        } yield assert(result)(isTrue)
      },
      testM("detects lack of satisfaction") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.forallM(i => STM.succeed(i < n - 1)).commit
        } yield assert(result)(isFalse)
      },
      testM("true for empty") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.forallM(_ => STM.succeed(false)).commit
        } yield assert(result)(isTrue)
      },
      testM("fails for errors before counterexample") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.forallM(i => if (i == 4) STM.fail(boom) else STM.succeed(i != 5)).commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors),
      testM("fails for errors after counterexample") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.forallM(i => if (i == 6) STM.fail(boom) else STM.succeed(i == 5)).commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors)
    ),
    suite("foreach")(
      testM("side-effect is transactional") {
        for {
          ref    <- TRef.make(0).commit
          tArray <- makeTArray(n)(1).commit
          _      <- tArray.foreach(a => ref.update(_ + a).unit).commit.fork
          value  <- ref.get.commit
        } yield assert(value)(equalTo(0) || equalTo(n))
      }
    ),
    suite("indexOf")(
      testM("correct index if in array") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.indexOf(2).commit
        } yield assert(result)(equalTo(1))
      },
      testM("-1 for empty") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.indexOf(1).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for absent") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.indexOf(4).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("correct index if in array, with offset") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.indexOf(2, 2).commit
        } yield assert(result)(equalTo(4))
      },
      testM("-1 if absent after offset") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.indexOf(1, 7).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for negative offset") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.indexOf(2, -1).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for too high offset") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.indexOf(2, 9).commit
        } yield assert(result)(equalTo(-1))
      }
    ),
    suite("indexWhere")(
      testM("determines the correct index") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhere(_ % 5 == 0).commit
        } yield assert(result)(equalTo(4))
      },
      testM("-1 for empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.indexWhere(_ => true).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for absent") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhere(_ > n).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("is atomic") {
        for {
          tArray    <- makeStair(N).commit
          findFiber <- tArray.indexWhere(_ % largePrime == 0).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => 1)).commit
          result    <- findFiber.join
        } yield assert(result)(equalTo(largePrime - 1) || equalTo(-1))
      },
      testM("correct index if in array, with offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhere(_ % 2 == 0, 5).commit
        } yield assert(result)(equalTo(5))
      },
      testM("-1 if absent after offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhere(_ % 7 == 0, 7).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for negative offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhere(_ => true, -1).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for too high offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhere(_ => true, n + 1).commit
        } yield assert(result)(equalTo(-1))
      }
    ),
    suite("indexWhereM")(
      testM("determines the correct index") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(i => STM.succeed(i % 5 == 0)).commit
        } yield assert(result)(equalTo(4))
      },
      testM("-1 for empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.indexWhereM(_ => STM.succeed(true)).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for absent") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(i => STM.succeed(i > n)).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("is atomic") {
        for {
          tArray    <- makeStair(N).commit
          findFiber <- tArray.indexWhereM(i => STM.succeed(i % largePrime == 0)).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => 1)).commit
          result    <- findFiber.join
        } yield assert(result)(equalTo(largePrime - 1) || equalTo(-1))
      },
      testM("correct index if in array, with offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(i => STM.succeed(i % 2 == 0), 5).commit
        } yield assert(result)(equalTo(5))
      },
      testM("-1 if absent after offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(i => STM.succeed(i % 7 == 0), 7).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for negative offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(_ => STM.succeed(true), -1).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for too high offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(_ => STM.succeed(true), n + 1).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("fails on errors before result found") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(i => if (i == 4) STM.fail(boom) else STM.succeed(i % 5 == 0)).commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors),
      testM("succeeds on errors after result found") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(i => if (i == 6) STM.fail(boom) else STM.succeed(i % 5 == 0)).commit
        } yield assert(result)(equalTo(4))
      },
      testM("succeeds when error excluded by offset") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.indexWhereM(i => if (i == 1) STM.fail(boom) else STM.succeed(i % 5 == 0), 2).commit
        } yield assert(result)(equalTo(4))
      }
    ),
    suite("lastIndexOf")(
      testM("correct index if in array") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.lastIndexOf(2).commit
        } yield assert(result)(equalTo(7))
      },
      testM("-1 for empty") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.lastIndexOf(1).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for absent") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.lastIndexOf(4).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("correct index if in array, with limit") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.lastIndexOf(2, 6).commit
        } yield assert(result)(equalTo(4))
      },
      testM("-1 if absent before limit") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.lastIndexOf(3, 1).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for negative offset") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.lastIndexOf(2, -1).commit
        } yield assert(result)(equalTo(-1))
      },
      testM("-1 for too high offset") {
        for {
          tArray <- makeRepeats(3)(3).commit
          result <- tArray.lastIndexOf(2, 9).commit
        } yield assert(result)(equalTo(-1))
      }
    ),
    suite("lastOption")(
      testM("retrieves the last entry") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.lastOption.commit
        } yield assert(result)(isSome(equalTo(n)))
      },
      testM("is none for an empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.lastOption.commit
        } yield assert(result)(isNone)
      }
    ),
    suite("transform")(
      testM("updates values atomically") {
        for {
          tArray         <- makeTArray(N)("a").commit
          transformFiber <- tArray.transform(_ + "+b").commit.fork
          _              <- STM.foreach(List.range(0, N))(idx => tArray.update(idx, _ + "+c")).commit
          _              <- transformFiber.join
          first          <- tArray(0).commit
          last           <- tArray(N - 1).commit
        } yield assert((first, last))(equalTo(("a+b+c", "a+b+c")) || equalTo(("a+c+b", "a+c+b")))
      }
    ),
    suite("transformM")(
      testM("updates values atomically") {
        for {
          tArray         <- makeTArray(N)("a").commit
          transformFiber <- tArray.transformM(a => STM.succeed(a + "+b")).commit.fork
          _              <- STM.foreach(List.range(0, N))(idx => tArray.update(idx, _ + "+c")).commit
          _              <- transformFiber.join
          first          <- tArray(0).commit
          last           <- tArray(N - 1).commit
        } yield assert((first, last))(equalTo(("a+b+c", "a+b+c")) || equalTo(("a+c+b", "a+c+b")))
      },
      testM("updates all or nothing") {
        for {
          tArray <- makeTArray(N)(0).commit
          _      <- tArray.update(N / 2, _ => 1).commit
          result <- tArray.transformM(a => if (a == 0) STM.succeed(42) else STM.fail(boom)).commit.either
          first  <- tArray(0).commit
        } yield assert(result.left.map(r => (first, r)))(isLeft(equalTo((0, boom))))
      }
    ),
    suite("update")(
      testM("happy-path") {
        for {
          tArray <- makeTArray(1)(42).commit
          items  <- (tArray.update(0, a => -a) *> valuesOf(tArray)).commit
        } yield assert(items)(equalTo(List(-42)))
      },
      testM("dies with ArrayIndexOutOfBounds when index is out of bounds") {
        for {
          tArray <- makeTArray(1)(42).commit
          result <- tArray.update(-1, identity).commit.run
        } yield assert(result)(dies(isArrayIndexOutOfBoundsException))
      }
    ),
    suite("updateM")(
      testM("happy-path") {
        for {
          tArray <- makeTArray(1)(42).commit
          items  <- (tArray.updateM(0, a => STM.succeed(-a)) *> valuesOf(tArray)).commit
        } yield assert(items)(equalTo(List(-42)))
      },
      testM("dies with ArrayIndexOutOfBounds when index is out of bounds") {
        for {
          tArray <- makeTArray(10)(0).commit
          result <- tArray.updateM(10, STM.succeed(_)).commit.run
        } yield assert(result)(dies(isArrayIndexOutOfBoundsException))
      },
      testM("updateM failure") {
        for {
          tArray <- makeTArray(n)(0).commit
          result <- tArray.updateM(0, _ => STM.fail(boom)).commit.either
        } yield assert(result)(isLeft(equalTo(boom)))
      } @@ zioTag(errors)
    ),
    suite("maxOption")(
      testM("computes correct maximum") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.maxOption.commit
        } yield assert(result)(isSome(equalTo(n)))
      },
      testM("returns none for an empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.maxOption.commit
        } yield assert(result)(isNone)
      }
    ),
    suite("minOption")(
      testM("computes correct minimum") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.minOption.commit
        } yield assert(result)(isSome(equalTo(1)))
      },
      testM("returns none for an empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.minOption.commit
        } yield assert(result)(isNone)
      }
    ),
    suite("reduceOption")(
      testM("reduces correctly") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.reduceOption(_ + _).commit
        } yield assert(result)(isSome(equalTo((n * (n + 1)) / 2)))
      },
      testM("returns single entry") {
        for {
          tArray <- makeTArray(1)(1).commit
          result <- tArray.reduceOption(_ + _).commit
        } yield assert(result)(isSome(equalTo(1)))
      },
      testM("returns None for an empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.reduceOption(_ + _).commit
        } yield assert(result)(isNone)
      },
      testM("is atomic") {
        for {
          tArray    <- makeStair(N).commit
          findFiber <- tArray.reduceOption(_ + _).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => 1)).commit
          result    <- findFiber.join
        } yield assert(result)(isSome(equalTo((N * (N + 1)) / 2)) || isSome(equalTo(N)))
      }
    ),
    suite("reduceOptionM")(
      testM("reduces correctly") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.reduceOptionM(sumSucceed).commit
        } yield assert(result)(isSome(equalTo((n * (n + 1)) / 2)))
      },
      testM("returns single entry") {
        for {
          tArray <- makeTArray(1)(1).commit
          result <- tArray.reduceOptionM(sumSucceed).commit
        } yield assert(result)(isSome(equalTo(1)))
      },
      testM("returns None for an empty array") {
        for {
          tArray <- TArray.empty[Int].commit
          result <- tArray.reduceOptionM(sumSucceed).commit
        } yield assert(result)(isNone)
      },
      testM("is atomic") {
        for {
          tArray    <- makeStair(N).commit
          findFiber <- tArray.reduceOptionM(sumSucceed).commit.fork
          _         <- STM.foreach(List.range(0, N))(i => tArray.update(i, _ => 1)).commit
          result    <- findFiber.join
        } yield assert(result)(isSome(equalTo((N * (N + 1)) / 2)) || isSome(equalTo(N)))
      },
      testM("fails on errors") {
        for {
          tArray <- makeStair(n).commit
          result <- tArray.reduceOptionM((a, b) => if (b == 4) STM.fail(boom) else STM.succeed(a + b)).commit.flip
        } yield assert(result)(equalTo(boom))
      } @@ zioTag(errors),
      testM("toList") {
        for {
          tArray <- TArray.make(1, 2, 3, 4).commit
          result <- tArray.toList.commit
        } yield assert(result)(equalTo(List(1, 2, 3, 4)))
      },
      testM("toChunk") {
        for {
          tArray <- TArray.make(1, 2, 3, 4).commit
          result <- tArray.toChunk.commit
        } yield assert(result)(equalTo(Chunk(1, 2, 3, 4)))
      }
    ),
    suite("size") {
      testM("returns the size of the array") {
        checkM(Gen.listOf(Gen.anyInt)) { as =>
          val size = TArray.fromIterable(as).map(_.size)
          assertM(size.commit)(equalTo(as.size))
        }
      }
    }
  )

  val N    = 1000
  val n    = 10
  val boom = new Exception("Boom!")

  val largePrime = 223

  val isArrayIndexOutOfBoundsException: Assertion[Throwable] =
    Assertion.assertion[Throwable]("isArrayIndexOutOfBoundsException")()(_.isInstanceOf[ArrayIndexOutOfBoundsException])

  def sumSucceed(a: Int, b: Int): STM[Nothing, Int] = STM.succeed(a + b)

  def makeTArray[T](n: Int)(a: T): STM[Nothing, TArray[T]] =
    TArray.fromIterable(List.fill(n)(a))

  def makeStair(n: Int): STM[Nothing, TArray[Int]] =
    TArray.fromIterable(1 to n)

  def makeRepeats(blocks: Int)(len: Int): STM[Nothing, TArray[Int]] =
    TArray.fromIterable((0 until (blocks * len)).map(i => (i % len) + 1))

  def makeStairWithHoles(n: Int): STM[Nothing, TArray[Option[Int]]] =
    TArray.fromIterable((1 to n).map(i => if (i % 3 == 0) None else Some(i)))

  def valuesOf[T](array: TArray[T]): STM[Nothing, List[T]] =
    array.fold(List.empty[T])((acc, a) => a :: acc).map(_.reverse)
}
