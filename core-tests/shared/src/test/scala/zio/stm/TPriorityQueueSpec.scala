package zio.stm

import zio.test.Assertion._
import zio.test._
import zio.{Chunk, ZIOBaseSpec}

object TPriorityQueueSpec extends ZIOBaseSpec {

  final case class Event(time: Int, description: String)

  implicit val eventOrdering: Ordering[Event] =
    Ordering.by(_.time)

  val genEvent: Gen[Any, Event] =
    for {
      time        <- Gen.int(-10, 10)
      description <- Gen.alphaNumericString
    } yield Event(time, description)

  val genEvents: Gen[Any, Chunk[Event]] =
    Gen.chunkOf(genEvent)

  val genPredicate: Gen[Any, Event => Boolean] =
    Gen.function(Gen.boolean)

  def spec = suite("TPriorityQueueSpec")(
    test("isEmpty") {
      check(genEvents) { as =>
        val transaction = for {
          queue <- TPriorityQueue.empty[Event]
          _     <- queue.offerAll(as)
          empty <- queue.isEmpty
        } yield empty
        assertZIO(transaction.commit)(equalTo(as.isEmpty))
      }
    },
    test("nonEmpty") {
      check(genEvents) { as =>
        val transaction = for {
          queue    <- TPriorityQueue.empty[Event]
          _        <- queue.offerAll(as)
          nonEmpty <- queue.nonEmpty
        } yield nonEmpty
        assertZIO(transaction.commit)(equalTo(as.nonEmpty))
      }
    },
    test("offerAll and takeAll") {
      check(genEvents) { as =>
        val transaction = for {
          queue  <- TPriorityQueue.empty[Event]
          _      <- queue.offerAll(as)
          values <- queue.takeAll
        } yield values
        assertZIO(transaction.commit)(hasSameElements(as) && isSorted)
      }
    },
    test("removeIf") {
      check(genEvents, genPredicate) { (as, f) =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          _     <- queue.removeIf(f)
          list  <- queue.toChunk
        } yield list
        assertZIO(transaction.commit)(hasSameElements(as.filterNot(f)) && isSorted)
      }
    },
    test("retainIf") {
      check(Gen.listOf(genEvent), genPredicate) { (as, f) =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          _     <- queue.retainIf(f)
          list  <- queue.toList
        } yield list
        assertZIO(transaction.commit)(hasSameElements(as.filter(f)) && isSorted)
      }
    },
    test("take") {
      check(genEvents) { as =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          takes <- STM.collectAll(STM.replicate(as.length)(queue.take))
        } yield takes
        assertZIO(transaction.commit)(hasSameElements(as) && isSorted)
      }
    },
    test("takeUpTo") {
      val gen = for {
        as <- genEvents
        n  <- Gen.int(0, as.length)
      } yield (as, n)
      check(gen) { case (as, n) =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          left  <- queue.takeUpTo(n)
          right <- queue.takeAll
        } yield left ++ right
        assertZIO(transaction.commit)(hasSameElements(as) && isSorted)
      }
    },
    test("toChunk") {
      check(genEvents) { as =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          list  <- queue.toChunk
        } yield list
        assertZIO(transaction.commit)(hasSameElements(as) && isSorted)
      }
    },
    test("toList") {
      check(genEvents) { as =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          list  <- queue.toList
        } yield list
        assertZIO(transaction.commit)(hasSameElements(as) && isSorted)
      }
    },
    test("toVector") {
      check(genEvents) { as =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          list  <- queue.toVector
        } yield list
        assertZIO(transaction.commit)(hasSameElements(as) && isSorted)
      }
    }
  )
}
