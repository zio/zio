package zio.internal

import zio.internal.ConcurrentWeakHashSetAbstractSpec._
import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test._
import zio.{UIO, ZIO, ZIOBaseSpec, durationInt}

import scala.collection.mutable

trait ConcurrentWeakHashSetAbstractSpec extends ZIOBaseSpec {

  def spec = suite("ConcurrentWeakHashSetSpec")(
    test("Empty set is empty") {
      assertTrue(createSet[Wrapper[Int]]().isEmpty)
    },
    test("Add should insert elements") {
      val set  = createSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2), Wrapper(3))
      set.add(refs.head)
      set.addOne(refs(1))
      set.addAll(List(refs(2)))
      assert(set.size)(equalTo(3)) && reachabilityFence(refs)
    },
    test("Set resolves duplicated values") {
      val set = createSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      set.add(ref)
      assert(set.size)(equalTo(1)) && reachabilityFence(ref)
    },
    test("Adding an element to set makes it non-empty") {
      val set = createSet[Wrapper[Int]]()
      val ref = Wrapper(Int.MaxValue)
      set.add(ref)
      assertTrue(set.nonEmpty) && reachabilityFence(ref)
    },
    test("Remove should delete reference from set") {
      val set  = createSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2))
      set.addAll(refs)
      set.remove(Wrapper(1))
      set.subtractOne(Wrapper(2))
      assert(set.size)(equalTo(0)) && reachabilityFence(refs)
    },
    test("Removing non-existent element should be allowed") {
      val set    = createSet[Wrapper[Int]]()
      val result = set.remove(Wrapper(1))
      assertTrue(!result)
    },
    test("Contains should return true if set stores reference to the element") {
      val set = createSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      assertTrue(set.contains(ref)) && reachabilityFence(ref)
    },
    test("Clearing the set makes it empty") {
      val set = createSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      set.clear()
      assertTrue(set.isEmpty) && reachabilityFence(ref)
    },
    test("Can iterate over elements") {
      val set  = createSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2))
      set.addAll(refs)
      val allValues = set.iterator.toList.sortBy(_.value)
      assert(allValues)(equalTo(refs)) && reachabilityFence(refs)
    },
    test("Removing element with the same index from chain deletes only matched reference") {
      val refs         = (0 to 8).map(Wrapper(_))
      val corruptedSet = createSet[Wrapper[Int]]()
      corruptedSet.addAll(refs)
      println(corruptedSet.remove(Wrapper(4))) // matched index (calculated from hashcode) is the same for 4 and 8
      assert(corruptedSet.toList.map(_.value).sorted)(equalTo(List(0, 1, 2, 3, 5, 6, 7, 8))) && reachabilityFence(refs)
    },
    test("Check if set is thread-safe with concurrent race condition between add & remove") {
      val sampleSize = 1_000_000
      val refs       = createConcurrentQueue[Wrapper[Int]]()
      val set        = createSet[Wrapper[Int]]()

      def addElements() =
        ZIO
          .foreachPar(0 to sampleSize) { idx =>
            val element = Wrapper(idx)
            ZIO.succeed {
              refs.add(element)
              set.add(element)
            }
          }

      def removeHalfOfElements() = {

        var removedCount = 0

        sealed trait IterationState
        object Done                  extends IterationState
        object MoreIterationRequired extends IterationState

        def removeHalfOfElementsSync(): IterationState = {
          for (idx <- 0 to sampleSize if idx % 2 == 0) {
            if (set.remove(Wrapper(idx))) {
              removedCount += 1
            }
          }

          println(s"[$removedCount] vs [${(sampleSize / 2) + 1}]")
          if (removedCount == (sampleSize / 2) + 1) {
            Done
          } else {
            MoreIterationRequired
          }
        }

        val initial: IterationState = MoreIterationRequired
        ZIO.iterate(initial)(_ != Done) { _ =>
          for {
            result <- ZIO.succeed(removeHalfOfElementsSync())
            _      <- ZIO.sleep(1.second)
          } yield result
        }
      }

      for {
        addElementsFork          <- addElements().fork
        removeHalfOfElementsFork <- removeHalfOfElements().fork
        _                        <- addElementsFork.join
        _                        <- removeHalfOfElementsFork.join
        expected                  = (0 to sampleSize).filter(_ % 2 == 1).map(Wrapper(_)).toList
        actual                    = set.iterator.toList.sortBy(_.value)
      } yield assert(set.size)(equalTo(sampleSize / 2)) && assert(actual)(equalTo(expected)) && reachabilityFence(refs)
    } @@ withLiveClock,
    test("Dead references are removed from set") {
      val set = createSet[Wrapper[Int]]()
      set.add(Wrapper(1)) // dead ref

      val ref = Wrapper(2)
      set.add(ref) // ref from scope

      for {
        _ <- performGCsAndWait()
      } yield assert(set.size)(equalTo(1)) && assert(set.iterator.next())(equalTo(ref)) && reachabilityFence(ref)
    } @@ withLiveClock
  ) @@ parallel

  private def performGCsAndWait(): UIO[Unit] = for {
    _ <- performGC()
    _ <- ZIO.sleep(10.seconds)
    _ <- performGC()
    _ <- ZIO.sleep(10.seconds)
  } yield ()

  def performGC(): UIO[Unit]

  def createSet[V <: AnyRef](): mutable.Set[V]

  def createConcurrentQueue[V](): java.util.Queue[V]

  /**
   * Ensures that `element` is not GCed before tests end
   */
  def reachabilityFence(element: AnyRef): TestResult =
    TestResult(
      TestArrow.succeed(element.hashCode()) >>> TestArrow.succeed(true)
    )

}

object ConcurrentWeakHashSetAbstractSpec {

  final case class Wrapper[A](value: A) {
    override def toString: String = value.toString
  }

}
