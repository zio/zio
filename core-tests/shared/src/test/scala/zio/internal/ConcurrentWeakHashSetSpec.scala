package zio.internal

import zio.test._
import zio.ZIOBaseSpec
import zio.test.Assertion.equalTo

import java.util.concurrent.Executors
import zio.test.TestAspect.flaky

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}

object ConcurrentWeakHashSetSpec extends ZIOBaseSpec {

  final case class Wrapper[A](value: A) {
    override def toString: String = value.toString
  }

  def spec = suite("ConcurrentWeakHashSetSpec")(
    test("Empty set is empty") {
      assertTrue(new ConcurrentWeakHashSet[Wrapper[Int]]().isEmpty)
    },
    test("Add should insert elements") {
      val set  = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2), Wrapper(3))
      set.add(refs.head)
      set.addOne(refs(1))
      set.addAll(List(refs(2)))
      assert(set.size())(equalTo(3))
    },
    test("Set resolves duplicated values") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      set.add(ref)
      assert(set.size())(equalTo(1))
    },
    test("Adding an element to set makes it non-empty") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref = Wrapper(Int.MaxValue)
      set.add(ref)
      assertTrue(!set.isEmpty)
    },
    test("Remove should delete reference from set") {
      val set  = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2))
      set.addAll(refs)
      set.remove(Wrapper(1))
      set.subtractOne(Wrapper(2))
      assert(set.size())(equalTo(0))
    },
    test("Removing non-existent element should be allowed") {
      val set    = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val result = set.remove(Wrapper(1))
      assertTrue(!result)
    },
    test("Contains should return true if set stores reference to the element") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      assertTrue(set.contains(ref))
    },
    test("Clearing the set makes it empty") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      set.clear()
      assertTrue(set.isEmpty)
    },
    test("Can iterate over elements") {
      val set  = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2))
      set.addAll(refs)
      val allValues = set.iterator.toList.sortBy(_.value)
      assert(allValues)(equalTo(refs))
    },
    test("Removing element with the same index from chain deletes only matched reference") {
      val refs         = (0 to 8).map(Wrapper(_))
      val corruptedSet = new ConcurrentWeakHashSet[Wrapper[Int]]()
      corruptedSet.addAll(refs)
      println(corruptedSet.remove(Wrapper(4))) // matched index (calculated from hashcode) is the same for 4 and 8
      assert(corruptedSet.toList.map(_.value).sorted)(equalTo(List(0, 1, 2, 3, 5, 6, 7, 8)))
    },
    test("Check if set is thread-safe with concurrent race condition between add & remove") {
      val sampleSize      = 1_000_000
      val executorService = Executors.newFixedThreadPool(2)
      val refs            = new ConcurrentLinkedQueue[Wrapper[Int]]()
      val set             = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val lock            = new CountDownLatch(1)

      executorService.submit(new Runnable {
        override def run(): Unit =
          (0 to sampleSize).foreach { idx =>
            val element = Wrapper(idx)
            refs.add(element)
            set.add(element)
          }
      })

      // remove half of the elements with even indices
      // it'll iterate multiple times over the set (often locking segments), because removing is faster than adding
      executorService.submit(new Runnable {
        override def run(): Unit = {
          var removedCount = 0
          while (removedCount < (sampleSize / 2) + 1) {
            for (idx <- 0 to sampleSize if idx % 2 == 0) {
              if (set.remove(Wrapper(idx))) removedCount += 1
            }
          }
          lock.countDown()
        }
      })

      lock.await() // await for the removal to finish
      assert(set.size())(equalTo((sampleSize / 2) + 1))

      // make sure only odd elements are left from full range
      val expected = (0 to sampleSize).filter(_ % 2 == 1).map(Wrapper(_)).toList
      val actual   = set.iterator.toList.sortBy(_.value)
      assert(actual)(equalTo(expected))
    },
    test("Dead references are removed from set") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(1)) // dead ref

      val ref = Wrapper(2)
      set.add(ref) // ref from scope

      System.gc()
      set.gc()

      assert(set.size())(equalTo(1))
      assert(set.iterator.next())(equalTo(ref))
    } @@ flaky
  )

}
