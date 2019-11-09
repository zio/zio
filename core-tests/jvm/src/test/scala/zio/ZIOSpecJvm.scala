package zio
import zio.ZIOSpecJvmUtils._
import zio.random.Random
import zio.test.Assertion._
import zio.test._

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
    }
  )
)
object ZIOSpecJvmUtils {

  def functionIOGen: Gen[Random with Sized, String => Task[Int]] =
    Gen.function[Random with Sized, String, Task[Int]](Gen.successes(Gen.anyInt))

  def listGen: Gen[Random with Sized, List[String]] =
    Gen.listOfN(100)(Gen.alphaNumericStr)
}
