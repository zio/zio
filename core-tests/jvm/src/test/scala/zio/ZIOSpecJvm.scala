package zio
import zio.ZIOSpecJvmUtils._
import zio.random.Random
import zio.test.Assertion.{equalTo, _}
import zio.test.{assertM, _}

object ZIOSpecJvm extends ZIOBaseSpec (
  suite("ZIOSpecJvm") (
    testM("`IO.foreach` returns the list of results") {
      checkAllM(functionIOGen, listGen) { (f, list) =>
        val res = IO.foreach(list)(f)
        assertM(res,  isSubtype[List[Int]](anything) && hasSize(equalTo(100)))
      }
    },
    testM("`IO.foreach` both evaluates effects and returns the list of Ints in the same order") {
      val list    = List("1", "2", "3")
      for {
        ref <- Ref.make(List.empty[String])
        res     <- IO.foreach(list) (x => ref.update(_ :+ x) *> IO.effectTotal[Int](x.toInt))
        effects <- ref.get
      } yield assert(effects, equalTo(list)) && assert(res, equalTo(List(1, 2, 3)))
    },
    testM("`IO.foreach` fails with a NumberFormatException exception") {
      val list = List("1", "h", "3")
      val res  = IO.foreach(list)(x => IO.effectTotal[Int](x.toInt))
      assertM(res.run, dies(isSubtype[NumberFormatException](anything)) )
    },
    testM("`IO.foreachPar` returns the list of Ints in the same order") {
      val list = List("1", "2", "3")
      val res  = IO.foreachPar(list)(x => IO.effectTotal[Int](x.toInt))
      assertM(res, equalTo(List(1, 2, 3)))
    },
    testM("For f: Int => String: `IO.bimap(f, identity)` maps an IO[Int, String] into an IO[String, String]") {
      checkM(Gen.anyInt) { i =>
        val res = IO.fail(i).bimap(_.toString, identity).either
        assertM(res, isLeft(equalTo(i.toString)))
      }
    },
    testM("`IO.collectAllPar` returns the list in the same order") {
      val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
      val res  = IO.collectAllPar(list)
      assertM(res, equalTo(List(1, 2, 3)))
    },
    testM("`IO.forkAll` returns the list of Ints in the same order") {
      val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
      val res  = IO.forkAll(list).flatMap[Any, Nothing, List[Int]](_.join)
      assertM(res, equalTo(List(1, 2, 3)))
    },
    testM("`IO.collectAllParN` returns the list of Ints in the same order") {
      val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
      val res  = IO.collectAllParN(2)(list)
      assertM(res, equalTo(List(1, 2, 3)))
    },
    testM("`IO.foreachParN` returns the list of created Strings in the appropriate order") {
      val list = List(1, 2, 3)
      val res  = IO.foreachParN(2)(list)(x => IO.effectTotal(x.toString))
      assertM(res, equalTo(List("1", "2", "3")))
    },
    testM("`IO.foldLeft` with a successful step function sums the list properly") {
      checkM(Gen.listOf(Gen.anyInt)) { l =>
        val res = IO.foldLeft(l)(0)((acc, el) => IO.succeed(acc + el))
        assertM(res, equalTo(l.sum))
      }
    },
    testM("`IO.foldLeft` with a failing step function returns a failed IO") {
      val genNonEmpty = Gen.anyInt.zipWith(Gen.listOf(Gen.anyInt))(_ :: _)
      checkM(genNonEmpty) { l =>
        val res = IO.foldLeft(l)(0)((_, _) => IO.fail("fail"))
        assertM(res.run, fails(equalTo("fail")))
      }
    },
    testM("Check done lifts exit result into IO") {

      val error = exampleError
      val completed = Exit.succeed(1)
      val interrupted: Exit[Error, Int] = Exit.interrupt
      val terminated: Exit[Error, Int] = Exit.die(error)
      val failed: Exit[Error, Int] = Exit.fail(error)

      assertM(IO.done(completed), equalTo(1)) &&
        assertM(IO.done(interrupted).run, isInterrupted) &&
        assertM(IO.done(terminated).run, dies(equalTo(error))) &&
        assertM(IO.done(failed).run, fails(equalTo(error)))
    }
  )
)
object ZIOSpecJvmUtils {

  def functionIOGen: Gen[Random with Sized, String => Task[Int]] =
    Gen.function[Random with Sized, String, Task[Int]](Gen.successes(Gen.anyInt))

  def listGen: Gen[Random with Sized, List[String]] =
    Gen.listOfN(100)(Gen.alphaNumericStr)

  val exampleError = new Error("something went wrong")
}
