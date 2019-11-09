package zio
import zio.test._
import ZIOSpecJvmUtils._
import zio.random.Random
import Assertion._

object ZIOSpecJvm extends ZIOBaseSpec (
  suite("ZIOSpecJvm") {
    testM("IO.foreach` returns the list of results") {
      checkAllM(functionIOGen, listGen) { (f, list) =>
        val res = IO.foreach(list)(f)
        assertM(res,  isSubtype[List[Int]](anything) && hasSize(equalTo(100)))
      }
    }
  }
)
object ZIOSpecJvmUtils {

  def functionIOGen: Gen[Random with Sized, String => Task[Int]] =
    Gen.function[Random with Sized, String, Task[Int]](Gen.successes(Gen.anyInt))

  def listGen: Gen[Random with Sized, List[String]] =
    Gen.listOfN(100)(Gen.alphaNumericStr)
}
