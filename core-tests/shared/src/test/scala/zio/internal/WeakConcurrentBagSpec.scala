package zio.internal

import zio.test._
import zio.test.TestAspect.flaky
import zio.ZIOBaseSpec
import java.lang.ref.WeakReference

object WeakConcurrentBagSpec extends ZIOBaseSpec {
  def spec =
    suite("WeakConcurrentBagSpec") {
      test("size of singleton bag") {
        val bag = WeakConcurrentBag[String](10)

        val value = "foo"

        bag.add(value)

        assertTrue(bag.size == 1)
      } +
        test("iteration over 100 (buckets: 100)") {
          val bag = WeakConcurrentBag[String](100)

          var hard = Set.empty[String]

          (1 to 100).map(_.toString).foreach { str =>
            hard = hard + str

            bag.add(str)
          }

          assertTrue((bag.size == 100) && (bag.iterator.toSet == hard))
        } +
        test("manual gc") {
          val bag = WeakConcurrentBag[String](100)

          var hard = Map.empty[Int, WeakReference[String]]

          (1 to 100).foreach { int =>
            val str = int.toString

            val ref = bag.add(str)

            hard = hard + (int -> ref)
          }

          (1 to 100).foreach { i =>
            if (i % 2 == 0) hard(i).clear()
          }

          bag.gc()

          assertTrue(bag.size == 50)
        } +
        test("auto gc") {
          val bag = WeakConcurrentBag[String](100)

          (1 to 10000).foreach { int =>
            val ref = bag.add(int.toString)

            ref.clear()
          }

          assertTrue(bag.size < 100)
        } @@ flaky
    }
}
