package zio.test

//import zio.Console.printLine

import zio._

import java.util.concurrent.atomic.AtomicReference
//import zio.test.{TestFailure, TestSuccess}

/*
  Fiber Refs
    - Current Path
      - List of Strings
      - Empty initially, add new value each time you go deeper
    - Output
 */

case class TestOutputZ(lines: Chunk[String])
case class Lineage(ancestors: Chunk[SuiteId])

object  TestOutputZ {
  def log(ref: FiberRef[TestOutputZ])(string: String): UIO[Unit] = {
    ref.update{log =>
      log.copy(lines = log.lines :+ string)
    }
  }
}

object  Lineage {
  def recordParent(ref: FiberRef[Lineage])(ancestor: SuiteId): UIO[Unit] = {
    ref.update{log =>
      log.copy(ancestors = log.ancestors :+ ancestor)
    }
  }
}

object FiberRefTestOutput {
  val outputRef: FiberRef[TestOutputZ] = FiberRef.unsafe.make[TestOutputZ](
    TestOutputZ(Chunk.empty)
  )(Unsafe.unsafe)

  val lineageRef: FiberRef[Lineage] = FiberRef.unsafe.make[Lineage](
    Lineage(Chunk.empty)
  )(Unsafe.unsafe)
}