package zio.internal

import zio.{Chunk, Duration, Unsafe}
import zio.test._

object FiberSetSpec extends ZIOSpecDefault {

  implicit val unsafe: Unsafe = Unsafe.unsafe

  def spec: Spec[TestEnvironment, Any] =
    suite("FiberSetSpec")(
      test("add and iterate single element") {
        val set = FiberSet[String]()
        set.add("hello")

        val result = set.iterator.toList
        assertTrue(result.contains("hello"))
      },
      test("add multiple elements") {
        val set = FiberSet[String]()

        for (i <- 1 to 100) {
          set.add(s"element-$i")
        }

        val result = set.iterator.toList
        assertTrue(result.size == 100)
      },
      test("remove element") {
        val set = FiberSet[String]()
        set.add("hello")
        set.add("world")

        val removed = set.remove("hello")
        val result = set.iterator.toList

        assertTrue(removed) &&
        assertTrue(result.contains("world")) &&
        assertTrue(!result.contains("hello"))
      },
      test("remove non-existent element") {
        val set = FiberSet[String]()
        set.add("hello")

        val removed = set.remove("not-there")
        assertFalse(removed)
      },
      test("concurrent add from multiple threads") {
        val set = FiberSet[Int]()
        val numThreads = 16
        val elementsPerThread = 1000

        val threads = (0 until numThreads).map { threadId =>
          new Thread {
            override def run(): Unit = {
              val start = threadId * elementsPerThread
              for (i <- start until start + elementsPerThread) {
                set.add(i)
              }
            }
          }
        }

        threads.foreach(_.start())
        threads.foreach(_.join())

        val result = set.iterator.toList
        assertTrue(result.size == numThreads * elementsPerThread)
      },
      test("iterator is weakly consistent") {
        val set = FiberSet[Int]()

        for (i <- 1 to 100) {
          set.add(i)
        }

        val iterator = set.iterator
        var count = 0

        // Add more elements during iteration
        while (iterator.hasNext) {
          iterator.next()
          count += 1
          set.add(count + 1000)
        }

        // Should not throw exception and should have seen some elements
        assertTrue(count > 0)
      },
      test("gc removes dead references") {
        var aliveCount = 0
        val set = FiberSet[DummyFiber](
          isAlive = new FiberSet.IsAlive[DummyFiber] {
            def apply(f: DummyFiber): Boolean = f.alive
          }
        )

        val alive1 = new DummyFiber(true)
        val alive2 = new DummyFiber(true)
        val dead = new DummyFiber(false)

        set.add(alive1)
        set.add(alive2)
        set.add(dead)

        set.gc()

        val result = set.iterator.toList
        assertTrue(result.contains(alive1)) &&
        assertTrue(result.contains(alive2)) &&
        assertTrue(!result.contains(dead))
      },
      test("size approximation") {
        val set = FiberSet[String]()

        for (i <- 1 to 50) {
          set.add(s"elem-$i")
        }

        val size = set.size
        assertTrue(size >= 50)
      },
      test("hot buffer overflow spills to warm storage") {
        val set = FiberSet[String](hotCapacity = 10, warmCapacity = 100)

        // Add more than hot capacity
        for (i <- 1 to 50) {
          set.add(s"elem-$i")
        }

        val result = set.iterator.toList
        assertTrue(result.size == 50)
      },
      test("empty set iterator") {
        val set = FiberSet[String]()
        val result = set.iterator.toList
        assertTrue(result.isEmpty)
      },
      test("toString works") {
        val set = FiberSet[String]()
        set.add("test")
        val str = set.toString
        assertTrue(str.startsWith("FiberSet("))
      },
      test("concurrent add and remove") {
        val set = FiberSet[Int]()
        val numThreads = 8
        val operationsPerThread = 500

        val addThreads = (0 until numThreads).map { threadId =>
          new Thread {
            override def run(): Unit = {
              for (i <- 0 until operationsPerThread) {
                set.add(threadId * 1000 + i)
              }
            }
          }
        }

        val removeThreads = (0 until numThreads).map { threadId =>
          new Thread {
            override def run(): Unit = {
              for (i <- 0 until operationsPerThread) {
                set.remove(threadId * 1000 + i)
              }
            }
          }
        }

        (addThreads ++ removeThreads).foreach(_.start())
        (addThreads ++ removeThreads).foreach(_.join())

        // Should not crash, size should be non-negative
        assertTrue(set.size >= 0)
      },
      test("isAlive predicate is respected") {
        var checkCount = 0
        val set = FiberSet[DummyFiber](
          isAlive = new FiberSet.IsAlive[DummyFiber] {
            def apply(f: DummyFiber): Boolean = {
              checkCount += 1
              f.alive
            }
          }
        )

        val alive = new DummyFiber(true)
        val dead = new DummyFiber(false)

        set.add(alive)
        set.add(dead)

        // Reset count before iteration
        checkCount = 0
        val result = set.iterator.toList

        assertTrue(result.contains(alive)) &&
        assertTrue(!result.contains(dead)) &&
        assertTrue(checkCount > 0)
      }
    )
}

/** Dummy fiber for testing */
final class DummyFiber(var alive: Boolean)
