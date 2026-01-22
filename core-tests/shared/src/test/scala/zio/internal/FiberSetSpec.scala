package zio.internal

import zio.test._
import zio.test.TestAspect.{flaky, jvmOnly}
import zio.{ZIO, ZIOBaseSpec}

object FiberSetSpec extends ZIOBaseSpec {

  final case class Wrapper[A](value: A) {
    def isAlive(): Boolean = true
  }

  final case class MortalWrapper[A](value: A, var alive: Boolean = true) {
    def isAlive(): Boolean = alive
    def kill(): Unit       = alive = false
  }

  def spec =
    suite("FiberSetSpec") {
      test("size of singleton set") {
        val set = FiberSet[Wrapper[String]](10)

        val value = Wrapper("foo")
        set.add(value)

        assertTrue(set.size == 1)
      } +
        test("add and iterate 100 elements (nursery size: 100)") {
          val set = FiberSet[Wrapper[String]](100)

          var hard = Set.empty[Wrapper[String]]

          (1 to 100).map(i => Wrapper(i.toString)).foreach { wrapper =>
            hard = hard + wrapper
            set.add(wrapper)
          }

          assertTrue((set.size == 100) && (set.iterator.toSet == hard))
        } +
        test("remove returns true for graduated entry") {
          val set = FiberSet[Wrapper[String]](10)

          val entries = (1 to 20).map(i => Wrapper(i.toString))
          entries.foreach(set.add)

          // Force graduation by exceeding nursery
          set.graduate()

          // Remove should succeed for graduated entries
          val removed = entries.map(set.remove)

          assertTrue(removed.forall(_ == true) && set.size == 0)
        } +
        test("remove returns false for nursery entry") {
          val set = FiberSet[Wrapper[String]](100) // Large nursery

          val value = Wrapper("test")
          set.add(value)

          // Entry is still in nursery, remove returns false
          val removed = set.remove(value)

          // Entry is still accessible via iterator after graduation
          set.graduate()
          val inSet = set.iterator.contains(value)

          // Remove the graduated entry
          val removedAfterGrad = set.remove(value)

          assertTrue(!removed && inSet && removedAfterGrad)
        } +
        test("dead entries filtered during iteration") {
          val set = FiberSet[MortalWrapper[String]](100, _.isAlive())

          val entries = (1 to 100).map(i => MortalWrapper(i.toString))
          entries.foreach(set.add)

          // Kill half
          entries.filter(_.value.toInt % 2 == 0).foreach(_.kill())

          set.graduate()

          val alive = set.iterator.toList

          assertTrue(alive.length == 50)
        } +
        test("gc removes dead entries") {
          val set = FiberSet[MortalWrapper[String]](100, _.isAlive())

          val entries = (1 to 100).map(i => MortalWrapper(i.toString))
          entries.foreach(set.add)

          set.graduate()

          // Kill half
          entries.filter(_.value.toInt % 2 == 0).foreach(_.kill())

          // Run GC
          set.gc()

          assertTrue(set.size == 50)
        } +
        test("weak references are cleared after GC") {
          val set = FiberSet[Wrapper[String]](100)

          // Add entries without keeping strong references
          (1 to 1000).foreach { _ =>
            set.add(Wrapper(scala.util.Random.nextString(10)))
          }

          set.graduate()

          // Trigger GC
          System.gc()
          Thread.sleep(100)

          // Poll ref queue and run GC
          set.pollRefQueue()
          set.gc()

          assertTrue(set.size <= 100)
        } @@ flaky +
        test("stress test with many concurrent fibers") {
          val set = FiberSet[Wrapper[Int]](1000)

          val entries = (1 to 1000).map(i => Wrapper(i))

          for {
            _    <- ZIO.foreachParDiscard(entries)(e => ZIO.succeed(set.add(e)))
            _    <- ZIO.succeed(set.graduate())
            size <- ZIO.succeed(set.size)
            iter <- ZIO.succeed(set.iterator.toList)
          } yield assertTrue(size == 1000 && iter.length == 1000)
        } +
        test("concurrent add and remove") {
          val set = FiberSet[Wrapper[Int]](100)

          val entries = (1 to 200).map(i => Wrapper(i))

          for {
            // Add all entries
            _ <- ZIO.foreachParDiscard(entries)(e => ZIO.succeed(set.add(e)))
            _ <- ZIO.succeed(set.graduate())

            // Remove half concurrently
            toRemove = entries.filter(_.value % 2 == 0)
            _       <- ZIO.foreachParDiscard(toRemove)(e => ZIO.succeed(set.remove(e)))

            remaining = set.iterator.toList
          } yield assertTrue(remaining.length == 100)
        }
    } @@ jvmOnly
}
