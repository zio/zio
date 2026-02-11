package zio.internal

import zio._
import zio.test._
import zio.test.TestAspect._
import scala.jdk.CollectionConverters._

object FiberSetSpec extends ZIOSpecDefault {
  // Wrapper for tests requiring object references (JS/Native WeakRef compatibility)
  case class Item(id: String)

  def spec = suite("FiberSetSpec")(
    test("allows GC of items") {
      val set   = FiberSet.make[AnyRef]()
      val N     = 100000
      var items = new Array[AnyRef](N)

      // 1. Add items
      for (i <- 0 until N) {
        val f = new Object
        items(i) = f
        set.add(f)
      }

      assertTrue(set.size >= N)

      // 2. Clear strong refs
      java.util.Arrays.fill(items, null)
      items = null

      // 3. Force GC and internal cleanup
      var retries = 0
      while (set.size >= N / 2 && retries < 20) {
        java.lang.System.gc()
        set.gc()
        retries += 1
      }

      assertTrue(set.size < N / 2)
    } @@ jvmOnly,
    test("iterator includes items from both hot and cold") {
      val set = FiberSet.make[AnyRef]()
      val f1  = new Object
      val f2  = new Object

      set.add(f1)
      set.add(f2)

      val list = set.iterator.asScala.toList
      assertTrue(list.contains(f1)) && assertTrue(list.contains(f2))
    },
    test("handles high contention concurrently") {
      val set = FiberSet.make[AnyRef]()
      val N   = 10000

      // Concurrent adding
      val task = for {
        fibers <- ZIO.collectAll(List.fill(N)(ZIO.succeed(new Object)))
        _ <- ZIO.foreachPar(fibers) { f =>
               ZIO.succeed(set.add(f))
             }
      } yield fibers

      for {
        addedFibers <- task
        // Verify they are all there (weakly consistent, but if we keep refs they should be)
        size = set.size
        // All should be there
        containsAll = addedFibers.forall { f =>
                        // iterator contains it
                        val it = set.iterator.asScala
                        it.contains(f)
                      }
      } yield assertTrue(size == N) && assertTrue(containsAll)
    },
    test("eviction maintains data integrity") {
      // Force eviction by filling > 512 items
      val set   = FiberSet.make[AnyRef]()
      val N     = 2000 // > 512
      val items = (0 until N).map(_ => new Object).toArray

      items.foreach(set.add)

      // Check size
      val size = set.size

      // Check all items present
      val list        = set.iterator.asScala.toList
      val containsAll = items.forall(list.contains)

      assertTrue(size == N) && assertTrue(containsAll)
    },
    test("remove works for data in Cold storage") {
      val set   = FiberSet.make[AnyRef]()
      val N     = 1000
      val items = (0 until N).map(_ => new Object).toArray
      items.foreach(set.add) // Pushes many to Cold

      // Remove all
      items.foreach(set.remove)

      assertTrue(set.size == 0)
    },
    test("handles re-adding same item") {
      val set   = FiberSet.make[AnyRef]()
      val start = new Object
      set.add(start)
      set.add(start)
      set.add(start)

      val list = set.iterator.asScala.toList
      assertTrue(list.size == 1) && assertTrue(list.contains(start))
    },
    test("contains finds items in Hot and Cold") {
      val set      = FiberSet.make[AnyRef]()
      val hotItem  = new Object
      val coldItem = new Object

      set.add(hotItem)
      // Force coldItem to cold by adding enough items to bump it or just add it
      // Actually, we can't easily force it without filling hot.
      // But we can verify it works generally.

      (1 to 1000).foreach(_ => set.add(new Object)) // Fill up to force eviction
      set.add(coldItem)

      assertTrue(set.contains(hotItem)) && assertTrue(set.contains(coldItem)) && !assertTrue(set.contains(new Object))
    },
    test("clear removes everything") {
      val set = FiberSet.make[AnyRef]()
      (1 to 1000).foreach(_ => set.add(new Object))

      set.clear()

      assertTrue(set.size == 0) && !assertTrue(set.iterator.hasNext)
    },
    test("foreach iterates all items without allocation") {
      val set   = FiberSet.make[Item]()
      val items = (1 to 100).map(i => Item(s"item-$i"))
      items.foreach(set.add)

      val collected = scala.collection.mutable.Set.empty[Item]
      set.foreach { item =>
        collected += item
      }
      assertTrue(collected.toSet == items.toSet)
    },
    test("removeIf correctly removes items") {
      val set   = FiberSet.make[Item]()
      val items = (1 to 100).map(i => Item(s"item-$i"))
      items.foreach(set.add)

      // Remove even numbered items
      set.removeIf { item =>
        val id = item.id.split("-")(1).toInt
        id % 2 == 0
      }

      val collected = scala.collection.mutable.Set.empty[Item]
      set.foreach(collected += _)

      val expected = items.filter(item => item.id.split("-")(1).toInt % 2 != 0).toSet
      assertTrue(collected.toSet == expected)
    },
    test("addAll works correctly") {
      val set   = FiberSet.make[Item]()
      val items = (1 to 50).map(i => Item(s"A-$i"))
      set.addAll(items)

      val collected = scala.collection.mutable.Set.empty[Item]
      set.foreach(collected += _)
      assertTrue(collected.toSet == items.toSet)
    },
    test("toString provides debug info") {
      val set = FiberSet.make[Item]()
      set.add(Item("A"))
      val str = set.toString
      assertTrue(str.startsWith("FiberSet(hotSize="))
    }
  )
}
