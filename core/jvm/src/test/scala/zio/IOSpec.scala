package zio

import org.scalacheck._
import org.specs2.ScalaCheck
import org.specs2.execute.Result
import org.specs2.matcher.describe.Diffable

import scala.collection.mutable
import scala.util.Try
import zio.Cause.{ die, fail, interrupt, Both }

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
   Check non-`memoize`d IO[E, A] returns new instances on repeated calls due to referential transparency. $testNonMemoizationRT
   Check `memoize` method on IO[E, A] returns the same instance on repeated calls. $testMemoization
   Check `raceAll` method returns the same IO[E, A] as `IO.raceAll` does. $testRaceAll
   Check `zipPar` method does not swallow exit causes of loser. $testZipParInterupt
   Check `zipPar` method does not report failure when interrupting loser after it succeeded. $testZipParSucceed
   Check `orElse` method does not recover from defects. $testOrElseDefectHandling
   Check uncurried `bracket`. $testUncurriedBracket
   Check uncurried `bracket_`. $testUncurriedBracket_
   Check uncurried `bracketExit`. $testUncurriedBracketExit
   Check `bracketExit` error handling. $testBracketExitErrorHandling
   Check `foreach_` runs effects in order. $testForeach_Order
   Check `foreach_` can be run twice. $testForeach_Twice
   Check `foreachPar_` runs all effects. $testForeachPar_Full
   Check `foreachParN_` runs all effects. $testForeachParN_Full
   Check `filterOrElse` returns checked failure from held value $testFilterOrElse
   Check `filterOrElse_` returns checked failure ignoring value $testFilterOrElse_
   Check `filterOrFail` returns failure ignoring value $testFilterOrFail
   Check `collect` returns failure ignoring value $testCollect
   Check `collectM` returns failure ignoring value $testCollectM
   Check `reject` returns failure ignoring value $testReject
   Check `rejectM` returns failure ignoring value $testRejectM
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

  implicit val d
    : Diffable[Either[String, Nothing]] = Diffable.eitherDiffable[String, Nothing] //    TODO: Dotty has ambiguous implicits
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

  def t10: Prop = forAll { (l: List[Int]) =>
    unsafeRun(IO.foldLeft(l)(0)((acc, el) => IO.succeed(acc + el))) must_=== unsafeRun(IO.succeed(l.sum))
  }

  val ig = Gen.chooseNum(Int.MinValue, Int.MaxValue)
  val g  = Gen.nonEmptyListOf(ig) //    TODO: Dotty has ambiguous implicits
  def t11: Prop = forAll(g) { (l: List[Int]) =>
    (unsafeRunSync(IO.foldLeft(l)(0)((_, _) => IO.fail("fail"))) must_=== unsafeRunSync(IO.fail("fail")))
  }

  def testDone = {
    val error                         = new Error("something went wrong")
    val completed                     = Exit.succeed(1)
    val interrupted: Exit[Error, Int] = Exit.interrupt
    val terminated: Exit[Error, Int]  = Exit.die(error)
    val failed: Exit[Error, Int]      = Exit.fail(error)

    unsafeRun(IO.done(completed)) must_=== 1
    unsafeRunSync(IO.done(interrupted)) must_=== Exit.interrupt
    unsafeRunSync(IO.done(terminated)) must_=== Exit.die(error)
    unsafeRunSync(IO.done(failed)) must_=== Exit.fail(error)
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
      } yield (val1 must_=== 0) and
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
      } yield (val1 must_=== 0) and
        (conditionVal1 must_=== 1) and
        (val2 must_=== 2) and
        (conditionVal2 must_=== 2) and
        (failed must beLeft(failure))
    )

  def testUnsandbox = {
    val failure: IO[Cause[Exception], String] = IO.fail(fail(new Exception("fail")))
    val success: IO[Cause[Any], Int]          = IO.succeed(100)
    unsafeRun(for {
      message <- failure.unsandbox.foldM(e => IO.succeed(e.getMessage), _ => IO.succeed("unexpected"))
      result  <- success.unsandbox
    } yield (message must_=== "fail") and (result must_=== 100))
  }

  def testSupervise = {
    val io = IO.effectTotal("supercalifragilisticexpialadocious")
    unsafeRun(for {
      supervise1 <- io.interruptChildren
      supervise2 <- IO.interruptChildren(io)
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

  def testNonMemoizationRT = forAll(Gen.alphaStr) { str =>
    val io: UIO[Option[String]] = IO.succeedLazy(Some(str)) // using `Some` for object allocation
    unsafeRun(
      (io <*> io)
        .map(tuple => tuple._1 must not beTheSameAs (tuple._2))
    )
  }

  def testMemoization = forAll(Gen.alphaStr) { str =>
    val ioMemo: UIO[UIO[Option[String]]] = IO.succeedLazy(Some(str)).memoize // using `Some` for object allocation
    unsafeRun(
      ioMemo
        .flatMap(io => io <*> io)
        .map(tuple => tuple._1 must beTheSameAs(tuple._2))
    )
  }

  def testRaceAll = {
    val io  = IO.effectTotal("supercalifragilisticexpialadocious")
    val ios = List.empty[UIO[String]]
    unsafeRun(for {
      race1 <- io.raceAll(ios)
      race2 <- IO.raceAll(io, ios)
    } yield race1 must ===(race2))
  }

  def testZipParInterupt = {
    val io = ZIO.interrupt.zipPar(IO.interrupt)
    unsafeRunSync(io) must_=== Exit.Failure(Both(interrupt, interrupt))
  }

  def testZipParSucceed = {
    val io = ZIO.interrupt.zipPar(IO.succeed(1))
    unsafeRun(io.sandbox.either).left.map(_.interrupted) must_=== Left(true)
  }

  def testOrElseDefectHandling = {
    val ex = new Exception("Died")

    unsafeRun {
      for {
        plain <- (ZIO.die(ex) <> IO.unit).run
        both  <- (ZIO.halt(Cause.Both(interrupt, die(ex))) <> IO.unit).run
        thn   <- (ZIO.halt(Cause.Then(interrupt, die(ex))) <> IO.unit).run
        fail  <- (ZIO.fail(ex) <> IO.unit).run
      } yield (plain must_=== Exit.die(ex))
        .and(both must_=== Exit.die(ex))
        .and(thn must_=== Exit.die(ex))
        .and(fail must_=== Exit.succeed(()))
    }
  }

  def testUncurriedBracket =
    unsafeRun {
      for {
        release  <- Ref.make(false)
        result   <- ZIO.bracket(IO.succeed(42), (_: Int) => release.set(true), (a: Int) => ZIO.succeedLazy(a + 1))
        released <- release.get
      } yield (result must_=== 43) and (released must_=== true)
    }

  def testUncurriedBracket_ =
    unsafeRun {
      for {
        release  <- Ref.make(false)
        result   <- IO.succeed(42).bracket_(release.set(true), ZIO.succeedLazy(0))
        released <- release.get
      } yield (result must_=== 0) and (released must_=== true)
    }

  def testUncurriedBracketExit =
    unsafeRun {
      for {
        release <- Ref.make(false)
        result <- ZIO.bracketExit(
                   IO.succeed(42),
                   (_: Int, _: Exit[_, _]) => release.set(true),
                   (_: Int) => IO.succeed(0L)
                 )
        released <- release.get
      } yield (result must_=== 0L) and (released must_=== true)
    }

  def testBracketExitErrorHandling = {
    val releaseDied = new RuntimeException("release died")
    val exit: Exit[String, Int] = unsafeRunSync {
      ZIO.bracketExit[Any, String, Int, Int](
        ZIO.succeed(42),
        (_, _) => ZIO.die(releaseDied),
        _ => ZIO.fail("use failed")
      )
    }

    exit.fold[Result](
      cause => (cause.failures must_=== List("use failed")) and (cause.defects must_=== List(releaseDied)),
      value => failure(s"unexpectedly completed with value $value")
    )
  }

  object UncurriedBracketCompilesRegardlessOrderOfEAndRTypes {
    class A
    class B
    class R
    class R1 extends R
    class R2 extends R1
    class E
    class E1 extends E

    def infersEType1: ZIO[R, E, B] = {
      val acquire: ZIO[R, E, A]            = ???
      val release: A => ZIO[R, Nothing, _] = ???
      val use: A => ZIO[R, E1, B]          = ???
      ZIO.bracket(acquire, release, use)
    }

    def infersEType2: ZIO[R, E, B] = {
      val acquire: ZIO[R, E1, A]           = ???
      val release: A => ZIO[R, Nothing, _] = ???
      val use: A => ZIO[R, E, B]           = ???
      ZIO.bracket(acquire, release, use)
    }

    def infersRType1: ZIO[R2, E, B] = {
      val acquire: ZIO[R, E, A]             = ???
      val release: A => ZIO[R1, Nothing, _] = ???
      val use: A => ZIO[R2, E, B]           = ???
      ZIO.bracket(acquire, release, use)
    }

    def infersRType2: ZIO[R2, E, B] = {
      val acquire: ZIO[R2, E, A]            = ???
      val release: A => ZIO[R1, Nothing, _] = ???
      val use: A => ZIO[R, E, B]            = ???
      ZIO.bracket(acquire, release, use)
    }

    def infersRType3: ZIO[R2, E, B] = {
      val acquire: ZIO[R1, E, A]            = ???
      val release: A => ZIO[R2, Nothing, _] = ???
      val use: A => ZIO[R, E, B]            = ???
      ZIO.bracket(acquire, release, use)
    }
  }

  def testForeach_Order = {
    val as = List(1, 2, 3, 4, 5)
    val r = unsafeRun {
      for {
        ref <- Ref.make(List.empty[Int])
        _   <- ZIO.foreach_(as)(a => ref.update(_ :+ a))
        rs  <- ref.get
      } yield rs
    }
    r must_== as
  }

  def testForeach_Twice = {
    val as = List(1, 2, 3, 4, 5)
    val r = unsafeRun {
      for {
        ref <- Ref.make(0)
        zio = ZIO.foreach_(as)(a => ref.update(_ + a))
        _   <- zio
        _   <- zio
        sum <- ref.get
      } yield sum
    }
    r must_=== 30
  }

  def testForeachPar_Full = {
    val as = Seq(1, 2, 3, 4, 5)
    val r = unsafeRun {
      for {
        ref <- Ref.make(Seq.empty[Int])
        _   <- ZIO.foreachPar_(as)(a => ref.update(_ :+ a))
        rs  <- ref.get
      } yield rs
    }
    r must have length as.length
    r must containTheSameElementsAs(as)
  }

  def testForeachParN_Full = {
    val as = Seq(1, 2, 3, 4, 5)
    val r = unsafeRun {
      for {
        ref <- Ref.make(Seq.empty[Int])
        _   <- ZIO.foreachParN_(2)(as)(a => ref.update(_ :+ a))
        rs  <- ref.get
      } yield rs
    }
    r must have length as.length
    r must containTheSameElementsAs(as)
  }

  def testFilterOrElse = {
    val goodCase = unsafeRun(
      exactlyOnce(0)(_.filterOrElse(_ == 0)(a => ZIO.fail(s"$a was not 0"))).sandbox.either
    ) must_=== Right(0)

    val badCase = unsafeRun(
      exactlyOnce(1)(_.filterOrElse(_ == 0)(a => ZIO.fail(s"$a was not 0"))).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("1 was not 0"))

    goodCase and badCase
  }

  def testFilterOrElse_ = {
    val goodCase = unsafeRun(
      exactlyOnce(0)(_.filterOrElse_(_ == 0)(ZIO.fail("Predicate failed!"))).sandbox.either
    ) must_=== Right(0)

    val badCase = unsafeRun(
      exactlyOnce(1)(_.filterOrElse_(_ == 0)(ZIO.fail("Predicate failed!"))).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("Predicate failed!"))

    goodCase and badCase
  }

  def testFilterOrFail = {
    val goodCase = unsafeRun(
      exactlyOnce(0)(_.filterOrFail(_ == 0)("Predicate failed!")).sandbox.either
    ) must_=== Right(0)

    val badCase = unsafeRun(
      exactlyOnce(1)(_.filterOrFail(_ == 0)("Predicate failed!")).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("Predicate failed!"))

    goodCase and badCase
  }

  def testCollect = {
    val goodCase = unsafeRun(
      exactlyOnce(0)(_.collect(s"value was not 0")({ case v @ 0 => v })).sandbox.either
    ) must_=== Right(0)

    val badCase = unsafeRun(
      exactlyOnce(1)(_.collect(s"value was not 0")({ case v @ 0 => v })).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("value was not 0"))

    goodCase and badCase
  }

  def testCollectM = {
    val goodCase = unsafeRun(
      exactlyOnce(0)(_.collectM("Predicate failed!")({ case v @ 0 => ZIO.succeed(v) })).sandbox.either
    ) must_=== Right(0)

    val partialBadCase = unsafeRun(
      exactlyOnce(0)(_.collectM("Predicate failed!")({ case v @ 0 => ZIO.fail("Partial failed!") })).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("Partial failed!"))

    val badCase = unsafeRun(
      exactlyOnce(1)(_.collectM("Predicate failed!")({ case v @ 0 => ZIO.succeed(v) })).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("Predicate failed!"))

    goodCase and partialBadCase and badCase
  }

  def testReject = {
    val goodCase = unsafeRun(
      exactlyOnce(0)(_.reject({ case v if v != 0 => "Partial failed!" })).sandbox.either
    ) must_=== Right(0)

    val badCase = unsafeRun(
      exactlyOnce(1)(_.reject({ case v if v != 0 => "Partial failed!" })).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("Partial failed!"))

    goodCase and badCase
  }

  def testRejectM = {
    val goodCase = unsafeRun(
      exactlyOnce(0)(_.rejectM({ case v if v != 0 => ZIO.succeed("Partial failed!") })).sandbox.either
    ) must_=== Right(0)

    val partialBadCase = unsafeRun(
      exactlyOnce(1)(_.rejectM({ case v if v != 0 => ZIO.fail("Partial failed!") })).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("Partial failed!"))

    val badCase = unsafeRun(
      exactlyOnce(1)(_.rejectM({ case v if v != 0 => ZIO.fail("Partial failed!") })).sandbox.either
    ).left.map(_.failureOrCause) must_=== Left(Left("Partial failed!"))

    goodCase and partialBadCase and badCase
  }
}
