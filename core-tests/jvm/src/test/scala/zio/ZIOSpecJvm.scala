package zio

import org.scalacheck._
import org.specs2.ScalaCheck

import scala.collection.mutable
import scala.concurrent.duration.{ Duration => SDuration }
import scala.util.Try
import java.util.concurrent.TimeUnit
import zio.Cause.{ die, fail, interrupt }
import zio.duration._
import zio.syntax._
import zio.test.environment.TestClock

class ZIOSpecJvm(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  import Prop.forAll

  override val DefaultTimeout: SDuration = SDuration(60, TimeUnit.SECONDS)

  def is = "ZIOSpecJvm".title ^ s2"""
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
   Check `catchSomeCause` catches matching cause $testCatchSomeCauseMatch
   Check `catchSomeCause` halts if cause doesn't match $testCatchSomeCauseNoMatch
   Check `when` executes correct branch only. $testWhen
   Check `whenM` executes condition effect and correct branch. $testWhenM
   Check `whenCase` executes correct branch only. $testWhenCase
   Check `whenCaseM` executes condition effect and correct branch. $testWhenCaseM
   Check `unsandbox` unwraps exception. $testUnsandbox
   Check `flatten` method on IO[E, IO[E, String] returns the same IO[E, String] as `IO.flatten` does. $testFlatten
   Check `absolve` method on IO[E, Either[E, A]] returns the same IO[E, Either[E, String]] as `IO.absolve` does. $testAbsolve
   Check non-`memoize`d IO[E, A] returns new instances on repeated calls due to referential transparency. $testNonMemoizationRT
   Check `memoize` method on IO[E, A] returns the same instance on repeated calls. $testMemoization
   Check `cached` method on IO[E, A] returns new instances after duration. $testCached
   Check `raceAll` method returns the same IO[E, A] as `IO.raceAll` does. $testRaceAll
   Check `firstSuccessOf` method returns the same IO[E, A] as `IO.firstSuccessOf` does. $testfirstSuccessOf
   Check `zipPar` method does not swallow exit causes of loser. $testZipParInterupt
   Check `zipPar` method does not report failure when interrupting loser after it succeeded. $testZipParSucceed
   Check `orElse` method does not recover from defects. $testOrElseDefectHandling
   Check `eventually` method succeeds eventually. $testEventually
   Check `some` method extracts the value from Some. $testSomeOnSomeOption
   Check `some` method fails on None. $testSomeOnNoneOption
   Check `some` method fails when given an exception. $testSomeOnException
   Check `someOrFail` method extracts the optional value. $testSomeOrFailExtractOptionalValue
   Check `someOrFail` method fails when given a None. $testSomeOrFailWithNone
   Check `someOrFailException` method extracts the optional value. $testSomeOrFailExceptionOnOptionalValue
   Check `someOrFailException` method fails when given a None. $testSomeOrFailExceptionOnEmptyValue
   Check `none` method extracts the value from Some. $testNoneOnSomeOption
   Check `none` method fails on None. $testNoneOnNoneOption
   Check `none` method fails when given an exception. $testNoneOnException
   Check `flattenErrorOption` method fails when given Some error. $testFlattenErrorOptionOnSomeError
   Check `flattenErrorOption` method fails with Default when given None error. $testFlattenErrorOptionOnNoneError
   Check `flattenErrorOption` method succeeds when given a value. $testFlattenErrorOptionOnSomeValue
   Check `optional` method fails when given Some error. $testOptionalOnSomeError
   Check `optional` method succeeds with None given None error. $testOptionalOnNoneError
   Check `optional` method succeeds with Some given a value. $testOptionalOnSomeValue
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
   Check `foreachParN` works on large lists $testForeachParN_Threads
   Check `foreachParN` runs effects in parallel $testForeachParN_Parallel
   Check `foreachParN` propogates error $testForeachParN_Error
   Check `foreachParN` interrupts effects on first failure $testForeachParN_Interruption

   Eager - Generate a String:
      `.succeed` extension method returns the same UIO[String] as `IO.succeed` does. $eagerT1
   Eager - Generate a String:
      `.fail` extension method returns the same IO[String, Nothing] as `IO.fail` does. $eagerT2
   Eager - Generate a String:
      `.ensure` extension method returns the same IO[E, Option[A]] => IO[E, A] as `IO.ensure` does. $eagerT3

   Lazy - Generate a String:
      `.effect` extension method returns the same UIO[String] as `IO.effect` does. $lazyT1
   Lazy - Generate a String:
      `.effect` extension method returns the same Task[String] as `IO.effect` does. $lazyT2
   Lazy - Generate a String:
      `.effect` extension method returns the same PartialFunction[Throwable, E] => IO[E, A] as `IO.effect` does. $lazyT3

   Generate an Iterable of Char:
     `.mergeAll` extension method returns the same IO[E, B] as `IO.mergeAll` does. $iterableT1
   Generate an Iterable of Char:
     `.parAll` extension method returns the same IO[E, List[A]] as `IO.parAll` does. $iterableT2
   Generate an Iterable of Char:
     `.forkAll` extension method returns the same UIO[Fiber[E, List[A]]] as `IO.forkAll` does. $iterableT3
   Generate an Iterable of Char:
     `.sequence` extension method returns the same IO[E, List[A]] as `IO.sequence` does. $iterableT4

   Generate a Tuple2 of (Int, String):
     `.map2` extension method should combine them to an IO[E, Z] with a function (A, B) => Z. $tupleT1
   Generate a Tuple3 of (Int, String, String):
     `.map3` extension method should combine them to an IO[E, Z] with a function (A, B, C) => Z. $tupleT2
   Generate a Tuple4 of (Int, String, String, String):
     `.map4` extension method should combine them to an IO[E, C] with a function (A, B, C, D) => Z. $tupleT3
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
    val res     = unsafeRun(IO.foreach(list)(x => IO.effectTotal(effects += x) *> IO.effectTotal[Int](x.toInt)))
    (effects.toList, res) must be_===((list, List(1, 2, 3)))
  }

  def t3 = {
    val list = List("1", "h", "3")
    val res  = Try(unsafeRun(IO.foreach(list)(x => IO.effectTotal[Int](x.toInt))))
    res must beAFailedTry.withThrowable[FiberFailure]
  }

  def t4 = {
    val list = List("1", "2", "3")
    val res  = unsafeRun(IO.foreachPar(list)(x => IO.effectTotal[Int](x.toInt)))
    res must be_===(List(1, 2, 3))
  }

  def t5 = forAll { (i: Int) =>
    val res = unsafeRun(IO.fail[Int](i).bimap(_.toString, identity).either)
    res must_=== Left(i.toString)
  }

  def t6 = {
    val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
    val res  = unsafeRun(IO.collectAllPar(list))
    res must be_===(List(1, 2, 3))
  }

  def t7 = {
    val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
    val res  = unsafeRun(IO.forkAll(list).flatMap[Any, Nothing, List[Int]](_.join))
    res must be_===(List(1, 2, 3))
  }

  def t8 = {
    val list = List(1, 2, 3).map(IO.effectTotal[Int](_))
    val res  = unsafeRun(IO.collectAllParN(2)(list))
    res must be_===(List(1, 2, 3))
  }

  def t9 = {
    val list = List(1, 2, 3)
    val res  = unsafeRun(IO.foreachParN(2)(list)(x => IO.effectTotal(x.toString)))
    res must be_===(List("1", "2", "3"))
  }

  def t10 = forAll { (l: List[Int]) =>
    unsafeRun(IO.foldLeft(l)(0)((acc, el) => IO.succeed(acc + el))) must_=== unsafeRun(IO.succeed(l.sum))
  }

  def t11 = forAll { (l: List[Int]) =>
    l.size > 0 ==>
      (unsafeRunSync(IO.foldLeft(l)(0)((_, _) => IO.fail("fail"))) must_=== unsafeRunSync(IO.fail("fail")))
  }

  private val exampleError = new Error("something went wrong")

  def testDone = {
    val fiberId                       = 123L
    val error                         = exampleError
    val completed                     = Exit.succeed(1)
    val interrupted: Exit[Error, Int] = Exit.interrupt(fiberId)
    val terminated: Exit[Error, Int]  = Exit.die(error)
    val failed: Exit[Error, Int]      = Exit.fail(error)

    unsafeRun(IO.done(completed)) must_=== 1
    unsafeRunSync(IO.done(interrupted)) must_=== Exit.interrupt(fiberId)
    unsafeRunSync(IO.done(terminated)) must_=== Exit.die(error)
    unsafeRunSync(IO.done(failed)) must_=== Exit.fail(error)
  }

  private def testCatchSomeCauseMatch =
    unsafeRun {
      ZIO.interrupt.catchSomeCause {
        case c if (c.interrupted) => ZIO.succeed(true)
      }.sandbox.map(_ must_=== true)
    }

  private def testCatchSomeCauseNoMatch =
    unsafeRun {
      ZIO.fiberId.flatMap { selfId =>
        ZIO.interrupt.catchSomeCause {
          case c if (!c.interrupted) => ZIO.succeed(true)
        }.sandbox.either.map(_ must_=== Left(Cause.interrupt(selfId)))
      }
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

  def testWhenCase =
    unsafeRun {
      val v1: Option[Int] = None
      val v2: Option[Int] = Some(0)
      for {
        ref  <- Ref.make(false)
        _    <- ZIO.whenCase(v1) { case Some(_) => ref.set(true) }
        res1 <- ref.get
        _    <- ZIO.whenCase(v2) { case Some(_) => ref.set(true) }
        res2 <- ref.get
      } yield (res1 must_=== false) and (res2 must_=== true)
    }

  def testWhenCaseM =
    unsafeRun {
      val v1: Option[Int] = None
      val v2: Option[Int] = Some(0)
      for {
        ref  <- Ref.make(false)
        _    <- ZIO.whenCaseM(IO.succeed(v1)) { case Some(_) => ref.set(true) }
        res1 <- ref.get
        _    <- ZIO.whenCaseM(IO.succeed(v2)) { case Some(_) => ref.set(true) }
        res2 <- ref.get
      } yield (res1 must_=== false) and (res2 must_=== true)
    }

  def testUnsandbox = {
    val failure: IO[Cause[Exception], String] = IO.fail(fail(new Exception("fail")))
    val success: IO[Cause[Any], Int]          = IO.succeed(100)
    unsafeRun(for {
      message <- failure.unsandbox.foldM(e => IO.succeed(e.getMessage), _ => IO.succeed("unexpected"))
      result  <- success.unsandbox
    } yield (message must_=== "fail") and (result must_=== 100))
  }

  def testFlatten = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      flatten1 <- IO.effectTotal(IO.effectTotal(str)).flatten
      flatten2 <- IO.flatten(IO.effectTotal(IO.effectTotal(str)))
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
    val io: UIO[Option[String]] = IO.effectTotal(Some(str)) // using `Some` for object allocation
    unsafeRun(
      (io <*> io)
        .map(tuple => tuple._1 must not beTheSameAs (tuple._2))
    )
  }

  def testMemoization = forAll(Gen.alphaStr) { str =>
    val ioMemo: UIO[UIO[Option[String]]] = IO.effectTotal(Some(str)).memoize // using `Some` for object allocation
    unsafeRun(
      ioMemo
        .flatMap(io => io <*> io)
        .map(tuple => tuple._1 must beTheSameAs(tuple._2))
    )
  }

  def testCached = unsafeRunWith(TestClock.make(TestClock.DefaultData)) {
    def incrementAndGet(ref: Ref[Int]): UIO[Int] = ref.update(_ + 1)
    for {
      ref   <- Ref.make(0)
      cache <- incrementAndGet(ref).cached(60.minutes)
      a     <- cache
      _     <- TestClock.adjust(59.minutes)
      b     <- cache
      _     <- TestClock.adjust(1.minute)
      c     <- cache
      _     <- TestClock.adjust(59.minutes)
      d     <- cache
    } yield (a must_=== b) and (b must_!== c) and (c must_=== d)
  }

  def testRaceAll = {
    val io  = IO.effectTotal("supercalifragilisticexpialadocious")
    val ios = List.empty[UIO[String]]
    unsafeRun(for {
      race1 <- io.raceAll(ios)
      race2 <- IO.raceAll(io, ios)
    } yield race1 must ===(race2))
  }

  def testfirstSuccessOf = {
    val io  = IO.effectTotal("supercalifragilisticexpialadocious")
    val ios = List.empty[UIO[String]]
    unsafeRun(for {
      race1 <- io.firstSuccessOf(ios)
      race2 <- IO.firstSuccessOf(io, ios)
    } yield race1 must ===(race2))
  }

  def testZipParInterupt = {
    val io = ZIO.interrupt.zipPar(IO.interrupt)
    unsafeRunSync(io) match {
      case Exit.Failure(cause) => cause.interruptors.size must_!== 0
      case _                   => false must_=== true
    }
  }

  def testZipParSucceed = {
    val io = ZIO.interrupt.zipPar(IO.succeed(1))
    unsafeRun(io.sandbox.either).left.map(_.interrupted) must_=== Left(true)
  }

  def testOrElseDefectHandling = {
    val ex = new Exception("Died")

    unsafeRun {
      val fiberId = 123L

      import zio.CanFail.canFail
      for {
        plain <- (ZIO.die(ex) <> IO.unit).run
        both  <- (ZIO.halt(Cause.Both(interrupt(fiberId), die(ex))) <> IO.unit).run
        thn   <- (ZIO.halt(Cause.Then(interrupt(fiberId), die(ex))) <> IO.unit).run
        fail  <- (ZIO.fail(ex) <> IO.unit).run
      } yield (plain must_=== Exit.die(ex))
        .and(both must_=== Exit.die(ex))
        .and(thn must_=== Exit.die(ex))
        .and(fail must_=== Exit.succeed(()))
    }
  }

  def testEventually = {
    def effect(ref: Ref[Int]) =
      ref.get.flatMap(n => if (n < 10) ref.update(_ + 1) *> IO.fail("Ouch") else UIO.succeed(n))

    val test = for {
      ref <- Ref.make(0)
      n   <- effect(ref).eventually
    } yield n

    unsafeRun(test) must_=== 10
  }

  def testSomeOnSomeOption = {
    val task: IO[Option[Throwable], Int] = Task(Some(1)).some
    unsafeRun(task) must_=== 1
  }

  def testSomeOnNoneOption = {
    val task: IO[Option[Throwable], Int] = Task(None).some
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testSomeOnException = {
    val task: IO[Option[Throwable], Int] = Task.fail(new RuntimeException("Failed Task")).some
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testNoneOnSomeOption = {
    val task: IO[Option[Throwable], Unit] = Task(Some(1)).none
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testNoneOnNoneOption = {
    val task: IO[Option[Throwable], Unit] = Task(None).none
    unsafeRun(task) must_=== (())
  }

  def testNoneOnException = {
    val task: IO[Option[Throwable], Unit] = Task.fail(new RuntimeException("Failed Task")).none
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testFlattenErrorOptionOnSomeError = {
    val task: IO[String, Int] = IO.fail(Some("Error")).flattenErrorOption("Default")
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testFlattenErrorOptionOnNoneError = {
    val task: IO[String, Int] = IO.fail(None).flattenErrorOption("Default")
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testFlattenErrorOptionOnSomeValue = {
    val task: IO[String, Int] = IO.succeed(1).flattenErrorOption("Default")
    unsafeRun(task) must_=== 1
  }

  def testOptionalOnSomeError = {
    val task: IO[String, Option[Int]] = IO.fail(Some("Error")).optional
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testOptionalOnNoneError = {
    val task: IO[String, Option[Int]] = IO.fail(None).optional
    unsafeRun(task) must_=== None
  }

  def testOptionalOnSomeValue = {
    val task: IO[String, Option[Int]] = IO.succeed(1).optional
    unsafeRun(task) must_=== Some(1)
  }

  def testSomeOrFailWithNone = {
    val task: Task[Int] = UIO(Option.empty[Int]).someOrFail(exampleError)
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testSomeOrFailExtractOptionalValue = {
    val task: Task[Int] = UIO(Some(42)).someOrFail(exampleError)
    unsafeRun(task) must_=== 42
  }

  def testSomeOrFailExceptionOnOptionalValue = unsafeRun(ZIO.succeed(Some(42)).someOrFailException) must_=== 42

  def testSomeOrFailExceptionOnEmptyValue = {
    val task = ZIO.succeed(Option.empty[Int]).someOrFailException
    unsafeRun(task) must throwA[FiberFailure]
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

  def testForeachParN_Threads = {
    val n   = 10
    val seq = 0 to 100000
    val res = unsafeRun(IO.foreachParN(n)(seq)(UIO.succeed))
    res must be_===(seq)
  }

  def testForeachParN_Parallel = {
    val io = for {
      p <- Promise.make[Nothing, Unit]
      _ <- UIO.foreachParN(2)(List(UIO.never, p.succeed(())))(a => a).fork
      _ <- p.await
    } yield true
    unsafeRun(io)
  }

  def testForeachParN_Error = {
    val ints = List(1, 2, 3, 4, 5, 6)
    val odds = ZIO.foreachParN(4)(ints) { n =>
      if (n % 2 != 0) ZIO.succeed(n) else ZIO.fail("not odd")
    }
    unsafeRun(odds.either) must_=== Left("not odd")
  }

  def testForeachParN_Interruption = {
    val actions = List(
      ZIO.never,
      ZIO.succeed(1),
      ZIO.fail("C")
    )
    val io = ZIO.foreachParN(4)(actions)(a => a)
    unsafeRun(io.either) must_=== Left("C")
  }

  def eagerT1 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      a <- str.succeed
      b <- IO.succeed(str)
    } yield a must ===(b))
  }

  def eagerT2 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      a <- str.fail.either
      b <- IO.fail(str).either
    } yield a must ===(b))
  }

  def eagerT3 = forAll(Gen.alphaStr) { str =>
    val ioSome = IO.succeed(Some(42))
    unsafeRun(for {
      a <- str.require(ioSome)
      b <- IO.require(str)(ioSome)
    } yield a must ===(b))
  }

  def lazyT1 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.effect
      b <- IO.effectTotal(lazyStr)
    } yield a must ===(b))
  }

  def lazyT2 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.effect
      b <- IO.effect(lazyStr)
    } yield a must ===(b))
  }

  def lazyT3 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    val partial: PartialFunction[Throwable, Int] = { case _: Throwable => 42 }
    unsafeRun(for {
      a <- lazyStr.effect.refineOrDie(partial)
      b <- IO.effect(lazyStr).refineOrDie(partial)
    } yield a must ===(b))
  }

  val TestData = "supercalifragilisticexpialadocious".toList

  def iterableT1 = {
    val ios                          = TestData.map(IO.succeed)
    val zero                         = List.empty[Char]
    def merger[A](as: List[A], a: A) = a :: as
    unsafeRun(for {
      merged1 <- ios.mergeAll(zero)(merger)
      merged2 <- IO.mergeAll(ios)(zero)(merger)
    } yield merged1 must ===(merged2))
  }

  def iterableT2 = {
    val ios = TestData.map(IO.effectTotal(_))
    unsafeRun(for {
      parAll1 <- ios.collectAllPar
      parAll2 <- IO.collectAllPar(ios)
    } yield parAll1 must ===(parAll2))
  }

  def iterableT3 = {
    val ios: Iterable[IO[String, Char]] = TestData.map(IO.effectTotal(_))
    unsafeRun(for {
      f1       <- ios.forkAll
      forkAll1 <- f1.join
      f2       <- IO.forkAll(ios)
      forkAll2 <- f2.join
    } yield forkAll1 must ===(forkAll2))
  }

  def iterableT4 = {
    val ios = TestData.map(IO.effectTotal(_))
    unsafeRun(for {
      sequence1 <- ios.collectAll
      sequence2 <- IO.collectAll(ios)
    } yield sequence1 must ===(sequence2))
  }

  def tupleT1 = forAll(Gen.posNum[Int], Gen.alphaStr) { (int: Int, str: String) =>
    def f(i: Int, s: String): String = i.toString + s
    val ios                          = (IO.succeed(int), IO.succeed(str))
    unsafeRun(for {
      map2 <- ios.map2[String](f)
    } yield map2 must ===(f(int, str)))
  }

  def tupleT2 = forAll(Gen.posNum[Int], Gen.alphaStr, Gen.alphaStr) { (int: Int, str1: String, str2: String) =>
    def f(i: Int, s1: String, s2: String): String = i.toString + s1 + s2
    val ios                                       = (IO.succeed(int), IO.succeed(str1), IO.succeed(str2))
    unsafeRun(for {
      map3 <- ios.map3[String](f)
    } yield map3 must ===(f(int, str1, str2)))
  }

  def tupleT3 = forAll(Gen.posNum[Int], Gen.alphaStr, Gen.alphaStr, Gen.alphaStr) {
    (int: Int, str1: String, str2: String, str3: String) =>
      def f(i: Int, s1: String, s2: String, s3: String): String = i.toString + s1 + s2 + s3
      val ios                                                   = (IO.succeed(int), IO.succeed(str1), IO.succeed(str2), IO.succeed(str3))
      unsafeRun(for {
        map4 <- ios.map4[String](f)
      } yield map4 must ===(f(int, str1, str2, str3)))
  }
}
