package scalaz.zio

import org.scalacheck._
import org.specs2.ScalaCheck
import scalaz.zio.Exit.Cause

import scala.collection.mutable
import scala.util.Try
import scalaz.zio.Exit.Cause.{ Die, Fail, Interrupt }

class IOSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  import Prop.forAll

  def is = "IOSpec".title ^ s2"""
   Generate a list of String and a f: String => Task[Int]:
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
   Create a list of Strings and pass an f: String => UIO[Int]:
      `IO.collectAllParN` returns the list of Ints in the same order. $t8
   Create a list of Ints and pass an f: Int => UIO[Int]:
      `IO.foreachParN` returns the list of created Strings in the appropriate order. $t9
   Create a list of Ints:
      `IO.foldLeft` with a successful step function sums the list properly. $t10
   Create a non-empty list of Ints:
      `IO.foldLeft` with a failing step function returns a failed IO. $t11
   Check done lifts exit result into IO. $testDone
   Check `when` executes correct branch only. $testWhen
   Check `whenM` executes condition effect and correct branch. $testWhenM
   Check `unsandbox` unwraps exception. $testUnsandbox
   Check `supervise` returns same value as IO.supervise. $testSupervise
   Check `flatten` method on IO[E, IO[E, String] returns the same IO[E, String] as `IO.flatten` does. $testFlatten
   Check `absolve` method on IO[E, Either[E, A]] returns the same IO[E, Either[E, String]] as `IO.absolve` does. $testAbsolve
   Check `raceAll` method returns the same IO[E, A] as `IO.raceAll` does. $testRaceAll
    """

  def functionIOGen: Gen[String => Task[Int]] =
    Gen.function1[String, Task[Int]](genSuccess[Throwable, Int])

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
    val res     = unsafeRun(IO.foreach(list)(x => IO.effectTotal(effects += x) *> IO.succeedLazy[Int](x.toInt)))
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
    val res = unsafeRun(IO.fail[Int](i).bimap(_.toString, identity).either)
    res must_=== Left(i.toString)
  }

  def t6 = {
    val list = List(1, 2, 3).map(IO.succeedLazy[Int](_))
    val res  = unsafeRun(IO.collectAllPar(list))
    res must be_===(List(1, 2, 3))
  }

  def t7 = {
    val list = List(1, 2, 3).map(IO.succeedLazy[Int](_))
    val res  = unsafeRun(IO.forkAll(list).flatMap[Any, Nothing, List[Int]](_.join))
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

  def testWhen =
    unsafeRun(
      for {
        effectRef <- Ref.make(0)
        _         <- effectRef.set(1).when(false)
        val1      <- effectRef.get
        _         <- effectRef.set(2).when(true)
        val2      <- effectRef.get
        failure   = new Exception("expected")
        _         <- IO.fail(failure).when(false)
        failed    <- IO.fail(failure).when(true).either
      } yield
        (val1 must_=== 0) and
          (val2 must_=== 2) and
          (failed must beLeft(failure))
    )

  def testWhenM =
    unsafeRun(
      for {
        effectRef      <- Ref.make(0)
        conditionRef   <- Ref.make(0)
        conditionTrue  = conditionRef.update(_ + 1).map(_ => true)
        conditionFalse = conditionRef.update(_ + 1).map(_ => false)
        _              <- effectRef.set(1).whenM(conditionFalse)
        val1           <- effectRef.get
        conditionVal1  <- conditionRef.get
        _              <- effectRef.set(2).whenM(conditionTrue)
        val2           <- effectRef.get
        conditionVal2  <- conditionRef.get
        failure        = new Exception("expected")
        _              <- IO.fail(failure).whenM(conditionFalse)
        failed         <- IO.fail(failure).whenM(conditionTrue).either
      } yield
        (val1 must_=== 0) and
          (conditionVal1 must_=== 1) and
          (val2 must_=== 2) and
          (conditionVal2 must_=== 2) and
          (failed must beLeft(failure))
    )

  def testUnsandbox = {
    val failure: IO[Exit.Cause[Exception], String] = IO.fail(Cause.fail(new Exception("fail")))
    val success: IO[Exit.Cause[Any], Int]          = IO.succeed(100)
    unsafeRun(for {
      message <- failure.unsandbox.foldM(e => IO.succeed(e.getMessage), _ => IO.succeed("unexpected"))
      result  <- success.unsandbox
    } yield (message must_=== "fail") and (result must_=== 100))
  }

  def testSupervise = {
    val io = IO.effectTotal("supercalifragilisticexpialadocious")
    unsafeRun(for {
      supervise1 <- io.supervise
      supervise2 <- IO.supervise(io)
    } yield supervise1 must ===(supervise2))
  }

  def testFlatten = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      flatten1 <- IO.succeedLazy(IO.succeedLazy(str)).flatten
      flatten2 <- IO.flatten(IO.succeedLazy(IO.succeedLazy(str)))
    } yield flatten1 must ===(flatten2))
  }

  def testAbsolve = forAll(Gen.alphaStr) { str =>
    val ioEither: UIO[Either[Nothing, String]] = IO.succeed(Right(str))
    unsafeRun(for {
      abs1 <- ioEither.absolve
      abs2 <- IO.absolve(ioEither)
    } yield abs1 must ===(abs2))
  }

  def testRaceAll = {
    val io  = IO.effectTotal("supercalifragilisticexpialadocious")
    val ios = List.empty[UIO[String]]
    unsafeRun(for {
      race1 <- io.raceAll(ios)
      race2 <- IO.raceAll(io, ios)
    } yield race1 must ===(race2))
  }

  def acquireTypeInferenceOnE = {
    type Resource = Unit
    val acquire: ZIO[Any, RuntimeException, Resource] = ???
    val release: Resource => ZIO[Any, Nothing, String] = ???
    val use: Resource => ZIO[Any, Exception, Int] = ???
    acquire.bracket(release, use): ZIO[Any, Throwable, Int]
    ZIO.bracket(acquire, release, use): ZIO[Any, Throwable, Int]
  }

  def acquireTypeInferenceOnE2 = {
    type Resource = Unit
    val acquire: ZIO[Any, Exception, Resource] = ???
    val release: Resource => ZIO[Any, Nothing, String] = ???
    val use: Resource => ZIO[Any, RuntimeException, Int] = ???
    acquire.bracket(release, use): ZIO[Any, Throwable, Int]
    ZIO.bracket(acquire, release, use): ZIO[Any, Throwable, Int]
  }

  def zioAcquireTypeInferenceOnR = {
    type Resource = Unit
    val acquire: ZIO[Unit, Nothing, Resource] = ???
    val release: Resource => ZIO[Any, Nothing, String] = ???
    val use: Resource => ZIO[Any, Nothing, Int] = ???
    acquire.bracket(release, use): ZIO[Unit, Nothing, Int]
    ZIO.bracket(acquire, release, use): ZIO[Unit, Nothing, Int]
  }

  def zioAcquireTypeInferenceOnR2 = {
    type Resource = Unit
    val acquire: ZIO[Any, Nothing, Resource] = ???
    val release: Resource => ZIO[Unit, Nothing, String] = ???
    val use: Resource => ZIO[Any, Nothing, Int] = ???
    acquire.bracket(release, use): ZIO[Unit, Nothing, Int]
    ZIO.bracket(acquire, release, use): ZIO[Unit, Nothing, Int]
  }

  def zioAcquireTypeInferenceOnR3 = {
    type Resource = Unit
    val acquire: ZIO[Any, Nothing, Resource] = ???
    val release: Resource => ZIO[Any, Nothing, String] = ???
    val use: Resource => ZIO[Unit, Nothing, Int] = ???
    acquire.bracket(release, use): ZIO[Unit, Nothing, Int]
    ZIO.bracket(acquire, release, use): ZIO[Unit, Nothing, Int]
  }
}
