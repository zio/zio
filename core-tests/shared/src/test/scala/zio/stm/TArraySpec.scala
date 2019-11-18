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
import zio.{ ZIO, ZIOBaseSpec }

object TArraySpec extends ZIOBaseSpec {

  def spec = suite("TArraySpec")(
    suite("apply")(
      testM("happy-path") {
        val res = for {
          tArray <- makeTArray(1)(42)
          value  <- tArray(0)
        } yield value
        assertM(res.commit, equalTo(42))
      },
      testM("dies with ArrayIndexOutOfBounds when index is out of bounds") {
        for {
          tArray <- makeTArray(1)(42).commit
          result <- ZIO.effect(tArray(-1)).run
        } yield assert(result, fails(isArrayIndexOutOfBoundsException))
      }
    ),
    suite("fold")(
      testM("is atomic") {
        for {
          tArray    <- makeTArray(N)(0).commit
          sum1Fiber <- tArray.fold(0)(_ + _).commit.fork
          _         <- STM.foreach(0 until N)(i => tArray.array(i).update(_ + 1)).commit
          sum1      <- sum1Fiber.join
        } yield assert(sum1, equalTo(0) || equalTo(N))
      }
    ),
    suite("foldM")(
      testM("is atomic") {
        for {
          tArray    <- makeTArray(N)(0).commit
          sum1Fiber <- tArray.foldM(0)((z, a) => STM.succeed(z + a)).commit.fork
          _         <- STM.foreach(0 until N)(i => tArray.array(i).update(_ + 1)).commit
          sum1      <- sum1Fiber.join
        } yield assert(sum1, equalTo(0) || equalTo(N))
      },
      testM("returns effect failure") {
        def failInTheMiddle(acc: Int, a: Int): STM[Exception, Int] =
          if (acc == N / 2) STM.fail(boom) else STM.succeed(acc + a)

        for {
          tArray <- makeTArray(N)(1).commit
          res    <- tArray.foldM(0)(failInTheMiddle).commit.either
        } yield assert(res, isLeft(equalTo(boom)))
      }
    ),
    suite("foreach")(
      testM("side-effect is transactional") {
        for {
          ref    <- TRef.make(0).commit
          tArray <- makeTArray(n)(1).commit
          _      <- tArray.foreach(a => ref.update(_ + a).unit).commit.fork
          value  <- ref.get.commit
        } yield assert(value, equalTo(0) || equalTo(n))
      }
    ),
    suite("transform")(
      testM("updates values atomically") {
        for {
          tArray         <- makeTArray(N)("a").commit
          transformFiber <- tArray.transform(_ + "+b").commit.fork
          _              <- STM.foreach(0 until N)(idx => tArray.array(idx).update(_ + "+c")).commit
          _              <- transformFiber.join
          first          <- tArray.array(0).get.commit
          last           <- tArray.array(N - 1).get.commit
        } yield assert((first, last), equalTo(("a+b+c", "a+b+c")) || equalTo(("a+c+b", "a+c+b")))
      }
    ),
    suite("transformM")(
      testM("updates values atomically") {
        for {
          tArray         <- makeTArray(N)("a").commit
          transformFiber <- tArray.transformM(a => STM.succeed(a + "+b")).commit.fork
          _              <- STM.foreach(0 until N)(idx => tArray.array(idx).update(_ + "+c")).commit
          _              <- transformFiber.join
          first          <- tArray.array(0).get.commit
          last           <- tArray.array(N - 1).get.commit
        } yield assert((first, last), equalTo(("a+b+c", "a+b+c")) || equalTo(("a+c+b", "a+c+b")))
      },
      testM("updates all or nothing") {
        for {
          tArray <- makeTArray(N)(0).commit
          _      <- tArray.array(N / 2).update(_ => 1).commit
          result <- tArray.transformM(a => if (a == 0) STM.succeed(42) else STM.fail(boom)).commit.either
          first  <- tArray.array(0).get.commit
        } yield assert(result.left.map(r => (first, r)), isLeft(equalTo((0, boom))))
      }
    ),
    suite("update")(
      testM("happy-path") {
        for {
          tArray <- makeTArray(1)(42).commit
          items  <- (tArray.update(0, a => -a) *> valuesOf(tArray)).commit
        } yield assert(items, equalTo(List(-42)))
      },
      testM("dies with ArrayIndexOutOfBounds when index is out of bounds") {
        for {
          tArray <- makeTArray(1)(42).commit
          result <- ZIO.effect(tArray.update(-1, identity)).run
        } yield assert(result, fails(isArrayIndexOutOfBoundsException))
      }
    ),
    suite("updateM")(
      testM("happy-path") {
        for {
          tArray <- makeTArray(1)(42).commit
          items  <- (tArray.updateM(0, a => STM.succeed(-a)) *> valuesOf(tArray)).commit
        } yield assert(items, equalTo(List(-42)))
      },
      testM("dies with ArrayIndexOutOfBounds when index is out of bounds") {
        for {
          tArray <- makeTArray(10)(0).commit
          result <- ZIO.effect(tArray.updateM(10, STM.succeed)).run
        } yield assert(result, fails(isArrayIndexOutOfBoundsException))
      },
      testM("updateM failure") {
        for {
          tArray <- makeTArray(n)(0).commit
          result <- tArray.updateM(0, _ => STM.fail(boom)).commit.either
        } yield assert(result, isLeft(equalTo(boom)))
      }
    )
  )

  val N    = 1000
  val n    = 10
  val boom = new Exception("Boom!")

  val isArrayIndexOutOfBoundsException: Assertion[Throwable] =
    Assertion.assertion[Throwable]("isArrayIndexOutOfBoundsException")()(_.isInstanceOf[ArrayIndexOutOfBoundsException])

  def makeTArray[T](n: Int)(a: T): STM[Nothing, TArray[T]] =
    TArray.fromIterable(List.fill(n)(a))

  def valuesOf[T](array: TArray[T]): STM[Nothing, List[T]] =
    array.fold(List.empty[T])((acc, a) => a :: acc).map(_.reverse)
}
