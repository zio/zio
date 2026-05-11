package zio.internal

import zio.{ZIO, ZIOBaseSpec}
import zio.test._
import zio.test.TestAspect.{flaky, jvmOnly}

import java.util.concurrent.atomic.AtomicBoolean

object FiberSetSpec extends ZIOBaseSpec {
  final class Entry(val value: Int, alive: Boolean = true) {
    private val aliveRef = new AtomicBoolean(alive)

    def isAlive(): Boolean =
      aliveRef.get()

    def setAlive(alive: Boolean): Unit =
      aliveRef.set(alive)

    override def equals(that: Any): Boolean =
      that match {
        case that: Entry => value == that.value
        case _           => false
      }

    override def hashCode(): Int =
      value.hashCode()

    override def toString: String =
      s"Entry($value)"
  }

  def spec =
    suite("FiberSetSpec")(
      test("iterates live entries") {
        val set     = FiberSet[Entry](_.isAlive())
        val entries = List(new Entry(1), new Entry(2), new Entry(3))

        entries.foreach(set.add)

        assertTrue(set.iterator.toSet == entries.toSet)
      },
      test("remove deletes an entry") {
        val set    = FiberSet[Entry](_.isAlive())
        val first  = new Entry(1)
        val second = new Entry(2)

        set.add(first)
        set.add(second)
        set.remove(first)

        assertTrue(set.iterator.toSet == Set(second))
      },
      test("remove is idempotent") {
        val set   = FiberSet[Entry](_.isAlive())
        val entry = new Entry(1)

        set.add(entry)
        set.remove(entry)
        set.remove(entry)

        assertTrue(set.isEmpty)
      },
      test("filters entries that are no longer alive") {
        val set   = FiberSet[Entry](_.isAlive())
        val entry = new Entry(1)

        set.add(entry)
        entry.setAlive(false)

        assertTrue(set.isEmpty)
      },
      test("concurrent add remove and iteration is weakly consistent") {
        val set     = FiberSet[Entry](_.isAlive())
        val entries = (0 until 1000).map(new Entry(_)).toVector

        ZIO
          .foreachParDiscard(entries) { entry =>
            ZIO.succeed {
              set.add(entry)
              if (entry.value % 2 == 0) set.remove(entry)
              set.iterator.foreach(_ => ())
            }
          }
          .as(assertTrue(set.iterator.forall(_.value % 2 != 0)))
      },
      test("manual gc drops collected entries") {
        val set  = FiberSet[Entry](_.isAlive())
        val live = new Entry(2)

        set.add(new Entry(1))
        set.add(live)

        System.gc()
        set.gc()

        assertTrue(set.iterator.toSet == Set(live))
      } @@ flaky @@ jvmOnly
    )
}
