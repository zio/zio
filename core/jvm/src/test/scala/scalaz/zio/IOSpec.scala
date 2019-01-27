package scalaz.zio

import org.scalacheck._
import org.specs2.ScalaCheck
import scala.collection.mutable
import scala.util.Try
import scalaz.zio.Exit.Cause.{ Die, Fail, Interrupt }

class IOSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec with GenIO with ScalaCheck {
  import Prop.forAll

  def is = "IOSpec".title ^ s2"""
   Generate a list of String and a f: String => IO[Throwable, Int]:
      `IO.foreach` returns the list of results. $t1
   Create a list of Strings and pass an f: String => IO[String, Int]:
      `IO.foreach` both evaluates effects and returns the list of Ints in the same order. $t2
   Create a list of String and pass an f: String => IO[String, Int]:
      `IO.foreach` fails with a NumberFormatException exception. $t3
   Create a list of Strings and pass an f: String => IO[String, Int]:
      `IO.foreachPar` returns the list of Ints in the same order. $t4
   Create an integer and an f: Int => String:
      `IO.bimap(f, identity)` maps an IO[Int, String] into an IO[String, String]. $t5
   Create a list of Ints and map with IO.point:
      `IO.collectAllPar` returns the list of Ints in the same order. $t6
   Create a list of Ints and map with IO.point:
      `IO.forkAll` returns the list of Ints in the same order. $t7
   Create a list of Strings and pass an f: String => IO[Nothing, Int]:
      `IO.collectAllParN` returns the list of Ints in the same order. $t8
   Create a list of Ints and pass an f: Int => IO[Nothing, Int]:
      `IO.foreachParN` returns the list of created Strings in the appropriate order. $t9
   Create a list of Ints:
      `IO.foldLeft` with a successful step function sums the list properly. $t10
   Create a non-empty list of Ints:
      `IO.foldLeft` with a failing step function returns a failed IO. $t11
   Check done lifts exit result into IO. $testDone
    """

  def functionIOGen: Gen[String => IO[Throwable, Int]] =
    Gen.function1[String, IO[Throwable, Int]](genSuccess[Throwable, Int])

  def listGen: Gen[List[String]] =
    Gen.listOfN(100, Gen.alphaNumStr)

  def t1 = forAll(functionIOGen, listGen) { (f, list) =>
    val res = unsafeRun(IO.foreach(list)(f))
    res must be size 100
    res must beAnInstanceOf[List[Int]]
  }

  def t2 = {
    val list    = List("1", "2", "3")
    val effects = new mutable.ListBuffer[String]
    val res     = unsafeRun(IO.foreach(list)(x => IO.sync(effects += x) *> IO.succeedLazy[Int](x.toInt)))
    (effects.toList, res) must be_===((list, List(1, 2, 3)))
  }

  def t3 = {
    val list = List("1", "h", "3")
    val res  = Try(unsafeRun(IO.foreach(list)(x => IO.succeedLazy[Int](x.toInt))))
    res must beAFailedTry.withThrowable[FiberFailure]
  }

  def t4 = {
    val list = List("1", "2", "3")
    val res  = unsafeRun(IO.foreachPar(list)(x => IO.succeedLazy[Int](x.toInt)))
    res must be_===(List(1, 2, 3))
  }

  def t5 = forAll { (i: Int) =>
    val res = unsafeRun(IO.fail[Int](i).bimap(_.toString, identity).attempt)
    res must_=== Left(i.toString)
  }

  def t6 = {
    val list = List(1, 2, 3).map(IO.succeedLazy[Int](_))
    val res  = unsafeRun(IO.collectAllPar(list))
    res must be_===(List(1, 2, 3))
  }

  def t7 = {
    val list = List(1, 2, 3).map(IO.succeedLazy[Int](_))
    val res  = unsafeRun(IO.forkAll(list).flatMap(_.join))
    res must be_===(List(1, 2, 3))
  }

  def t8 = {
    val list = List(1, 2, 3).map(IO.succeedLazy[Int](_))
    val res  = unsafeRun(IO.collectAllParN(2)(list))
    res must be_===(List(1, 2, 3))
  }

  def t9 = {
    val list = List(1, 2, 3)
    val res  = unsafeRun(IO.foreachParN(2)(list)(x => IO.succeedLazy(x.toString)))
    res must be_===(List("1", "2", "3"))
  }

  def t10 = forAll { (l: List[Int]) =>
    unsafeRun(IO.foldLeft(l)(0)((acc, el) => IO.succeed(acc + el))) must_=== unsafeRun(IO.succeed(l.sum))
  }

  def t11 = forAll { (l: List[Int]) =>
    l.size > 0 ==>
      (unsafeRunSync(IO.foldLeft(l)(0)((_, _) => IO.fail("fail"))) must_=== unsafeRunSync(IO.fail("fail")))
  }

  def testDone = {
    val error                         = new Error("something went wrong")
    val completed                     = Exit.succeed(1)
    val interrupted: Exit[Error, Int] = Exit.interrupt
    val terminated: Exit[Error, Int]  = Exit.die(error)
    val failed: Exit[Error, Int]      = Exit.fail(error)

    unsafeRun(IO.done(completed)) must_=== 1
    unsafeRun(IO.done(interrupted)) must throwA(FiberFailure(Interrupt))
    unsafeRun(IO.done(terminated)) must throwA(FiberFailure(Die(error)))
    unsafeRun(IO.done(failed)) must throwA(FiberFailure(Fail(error)))
  }
}
