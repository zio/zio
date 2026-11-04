package zio.internal

import zio.test._
import zio.test.TestAspect.{flaky, jvmOnly}
import zio.{ZIO, ZIOBaseSpec}

object WeakConcurrentBagSpec extends ZIOBaseSpec {
  final case class Wrapper[A](value: A)

  def spec =
    suite("WeakConcurrentBagSpec") {
      test("size of singleton bag") {
        val bag = WeakConcurrentBag[Wrapper[String]](10)

        val value = Wrapper("foo")

        bag.add(value)

        assertTrue(bag.size == 1)
      } +
        test("iteration over 100 (nursery size: 100)") {
          val bag = WeakConcurrentBag[Wrapper[String]](100)

          var hard = Set.empty[Wrapper[String]]

          (1 to 100).map(int => Wrapper(int.toString)).foreach { str =>
            hard = hard + str

            bag.add(str)
          }

          assertTrue((bag.size == 100) && (bag.iterator.toSet == hard))
        } +
        test("manual gc") {
          val bag = WeakConcurrentBag[Wrapper[String]](100)

          val hard = scala.collection.mutable.Map.empty[Int, Wrapper[String]]

          (1 to 100).foreach { int =>
            val str = Wrapper(int.toString)

            bag.add(str)

            hard.update(int, str)
          }

          bag.graduate()

          (1 to 100).foreach { i =>
            if (i % 2 == 0) hard.remove(i)
          }

          System.gc()
          bag.gc()

          assertTrue(bag.size == 50)
        } @@ flaky @@ jvmOnly +
        test("auto gc") {
          val bag = WeakConcurrentBag[Wrapper[String]](100)

          (1 to 10000).foreach { _ =>
            val str = Wrapper(scala.util.Random.nextString(10))

            bag.add(str)
          }

          System.gc()

          bag.graduate()

          assertTrue(bag.size <= 100)
        } @@ flaky @@ jvmOnly +
        test("graduating 10000 elements under concurrency does not fail") {
          val bag = WeakConcurrentBag[Wrapper[Int]](100)
          for {
            _ <- ZIO.foreachParDiscard(1 to 10000)(i => ZIO.succeed(bag.add(Wrapper(i))))
            _ <- ZIO.succeed(bag.graduate())
          } yield assertTrue(bag.size > 0)
        } +
        test("forking 10000 fibers does not fail in WeakConcurrentBag") {
          for {
            fibers <- ZIO.foreach(1 to 10000)(_ => ZIO.never.forkDaemon)
            _      <- ZIO.foreachDiscard(fibers)(_.interrupt)
          } yield assertCompletes
        }
    }
}
