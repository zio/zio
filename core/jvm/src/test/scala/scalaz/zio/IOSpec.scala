package scalaz.zio

import org.scalacheck._
import org.specs2.Specification
import org.specs2.ScalaCheck

import scala.util.Try

class IOSpec extends Specification with GenIO with RTS with ScalaCheck {
  import Prop.forAll

  def is = "IOSpec".title ^ s2"""
   Generate a list of String and a f: String => IO[Throwable, Int]:
      `IO.traverse` returns the list of results. $t1
   Create a list of Strings and pass an f: String => IO[String, Int]:
      `IO.traverse` returns the list of Ints in the same order. $t2
   Create a list of String and pass an f: String => IO[String, Int]:
      `IO.traverse` fails with a NumberFormatException exception. $t3
   Create a list of Strings and pass an f: String => IO[String, Int]:
      `IO.parTraverse` returns the list of Ints in any order. $t4
    """

  def functionIOGen: Gen[String => IO[Throwable, Int]] =
    Gen.function1[String, IO[Throwable, Int]](genSuccess[Throwable, Int])

  def listGen: Gen[List[String]] =
    Gen.listOfN(100, Gen.alphaNumStr)

  def t1 = forAll(functionIOGen, listGen) { (f, list) =>
    val res = unsafePerformIO(IO.traverse(list)(f))
    res must be size 100
    res must beAnInstanceOf[List[Int]]
  }

  def t2 = {
    val list = List("1", "2", "3")
    val res  = unsafePerformIO(IO.traverse(list)(x => IO.point[String, Int](x.toInt)))
    res must be_===(List(1, 2, 3))
  }

  def t3 = {
    val list = List("1", "h", "3")
    val res  = Try(unsafePerformIO(IO.traverse(list)(x => IO.point[String, Int](x.toInt))))
    res must beAFailedTry.withThrowable[NumberFormatException]
  }

  def t4 = {
    val list = List("1", "2", "3")
    val res  = unsafePerformIO(IO.parTraverse(list)(x => IO.point[String, Int](x.toInt)))
    res must containTheSameElementsAs(List(1, 2, 3))
  }

}
