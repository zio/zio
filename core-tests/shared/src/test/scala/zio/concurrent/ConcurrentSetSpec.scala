package zio.concurrent

import zio.test._
import zio.test.Assertion._
import zio.{Chunk, ZIOBaseSpec}

object ConcurrentSetSpec extends ZIOBaseSpec {

  override def spec: ZSpec[Environment, Failure] = suite("ConcurrentSetSpec")(
    testM("add") {
      for {
        concurrentSet <- ConcurrentSet.make[Int](3)
        added         <- concurrentSet.add(1)
      } yield assert(added)(equalTo(true))
    },
    testM("addAll") {
      for {
        concurrentSet <- ConcurrentSet.make[Int](3)
        added         <- concurrentSet.addAll(Chunk(1, 2))
      } yield assert(added)(equalTo(true))
    },
    testM("remove") {
      for {
        concurrentSet <- ConcurrentSet.make(1, 2, 3)
        isRemoved     <- concurrentSet.remove(1)
      } yield assert(isRemoved)(equalTo(true))
    },
    testM("clear and isEmpty") {
      for {
        concurrentSet <- ConcurrentSet.make(1, 2, 3)
        _             <- concurrentSet.clear
        isEmpty       <- concurrentSet.isEmpty
      } yield assert(isEmpty)(equalTo(true))
    },
    testM("contains") {
      for {
        concurrentSet <- ConcurrentSet.make(1, 2, 3)
        contains      <- concurrentSet.contains(5)
      } yield assert(contains)(equalTo(false))
    },
    testM("containsAll") {
      for {
        concurrentSet <- ConcurrentSet.make(1, 2, 3)
        containsAll   <- concurrentSet.containsAll(Chunk(1, 2, 3))
      } yield assert(containsAll)(equalTo(true))
    },
    testM("size") {
      for {
        concurrentSet <- ConcurrentSet.make(1, 2, 3, 4, 5)
        size          <- concurrentSet.size
      } yield assert(size)(equalTo(5))
    }
  )
}
