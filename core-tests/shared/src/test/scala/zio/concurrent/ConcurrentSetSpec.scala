package zio.concurrent

import zio._
import zio.test._

object ConcurrentSetSpec extends ZIOBaseSpec {

  override def spec: ZSpec[Environment, Failure] = suite("ConcurrentSetSpec")(
    testM("add") {
      for {
        set    <- ConcurrentSet.empty[Int]
        added  <- set.add(1)
        result <- set.toSet
      } yield assertTrue(added, result == Set(1))
    },
    testM("addAll") {
      for {
        set    <- ConcurrentSet.make[Int](3, 4)
        added  <- set.addAll(Chunk(1, 2))
        result <- set.toSet
      } yield assertTrue(added, result == Set(1, 2, 3, 4))
    },
    testM("remove") {
      for {
        set       <- ConcurrentSet.make(1, 2, 3)
        isRemoved <- set.remove(1)
        result    <- set.toSet
      } yield assertTrue(isRemoved, result == Set(2, 3))
    },
    testM("removeAll") {
      for {
        set       <- ConcurrentSet.make(1, 2, 3)
        isRemoved <- set.removeAll(List(1, 3))
        result    <- set.toSet
      } yield assertTrue(isRemoved, result == Set(2))
    },
    testM("retainAll") {
      for {
        set       <- ConcurrentSet.make(1, 2, 3, 4)
        isRemoved <- set.retainAll(List(1, 3, 5, 6))
        result    <- set.toSet
      } yield assertTrue(isRemoved, result == Set(1, 3))
    },
    testM("clear and isEmpty") {
      for {
        set     <- ConcurrentSet.make(1, 2, 3)
        _       <- set.clear
        isEmpty <- set.isEmpty
        result  <- set.toSet
      } yield assertTrue(isEmpty, result == Set.empty[Int])
    },
    testM("contains") {
      for {
        set      <- ConcurrentSet.make(1, 2, 3)
        contains <- set.contains(5)
      } yield assertTrue(!contains)
    },
    testM("containsAll") {
      for {
        set         <- ConcurrentSet.make(1, 2, 3)
        containsAll <- set.containsAll(Chunk(1, 2, 3))
      } yield assertTrue(containsAll)
    },
    testM("exists") {
      for {
        set       <- ConcurrentSet.make(1, 2, 3)
        exists    <- set.exists(_ > 2)
        notExists <- set.exists(_ > 3)
      } yield assertTrue(exists, !notExists)
    },
    testM("forall") {
      for {
        set       <- ConcurrentSet.make(1, 2, 3)
        forall    <- set.forall(_ < 4)
        notForall <- set.forall(_ > 5)
      } yield assertTrue(forall, !notForall)
    },
    testM("filter") {
      for {
        set    <- ConcurrentSet.make(1, 2, 3, 4)
        _      <- set.removeIf(_ % 2 == 0)
        result <- set.toSet
      } yield assertTrue(result == Set(2, 4))
    },
    testM("filterNot") {
      for {
        set    <- ConcurrentSet.make(1, 2, 3, 4)
        _      <- set.retainIf(_ % 2 == 0)
        result <- set.toSet
      } yield assertTrue(result == Set(1, 3))
    },
    testM("find") {
      for {
        set    <- ConcurrentSet.make(1, 2, 3)
        result <- set.find(_ > 2)
      } yield assertTrue(result.get == 3)
    },
    testM("collectFirst") {
      for {
        set    <- ConcurrentSet.make(1, 2, 3)
        result <- set.collectFirst { case 3 => "Three" }
      } yield assertTrue(result.get == "Three")
    },
    testM("size") {
      for {
        set  <- ConcurrentSet.make(1, 2, 3, 4, 5)
        size <- set.size
      } yield assertTrue(size == 5)
    },
    testM("toSet") {
      for {
        set    <- ConcurrentSet.make(1, 2, 3, 4, 5)
        result <- set.toSet
      } yield assertTrue(result == Set(1, 2, 3, 4, 5))
    },
    testM("transform") {
      for {
        set    <- ConcurrentSet.make(1, 2, 3, 4, 5)
        _      <- set.transform(_ + 10)
        result <- set.toSet
      } yield assertTrue(result == Set(11, 12, 13, 14, 15))
    },
    testM("fold") {
      for {
        set    <- ConcurrentSet.make(1, 2, 3, 4, 5)
        result <- set.fold(0)(_ + _)
      } yield assertTrue(result == 15)
    }
  )
}
