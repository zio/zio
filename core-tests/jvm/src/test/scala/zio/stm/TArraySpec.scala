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

import zio._

final class TArraySpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {
  def is = "TArraySpec".title ^ s2"""
        apply:
          happy-path $applyHappy
          dies with ArrayIndexOutOfBounds when index is out of bounds $applyOutOfBounds
        collect:
          is atomic $collectAtomic
          is safe for empty array $collectEmpty
        fold:
          is atomic $foldAtomic
        foldM:
          is atomic $foldMAtomic
          returns effect failure $foldMFailure
        foreach:
          side-effect is transactional $foreachTransactional
        map:
          creates new array atomically $mapAtomically
        mapM:
          creates new array atomically $mapMAtomically
          returns effect failure $mapMFailure
        transform:
          updates values atomically $transformAtomically
        transformM:
          updates values atomically $transformMAtomically
          updates all or nothing $transformMTransactionally
        update:
          happy-path $updateHappy
          dies with ArrayIndexOutOfBounds when index is out of bounds $updateOutOfBounds
        updateM:
          happy-path $updateMHappy
          dies with ArrayIndexOutOfBounds when index is out of bounds $updateMOutOfBounds
          updateM failure $updateMFailure
          """

  def applyHappy =
    unsafeRun(
      for {
        tArray <- makeTArray(1)(42)
        value  <- tArray(0).commit
      } yield value
    ) mustEqual 42

  def applyOutOfBounds =
    unsafeRun(
      for {
        tArray <- makeTArray(1)(42)
        _      <- tArray(-1).commit
      } yield ()
    ) must throwA[FiberFailure]

  def collectAtomic =
    unsafeRun(
      for {
        tArray <- makeTArray(N)("alpha-bravo-charlie")
        _      <- STM.foreach(tArray.array)(_.update(_.take(11))).commit.fork
        collected <- tArray.collect {
                      case a if a.length == 11 => a
                    }.commit
      } yield collected.array.size
    ) must (equalTo(0) or equalTo(N))

  def collectEmpty =
    unsafeRun(
      for {
        tArray <- makeTArray(0)("nothing")
        collected <- tArray.collect {
                      case _ => ()
                    }.commit
      } yield collected.array.isEmpty
    ) mustEqual true

  def foldAtomic =
    unsafeRun(
      for {
        tArray    <- makeTArray(N)(0)
        sum1Fiber <- tArray.fold(0)(_ + _).commit.fork
        _         <- STM.foreach(0 until N)(i => tArray.array(i).update(_ + 1)).commit
        sum1      <- sum1Fiber.join
      } yield sum1
    ) must (equalTo(0) or equalTo(N))

  def foldMAtomic =
    unsafeRun(
      for {
        tArray    <- makeTArray(N)(0)
        sum1Fiber <- tArray.foldM(0)((z, a) => STM.succeed(z + a)).commit.fork
        _         <- STM.foreach(0 until N)(i => tArray.array(i).update(_ + 1)).commit
        sum1      <- sum1Fiber.join
      } yield sum1
    ) must (equalTo(0) or equalTo(N))

  def foldMFailure = {
    def failInTheMiddle(acc: Int, a: Int): STM[Exception, Int] =
      if (acc == N / 2) STM.fail(boom) else STM.succeed(acc + a)
    unsafeRun(
      for {
        tArray <- makeTArray(N)(1)
        res    <- tArray.foldM(0)(failInTheMiddle).commit.either
      } yield res
    ) mustEqual Left(boom)
  }

  def foreachTransactional =
    unsafeRun(for {
      ref    <- TRef.make(0).commit
      tArray <- makeTArray(n)(1)
      _      <- tArray.foreach(a => ref.update(_ + a).unit).commit.fork
      value  <- ref.get.commit
    } yield value) must (equalTo(0) or equalTo(n))

  def mapAtomically =
    unsafeRun(for {
      tArray       <- makeTArray(N)("alpha-bravo-charlie")
      lengthsFiber <- tArray.map(_.length).commit.fork
      _            <- STM.foreach(0 until N)(i => tArray.array(i).set("abc")).commit
      lengths      <- lengthsFiber.join
      firstAndLast <- lengths.array(0).get.zip(lengths.array(N - 1).get).commit
    } yield firstAndLast) must (equalTo((19, 19)) or equalTo((3, 3)))

