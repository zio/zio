package zio

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

import com.github.ghik.silencer.silent
import org.scalacheck._
import org.specs2.ScalaCheck
import org.specs2.execute.Result

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{ Failure, Success, Try }

import zio.Cause.{ die, fail, interrupt, Both, Fail, Then }
import zio.duration._
import zio.test.mock.MockClock
import zio.internal.PlatformLive
import zio.clock.Clock

class ZIOSpecJvm(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  import Prop.forAll

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
   Check `supervise` returns same value as IO.supervise. $testSupervise
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
   Check `right` method extracts the Right value. $testRightOnRightValue
   Check `right` method fails with `None` when given a Left value. $testRightOnLeftValue
   Check `right` method fails with `Some(exception)` when given an exception. $testRightOnException
   Check `rightOrFail` method extracts the Right value. $testRightOrFailExtractsRightValue
   Check `rightOrFail` method fails when given a Left. $testRightOrFailWithLeft
   Check `rightOrFailException` method extracts the Right value. $testRightOrFailExceptionOnRightValue
   Check `rightOrFailException` method fails when given a Left. $testRightOrFailExceptionOnLeftValue
   Check `left` method extracts the Left value. $testLeftOnLeftValue
   Check `left` method fails with `None` when given a Right value. $testLeftOnRightValue
   Check `left` method fails with `Some(exception)` when given an exception. $testLeftOnException
   Check `leftOrFail` method extracts the Left value. $testLeftOrFailExtractsLeftValue
   Check `leftOrFail` method fails when given a Right. $testLeftOrFailWithRight
   Check `leftOrFailException` method extracts the Left value. $testLeftOrFailExceptionOnLeftValue
   Check `leftOrFailException` method fails when given a Right. $testLeftOrFailExceptionOnRightValue
   Check `head` method extracts the head of a non-empty list. $testHeadOnNonEmptyList
   Check `head` method fails with `None` when given an empty list. $testHeadOnEmptyList
   Check `head` method fails with `Some(exception)` when given an exception. $testHeadOnException
   Check `replicate` method returns empty list when given non positive number. $testReplicateNonPositiveNumber
   Check `replicate` method returns list of the same effect. $testReplicate
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
   Check `foreachParN` works on large lists $testForeachParN_Threads
   Check `foreachParN` runs effects in parallel $testForeachParN_Parallel
   Check `foreachParN` propogates error $testForeachParN_Error
   Check `foreachParN` interrupts effects on first failure $testForeachParN_Interruption

   RTS synchronous correctness
    widen Nothing                                 $testWidenNothing
    blocking caches threads                       $testBlockingThreadCaching
    now must be eager                             $testNowIsEager
    effectSuspend must be lazy                    $testSuspendIsLazy
    effectSuspendTotal must not catch throwable   $testSuspendTotalThrowable
    effectSuspend must catch throwable            $testSuspendCatchThrowable
    effectSuspendWith must catch throwable        $testSuspendWithCatchThrowable
    suspend must be evaluatable                   $testSuspendIsEvaluatable
    point, bind, map                              $testSyncEvalLoop
    effect, bind, map                             $testSyncEvalLoopEffect
    effect, bind, map, redeem                     $testSyncEvalLoopEffectThrow
    sync effect                                   $testEvalOfSyncEffect
    deep effects                                  $testEvalOfDeepSyncEffect
    flip must make error into value               $testFlipError
    flip must make value into error               $testFlipValue
    flipping twice returns identical value        $testFlipDouble

  RTS failure
    error in sync effect                          $testEvalOfRedeemOfSyncEffectError
    attempt . fail                                $testEvalOfAttemptOfFail
    deep attempt sync effect error                $testAttemptOfDeepSyncEffectError
    deep attempt fail error                       $testAttemptOfDeepFailError
    attempt . sandbox . terminate                 $testSandboxAttemptOfTerminate
    fold . sandbox . terminate                    $testSandboxFoldOfTerminate
    catch sandbox terminate                       $testSandboxTerminate
    uncaught fail                                 $testEvalOfUncaughtFail
    uncaught fail supervised                      $testEvalOfUncaughtFailSupervised
    uncaught sync effect error                    $testEvalOfUncaughtThrownSyncEffect
    uncaught supervised sync effect error         $testEvalOfUncaughtThrownSupervisedSyncEffect
    deep uncaught sync effect error               $testEvalOfDeepUncaughtThrownSyncEffect
    deep uncaught fail                            $testEvalOfDeepUncaughtFail
    catch failing finalizers with fail            $testFailOfMultipleFailingFinalizers
    catch failing finalizers with terminate       $testTerminateOfMultipleFailingFinalizers
    run preserves interruption status             $testRunInterruptIsInterrupted
    run swallows inner interruption               $testRunSwallowsInnerInterrupt
    timeout a long computation                    $testTimeoutOfLongComputation
    catchAllCause                                 $testCatchAllCause
    exception in fromFuture does not kill fiber   $testFromFutureDoesNotKillFiber

  RTS finalizers
    fail ensuring                                 $testEvalOfFailEnsuring
    fail on error                                 $testEvalOfFailOnError
    finalizer errors not caught                   $testErrorInFinalizerCannotBeCaught
    finalizer errors reported                     $testErrorInFinalizerIsReported
    bracket exit is usage result                  $testExitIsUsageResult
    error in just acquisition                     $testBracketErrorInAcquisition
    error in just release                         $testBracketErrorInRelease
    error in just usage                           $testBracketErrorInUsage
    rethrown caught error in acquisition          $testBracketRethrownCaughtErrorInAcquisition
    rethrown caught error in release              $testBracketRethrownCaughtErrorInRelease
    rethrown caught error in usage                $testBracketRethrownCaughtErrorInUsage
    test eval of async fail                       $testEvalOfAsyncAttemptOfFail
    bracket regression 1                          $testBracketRegression1
    interrupt waits for finalizer                 $testInterruptWaitsForFinalizer

  RTS synchronous stack safety
    deep map of now                               $testDeepMapOfNow
    deep map of sync effect                       $testDeepMapOfSyncEffectIsStackSafe
    deep attempt                                  $testDeepAttemptIsStackSafe
    deep flatMap                                  $testDeepFlatMapIsStackSafe
    deep absolve/attempt is identity              $testDeepAbsolveAttemptIsIdentity
    deep async absolve/attempt is identity        $testDeepAsyncAbsolveAttemptIsIdentity

  RTS asynchronous correctness
    simple async must return                      $testAsyncEffectReturns
    simple asyncIO must return                    $testAsyncIOEffectReturns
    deep asyncIO doesn't block threads            $testDeepAsyncIOThreadStarvation
    interrupt of asyncPure register               $testAsyncPureInterruptRegister
    sleep 0 must return                           $testSleepZeroReturns
    shallow bind of async chain                   $testShallowBindOfAsyncChainIsCorrect
    effectAsyncM can fail before registering      $testEffectAsyncMCanFail
    effectAsyncM can defect before registering    $testEffectAsyncMCanDefect
    second callback call is ignored               $testAsyncSecondCallback

  RTS concurrency correctness
    shallow fork/join identity                    $testForkJoinIsId
    deep fork/join identity                       $testDeepForkJoinIsId
    asyncPure creation is interruptible           $testAsyncPureCreationIsInterruptible
    asyncInterrupt runs cancel token on interrupt $testAsync0RunsCancelTokenOnInterrupt
    supervising returns fiber refs                $testSupervising
    supervising in unsupervised returns Nil       $testSupervisingUnsupervised
    supervise fibers                              $testSuperviseRTS
    supervise fibers in supervised                $testSupervised
    supervise fibers in race                      $testSuperviseRace
    supervise fibers in fork                      $testSuperviseFork
    race of fail with success                     $testRaceChoosesWinner
    race of terminate with success                $testRaceChoosesWinnerInTerminate
    race of fail with fail                        $testRaceChoosesFailure
    race of value & never                         $testRaceOfValueNever
    raceAll of values                             $testRaceAllOfValues
    raceAll of failures                           $testRaceAllOfFailures
    raceAll of failures & one success             $testRaceAllOfFailuresOneSuccess
    firstSuccessOf of values                      $testFirstSuccessOfValues
    firstSuccessOf of failures                    $testFirstSuccessOfFailures
    firstSuccessOF of failures & 1 success        $testFirstSuccessOfFailuresOneSuccess
    raceAttempt interrupts loser on success       $testRaceAttemptInterruptsLoserOnSuccess
    raceAttempt interrupts loser on failure       $testRaceAttemptInterruptsLoserOnFailure
    par regression                                $testPar
    par of now values                             $testRepeatedPar
    mergeAll                                      $testMergeAll
    mergeAllEmpty                                 $testMergeAllEmpty
    reduceAll                                     $testReduceAll
    reduceAll Empty List                          $testReduceAllEmpty
    timeout of failure                            $testTimeoutFailure
    timeout of terminate                          $testTimeoutTerminate

  RTS regression tests
    deadlock regression 1                         $testDeadlockRegression
    check interruption regression 1               $testInterruptionRegression1
    max yield Ops 1                               $testOneMaxYield

  RTS option tests
    lifting a value to an option                  $testLiftingOptionalValue
    using the none value                          $testLiftingNoneValue

  RTS either helper tests
      lifting a value into right                  $liftValueIntoRight
      lifting a value into left                   $liftValueIntoLeft

  RTS interruption
    blocking IO is effect blocking                $testBlockingIOIsEffectBlocking
    sync forever is interruptible                 $testInterruptSyncForever
    interrupt of never                            $testNeverIsInterruptible
    asyncPure is interruptible                    $testAsyncPureIsInterruptible
    async is interruptible                        $testAsyncIsInterruptible
    bracket is uninterruptible                    $testBracketAcquireIsUninterruptible
    bracket0 is uninterruptible                   $testBracket0AcquireIsUninterruptible
    bracket use is interruptible                  $testBracketUseIsInterruptible
    bracket0 use is interruptible                 $testBracket0UseIsInterruptible
    bracket release called on interrupt           $testBracketReleaseOnInterrupt
    bracket0 release called on interrupt          $testBracket0ReleaseOnInterrupt
    redeem + ensuring + interrupt                 $testRedeemEnsuringInterrupt
    finalizer can detect interruption             $testFinalizerCanDetectInterruption
    interruption of raced                         $testInterruptedOfRaceInterruptsContestents
    cancelation is guaranteed                     $testCancelationIsGuaranteed
    interruption of unending bracket              $testInterruptionOfUnendingBracket
    recovery of error in finalizer                $testRecoveryOfErrorInFinalizer
    recovery of interruptible                     $testRecoveryOfInterruptible
    sandbox of interruptible                      $testSandboxOfInterruptible
    run of interruptible                          $testRunOfInterruptible
    alternating interruptibility                  $testAlternatingInterruptibility
    interruption after defect                     $testInterruptionAfterDefect
    interruption after defect 2                   $testInterruptionAfterDefect2
    cause reflects interruption                   $testCauseReflectsInterruption
    bracket use inherits interrupt status         $testUseInheritsInterruptStatus
    bracket use inherits interrupt status 2       $testCauseUseInheritsInterruptStatus
    async can be uninterruptible                  $testAsyncCanBeUninterruptible

  RTS environment
    provide is modular                            $testProvideIsModular
    provideManaged is modular                     $testProvideManagedIsModular
    effectAsync can use environment               $testAsyncCanUseEnvironment

  RTS forking inheritability
    interruption status is heritable              $testInterruptStatusIsHeritable
    executor is hereditble                        $testExecutorIsHeritable
    supervision is heritable                      $testSupervisionIsHeritable
    supervision inheritance                       $testSupervisingInheritance
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
    val error                         = exampleError
    val completed                     = Exit.succeed(1)
    val interrupted: Exit[Error, Int] = Exit.interrupt
    val terminated: Exit[Error, Int]  = Exit.die(error)
    val failed: Exit[Error, Int]      = Exit.fail(error)

    unsafeRun(IO.done(completed)) must_=== 1
    unsafeRunSync(IO.done(interrupted)) must_=== Exit.interrupt
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
      ZIO.interrupt.catchSomeCause {
        case c if (!c.interrupted) => ZIO.succeed(true)
      }.sandbox.either.map(_ must_=== Left(Cause.interrupt))
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

  def testSupervise = {
    val io = IO.effectTotal("supercalifragilisticexpialadocious")
    unsafeRun(for {
      supervise1 <- io.interruptChildren
      supervise2 <- IO.interruptChildren(io)
    } yield supervise1 must ===(supervise2))
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

  def testCached = unsafeRunWith(MockClock.make(MockClock.DefaultData)) {
    def incrementAndGet(ref: Ref[Int]): UIO[Int] = ref.update(_ + 1)
    for {
      ref   <- Ref.make(0)
      cache <- incrementAndGet(ref).cached(60.minutes)
      a     <- cache
      _     <- MockClock.adjust(59.minutes)
      b     <- cache
      _     <- MockClock.adjust(1.minute)
      c     <- cache
      _     <- MockClock.adjust(59.minutes)
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

  def testRightOnRightValue = {
    val task = ZIO.succeed(Right("Right")).right.either
    unsafeRun(task) must_=== Right("Right")
  }

  def testRightOnLeftValue = {
    val task = ZIO.succeed(Left("Left")).right.either
    unsafeRun(task) must_=== Left(None)
  }

  def testRightOnException = {
    val task = ZIO.fail("Fail").right.either
    unsafeRun(task) must_=== Left(Some("Fail"))
  }

  def testRightOrFailExceptionOnRightValue = unsafeRun(ZIO.succeed(Right(42)).rightOrFailException) must_=== 42

  def testRightOrFailExceptionOnLeftValue = {
    val task: Task[Int] = ZIO.succeed(Left(2)).rightOrFailException
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testRightOrFailExtractsRightValue = {
    val task: Task[Int] = UIO(Right(42)).rightOrFail(exampleError)
    unsafeRun(task) must_=== 42
  }

  def testRightOrFailWithLeft = {
    val task: Task[Int] = UIO(Left(1)).rightOrFail(exampleError)
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testLeftOnLeftValue = {
    val task = ZIO.succeed(Left("Left")).left.either
    unsafeRun(task) must_=== Right("Left")
  }

  def testLeftOnRightValue = {
    val task = ZIO.succeed(Right("Right")).left.either
    unsafeRun(task) must_=== Left(None)
  }

  def testLeftOnException = {
    val task = ZIO.fail("Fail").left.either
    unsafeRun(task) must_=== Left(Some("Fail"))
  }

  def testLeftOrFailExceptionOnLeftValue = unsafeRun(ZIO.succeed(Left(42)).leftOrFailException) must_=== 42

  def testLeftOrFailExceptionOnRightValue = {
    val task: Task[Int] = ZIO.succeed(Right(2)).leftOrFailException
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testReplicateNonPositiveNumber = {
    val lst: Iterable[UIO[Int]] = ZIO.replicate(0)(ZIO.succeed(12))
    unsafeRun(ZIO.sequence(lst)) must_=== List()

    val anotherList: Iterable[UIO[Int]] = ZIO.replicate(-2)(ZIO.succeed(12))
    unsafeRun(ZIO.sequence(anotherList)) must_=== List()
  }

  def testReplicate = {
    val lst: Iterable[UIO[Int]] = ZIO.replicate(2)(ZIO.succeed(12))
    unsafeRun(ZIO.sequence(lst)) must_=== List.fill(2)(12)
  }

  def testLeftOrFailExtractsLeftValue = {
    val task: Task[Int] = UIO(Left(42)).leftOrFail(exampleError)
    unsafeRun(task) must_=== 42
  }

  def testLeftOrFailWithRight = {
    val task: Task[Int] = UIO(Right(12)).leftOrFail(exampleError)
    unsafeRun(task) must throwA[FiberFailure]
  }

  def testHeadOnNonEmptyList = {
    val task = ZIO.succeed(List(1, 2, 3)).head.either
    unsafeRun(task) must_=== Right(1)
  }

  def testHeadOnEmptyList = {
    val task = ZIO.succeed(List.empty).head.either
    unsafeRun(task) must_=== Left(None)
  }

  def testHeadOnException = {
    val task = ZIO.fail("Fail").head.either
    unsafeRun(task) must_=== Left(Some("Fail"))
  }

  def testUncurriedBracket =
    unsafeRun {
      for {
        release  <- Ref.make(false)
        result   <- ZIO.bracket(IO.succeed(42), (_: Int) => release.set(true), (a: Int) => ZIO.effectTotal(a + 1))
        released <- release.get
      } yield (result must_=== 43) and (released must_=== true)
    }

  def testUncurriedBracket_ =
    unsafeRun {
      for {
        release  <- Ref.make(false)
        result   <- IO.succeed(42).bracket_(release.set(true), ZIO.effectTotal(0))
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

  def testWidenNothing = {
    val op1 = IO.effectTotal[String]("1")
    val op2 = IO.effectTotal[String]("2")

    val result: IO[RuntimeException, String] = for {
      r1 <- op1
      r2 <- op2
    } yield r1 + r2

    unsafeRun(result) must_=== "12"
  }

  @silent
  def testNowIsEager =
    IO.succeed(throw new Error("Eager")) must (throwA[Error])

  def testSuspendIsLazy =
    IO.effectSuspendTotal(throw new Error("Eager")) must not(throwA[Throwable])

  def testSuspendTotalThrowable =
    unsafeRun(ZIO.effectSuspendTotal[Any, Nothing, Any](throw ExampleError).sandbox.either) must_=== Left(
      die(ExampleError)
    )

  def testSuspendCatchThrowable =
    unsafeRun(ZIO.effectSuspend[Any, Nothing](throw ExampleError).either) must_=== Left(ExampleError)

  def testSuspendWithCatchThrowable =
    unsafeRun(ZIO.effectSuspendWith[Any, Nothing](_ => throw ExampleError).either) must_=== Left(ExampleError)

  def testSuspendIsEvaluatable =
    unsafeRun(IO.effectSuspendTotal(IO.effectTotal[Int](42))) must_=== 42

  def testSyncEvalLoop = {
    def fibIo(n: Int): Task[BigInt] =
      if (n <= 1) IO.succeed(n)
      else
        for {
          a <- fibIo(n - 1)
          b <- fibIo(n - 2)
        } yield a + b

    unsafeRun(fibIo(10)) must_=== fib(10)
  }

  def testSyncEvalLoopEffect = {
    def fibIo(n: Int): Task[BigInt] =
      if (n <= 1) IO.effect(n)
      else
        for {
          a <- fibIo(n - 1)
          b <- fibIo(n - 2)
        } yield a + b

    unsafeRun(fibIo(10)) must_=== fib(10)
  }

  def testSyncEvalLoopEffectThrow = {
    def fibIo(n: Int): Task[BigInt] =
      if (n <= 1) Task.effect[BigInt](throw new Error).catchAll(_ => Task.effect(n))
      else
        for {
          a <- fibIo(n - 1)
          b <- fibIo(n - 2)
        } yield a + b

    unsafeRun(fibIo(10)) must_=== fib(10)
  }

  def testFlipError = {
    val error = new Error("Left")
    val io    = IO.fail(error).flip
    unsafeRun(io) must_=== error
  }

  def testFlipValue = {
    val io = IO.succeed(100).flip
    unsafeRun(io.either) must_=== Left(100)
  }

  def testFlipDouble = {
    val io = IO.succeed(100)
    unsafeRun(io.flip.flip) must_=== unsafeRun(io)
  }

  def testEvalOfSyncEffect = {
    def sumIo(n: Int): Task[Int] =
      if (n <= 0) IO.effectTotal(0)
      else IO.effectTotal(n).flatMap(b => sumIo(n - 1).map(a => a + b))

    unsafeRun(sumIo(1000)) must_=== sum(1000)
  }

  @silent
  def testEvalOfRedeemOfSyncEffectError =
    unsafeRun(
      IO.effect[Unit](throw ExampleError).fold[Option[Throwable]](Some(_), _ => None)
    ) must_=== Some(ExampleError)

  def testEvalOfAttemptOfFail = Seq(
    unsafeRun(TaskExampleError.either) must_=== Left(ExampleError),
    unsafeRun(IO.effectSuspendTotal(IO.effectSuspendTotal(TaskExampleError).either)) must_=== Left(
      ExampleError
    )
  )

  def testSandboxAttemptOfTerminate =
    unsafeRun(IO.effectTotal[Int](throw ExampleError).sandbox.either) must_=== Left(die(ExampleError))

  def testSandboxFoldOfTerminate =
    unsafeRun(
      IO.effectTotal[Int](throw ExampleError).sandbox.fold(Some(_), Function.const(None))
    ) must_=== Some(die(ExampleError))

  def testSandboxTerminate =
    unsafeRun(
      IO.effectTotal[Cause[Any]](throw ExampleError)
        .sandbox
        .fold[Cause[Any]](identity, identity)
    ) must_=== die(ExampleError)

  def testAttemptOfDeepSyncEffectError =
    unsafeRun(deepErrorEffect(100).either) must_=== Left(ExampleError)

  def testAttemptOfDeepFailError =
    unsafeRun(deepErrorFail(100).either) must_=== Left(ExampleError)

  def testEvalOfUncaughtFail =
    unsafeRunSync(Task.fail(ExampleError): Task[Any]) must_=== Exit.Failure(fail(ExampleError))

  def testEvalOfUncaughtFailSupervised =
    unsafeRunSync(Task.fail(ExampleError).interruptChildren: Task[Unit]) must_=== Exit.Failure(fail(ExampleError))

  def testEvalOfUncaughtThrownSyncEffect =
    unsafeRunSync(IO.effectTotal[Int](throw ExampleError)) must_=== Exit.Failure(die(ExampleError))

  def testEvalOfUncaughtThrownSupervisedSyncEffect =
    unsafeRunSync(IO.effectTotal[Int](throw ExampleError).interruptChildren) must_=== Exit.Failure(die(ExampleError))

  def testEvalOfDeepUncaughtThrownSyncEffect =
    unsafeRunSync(deepErrorEffect(100)) must_=== Exit.Failure(fail(ExampleError))

  def testEvalOfDeepUncaughtFail =
    unsafeRunSync(deepErrorEffect(100)) must_=== Exit.Failure(fail(ExampleError))

  def testFailOfMultipleFailingFinalizers =
    unsafeRun(
      TaskExampleError
        .ensuring(IO.effectTotal(throw InterruptCause1))
        .ensuring(IO.effectTotal(throw InterruptCause2))
        .ensuring(IO.effectTotal(throw InterruptCause3))
        .run
    ) must_=== Exit.halt(
      fail(ExampleError) ++
        die(InterruptCause1) ++
        die(InterruptCause2) ++
        die(InterruptCause3)
    )

  def testTerminateOfMultipleFailingFinalizers =
    unsafeRun(
      IO.die(ExampleError)
        .ensuring(IO.effectTotal(throw InterruptCause1))
        .ensuring(IO.effectTotal(throw InterruptCause2))
        .ensuring(IO.effectTotal(throw InterruptCause3))
        .run
    ) must_=== Exit.halt(
      die(ExampleError) ++
        die(InterruptCause1) ++
        die(InterruptCause2) ++
        die(InterruptCause3)
    )

  def testEvalOfFailEnsuring = {
    var finalized = false

    unsafeRunSync((Task.fail(ExampleError): Task[Unit]).ensuring(IO.effectTotal[Unit] { finalized = true; () })) must_===
      Exit.Failure(fail(ExampleError))
    finalized must_=== true
  }

  def testEvalOfFailOnError = {
    @volatile var finalized = false
    val cleanup: Cause[Throwable] => UIO[Unit] =
      _ => IO.effectTotal[Unit] { finalized = true; () }

    unsafeRunSync(
      Task.fail(ExampleError).onError(cleanup): Task[Unit]
    ) must_=== Exit.Failure(fail(ExampleError))

    // FIXME: Is this an issue with thread synchronization?
    while (!finalized) Thread.`yield`()

    finalized must_=== true
  }

  def testErrorInFinalizerCannotBeCaught = {

    val e2 = new Error("e2")
    val e3 = new Error("e3")

    val nested: Task[Int] =
      TaskExampleError
        .ensuring(IO.die(e2))
        .ensuring(IO.die(e3))

    unsafeRunSync(nested) must_=== Exit.Failure(Then(fail(ExampleError), Then(die(e2), die(e3))))
  }

  def testLiftingOptionalValue = unsafeRun(ZIO.some(42)) must_=== Some(42)

  def testLiftingNoneValue = unsafeRun(ZIO.none) must_=== None

  def liftValueIntoRight = unsafeRun(ZIO.right(42)) must_=== Right(42)

  def liftValueIntoLeft = unsafeRun(ZIO.left(42)) must_=== Left(42)

  def testErrorInFinalizerIsReported = {
    @volatile var reported: Exit[Nothing, Int] = null

    unsafeRun {
      IO.succeed[Int](42)
        .ensuring(IO.die(ExampleError))
        .fork
        .flatMap(_.await.flatMap[Any, Nothing, Any](e => UIO.effectTotal { reported = e }))
    }

    reported must_=== Exit.Failure(die(ExampleError))
  }

  def testExitIsUsageResult =
    unsafeRun(IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.succeed[Int](42))) must_=== 42

  def testBracketErrorInAcquisition =
    unsafeRunSync(IO.bracket(TaskExampleError)(_ => IO.unit)(_ => IO.unit)) must_=== Exit.Failure(fail(ExampleError))

  def testBracketErrorInRelease =
    unsafeRunSync(IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit)) must_=== Exit.Failure(die(ExampleError))

  def testBracketErrorInUsage =
    unsafeRunSync(Task.bracket(Task.unit)(_ => Task.unit)(_ => Task.fail(ExampleError): Task[Unit])) must_=== Exit
      .Failure(fail(ExampleError))

  def testBracketRethrownCaughtErrorInAcquisition = {
    val io = IO.absolve(IO.bracket(TaskExampleError)(_ => IO.unit)(_ => IO.unit).either)

    unsafeRunSync(io) must_=== Exit.Failure(fail(ExampleError))
  }

  def testBracketRethrownCaughtErrorInRelease = {
    val io = IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit)

    unsafeRunSync(io) must_=== Exit.Failure(die(ExampleError))
  }

  def testBracketRethrownCaughtErrorInUsage = {
    val io = IO.absolve(IO.unit.bracket_(IO.unit)(TaskExampleError).either)

    unsafeRunSync(io) must_=== Exit.Failure(fail(ExampleError))
  }

  def testEvalOfAsyncAttemptOfFail = {
    val io1 = IO.unit.bracket_(AsyncUnit[Nothing])(asyncExampleError[Unit])
    val io2 = AsyncUnit[Throwable].bracket_(IO.unit)(asyncExampleError[Unit])

    unsafeRunSync(io1) must_=== Exit.Failure(fail(ExampleError))
    unsafeRunSync(io2) must_=== Exit.Failure(fail(ExampleError))
    unsafeRunSync(IO.absolve(io1.either)) must_=== Exit.Failure(fail(ExampleError))
    unsafeRunSync(IO.absolve(io2.either)) must_=== Exit.Failure(fail(ExampleError))
  }

  def testBracketRegression1 = {
    def makeLogger: Ref[List[String]] => String => UIO[Unit] =
      (ref: Ref[List[String]]) => (line: String) => ref.update(_ ::: List(line)).unit

    unsafeRun(for {
      ref <- Ref.make[List[String]](Nil)
      log = makeLogger(ref)
      f <- ZIO
            .bracket(
              ZIO.bracket(ZIO.unit)(_ => log("start 1") *> clock.sleep(10.millis) *> log("release 1"))(
                _ => ZIO.unit
              )
            )(_ => log("start 2") *> clock.sleep(10.millis) *> log("release 2"))(_ => ZIO.unit)
            .fork
      _ <- (ref.get <* clock.sleep(1.millis)).repeat(ZSchedule.doUntil[List[String]](_.contains("start 1")))
      _ <- f.interrupt
      _ <- (ref.get <* clock.sleep(1.millis)).repeat(ZSchedule.doUntil[List[String]](_.contains("release 2")))
      l <- ref.get
    } yield l) must_=== ("start 1" :: "release 1" :: "start 2" :: "release 2" :: Nil)
  }

  def testInterruptWaitsForFinalizer =
    unsafeRun(for {
      r  <- Ref.make(false)
      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Int]
      s <- (p1.succeed(()) *> p2.await)
            .ensuring(r.set(true) *> clock.sleep(10.millis))
            .fork
      _    <- p1.await
      _    <- s.interrupt
      test <- r.get
    } yield test must_=== true)

  def testRunInterruptIsInterrupted =
    unsafeRun(for {
      p    <- Promise.make[Nothing, Unit]
      f    <- (p.succeed(()) *> IO.never).run.fork
      _    <- p.await
      _    <- f.interrupt
      test <- f.await.map(_.interrupted)
    } yield test) must_=== true

  def testRunSwallowsInnerInterrupt =
    unsafeRun(for {
      p   <- Promise.make[Nothing, Int]
      _   <- IO.interrupt.run *> p.succeed(42)
      res <- p.await
    } yield res) must_=== 42

  def testTimeoutOfLongComputation =
    aroundTimeout(10.milliseconds.asScala)(ee)
      .around(
        unsafeRun(
          clock.sleep(60.seconds) *> UIO(true)
        )
      )
      .message must_== "TIMEOUT: 10000000 nanoseconds"

  def testCatchAllCause =
    unsafeRun((for {
      _ <- ZIO succeed 42
      f <- ZIO fail "Uh oh!"
    } yield f) catchAllCause ZIO.succeed) must_=== Fail("Uh oh!")

  def testFromFutureDoesNotKillFiber = {
    val e = new RuntimeException("Foo")
    unsafeRun(ZIO.fromFuture(_ => throw e).either) must_=== Left(e)
  }

  def testEvalOfDeepSyncEffect = {
    def incLeft(n: Int, ref: Ref[Int]): Task[Int] =
      if (n <= 0) ref.get
      else incLeft(n - 1, ref) <* ref.update(_ + 1)

    def incRight(n: Int, ref: Ref[Int]): Task[Int] =
      if (n <= 0) ref.get
      else ref.update(_ + 1) *> incRight(n - 1, ref)

    val l = unsafeRun(for {
      ref <- Ref.make(0)
      v   <- incLeft(100, ref)
    } yield v)

    val r = unsafeRun(for {
      ref <- Ref.make(0)
      v   <- incRight(1000, ref)
    } yield v)

    (l must_=== 0) and (r must_=== 1000)
  }

  def testDeepMapOfNow =
    unsafeRun(deepMapNow(10000)) must_=== 10000

  def testDeepMapOfSyncEffectIsStackSafe =
    unsafeRun(deepMapEffect(10000)) must_=== 10000

  def testDeepAttemptIsStackSafe =
    unsafeRun((0 until 10000).foldLeft(IO.effect[Unit](())) { (acc, _) =>
      acc.either.unit
    }) must_=== (())

  def testDeepFlatMapIsStackSafe = {
    def fib(n: Int, a: BigInt = 0, b: BigInt = 1): IO[Error, BigInt] =
      IO.succeed(a + b).flatMap { b2 =>
        if (n > 0)
          fib(n - 1, b, b2)
        else
          IO.succeed(b2)
      }

    val future = fib(1000)
    unsafeRun(future) must_=== BigInt(
      "113796925398360272257523782552224175572745930353730513145086634176691092536145985470146129334641866902783673042322088625863396052888690096969577173696370562180400527049497109023054114771394568040040412172632376"
    )
  }

  def testDeepAbsolveAttemptIsIdentity =
    unsafeRun((0 until 1000).foldLeft(IO.succeed[Int](42))((acc, _) => IO.absolve(acc.either))) must_=== 42

  def testDeepAsyncAbsolveAttemptIsIdentity =
    unsafeRun(
      (0 until 1000)
        .foldLeft(IO.effectAsync[Int, Int](k => k(IO.succeed(42))))((acc, _) => IO.absolve(acc.either))
    ) must_=== 42

  def testAsyncEffectReturns =
    unsafeRun(IO.effectAsync[Throwable, Int](k => k(IO.succeed(42)))) must_=== 42

  def testAsyncIOEffectReturns =
    unsafeRun(IO.effectAsyncM[Throwable, Int](k => IO.effectTotal(k(IO.succeed(42))))) must_=== 42

  def testDeepAsyncIOThreadStarvation = {
    def stackIOs(clock: Clock.Service[Any], count: Int): UIO[Int] =
      if (count <= 0) IO.succeed(42)
      else asyncIO(clock, stackIOs(clock, count - 1))

    def asyncIO(clock: Clock.Service[Any], cont: UIO[Int]): UIO[Int] =
      IO.effectAsyncM[Nothing, Int] { k =>
        clock.sleep(5.millis) *> cont *> IO.effectTotal(k(IO.succeed(42)))
      }

    val procNum = java.lang.Runtime.getRuntime.availableProcessors()

    unsafeRun(clock.clockService.flatMap(stackIOs(_, procNum + 1))) must_=== 42
  }

  def testAsyncPureInterruptRegister =
    unsafeRun(for {
      release <- Promise.make[Nothing, Unit]
      acquire <- Promise.make[Nothing, Unit]
      fiber <- IO
                .effectAsyncM[Nothing, Unit] { _ =>
                  IO.bracket(acquire.succeed(()))(_ => release.succeed(()))(_ => IO.never)
                }
                .fork
      _ <- acquire.await
      _ <- fiber.interrupt.fork
      a <- release.await
    } yield a) must_=== (())

  def testEffectAsyncMCanFail =
    unsafeRun {
      ZIO
        .effectAsyncM[Any, String, Nothing](_ => ZIO.fail("Ouch"))
        .flip
        .map(_ must_=== "Ouch")
    }

  def testEffectAsyncMCanDefect =
    unsafeRun {
      ZIO
        .effectAsyncM[Any, String, Unit](_ => ZIO.effectTotal(throw new Error("Ouch")))
        .run
        .map(_.fold(_.defects.headOption.map(_.getMessage), _ => None))
        .map(_ must beSome("Ouch"))
    }

  def testAsyncSecondCallback =
    unsafeRun(for {
      _ <- IO.effectAsync[Throwable, Int] { k =>
            k(IO.succeed(42))
            Thread.sleep(500)
            k(IO.succeed(42))
          }
      res <- IO.effectAsync[Throwable, String] { k =>
              Thread.sleep(1000)
              k(IO.succeed("ok"))
            }
    } yield res) must_=== "ok"

  def testSleepZeroReturns =
    unsafeRun(clock.sleep(1.nanos)) must_=== ((): Unit)

  def testShallowBindOfAsyncChainIsCorrect = {
    val result = (0 until 10).foldLeft[Task[Int]](IO.succeed[Int](0)) { (acc, _) =>
      acc.flatMap(n => IO.effectAsync[Throwable, Int](_(IO.succeed(n + 1))))
    }

    unsafeRun(result) must_=== 10
  }

  def testForkJoinIsId =
    unsafeRun(IO.succeed[Int](42).fork.flatMap(_.join)) must_=== 42

  def testDeepForkJoinIsId = {
    val n = 20

    unsafeRun(concurrentFib(n)) must_=== fib(n)
  }

  def testNeverIsInterruptible = {
    val io =
      for {
        fiber <- IO.never.fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testBracketAcquireIsUninterruptible = {
    val io =
      for {
        promise <- Promise.make[Nothing, Unit]
        fiber   <- IO.bracket(promise.succeed(()) <* IO.never)(_ => IO.unit)(_ => IO.unit).fork
        res     <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 42
  }

  def testBracket0AcquireIsUninterruptible = {
    val io =
      for {
        promise <- Promise.make[Nothing, Unit]
        fiber <- IO
                  .bracketExit(promise.succeed(()) *> IO.never *> IO.succeed(1))((_, _: Exit[_, _]) => IO.unit)(
                    _ => IO.unit: IO[Nothing, Unit]
                  )
                  .fork
        res <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 42
  }

  def testBracketReleaseOnInterrupt = {
    val io =
      for {
        p1    <- Promise.make[Nothing, Unit]
        p2    <- Promise.make[Nothing, Unit]
        fiber <- IO.bracket(IO.unit)(_ => p2.succeed(()) *> IO.unit)(_ => p1.succeed(()) *> IO.never).fork
        _     <- p1.await
        _     <- fiber.interrupt
        _     <- p2.await
      } yield ()

    unsafeRun(io.timeoutTo(42)(_ => 0)(1.second)) must_=== 0
  }

  def testBracket0ReleaseOnInterrupt =
    unsafeRun(for {
      done <- Promise.make[Nothing, Unit]
      fiber <- withLatch { release =>
                IO.bracketExit(IO.unit)((_, _: Exit[_, _]) => done.succeed(()))(
                    _ => release *> IO.never
                  )
                  .fork
              }

      _ <- fiber.interrupt
      r <- done.await.timeoutTo(42)(_ => 0)(60.second)
    } yield r must_=== 0)

  def testRedeemEnsuringInterrupt = {
    val io = for {
      cont <- Promise.make[Nothing, Unit]
      p1   <- Promise.make[Nothing, Boolean]
      f1   <- (cont.succeed(()) *> IO.never).catchAll(IO.fail).ensuring(p1.succeed(true)).fork
      _    <- cont.await
      _    <- f1.interrupt
      res  <- p1.await
    } yield res

    unsafeRun(io) must_=== true
  }

  def testFinalizerCanDetectInterruption = {
    val io = for {
      p1  <- Promise.make[Nothing, Boolean]
      c   <- Promise.make[Nothing, Unit]
      f1  <- (c.succeed(()) *> IO.never).ensuring(IO.descriptor.flatMap(d => p1.succeed(d.interrupted))).fork
      _   <- c.await
      _   <- f1.interrupt
      res <- p1.await
    } yield res

    unsafeRun(io) must_=== true
  }

  def testInterruptedOfRaceInterruptsContestents = {
    val io = for {
      ref   <- Ref.make(0)
      cont1 <- Promise.make[Nothing, Unit]
      cont2 <- Promise.make[Nothing, Unit]
      make  = (p: Promise[Nothing, Unit]) => (p.succeed(()) *> IO.never).onInterrupt(ref.update(_ + 1))
      raced <- (make(cont1) race (make(cont2))).fork
      _     <- cont1.await *> cont2.await
      _     <- raced.interrupt
      count <- ref.get
    } yield count

    unsafeRun(io) must_=== 2
  }

  def testCancelationIsGuaranteed = {
    val io = for {
      release <- zio.Promise.make[Nothing, Int]
      latch   = internal.OneShot.make[Unit]
      async = IO.effectAsyncInterrupt[Nothing, Unit] { _ =>
        latch.set(()); Left(release.succeed(42).unit)
      }
      fiber  <- async.fork
      _      <- IO.effectTotal(latch.get(1000))
      _      <- fiber.interrupt.fork
      result <- release.await
    } yield result

    nonFlaky(io.map(_ must_=== 42))
  }

  def testInterruptionOfUnendingBracket = {
    val io = for {
      startLatch <- Promise.make[Nothing, Int]
      exitLatch  <- Promise.make[Nothing, Int]
      bracketed = IO
        .succeed(21)
        .bracketExit(
          (r: Int, exit: Exit[_, _]) =>
            if (exit.interrupted) exitLatch.succeed(r)
            else IO.die(new Error("Unexpected case"))
        )(a => startLatch.succeed(a) *> IO.never *> IO.succeed(1))
      fiber      <- bracketed.fork
      startValue <- startLatch.await
      _          <- fiber.interrupt.fork
      exitValue  <- exitLatch.await
    } yield startValue + exitValue

    nonFlaky(io.map(_ must_=== 42))
  }

  def testRecoveryOfErrorInFinalizer =
    unsafeRun(for {
      recovered <- Ref.make(false)
      fiber <- withLatch { release =>
                (release *> ZIO.never)
                  .ensuring(
                    (ZIO.unit *> ZIO.fail("Uh oh")).catchAll(_ => recovered.set(true))
                  )
                  .fork
              }
      _     <- fiber.interrupt
      value <- recovered.get
    } yield value must_=== true)

  def testRecoveryOfInterruptible =
    unsafeRun(for {
      recovered <- Ref.make(false)
      fiber <- withLatch { release =>
                (release *> ZIO.never.interruptible)
                  .foldCauseM(
                    cause => recovered.set(cause.interrupted),
                    _ => recovered.set(false)
                  )
                  .uninterruptible
                  .fork
              }
      _     <- fiber.interrupt
      value <- recovered.get
    } yield value must_=== true)

  def testSandboxOfInterruptible =
    unsafeRun(for {
      recovered <- Ref.make[Option[Either[Cause[Nothing], Any]]](None)
      fiber <- withLatch { release =>
                (release *> ZIO.never.interruptible).sandbox.either
                  .flatMap(exit => recovered.set(Some(exit)))
                  .uninterruptible
                  .fork
              }
      _     <- fiber.interrupt
      value <- recovered.get
    } yield value must_=== Some(Left(Cause.interrupt)))

  def testRunOfInterruptible =
    unsafeRun(for {
      recovered <- Ref.make[Option[Exit[Nothing, Any]]](None)
      fiber <- withLatch { release =>
                (release *> ZIO.never.interruptible).run
                  .flatMap(exit => recovered.set(Some(exit)))
                  .uninterruptible
                  .fork
              }
      _     <- fiber.interrupt
      value <- recovered.get
    } yield value must_=== Some(Exit.Failure(Cause.interrupt)))

  def testAlternatingInterruptibility =
    unsafeRun(for {
      counter <- Ref.make(0)
      fiber <- withLatch { release =>
                ((((release *> ZIO.never.interruptible.run *> counter
                  .update(_ + 1)).uninterruptible).interruptible).run
                  *> counter.update(_ + 1)).uninterruptible.fork
              }
      _     <- fiber.interrupt
      value <- counter.get
    } yield value must_=== 2)

  def testInterruptionAfterDefect =
    unsafeRun(for {
      ref <- Ref.make(false)
      fiber <- withLatch { release =>
                (ZIO.effect(throw new Error).run *> release *> ZIO.never)
                  .ensuring(ref.set(true))
                  .fork
              }
      _     <- fiber.interrupt
      value <- ref.get
    } yield value must_=== true)

  def testInterruptionAfterDefect2 =
    unsafeRun(for {
      ref <- Ref.make(false)
      fiber <- withLatch { release =>
                (ZIO.effect(throw new Error).run *> release *> ZIO.unit.forever)
                  .ensuring(ref.set(true))
                  .fork
              }
      _     <- fiber.interrupt
      value <- ref.get
    } yield value must_=== true)

  def testCauseReflectsInterruption =
    nonFlaky {
      for {
        finished <- Ref.make(false)
        fiber <- withLatch { release =>
                  (release *> ZIO.fail("foo")).catchAll(_ => finished.set(true)).fork
                }
        exit     <- fiber.interrupt
        finished <- finished.get
      } yield (exit.interrupted must_=== true) or (finished must_=== true)
    }

  def testAsyncCanBeUninterruptible =
    unsafeRun(for {
      ref <- Ref.make(false)
      fiber <- withLatch { release =>
                (release *> clock.sleep(10.millis) *> ref.set(true).unit).uninterruptible.fork
              }
      _     <- fiber.interrupt
      value <- ref.get
    } yield value must_=== true)

  def testUseInheritsInterruptStatus =
    unsafeRun(
      for {
        ref <- Ref.make(false)
        fiber1 <- withLatch { (release2, await2) =>
                   withLatch { release1 =>
                     release1
                       .bracket_(ZIO.unit, await2 *> clock.sleep(10.millis) *> ref.set(true))
                       .uninterruptible
                       .fork
                   } <* release2
                 }
        _     <- fiber1.interrupt
        value <- ref.get
      } yield value must_=== true
    )

  def testCauseUseInheritsInterruptStatus =
    unsafeRun(
      for {
        latch1 <- Promise.make[Nothing, Unit]
        latch2 <- Promise.make[Nothing, Unit]
        ref    <- Ref.make(false)
        fiber1 <- latch1
                   .succeed(())
                   .bracketExit[Clock, Nothing, Unit](
                     (_: Boolean, _: Exit[_, _]) => ZIO.unit,
                     (_: Boolean) => latch2.await *> clock.sleep(10.millis) *> ref.set(true).unit
                   )
                   .uninterruptible
                   .fork
        _     <- latch1.await
        _     <- latch2.succeed(())
        _     <- fiber1.interrupt
        value <- ref.get
      } yield value must_=== true
    )

  def testProvideIsModular = {
    val zio =
      (for {
        v1 <- ZIO.environment[Int]
        v2 <- ZIO.environment[Int].provide(2)
        v3 <- ZIO.environment[Int]
      } yield (v1, v2, v3)).provide(4)
    unsafeRun(zio) must_=== ((4, 2, 4))
  }

  def testProvideManagedIsModular = {
    def managed(v: Int): ZManaged[Any, Nothing, Int] =
      ZManaged.make(IO.succeed(v))(_ => IO.effectTotal { () })
    val zio = (for {
      v1 <- ZIO.environment[Int]
      v2 <- ZIO.environment[Int].provideManaged(managed(2))
      v3 <- ZIO.environment[Int]
    } yield (v1, v2, v3)).provideManaged(managed(4))

    unsafeRun(zio) must_=== ((4, 2, 4))
  }

  def testAsyncCanUseEnvironment = unsafeRun {
    for {
      result <- ZIO
                 .effectAsync[Int, Nothing, Int] { cb =>
                   cb(ZIO.environment[Int])
                 }
                 .provide(10)
    } yield result must_=== 10
  }

  def testInterruptStatusIsHeritable = nonFlaky {
    for {
      latch <- Promise.make[Nothing, Unit]
      ref   <- Ref.make(InterruptStatus.interruptible)
      _     <- ZIO.uninterruptible((ZIO.checkInterruptible(ref.set) *> latch.succeed(())).fork *> latch.await)
      v     <- ref.get
    } yield v must_=== InterruptStatus.uninterruptible
  }

  def testExecutorIsHeritable =
    nonFlaky(for {
      ref  <- Ref.make(Option.empty[internal.Executor])
      exec = internal.Executor.fromExecutionContext(100)(scala.concurrent.ExecutionContext.Implicits.global)
      _    <- withLatch(release => IO.descriptor.map(_.executor).flatMap(e => ref.set(Some(e)) *> release).fork.lock(exec))
      v    <- ref.get
    } yield v must_=== Some(exec))

  def testSupervisionIsHeritable = nonFlaky {
    for {
      latch <- Promise.make[Nothing, Unit]
      ref   <- Ref.make(SuperviseStatus.unsupervised)
      _     <- ((ZIO.checkSupervised(ref.set) *> latch.succeed(())).fork *> latch.await).supervised
      v     <- ref.get
    } yield v must_=== SuperviseStatus.Supervised
  }

  def testSupervisingInheritance = {
    def forkAwaitStart[A](io: UIO[A], refs: Ref[List[Fiber[_, _]]]): UIO[Fiber[Nothing, A]] =
      withLatch(release => (release *> io).fork.tap(f => refs.update(f :: _)))

    nonFlaky(
      (for {
        ref  <- Ref.make[List[Fiber[_, _]]](Nil) // To make strong ref
        _    <- forkAwaitStart(forkAwaitStart(forkAwaitStart(IO.succeed(()), ref), ref), ref)
        fibs <- ZIO.children
      } yield fibs must have size 1).supervised
    )
  }

  def testAsyncPureIsInterruptible = {
    val io =
      for {
        fiber <- IO.effectAsyncM[Nothing, Nothing](_ => IO.never).fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testAsyncIsInterruptible = {
    val io =
      for {
        fiber <- IO.effectAsync[Nothing, Nothing](_ => ()).fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testAsyncPureCreationIsInterruptible = {
    val io = for {
      release <- Promise.make[Nothing, Int]
      acquire <- Promise.make[Nothing, Unit]
      task = IO.effectAsyncM[Nothing, Unit] { _ =>
        IO.bracket(acquire.succeed(()))(_ => release.succeed(42).unit)(_ => IO.never)
      }
      fiber <- task.fork
      _     <- acquire.await
      _     <- fiber.interrupt
      a     <- release.await
    } yield a

    unsafeRun(io) must_=== 42
  }

  def testAsync0RunsCancelTokenOnInterrupt = {
    val io = for {
      release <- Promise.make[Nothing, Int]
      latch   = scala.concurrent.Promise[Unit]()
      async = IO.effectAsyncInterrupt[Nothing, Nothing] { _ =>
        latch.success(()); Left(release.succeed(42).unit)
      }
      fiber <- async.fork
      _ <- IO.effectAsync[Throwable, Unit] { k =>
            latch.future.onComplete {
              case Success(a) => k(IO.succeed(a))
              case Failure(t) => k(IO.fail(t))
            }(scala.concurrent.ExecutionContext.global)
          }
      _      <- fiber.interrupt
      result <- release.await
    } yield result

    unsafeRun(io) must_=== 42
  }

  def testBracketUseIsInterruptible = {
    val io =
      for {
        fiber <- IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.never).fork
        res   <- fiber.interrupt
      } yield res
    unsafeRun(io) must_=== Exit.interrupt
  }

  def testBracket0UseIsInterruptible = {
    val io =
      for {
        fiber <- IO.bracketExit(IO.unit)((_, _: Exit[_, _]) => IO.unit)(_ => IO.never).fork
        res   <- fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 0
  }

  def testSupervising = {
    def forkAwaitStart(ref: Ref[List[Fiber[_, _]]]) =
      withLatch(release => (release *> UIO.never).fork.tap(fiber => ref.update(fiber :: _)))

    flaky(
      (for {
        ref   <- Ref.make(List.empty[Fiber[_, _]])
        fibs0 <- ZIO.children
        _     <- forkAwaitStart(ref)
        fibs1 <- ZIO.children
        _     <- forkAwaitStart(ref)
        fibs2 <- ZIO.children
      } yield (fibs0 must have size (0)) and (fibs1 must have size (1)) and (fibs2 must have size (2))).supervised
    )
  }

  def testSupervisingUnsupervised =
    flaky(
      for {
        ref  <- Ref.make(Option.empty[Fiber[_, _]])
        _    <- withLatch(release => (release *> UIO.never).fork.tap(fiber => ref.set(Some(fiber))))
        fibs <- ZIO.children
      } yield fibs must have size (0)
    )

  def testSuperviseRTS = {
    def makeChild(n: Int, fibers: Ref[List[Fiber[_, _]]]) =
      (clock.sleep(20.millis * n.toDouble) *> IO.unit).fork.tap(fiber => fibers.update(fiber :: _))

    flaky(for {
      fibers  <- Ref.make(List.empty[Fiber[_, _]])
      counter <- Ref.make(0)
      _ <- (makeChild(1, fibers) *> makeChild(2, fibers)).handleChildrenWith { fs =>
            fs.foldLeft(IO.unit)((io, f) => io *> f.join.either *> counter.update(_ + 1).unit)
          }
      value <- counter.get
    } yield value must_=== 2)
  }

  def testSuperviseRace =
    flaky(for {
      pa <- Promise.make[Nothing, Int]
      pb <- Promise.make[Nothing, Int]

      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Unit]
      f <- (
            p1.succeed(()).bracket_(pa.succeed(1).unit)(IO.never) race
              p2.succeed(()).bracket_(pb.succeed(2).unit)(IO.never)
          ).interruptChildren.fork
      _ <- p1.await *> p2.await

      _ <- f.interrupt
      r <- pa.await zip pb.await
    } yield r must_=== (1 -> 2))

  def testSuperviseFork =
    flaky(for {
      pa <- Promise.make[Nothing, Int]
      pb <- Promise.make[Nothing, Int]

      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Unit]
      f <- (
            p1.succeed(()).bracket_(pa.succeed(1).unit)(IO.never).fork *>
              p2.succeed(()).bracket_(pb.succeed(2).unit)(IO.never).fork *>
              IO.never
          ).interruptChildren.fork
      _ <- p1.await *> p2.await

      _ <- f.interrupt
      r <- pa.await zip pb.await
    } yield r must_=== (1 -> 2))

  def testSupervised =
    flaky(for {
      pa <- Promise.make[Nothing, Int]
      pb <- Promise.make[Nothing, Int]
      _ <- (for {
            p1 <- Promise.make[Nothing, Unit]
            p2 <- Promise.make[Nothing, Unit]
            _  <- p1.succeed(()).bracket_(pa.succeed(1).unit)(IO.never).fork
            _  <- p2.succeed(()).bracket_(pb.succeed(2).unit)(IO.never).fork
            _  <- p1.await *> p2.await
          } yield ()).interruptChildren
      r <- pa.await zip pb.await
    } yield r must_=== (1 -> 2))

  def testRaceChoosesWinner =
    unsafeRun(IO.fail(42).race(IO.succeed(24)).either) must_=== Right(24)

  def testRaceChoosesWinnerInTerminate =
    unsafeRun(IO.die(new Throwable {}).race(IO.succeed(24)).either) must_=== Right(24)

  def testRaceChoosesFailure =
    unsafeRun(IO.fail(42).race(IO.fail(42)).either) must_=== Left(42)

  def testRaceOfValueNever =
    unsafeRun(IO.effectTotal(42).race(IO.never)) must_=== 42

  def testRaceOfFailNever =
    unsafeRun(IO.fail(24).race(IO.never).timeout(10.milliseconds)) must beNone

  def testRaceAllOfValues =
    unsafeRun(IO.raceAll(IO.fail(42), List(IO.succeed(24))).either) must_=== Right(24)

  def testRaceAllOfFailures =
    unsafeRun(ZIO.raceAll(IO.fail(24).delay(10.millis), List(IO.fail(24))).either) must_=== Left(24)

  def testRaceAllOfFailuresOneSuccess =
    unsafeRun(ZIO.raceAll(IO.fail(42), List(IO.succeed(24).delay(1.millis))).either) must_=== Right(
      24
    )

  def testRaceBothInterruptsLoser =
    unsafeRun(for {
      s      <- Semaphore.make(0L)
      effect <- Promise.make[Nothing, Int]
      winner = s.acquire *> IO.effectAsync[Throwable, Unit](_(IO.unit))
      loser  = IO.bracket(s.release)(_ => effect.succeed(42).unit)(_ => IO.never)
      race   = winner raceEither loser
      _      <- race.either
      b      <- effect.await
    } yield b) must_=== 42

  def testFirstSuccessOfValues =
    unsafeRun(IO.firstSuccessOf(IO.fail(0), List(IO.succeed(100))).either) must_=== Right(100)

  def testFirstSuccessOfFailures =
    unsafeRun(ZIO.firstSuccessOf(IO.fail(0).delay(10.millis), List(IO.fail(101))).either) must_=== Left(101)

  def testFirstSuccessOfFailuresOneSuccess =
    unsafeRun(ZIO.firstSuccessOf(IO.fail(0), List(IO.succeed(102).delay(1.millis))).either) must_=== Right(
      102
    )

  def testRepeatedPar = {
    def countdown(n: Int): UIO[Int] =
      if (n == 0) IO.succeed(0)
      else IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => countdown(n - 1).map(y => t._1 + t._2 + y))

    unsafeRun(countdown(50)) must_=== 150
  }

  def testRaceAttemptInterruptsLoserOnSuccess =
    unsafeRun(for {
      s      <- Promise.make[Nothing, Unit]
      effect <- Promise.make[Nothing, Int]
      winner = s.await *> IO.fromEither(Right(()))
      loser  = IO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => IO.never)
      race   = winner raceAttempt loser
      _      <- race.either
      b      <- effect.await
    } yield b) must_=== 42

  def testRaceAttemptInterruptsLoserOnFailure =
    unsafeRun(for {
      s      <- Promise.make[Nothing, Unit]
      effect <- Promise.make[Nothing, Int]
      winner = s.await *> IO.fromEither(Left(new Exception))
      loser  = IO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => IO.never)
      race   = winner raceAttempt loser
      _      <- race.either
      b      <- effect.await
    } yield b) must_=== 42

  def testPar =
    nonFlaky {
      IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => IO.succeed(t._1 + t._2)).map(_ must_=== 3)
    }

  def testReduceAll =
    unsafeRun(
      IO.reduceAll(IO.effectTotal(1), List(2, 3, 4).map(IO.succeed[Int](_)))(_ + _)
    ) must_=== 10

  def testReduceAllEmpty =
    unsafeRun(
      IO.reduceAll(IO.effectTotal(1), Seq.empty)(_ + _)
    ) must_=== 1

  def testTimeoutFailure =
    unsafeRun(
      IO.fail("Uh oh").timeout(1.hour)
    ) must throwA[FiberFailure]

  def testTimeoutTerminate =
    unsafeRunSync(
      IO.die(ExampleError).timeout(1.hour): ZIO[Clock, Nothing, Option[Int]]
    ) must_=== Exit.die(ExampleError)

  def testDeadlockRegression = {

    import java.util.concurrent.Executors

    val rts = new DefaultRuntime {}

    val e = Executors.newSingleThreadExecutor()

    (0 until 10000).foreach { _ =>
      rts.unsafeRun {
        IO.effectAsync[Nothing, Int] { k =>
          val c: Callable[Unit] = () => k(IO.succeed(1))
          val _                 = e.submit(c)
        }
      }
    }

    e.shutdown() must_=== (())
  }

  def testInterruptionRegression1 = {

    val c = new AtomicInteger(0)

    def test =
      IO.effect {
        if (c.incrementAndGet() <= 1) throw new RuntimeException("x")
      }.forever
        .ensuring(IO.unit)
        .either
        .forever

    unsafeRun(
      for {
        f <- test.fork
        c <- (IO.effectTotal[Int](c.get) <* clock.sleep(1.millis)).repeat(ZSchedule.doUntil[Int](_ >= 1)) <* f.interrupt
      } yield c must be_>=(1)
    )

  }

  def testOneMaxYield = {
    val rts = new DefaultRuntime {
      override val Platform = PlatformLive.Default.withExecutor(PlatformLive.ExecutorUtil.makeDefault(1))
    }

    rts.unsafeRun(
      for {
        _ <- UIO.unit
        _ <- UIO.unit
      } yield true
    )
  }

  def testBlockingThreadCaching = {
    import zio.blocking.Blocking

    def runAndTrack(ref: Ref[Set[Thread]]): ZIO[Blocking with Clock, Nothing, Boolean] =
      blocking.blocking {
        UIO(Thread.currentThread()).flatMap(thread => ref.modify(set => (set.contains(thread), set + thread))) <* ZIO
          .sleep(1.millis)
      }

    unsafeRun(for {
      accum <- Ref.make(Set.empty[Thread])
      b     <- runAndTrack(accum).repeat(Schedule.doUntil[Boolean](_ == true))
    } yield b must_=== true)
  }

  def testBlockingIOIsEffectBlocking = unsafeRun(
    for {
      done  <- Ref.make(false)
      start <- IO.succeed(internal.OneShot.make[Unit])
      fiber <- blocking.effectBlocking { start.set(()); Thread.sleep(60L * 60L * 1000L) }.ensuring(done.set(true)).fork
      _     <- IO.succeed(start.get())
      res   <- fiber.interrupt
      value <- done.get
    } yield (res, value) must_=== ((Exit.interrupt, true))
  )

  def testInterruptSyncForever = unsafeRun(
    for {
      f <- IO.effectTotal[Int](1).forever.fork
      _ <- f.interrupt
    } yield true
  )

  // Utility stuff
  val ExampleError    = new Exception("Oh noes!")
  val InterruptCause1 = new Exception("Oh noes 1!")
  val InterruptCause2 = new Exception("Oh noes 2!")
  val InterruptCause3 = new Exception("Oh noes 3!")

  val TaskExampleError: Task[Int] = IO.fail[Throwable](ExampleError)

  def asyncExampleError[A]: Task[A] =
    IO.effectAsync[Throwable, A](_(IO.fail(ExampleError)))

  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  def deepMapNow(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.succeed(0))
  }

  def deepMapEffect(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.effectTotal(0))
  }

  def deepErrorEffect(n: Int): Task[Unit] =
    if (n == 0) IO.effect(throw ExampleError)
    else IO.unit *> deepErrorEffect(n - 1)

  def deepErrorFail(n: Int): Task[Unit] =
    if (n == 0) IO.fail(ExampleError)
    else IO.unit *> deepErrorFail(n - 1)

  def fib(n: Int): BigInt =
    if (n <= 1) n
    else fib(n - 1) + fib(n - 2)

  def concurrentFib(n: Int): Task[BigInt] =
    if (n <= 1) IO.succeed[BigInt](n)
    else
      for {
        f1 <- concurrentFib(n - 1).fork
        f2 <- concurrentFib(n - 2).fork
        v1 <- f1.join
        v2 <- f2.join
      } yield v1 + v2

  def AsyncUnit[E] = IO.effectAsync[E, Unit](_(IO.unit))

  def testMergeAll =
    unsafeRun(
      IO.mergeAll(List("a", "aa", "aaa", "aaaa").map(IO.succeed[String](_)))(0) { (b, a) =>
        b + a.length
      }
    ) must_=== 10

  def testMergeAllEmpty =
    unsafeRun(
      IO.mergeAll(List.empty[UIO[Int]])(0)(_ + _)
    ) must_=== 0
}
