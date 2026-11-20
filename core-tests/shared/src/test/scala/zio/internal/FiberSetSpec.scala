package zio.internal

import zio.{Promise, ZIO, ZIOBaseSpec}
import zio.test._
import zio.test.TestAspect.{flaky, jvmOnly}

object FiberSetSpec extends ZIOBaseSpec {
  final case class Entry(id: Int, @volatile var alive: Boolean = true)

  val entryIsAlive: WeakConcurrentBag.IsAlive[Entry] =
    new WeakConcurrentBag.IsAlive[Entry] {
      override def apply(value: Entry): Boolean = value.alive
    }

  def spec =
    suite("FiberSetSpec")(
      test("empty set is empty and has size 0") {
        val set = new FiberSet[Entry](16, entryIsAlive)
        assertTrue(set.isEmpty && set.size == 0)
      },
      test("add, contains, and remove within nursery capacity") {
        val set = new FiberSet[Entry](16, entryIsAlive)
        val e1  = Entry(1)
        val e2  = Entry(2)

        val added1 = set.add(e1)
        val added2 = set.add(e2)
        val dup1   = set.add(e1)

        val beforeRemove = set.contains(e1) && set.contains(e2) && (set.size == 2)
        val removed1     = set.remove(e1)
        val afterRemove  = !set.contains(e1) && set.contains(e2) && (set.size == 1)

        assertTrue(added1 && added2 && !dup1 && beforeRemove && removed1 && afterRemove)
      },
      test("dead elements are filtered and overwritten in nursery without graduating") {
        val set = new FiberSet[Entry](4, entryIsAlive)
        val e1  = Entry(1, alive = true)
        set.add(e1)
        e1.alive = false

        assertTrue(set.isEmpty && set.size == 0 && !set.contains(e1))
      },
      test("overflow beyond nursery capacity graduates live elements and iterates all") {
        val set     = new FiberSet[Entry](4, entryIsAlive)
        val entries = (1 to 100).map(Entry(_)).toVector

        entries.foreach(set.add)

        val iterated = set.iterator().toSet
        assertTrue(set.size == 100 && iterated == entries.toSet)
      },
      test("remove deletes graduated elements as well as nursery elements") {
        val set     = new FiberSet[Entry](4, entryIsAlive)
        val entries = (1 to 50).map(Entry(_)).toVector

        entries.foreach(set.add)
        set.graduate()

        val toRemove = entries.take(25)
        val toKeep   = entries.drop(25).toSet
        toRemove.foreach(set.remove)

        assertTrue(set.size == 25 && set.iterator().toSet == toKeep)
      },
      test("garbage collection reclaims unreachable graduated references") {
        val set  = new FiberSet[Entry](8, entryIsAlive)
        val hard = scala.collection.mutable.Map.empty[Int, Entry]

        (1 to 100).foreach { i =>
          val e = Entry(i)
          set.add(e)
          hard.update(i, e)
        }

        set.graduate()

        (1 to 100).foreach { i =>
          if (i % 2 == 0) hard.remove(i)
        }

        java.lang.System.gc()
        set.gc()

        assertTrue(set.size == 50)
      } @@ jvmOnly @@ flaky,
      test("parent fiber tracks and interrupts children using FiberSet") {
        for {
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          exit1  <- Promise.make[Nothing, Unit]
          exit2  <- Promise.make[Nothing, Unit]
          parent <- (for {
                      _ <- (latch1.succeed(()) *> ZIO.never).onInterrupt(exit1.succeed(())).fork
                      _ <- (latch2.succeed(()) *> ZIO.never).onInterrupt(exit2.succeed(())).fork
                      _ <- latch1.await *> latch2.await
                      _ <- ZIO.never
                    } yield ()).fork
          _    <- latch1.await *> latch2.await
          kids <- parent.children
          _    <- parent.interrupt
          _    <- exit1.await *> exit2.await
        } yield assertTrue(kids.length == 2)
      }
    )
}
