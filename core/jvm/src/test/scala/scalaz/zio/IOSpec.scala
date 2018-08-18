package scalaz.zio

import org.scalacheck._
import org.specs2.ScalaCheck
import scalaz.zio.ExitResult.{ Completed, Failed, Terminated }

import scala.collection.mutable
import scala.util.Try

class IOSpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  import Prop.forAll

  def is = "IOSpec".title ^ s2"""
   Generate a list of String and a f: String => IO[Throwable, Int]:
      `IO.traverse` returns the list of results. $t1
   Create a list of Strings and pass an f: String => IO[String, Int]:
      `IO.traverse` both evaluates effects and returns the list of Ints in the same order. $t2
   Create a list of String and pass an f: String => IO[String, Int]:
      `IO.traverse` fails with a NumberFormatException exception. $t3
   Create a list of Strings and pass an f: String => IO[String, Int]:
      `IO.parTraverse` returns the list of Ints in the same order. $t4
   Create an integer and an f: Int => String:
      `IO.bimap(f, identity)` maps an IO[Int, String] into an IO[String, String]. $t5
   Create a list of Ints and map with IO.point:
      `IO.parAll` returns the list of Ints in the same order. $t6
   Create a list of Ints and map with IO.point:
      `IO.forkAll` returns the list of Ints in the same order. $t7
   Check done lifts exit result into IO. $testDone
   Retry on failure according to a provided strategy
       for a given number of times $retryN
    """

  def functionIOGen: Gen[String => IO[Throwable, Int]] =
    Gen.function1[String, IO[Throwable, Int]](genSuccess[Throwable, Int])

  def listGen: Gen[List[String]] =
    Gen.listOfN(100, Gen.alphaNumStr)

  def t1 = forAll(functionIOGen, listGen) { (f, list) =>
    val res = unsafeRun(IO.traverse(list)(f))
    res must be size 100
    res must beAnInstanceOf[List[Int]]
  }

  def t2 = {
    val list    = List("1", "2", "3")
    val effects = new mutable.ListBuffer[String]
    val res     = unsafeRun(IO.traverse(list)(x => IO.sync(effects += x) *> IO.point[Int](x.toInt)))
    (effects.toList, res) must be_===((list, List(1, 2, 3)))
  }

  def t3 = {
    val list = List("1", "h", "3")
    val res  = Try(unsafeRun(IO.traverse(list)(x => IO.point[Int](x.toInt))))
    res must beAFailedTry.withThrowable[NumberFormatException]
  }

  def t4 = {
    val list = List("1", "2", "3")
    val res  = unsafeRun(IO.parTraverse(list)(x => IO.point[Int](x.toInt)))
    res must be_===(List(1, 2, 3))
  }

  def t5 = forAll { (i: Int) =>
    val res = unsafeRun(IO.fail[Int](i).bimap(_.toString, identity).attempt)
    res must_=== Left(i.toString)
  }

  def t6 = {
    val list = List(1, 2, 3).map(IO.point[Int](_))
    val res  = unsafeRun(IO.parAll(list))
    res must be_===(List(1, 2, 3))
  }

  def t7 = {
    val list = List(1, 2, 3).map(IO.point[Int](_))
    val res  = unsafeRun(IO.forkAll(list).flatMap(_.join))
    res must be_===(List(1, 2, 3))
  }

  def testDone = {
    val error      = new Error("something went wrong")
    val completed  = Completed[Nothing, Int](1)
    val terminated = Terminated[Nothing, Int](error :: Nil)
    val failed     = Failed[Error, Int](error)

    unsafeRun(IO.done(completed)) must_=== 1
    unsafeRun(IO.done(terminated)) must throwA(error)
    unsafeRun(IO.done(failed)) must throwA(Errors.UnhandledError(error))
  }

  def retryCollect[E, A, E1 >: E, S](io: IO[E, A], retry: Retry[E1, S]): IO[Nothing, (Either[E1, A], List[S])] = {
    type State = retry.State

    def loop(state: State, ss: List[S]): IO[Nothing, (Either[E1, A], List[S])] =
      io.redeem(
        err =>
          retry
            .update(err, state)
            .flatMap(
              step =>
                if (!step.retry) IO.now((Left(err), ss))
                else loop(step.value, retry.value(step.value) :: ss)
          ),
        suc => IO.now((Right(suc), ss))
      )

    retry.initial.flatMap(s => loop(s, Nil)).map(x => (x._1, x._2.reverse))
  }

  def retryN = {
    val retried  = unsafeRun(retryCollect(IO.fail("Error"), Retry.retries[String](5)))
    val expected = (Left("Error"), List(1, 2, 3, 4, 5))
    retried must_=== expected
  }

}
