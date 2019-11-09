package zio
import zio.test._
import ZIOSpecJvmUtils._
import zio.random.Random
import Assertion._

import scala.collection.mutable

object ZIOSpecJvm extends ZIOBaseSpec (
  suite("ZIOSpecJvm") (
    testM("Generate a list of String and a f: String => Task[Int]: `IO.foreach` returns the list of results") {
      checkAllM(functionIOGen, listGen) { (f, list) =>
        val res = IO.foreach(list)(f)
        assertM(res,  isSubtype[List[Int]](anything) && hasSize(equalTo(100)))
      }
    },
    testM("Create a list of Strings and pass an f: String => IO[String, Int] " +
      "`IO.foreach` both evaluates effects and returns the list of Ints in the same order") {
      val list    = List("1", "2", "3")
      for {
        ref <- Ref.make(List.empty[String])
        res     <- IO.foreach(list) (x => ref.update(_ :+ x) *> IO.effectTotal[Int](x.toInt))
        effects <- ref.get
      } yield assert(effects, equalTo(list)) && assert(res, equalTo(List(1, 2, 3)))
    }
  )
)
object ZIOSpecJvmUtils {

  def functionIOGen: Gen[Random with Sized, String => Task[Int]] =
    Gen.function[Random with Sized, String, Task[Int]](Gen.successes(Gen.anyInt))

  def listGen: Gen[Random with Sized, List[String]] =
    Gen.listOfN(100)(Gen.alphaNumericStr)
}
