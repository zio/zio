package zio.stm

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

object TPriorityQueueSpec extends ZIOBaseSpec {

  def spec = suite("TPriorityQueueSpec")(
    testM("offerAll and takeAll") {
      checkM(Gen.chunkOf(Gen.anyInt)) { as =>
        val transaction = for {
          queue  <- TPriorityQueue.empty[Int]
          _      <- queue.offerAll(as)
          values <- queue.takeAll
        } yield values
        assertM(transaction.commit)(equalTo(as.sorted))
      }
    },
    testM("removeIf") {
      checkM(Gen.listOf(Gen.anyInt), Gen.function(Gen.boolean)) { (as, f) =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          _     <- queue.removeIf(f)
          list  <- queue.toList
        } yield list
        assertM(transaction.commit)(equalTo(as.filterNot(f).sorted))
      }
    },
    testM("retainIf") {
      checkM(Gen.listOf(Gen.anyInt), Gen.function(Gen.boolean)) { (as, f) =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          _     <- queue.retainIf(f)
          list  <- queue.toList
        } yield list
        assertM(transaction.commit)(equalTo(as.filter(f).sorted))
      }
    },
    testM("take") {
      checkM(Gen.listOf(Gen.anyInt)) { as =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          takes <- STM.collectAll(STM.replicate(as.length)(queue.take))
        } yield takes
        assertM(transaction.commit)(equalTo((as.sorted)))
      }
    },
    testM("takeUpTo") {
      val gen = for {
        as <- Gen.chunkOf(Gen.int(1, 10))
        n  <- Gen.int(0, as.length)
      } yield (as, n)
      checkM(gen) {
        case (as, n) =>
          val transaction = for {
            queue <- TPriorityQueue.fromIterable(as)
            left  <- queue.takeUpTo(n)
            right <- queue.takeAll
          } yield (left, right)
          assertM(transaction.commit)(equalTo((as.sorted.take(n), as.sorted.drop(n))))
      }
    },
    testM("toChunk") {
      checkM(Gen.chunkOf(Gen.anyInt)) { as =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          list  <- queue.toChunk
        } yield list
        assertM(transaction.commit)(equalTo(as.sorted))
      }
    },
    testM("toList") {
      checkM(Gen.listOf(Gen.anyInt)) { as =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          list  <- queue.toList
        } yield list
        assertM(transaction.commit)(equalTo(as.sorted))
      }
    },
    testM("toVector") {
      checkM(Gen.vectorOf(Gen.anyInt)) { as =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          list  <- queue.toVector
        } yield list
        assertM(transaction.commit)(equalTo(as.sorted))
      }
    }
  )
}