  def mapMAtomically =
    unsafeRun(for {
      tArray       <- makeTArray(N)("thisStringLengthIs20")
      lengthsFiber <- tArray.mapM(a => STM.succeed(a.length)).commit.fork
      _            <- STM.foreach(0 until N)(idx => tArray.array(idx).set("abc")).commit
      lengths      <- lengthsFiber.join
      first        <- lengths.array(0).get.commit
      last         <- lengths.array(N - 1).get.commit
    } yield (first, last)) must (equalTo((20, 20)) or equalTo((3, 3)))

  def mapMFailure =
    unsafeRun(for {
      tArray <- makeTArray(N)("abc")
      _      <- tArray.array(N / 2).update(_ => "").commit
      result <- tArray.mapM(a => if (a.isEmpty) STM.fail(boom) else STM.succeed(())).commit.either
    } yield result) mustEqual (Left(boom))

  def transformAtomically =
    unsafeRun(for {
      tArray         <- makeTArray(N)("a")
      transformFiber <- tArray.transform(_ + "+b").commit.fork
      _              <- STM.foreach(0 until N)(idx => tArray.array(idx).update(_ + "+c")).commit
      _              <- transformFiber.join
      first          <- tArray.array(0).get.commit
      last           <- tArray.array(N - 1).get.commit
    } yield (first, last)) must (equalTo(("a+b+c", "a+b+c")) or equalTo(("a+c+b", "a+c+b")))

  def transformMAtomically =
    unsafeRun(for {
      tArray         <- makeTArray(N)("a")
      transformFiber <- tArray.transformM(a => STM.succeed(a + "+b")).commit.fork
      _              <- STM.foreach(0 until N)(idx => tArray.array(idx).update(_ + "+c")).commit
      _              <- transformFiber.join
      first          <- tArray.array(0).get.commit
      last           <- tArray.array(N - 1).get.commit
    } yield (first, last)) must (equalTo(("a+b+c", "a+b+c")) or equalTo(("a+c+b", "a+c+b")))

  def transformMTransactionally =
    unsafeRun(for {
      tArray <- makeTArray(N)(0)
      _      <- tArray.array(N / 2).update(_ => 1).commit
      result <- tArray.transformM(a => if (a == 0) STM.succeed(42) else STM.fail(boom)).commit.either
      first  <- tArray.array(0).get.commit
    } yield (first, result)) mustEqual ((0, Left(boom)))

  def updateHappy =
    unsafeRun(
      for {
        tArray <- makeTArray(1)(42)
        v      <- tArray.update(0, a => -a).commit
      } yield v
    ) mustEqual -42

  def updateOutOfBounds =
    unsafeRun(
      for {
        tArray <- makeTArray(1)(42)
        _      <- tArray.update(-1, identity).commit
      } yield ()
    ) must throwA[FiberFailure]

  def updateMHappy =
    unsafeRun(
      for {
        tArray <- makeTArray(1)(42)
        v      <- tArray.updateM(0, a => STM.succeed(-a)).commit
      } yield v
    ) mustEqual -42

  def updateMOutOfBounds =
    unsafeRun(
      for {
        tArray <- makeTArray(10)(0)
        _      <- tArray.updateM(10, STM.succeed).commit
      } yield ()
    ) must throwA[FiberFailure]

  def updateMFailure =
    unsafeRun(
      for {
        tArray <- makeTArray(n)(0)
        result <- tArray.updateM(0, _ => STM.fail(boom)).commit.either
      } yield result.fold(_.getMessage, _ => "unexpected")
    ) mustEqual ("Boom!")

  private val N    = 1000
  private val n    = 10
  private val boom = new Exception("Boom!")

  private def makeTArray[T](n: Int)(a: T) =
    ZIO.sequence(List.fill(n)(TRef.makeCommit(a))).map(refs => TArray(refs.toArray))
}
