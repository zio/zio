package zio.internal

import zio.test._
import zio.test.TestAspect.{flaky, jvmOnly, nonFlaky}
import zio.ZIOBaseSpec

object FiberSetSpec extends ZIOBaseSpec {

  final case class Entry(id: Int) {
    @volatile private var alive = true
    def isAlive(): Boolean = alive
    def kill(): Unit       = alive = false
  }

  private val isAlive: FiberSet.IsAlive[Entry] = _.isAlive()

  def spec = suite("FiberSetSpec")(
    test("add and iterate single element") {
      val set   = FiberSet[Entry](16, 1, isAlive)
      val entry = Entry(1)
      set.add(entry)
      assertTrue(set.iterator.toList == List(entry))
    },
    test("add multiple and iterate") {
      val set     = FiberSet[Entry](64, 1, isAlive)
      val entries = (1 to 50).map(Entry(_)).toList
      entries.foreach(set.add)
      val result = set.iterator.toSet
      assertTrue(result == entries.toSet)
    },
    test("size tracks entries") {
      val set     = FiberSet[Entry](32, 1, isAlive)
      val entries = (1 to 10).map(Entry(_)).toList
      entries.foreach(set.add)
      assertTrue(set.size >= 10)
    },
    test("isEmpty on empty set") {
      val set = FiberSet[Entry](16, 1, isAlive)
      assertTrue(set.isEmpty)
    },
    test("isEmpty after add") {
      val set   = FiberSet[Entry](16, 1, isAlive)
      val entry = Entry(1)
      set.add(entry)
      assertTrue(!set.isEmpty)
    },
    test("remove by identity") {
      val set = FiberSet[Entry](32, 1, isAlive)
      val a   = Entry(1)
      val b   = Entry(2)
      set.add(a)
      set.add(b)
      val removed = set.remove(a)
      assertTrue(removed && set.iterator.toList == List(b))
    },
    test("remove returns false for missing element") {
      val set = FiberSet[Entry](16, 1, isAlive)
      val a   = Entry(1)
      assertTrue(!set.remove(a))
    },
    test("dead entries filtered during iteration") {
      val set     = FiberSet[Entry](64, 1, isAlive)
      val entries = (1 to 20).map(Entry(_)).toList
      entries.foreach(set.add)
      entries.filter(_.id % 2 == 0).foreach(_.kill())
      val alive = set.iterator.toList
      assertTrue(alive.forall(_.id % 2 == 1) && alive.size == 10)
    },
    test("forEach visits all live entries") {
      val set     = FiberSet[Entry](64, 1, isAlive)
      val entries = (1 to 30).map(Entry(_)).toList
      entries.foreach(set.add)
      entries.filter(_.id % 3 == 0).foreach(_.kill())
      var visited = List.empty[Entry]
      set.forEach(e => visited = e :: visited)
      assertTrue(visited.size == 20 && visited.forall(_.isAlive()))
    },
    test("eviction preserves live entries") {
      val set     = FiberSet[Entry](8, 1, isAlive)
      val entries = (1 to 20).map(Entry(_)).toList
      entries.foreach(set.add)
      val result = set.iterator.toSet
      assertTrue(result == entries.toSet)
    },
    test("gc cleans dead graduates") {
      val set     = FiberSet[Entry](8, 1, isAlive)
      val entries = (1 to 20).map(Entry(_)).toList
      entries.foreach(set.add)
      entries.take(10).foreach(_.kill())
      set.gc()
      val alive = set.iterator.toList
      assertTrue(alive.size == 10 && alive.forall(_.id > 10))
    },
    test("unreachable entries are collected") {
      val set = FiberSet[Entry](8, 1, isAlive)
      (1 to 100).foreach(i => set.add(Entry(i)))
      System.gc()
      set.gc()
      assertTrue(set.size <= 100)
    } @@ flaky @@ jvmOnly,
    test("concurrent add from multiple threads") {
      import java.util.concurrent.{CountDownLatch, Executors}
      val nThreads = 8
      val perThread = 500
      val set       = FiberSet[Entry](256, nThreads, isAlive)
      val entries   = (1 to (nThreads * perThread)).map(Entry(_)).toArray
      val latch     = new CountDownLatch(1)
      val executor  = Executors.newFixedThreadPool(nThreads)

      (0 until nThreads).foreach { t =>
        executor.submit(new Runnable {
          def run(): Unit = {
            latch.await()
            var i = t * perThread
            val end = i + perThread
            while (i < end) {
              set.add(entries(i))
              i += 1
            }
          }
        })
      }
      latch.countDown()
      executor.shutdown()
      executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)

      val result = set.iterator.toSet
      assertTrue(result == entries.toSet)
    } @@ nonFlaky(3),
    test("mixed concurrent add and remove") {
      import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
      val n        = 2000
      val set      = FiberSet[Entry](256, 4, isAlive)
      val entries  = (1 to n).map(Entry(_)).toArray
      val latch    = new CountDownLatch(1)
      val executor = Executors.newFixedThreadPool(4)

      (0 until 2).foreach { t =>
        executor.submit(new Runnable {
          def run(): Unit = {
            latch.await()
            var i = t * (n / 2)
            val end = i + (n / 2)
            while (i < end) { set.add(entries(i)); i += 1 }
          }
        })
      }
      (0 until 2).foreach { t =>
        executor.submit(new Runnable {
          def run(): Unit = {
            latch.await()
            Thread.sleep(1)
            var i = t * (n / 2)
            val end = i + (n / 2)
            while (i < end) {
              if (entries(i).id % 2 == 0) set.remove(entries(i))
              i += 1
            }
          }
        })
      }
      latch.countDown()
      executor.shutdown()
      executor.awaitTermination(10, TimeUnit.SECONDS)

      val odd = set.iterator.toList.filter(_.id % 2 == 1)
      assertTrue(odd.nonEmpty)
    } @@ nonFlaky(3)
  )
}
