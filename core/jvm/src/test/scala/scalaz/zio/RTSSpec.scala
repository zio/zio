package scalaz.zio

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

import com.github.ghik.silencer.silent
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.describe.Diffable
import scalaz.zio.Exit.Cause
import scalaz.zio.Exit.Cause.{ Die, Fail, Then }
import scalaz.zio.duration._
import scalaz.zio.clock.Clock

import scala.annotation.tailrec
import scala.util.{ Failure, Success }

class RTSSpec(implicit ee: ExecutionEnv) extends TestRuntime {

  def is = {
    s2"""
  RTS synchronous correctness
    widen Nothing                           $testWidenNothing
    evaluation of point                     $testPoint
    point must be lazy                      $testPointIsLazy
    now must be eager                       $testNowIsEager
    suspend must be lazy                    $testSuspendIsLazy
    suspend must be evaluatable             $testSuspendIsEvaluatable
    point, bind, map                        $testSyncEvalLoop
    effect, bind, map                       $testSyncEvalLoopEffect
    effect, bind, map, redeem               $testSyncEvalLoopEffectThrow
    sync effect                             $testEvalOfSyncEffect
    sync on defer                           $testManualSyncOnDefer
    deep effects                            $testEvalOfDeepSyncEffect
    flip must make error into value         $testFlipError
    flip must make value into error         $testFlipValue
    flipping twice returns identical value  $testFlipDouble

  RTS failure
    error in sync effect                    $testEvalOfRedeemOfSyncEffectError
    attempt . fail                          $testEvalOfAttemptOfFail
    deep attempt sync effect error          $testAttemptOfDeepSyncEffectError
    deep attempt fail error                 $testAttemptOfDeepFailError
    attempt . sandbox . terminate           $testSandboxAttemptOfTerminate
    fold . sandbox . terminate              $testSandboxFoldOfTerminate
    catch sandbox terminate                 $testSandboxTerminate
    uncaught fail                           $testEvalOfUncaughtFail
    uncaught fail supervised                $testEvalOfUncaughtFailSupervised
    uncaught sync effect error              $testEvalOfUncaughtThrownSyncEffect
    uncaught supervised sync effect error   $testEvalOfUncaughtThrownSupervisedSyncEffect
    deep uncaught sync effect error         $testEvalOfDeepUncaughtThrownSyncEffect
    deep uncaught fail                      $testEvalOfDeepUncaughtFail
    catch failing finalizers with fail      $testFailOfMultipleFailingFinalizers
    catch failing finalizers with terminate $testTerminateOfMultipleFailingFinalizers
    run preserves interruption status       $testRunInterruptIsInterrupted
    run swallows inner interruption         $testRunSwallowsInnerInterrupt
    timeout a long computation              $testTimeoutOfLongComputation

  RTS finalizers
    fail ensuring                           $testEvalOfFailEnsuring
    fail on error                           $testEvalOfFailOnError
    finalizer errors not caught             $testErrorInFinalizerCannotBeCaught
    finalizer errors reported               $testErrorInFinalizerIsReported
    bracket exit is usage result            $testExitIsUsageResult
    error in just acquisition               $testBracketErrorInAcquisition
    error in just release                   $testBracketErrorInRelease
    error in just usage                     $testBracketErrorInUsage
    rethrown caught error in acquisition    $testBracketRethrownCaughtErrorInAcquisition
    rethrown caught error in release        $testBracketRethrownCaughtErrorInRelease
    rethrown caught error in usage          $testBracketRethrownCaughtErrorInUsage
    test eval of async fail                 $testEvalOfAsyncAttemptOfFail
    bracket regression 1                    $testBracketRegression1
    interrupt waits for finalizer           $testInterruptWaitsForFinalizer

  RTS synchronous stack safety
    deep map of point                       $testDeepMapOfPoint
    deep map of now                         $testDeepMapOfNow
    deep map of sync effect                 $testDeepMapOfSyncEffectIsStackSafe
    deep attempt                            $testDeepAttemptIsStackSafe
    deep flatMap                            $testDeepFlatMapIsStackSafe
    deep absolve/attempt is identity        $testDeepAbsolveAttemptIsIdentity
    deep async absolve/attempt is identity  $testDeepAsyncAbsolveAttemptIsIdentity

  RTS asynchronous correctness
    simple async must return                $testAsyncEffectReturns
    simple asyncIO must return              $testAsyncIOEffectReturns
    deep asyncIO doesn't block threads      $testDeepAsyncIOThreadStarvation
    interrupt of asyncPure register         $testAsyncPureInterruptRegister
    sleep 0 must return                     $testSleepZeroReturns
    shallow bind of async chain             $testShallowBindOfAsyncChainIsCorrect
    effectAsyncM can fail before registering $testEffectAsyncMCanFail

  RTS concurrency correctness
    shallow fork/join identity              $testForkJoinIsId
    deep fork/join identity                 $testDeepForkJoinIsId
    interrupt of never                      $testNeverIsInterruptible
    asyncPure is interruptible              $testAsyncPureIsInterruptible
    async is interruptible                  $testAsyncIsInterruptible
    bracket acquire is uninterruptible      $testBracketAcquireIsUninterruptible
    bracket0 acquire is uninterruptible     $testBracket0AcquireIsUninterruptible
    bracket use is interruptible            $testBracketUseIsInterruptible
    bracket0 use is interruptible           $testBracket0UseIsInterruptible
    bracket release called on interrupt     $testBracketReleaseOnInterrupt
    bracket0 release called on interrupt    $testBracket0ReleaseOnInterrupt
    asyncPure creation is interruptible     $testAsyncPureCreationIsInterruptible
    asyncInterrupt runs cancel token on interrupt   $testAsync0RunsCancelTokenOnInterrupt
    redeem + ensuring + interrupt           $testRedeemEnsuringInterrupt
    supervising returns fiber refs          $testSupervising
    supervising in unsupervised returns Nil $testSupervisingUnsupervised
    supervise fibers                        $testSupervise
    supervise fibers in supervised          $testSupervised
    supervise fibers in race                $testSuperviseRace
    supervise fibers in fork                $testSuperviseFork
    race of fail with success               $testRaceChoosesWinner
    race of terminate with success          $testRaceChoosesWinnerInTerminate
    race of fail with fail                  $testRaceChoosesFailure
    race of value & never                   $testRaceOfValueNever
    raceAll of values                       $testRaceAllOfValues
    raceAll of failures                     $testRaceAllOfFailures
    raceAll of failures & one success       $testRaceAllOfFailuresOneSuccess
    raceAttempt interrupts loser            $testRaceAttemptInterruptsLoser
    par regression                          $testPar
    par of now values                       $testRepeatedPar
    mergeAll                                $testMergeAll
    mergeAllEmpty                           $testMergeAllEmpty
    reduceAll                               $testReduceAll
    reduceAll Empty List                    $testReduceAllEmpty
    timeout of failure                      $testTimeoutFailure
    timeout of terminate                    $testTimeoutTerminate

  RTS regression tests
    deadlock regression 1                   $testDeadlockRegression
    check interruption regression 1         $testInterruptionRegression1
    manual sync interruption                $testManualSyncInterruption

  RTS interruption
    blocking IO is effect blocking          $testBlockingIOIsEffectBlocking
    sync forever is interruptible           $testInterruptSyncForever
    interrupt of never                      $testNeverIsInterruptible
    asyncPure is interruptible              $testAsyncPureIsInterruptible
    async is interruptible                  $testAsyncIsInterruptible
    bracket is uninterruptible              $testBracketAcquireIsUninterruptible
    bracket0 is uninterruptible             $testBracket0AcquireIsUninterruptible
    bracket use is interruptible            $testBracketUseIsInterruptible
    bracket0 use is interruptible           $testBracket0UseIsInterruptible
    bracket release called on interrupt     $testBracketReleaseOnInterrupt
    bracket0 release called on interrupt    $testBracket0ReleaseOnInterrupt
    redeem + ensuring + interrupt           $testRedeemEnsuringInterrupt
    finalizer can detect interruption       $testFinalizerCanDetectInterruption
    interruption of raced                   $testInterruptedOfRaceInterruptsContestents
    cancelation is guaranteed               $testCancelationIsGuaranteed
    interruption of unending bracket        $testInterruptionOfUnendingBracket
    recovery of error in finalizer          $testRecoveryOfErrorInFinalizer
    recovery of interruptible               $testRecoveryOfInterruptible
    sandbox of interruptible                $testSandboxOfInterruptible
    run of interruptible                    $testRunOfInterruptible
    alternating interruptibility            $testAlternatingInterruptibility
    interruption after defect               $testInterruptionAfterDefect
    interruption after defect 2             $testInterruptionAfterDefect2
    cause reflects interruption             $testCauseReflectsInterruption
    bracket use inherits interrupt status   $testUseInheritsInterruptStatus
    bracket use inherits interrupt status 2 $testCauseUseInheritsInterruptStatus
    async can be uninterruptible            $testAsyncCanBeUninterruptible

  RTS environment
    provide is modular                      $testProvideIsModular
    effectAsync can use environment         $testAsyncCanUseEnvironment
  """
  }

  def testPoint =
    unsafeRun(BIO.succeedLazy(1)) must_=== 1

  def testWidenNothing = {
    val op1 = BIO.effectTotal[String]("1")
    val op2 = BIO.effectTotal[String]("2")

    val result: BIO[RuntimeException, String] = for {
      r1 <- op1
      r2 <- op2
    } yield r1 + r2

    unsafeRun(result) must_=== "12"
  }

  def testPointIsLazy =
    BIO.succeedLazy(throw new Error("Not lazy")) must not(throwA[Throwable])

  @silent
  def testNowIsEager =
    BIO.succeed(throw new Error("Eager")) must (throwA[Error])

  def testSuspendIsLazy =
    BIO.suspend(throw new Error("Eager")) must not(throwA[Throwable])

  def testSuspendIsEvaluatable =
    unsafeRun(BIO.suspend(BIO.succeedLazy[Int](42))) must_=== 42

  def testSyncEvalLoop = {
    def fibIo(n: Int): Task[BigInt] =
      if (n <= 1) BIO.succeedLazy(n)
      else
        for {
          a <- fibIo(n - 1)
          b <- fibIo(n - 2)
        } yield a + b

    unsafeRun(fibIo(10)) must_=== fib(10)
  }

  def testSyncEvalLoopEffect = {
    def fibIo(n: Int): Task[BigInt] =
      if (n <= 1) BIO.effect(n)
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
    val io    = BIO.fail(error).flip
    unsafeRun(io) must_=== error
  }

  def testFlipValue = {
    implicit val d
      : Diffable[Right[Nothing, Int]] = Diffable.eitherRightDiffable[Int] //    TODO: Dotty has ambiguous implicits
    val io                            = BIO.succeed(100).flip
    unsafeRun(io.either) must_=== Left(100)
  }

  def testFlipDouble = {
    val io = BIO.succeedLazy(100)
    unsafeRun(io.flip.flip) must_=== unsafeRun(io)
  }

  def testEvalOfSyncEffect = {
    def sumIo(n: Int): Task[Int] =
      if (n <= 0) BIO.effectTotal(0)
      else BIO.effectTotal(n).flatMap(b => sumIo(n - 1).map(a => a + b))

    unsafeRun(sumIo(1000)) must_=== sum(1000)
  }

  def testManualSyncOnDefer = {
    def sync[A](effect: => A): BIO[Throwable, A] =
      BIO.effectTotal(effect)
        .foldCauseM({
          case Cause.Die(t) => BIO.fail(t)
          case cause        => BIO.halt(cause)
        }, BIO.succeed(_))

    def putStrLn(text: String): BIO[Throwable, Unit] =
      sync(println(text))

    unsafeRun(putStrLn("Hello")) must_=== (())
  }

  @silent
  def testEvalOfRedeemOfSyncEffectError =
    unsafeRun(
      BIO.effect[Unit](throw ExampleError).fold[Option[Throwable]](Some(_), _ => None)
    ) must_=== Some(ExampleError)

  def testEvalOfAttemptOfFail = Seq(
    unsafeRun(TaskExampleError.either) must_=== Left(ExampleError),
    unsafeRun(BIO.suspend(BIO.suspend(TaskExampleError).either)) must_=== Left(
      ExampleError
    )
  )

  def testSandboxAttemptOfTerminate =
    unsafeRun(BIO.effectTotal[Int](throw ExampleError).sandbox.either) must_=== Left(Die(ExampleError))

  def testSandboxFoldOfTerminate =
    unsafeRun(
      BIO.effectTotal[Int](throw ExampleError).sandbox.fold(Some(_), Function.const(None))
    ) must_=== Some(Die(ExampleError))

  def testSandboxTerminate =
    unsafeRun(
      BIO.effectTotal[Cause[Any]](throw ExampleError)
        .sandbox
        .fold[Cause[Any]](identity, identity)
    ) must_=== Die(ExampleError)

  def testAttemptOfDeepSyncEffectError =
    unsafeRun(deepErrorEffect(100).either) must_=== Left(ExampleError)

  def testAttemptOfDeepFailError =
    unsafeRun(deepErrorFail(100).either) must_=== Left(ExampleError)

  def testEvalOfUncaughtFail =
    unsafeRun(Task.fail(ExampleError): Task[Any]) must (throwA(FiberFailure(Fail(ExampleError))))

  def testEvalOfUncaughtFailSupervised =
    unsafeRun(Task.fail(ExampleError).supervise: Task[Unit]) must (throwA(
      FiberFailure(Fail(ExampleError))
    ))

  def testEvalOfUncaughtThrownSyncEffect =
    unsafeRun(BIO.effectTotal[Int](throw ExampleError)) must (throwA(FiberFailure(Die(ExampleError))))

  def testEvalOfUncaughtThrownSupervisedSyncEffect =
    unsafeRun(BIO.effectTotal[Int](throw ExampleError).supervise) must (throwA(FiberFailure(Die(ExampleError))))

  def testEvalOfDeepUncaughtThrownSyncEffect =
    unsafeRun(deepErrorEffect(100)) must (throwA(FiberFailure(Fail(ExampleError))))

  def testEvalOfDeepUncaughtFail =
    unsafeRun(deepErrorEffect(100)) must (throwA(FiberFailure(Fail(ExampleError))))

  def testFailOfMultipleFailingFinalizers =
    unsafeRun(
      TaskExampleError
        .ensuring(BIO.effectTotal(throw InterruptCause1))
        .ensuring(BIO.effectTotal(throw InterruptCause2))
        .ensuring(BIO.effectTotal(throw InterruptCause3))
        .run
    ) must_=== Exit.halt(
      Cause.fail(ExampleError) ++
        Cause.die(InterruptCause1) ++
        Cause.die(InterruptCause2) ++
        Cause.die(InterruptCause3)
    )

  def testTerminateOfMultipleFailingFinalizers =
    unsafeRun(
      BIO.die(ExampleError)
        .ensuring(BIO.effectTotal(throw InterruptCause1))
        .ensuring(BIO.effectTotal(throw InterruptCause2))
        .ensuring(BIO.effectTotal(throw InterruptCause3))
        .run
    ) must_=== Exit.halt(
      Cause.die(ExampleError) ++
        Cause.die(InterruptCause1) ++
        Cause.die(InterruptCause2) ++
        Cause.die(InterruptCause3)
    )

  def testEvalOfFailEnsuring = {
    var finalized = false

    unsafeRun((Task.fail(ExampleError): Task[Unit]).ensuring(BIO.effectTotal[Unit] { finalized = true; () })) must (throwA(
      FiberFailure(Fail(ExampleError))
    ))
    finalized must_=== true
  }

  def testEvalOfFailOnError = {
    @volatile var finalized = false
    val cleanup: Cause[Throwable] => UIO[Unit] =
      _ => BIO.effectTotal[Unit] { finalized = true; () }

    unsafeRun(
      Task.fail(ExampleError).onError(cleanup): Task[Unit]
    ) must (throwA(FiberFailure(Fail(ExampleError))))

    // FIXME: Is this an issue with thread synchronization?
    while (!finalized) Thread.`yield`()

    finalized must_=== true
  }

  def testErrorInFinalizerCannotBeCaught = {

    val e2 = new Error("e2")
    val e3 = new Error("e3")

    val nested: Task[Int] =
      TaskExampleError
        .ensuring(BIO.die(e2))
        .ensuring(BIO.die(e3))

    unsafeRun(nested) must (throwA(FiberFailure(Then(Fail(ExampleError), Then(Die(e2), Die(e3))))))
  }

  def testErrorInFinalizerIsReported = {
    @volatile var reported: Exit[Nothing, Int] = null

    unsafeRun {
      BIO.succeedLazy[Int](42)
        .ensuring(BIO.die(ExampleError))
        .fork
        .flatMap(_.await.flatMap[Any, Nothing, Any](e => UIO.effectTotal { reported = e }))
    }

    reported must_=== Exit.Failure(Die(ExampleError))
  }

  def testExitIsUsageResult =
    unsafeRun(BIO.bracket(BIO.unit)(_ => BIO.unit)(_ => BIO.succeedLazy[Int](42))) must_=== 42

  def testBracketErrorInAcquisition =
    unsafeRun(BIO.bracket(TaskExampleError)(_ => BIO.unit)(_ => BIO.unit)) must
      (throwA(FiberFailure(Fail(ExampleError))))

  def testBracketErrorInRelease =
    unsafeRun(BIO.bracket(BIO.unit)(_ => BIO.die(ExampleError))(_ => BIO.unit)) must
      (throwA(FiberFailure(Die(ExampleError))))

  def testBracketErrorInUsage =
    unsafeRun(Task.bracket(Task.unit)(_ => Task.unit)(_ => Task.fail(ExampleError): Task[Unit])) must
      (throwA(FiberFailure(Fail(ExampleError))))

  def testBracketRethrownCaughtErrorInAcquisition = {
    lazy val actual = unsafeRun(
      BIO.absolve(BIO.bracket(TaskExampleError)(_ => BIO.unit)(_ => BIO.unit).either)
    )

    actual must (throwA(FiberFailure(Fail(ExampleError))))
  }

  def testBracketRethrownCaughtErrorInRelease = {
    lazy val actual = unsafeRun(
      BIO.bracket(BIO.unit)(_ => BIO.die(ExampleError))(_ => BIO.unit)
    )

    actual must (throwA(FiberFailure(Die(ExampleError))))
  }

  def testBracketRethrownCaughtErrorInUsage = {
    lazy val actual: Int = unsafeRun(
      BIO.absolve(
        BIO.unit
          .bracket_[Any, Nothing]
          .apply[Any](BIO.unit)(TaskExampleError)
          .either //    TODO: Dotty doesn't infer this properly
      )
    )

    actual must (throwA(FiberFailure(Fail(ExampleError))))
  }

  def testEvalOfAsyncAttemptOfFail = {
    val io1 = BIO.unit.bracket_[Any, Nothing].apply[Any](AsyncUnit[Nothing])(asyncExampleError[Unit]) //    TODO: Dotty doesn't infer this properly
    val io2 = AsyncUnit[Throwable].bracket_[Any, Throwable].apply[Any](BIO.unit)(asyncExampleError[Unit])

    unsafeRun(io1) must (throwA(FiberFailure(Fail(ExampleError))))
    unsafeRun(io2) must (throwA(FiberFailure(Fail(ExampleError))))
    unsafeRun(BIO.absolve(io1.either)) must (throwA(FiberFailure(Fail(ExampleError))))
    unsafeRun(BIO.absolve(io2.either)) must (throwA(FiberFailure(Fail(ExampleError))))
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
      f    <- (p.succeed(()) *> BIO.never).run.fork
      _    <- p.await
      _    <- f.interrupt
      test <- f.await.map(_.interrupted)
    } yield test) must_=== true

  def testRunSwallowsInnerInterrupt =
    unsafeRun(for {
      p   <- Promise.make[Nothing, Int]
      _   <- BIO.interrupt.run *> p.succeed(42)
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

  def testDeepMapOfPoint =
    unsafeRun(deepMapPoint(10000)) must_=== 10000

  def testDeepMapOfNow =
    unsafeRun(deepMapNow(10000)) must_=== 10000

  def testDeepMapOfSyncEffectIsStackSafe =
    unsafeRun(deepMapEffect(10000)) must_=== 10000

  def testDeepAttemptIsStackSafe =
    unsafeRun((0 until 10000).foldLeft(BIO.effect[Unit](())) { (acc, _) =>
      acc.either.unit
    }) must_=== (())

  def testDeepFlatMapIsStackSafe = {
    def fib(n: Int, a: BigInt = 0, b: BigInt = 1): BIO[Error, BigInt] =
      BIO.succeed(a + b).flatMap { b2 =>
        if (n > 0)
          fib(n - 1, b, b2)
        else
          BIO.succeed(b2)
      }

    val future = fib(1000)
    unsafeRun(future) must_=== BigInt(
      "113796925398360272257523782552224175572745930353730513145086634176691092536145985470146129334641866902783673042322088625863396052888690096969577173696370562180400527049497109023054114771394568040040412172632376"
    )
  }

  def testDeepAbsolveAttemptIsIdentity =
    unsafeRun((0 until 1000).foldLeft(BIO.succeedLazy[Int](42))((acc, _) => BIO.absolve(acc.either))) must_=== 42

  def testDeepAsyncAbsolveAttemptIsIdentity =
    unsafeRun(
      (0 until 1000)
        .foldLeft(BIO.effectAsync[Any, Int, Int](k => k(BIO.succeed(42))))((acc, _) => BIO.absolve(acc.either))
    ) must_=== 42

  def testAsyncEffectReturns =
    unsafeRun(BIO.effectAsync[Any, Throwable, Int](k => k(BIO.succeed(42)))) must_=== 42

  def testAsyncIOEffectReturns =
    unsafeRun(BIO.effectAsyncM[Any, Throwable, Int](k => BIO.effectTotal(k(BIO.succeed(42))))) must_=== 42

  def testDeepAsyncIOThreadStarvation = {
    def stackIOs(clock: Clock.Service[Any], count: Int): UIO[Int] =
      if (count <= 0) BIO.succeed(42)
      else asyncIO(clock, stackIOs(clock, count - 1))

    def asyncIO(clock: Clock.Service[Any], cont: UIO[Int]): UIO[Int] =
      BIO.effectAsyncM[Any, Nothing, Int] { k =>
        clock.sleep(5.millis) *> cont *> BIO.effectTotal(k(BIO.succeed(42)))
      }

    val procNum = java.lang.Runtime.getRuntime.availableProcessors()

    unsafeRun(clock.clockService.flatMap(stackIOs(_, procNum + 1))) must_=== 42
  }

  def testAsyncPureInterruptRegister =
    unsafeRun(for {
      release <- Promise.make[Nothing, Unit]
      acquire <- Promise.make[Nothing, Unit]
      fiber <- BIO
                .effectAsyncM[Any, Nothing, Unit] { _ =>
                  BIO.bracket(acquire.succeed(()))(_ => release.succeed(()))(_ => BIO.never)
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

  def testSleepZeroReturns =
    unsafeRun(clock.sleep(1.nanos)) must_=== ((): Unit)

  def testShallowBindOfAsyncChainIsCorrect = {
    val result = (0 until 10).foldLeft[Task[Int]](BIO.succeedLazy[Int](0)) { (acc, _) =>
      acc.flatMap(n => BIO.effectAsync[Any, Throwable, Int](_(BIO.succeed(n + 1))))
    }

    unsafeRun(result) must_=== 10
  }

  def testForkJoinIsId =
    unsafeRun(BIO.succeedLazy[Int](42).fork.flatMap(_.join)) must_=== 42

  def testDeepForkJoinIsId = {
    val n = 20

    unsafeRun(concurrentFib(n)) must_=== fib(n)
  }

  def testNeverIsInterruptible = {
    val io =
      for {
        fiber <- BIO.never.fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testBracketAcquireIsUninterruptible = {
    val io =
      for {
        promise <- Promise.make[Nothing, Unit]
        fiber   <- BIO.bracket(promise.succeed(()) <* BIO.never)(_ => BIO.unit)(_ => BIO.unit).fork
        res     <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 42
  }

  def testBracket0AcquireIsUninterruptible = {
    val io =
      for {
        promise <- Promise.make[Nothing, Unit]
        fiber <- BIO
                  .bracketExit(promise.succeed(()) *> BIO.never *> BIO.succeed(1))((_, _: Exit[_, _]) => BIO.unit)(
                    _ => BIO.unit: BIO[Nothing, Unit]
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
        fiber <- BIO.bracket(BIO.unit)(_ => p2.succeed(()) *> BIO.unit)(_ => p1.succeed(()) *> BIO.never).fork
        _     <- p1.await
        _     <- fiber.interrupt
        _     <- p2.await
      } yield ()

    unsafeRun(io.timeoutTo(42)(_ => 0)(1.second)) must_=== 0
  }

  def testBracket0ReleaseOnInterrupt = {
    val io =
      for {
        p1 <- Promise.make[Nothing, Unit]
        p2 <- Promise.make[Nothing, Unit]
        fiber <- BIO
                  .bracketExit(BIO.unit)((_, _: Exit[_, _]) => p2.succeed(()) *> BIO.unit)(
                    _ => p1.succeed(()) *> BIO.never
                  )
                  .fork
        _ <- p1.await
        _ <- fiber.interrupt
        _ <- p2.await
      } yield ()

    unsafeRun(io.timeoutTo(42)(_ => 0)(1.second)) must_=== 0
  }

  def testRedeemEnsuringInterrupt = {
    val io = for {
      cont <- Promise.make[Nothing, Unit]
      p1   <- Promise.make[Nothing, Boolean]
      f1   <- (cont.succeed(()) *> BIO.never).catchAll(BIO.fail).ensuring(p1.succeed(true)).fork
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
      f1  <- (c.succeed(()) *> BIO.never).ensuring(BIO.descriptor.flatMap(d => p1.succeed(d.interrupted))).fork
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
      make  = (p: Promise[Nothing, Unit]) => (p.succeed(()) *> BIO.never).onInterrupt(ref.update(_ + 1))
      raced <- (make(cont1) race (make(cont2))).fork
      _     <- cont1.await *> cont2.await
      _     <- raced.interrupt
      count <- ref.get
    } yield count

    unsafeRun(io) must_=== 2
  }

  def testCancelationIsGuaranteed = {
    val io = for {
      release <- scalaz.zio.Promise.make[Nothing, Int]
      latch   = internal.OneShot.make[Unit]
      async = BIO.effectAsyncInterrupt[Any, Nothing, Unit] { _ =>
        latch.set(()); Left(release.succeed(42).unit)
      }
      fiber  <- async.fork
      _      <- BIO.effectTotal(latch.get(1000))
      _      <- fiber.interrupt.fork
      result <- release.await
    } yield result

    (0 to 1000).map { _ =>
      unsafeRun(io) must_=== 42
    }.reduce(_ and _)
  }

  def testInterruptionOfUnendingBracket = {
    val io = for {
      startLatch <- Promise.make[Nothing, Int]
      exitLatch  <- Promise.make[Nothing, Int]
      bracketed = BIO
        .succeed(21)
        .bracketExit[Any, Error, Int]( // TODO: Dotty doesn't infer curried version
          (r: Int, exit: Exit[Error, Int]) =>
            if (exit.interrupted) exitLatch.succeed(r)
            else BIO.die(new Error("Unexpected case")),
          (a: Int) => startLatch.succeed(a) *> BIO.never *> BIO.succeed(1)
        )
      fiber      <- bracketed.fork
      startValue <- startLatch.await
      _          <- fiber.interrupt.fork
      exitValue  <- exitLatch.await
    } yield startValue + exitValue

    (0 to 100).map { _ =>
      unsafeRun(io) must_=== 42
    }.reduce(_ and _)
  }

  def testRecoveryOfErrorInFinalizer =
    unsafeRun(for {
      startLatch <- Promise.make[Nothing, Unit]
      recovered  <- Ref.make(false)
      fiber <- (startLatch.succeed(()) *> ZIO.never)
                .ensuring(
                  (ZIO.unit *> ZIO.fail("Uh oh")).catchAll(_ => recovered.set(true))
                )
                .fork
      _     <- startLatch.await
      _     <- fiber.interrupt
      value <- recovered.get
    } yield value must_=== true)

  def testRecoveryOfInterruptible =
    unsafeRun(for {
      startLatch <- Promise.make[Nothing, Unit]
      recovered  <- Ref.make(false)
      fiber <- (startLatch.succeed(()) *> ZIO.never.interruptible)
                .foldCauseM(
                  cause => recovered.set(cause.interrupted),
                  _ => recovered.set(false)
                )
                .uninterruptible
                .fork
      _     <- startLatch.await
      _     <- fiber.interrupt
      value <- recovered.get
    } yield value must_=== true)

  def testSandboxOfInterruptible =
    unsafeRun(for {
      startLatch <- Promise.make[Nothing, Unit]
      recovered  <- Ref.make[Option[Either[Cause[Nothing], Any]]](None)
      fiber <- (startLatch.succeed(()) *> ZIO.never.interruptible).sandbox.either
                .flatMap(exit => recovered.set(Some(exit)))
                .uninterruptible
                .fork
      _     <- startLatch.await
      _     <- fiber.interrupt
      value <- recovered.get
    } yield value must_=== Some(Left(Cause.interrupt)))

  def testRunOfInterruptible =
    unsafeRun(for {
      startLatch <- Promise.make[Nothing, Unit]
      recovered  <- Ref.make[Option[Exit[Nothing, Any]]](None)
      fiber <- (startLatch.succeed(()) *> ZIO.never.interruptible).run
                .flatMap(exit => recovered.set(Some(exit)))
                .uninterruptible
                .fork
      _     <- startLatch.await
      _     <- fiber.interrupt
      value <- recovered.get
    } yield value must_=== Some(Exit.Failure(Cause.interrupt)))

  def testAlternatingInterruptibility =
    unsafeRun(for {
      startLatch <- Promise.make[Nothing, Unit]
      counter    <- Ref.make(0)
      fiber <- ((((startLatch.succeed(()) *> ZIO.never.interruptible.run *> counter
                .update(_ + 1)).uninterruptible).interruptible).run
                *> counter.update(_ + 1)).uninterruptible.fork
      _     <- startLatch.await
      _     <- fiber.interrupt
      value <- counter.get
    } yield value must_=== 2)

  def testInterruptionAfterDefect =
    unsafeRun(for {
      startLatch <- Promise.make[Nothing, Unit]
      ref        <- Ref.make(false)
      fiber <- (ZIO.succeedLazy(throw new Error).run *> startLatch.succeed(()) *> ZIO.never)
                .ensuring(ref.set(true))
                .fork
      _     <- startLatch.await
      _     <- fiber.interrupt
      value <- ref.get
    } yield value must_=== true)

  def testInterruptionAfterDefect2 =
    unsafeRun(for {
      startLatch <- Promise.make[Nothing, Unit]
      ref        <- Ref.make(false)
      fiber <- (ZIO.succeedLazy(throw new Error).run *> startLatch.succeed(()) *> ZIO.unit.forever)
                .ensuring(ref.set(true))
                .fork
      _     <- startLatch.await
      _     <- fiber.interrupt
      value <- ref.get
    } yield value must_=== true)

  def testCauseReflectsInterruption = {
    val result = (1 to 100).map { _ =>
      unsafeRun(for {
        startLatch <- Promise.make[Nothing, Unit]
        finished   <- Ref.make(false)
        fiber      <- (startLatch.succeed(()) *> ZIO.fail("foo")).catchAll(_ => finished.set(true)).fork
        _          <- startLatch.await
        exit       <- fiber.interrupt
        finished   <- finished.get
      } yield (exit.interrupted must_=== true) or (finished must_=== true))
    }.reduce(_ and _)

    result
  }

  def testAsyncCanBeUninterruptible =
    unsafeRun(for {
      startLatch <- Promise.make[Nothing, Unit]
      ref        <- Ref.make(false)
      fiber      <- (startLatch.succeed(()) *> clock.sleep(10.millis) *> ref.set(true).unit).uninterruptible.fork
      _          <- startLatch.await
      _          <- fiber.interrupt
      value      <- ref.get
    } yield value must_=== true)

  def testUseInheritsInterruptStatus =
    unsafeRun(
      for {
        latch1 <- Promise.make[Nothing, Unit]
        latch2 <- Promise.make[Nothing, Unit]
        ref    <- Ref.make(false)
        fiber1 <- latch1
                   .succeed(())
                   .bracket_(ZIO.unit, latch2.await *> clock.sleep(10.millis) *> ref.set(true))
                   .uninterruptible
                   .fork
        _     <- latch1.await
        _     <- latch2.succeed(())
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

  def testAsyncCanUseEnvironment = unsafeRun {
    for {
      result <- ZIO
                 .effectAsync[Int, Nothing, Int] { cb =>
                   cb(ZIO.environment[Int])
                 }
                 .provide(10)
    } yield result must_=== 10
  }

  def testAsyncPureIsInterruptible = {
    val io =
      for {
        fiber <- BIO.effectAsyncM[Any, Nothing, Nothing](_ => BIO.never).fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testAsyncIsInterruptible = {
    val io =
      for {
        fiber <- BIO.effectAsync[Any, Nothing, Nothing](_ => ()).fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testAsyncPureCreationIsInterruptible = {
    val io = for {
      release <- Promise.make[Nothing, Int]
      acquire <- Promise.make[Nothing, Unit]
      task = BIO.effectAsyncM[Any, Nothing, Unit] { _ =>
        BIO.bracket(acquire.succeed(()))(_ => release.succeed(42).unit)(_ => BIO.never)
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
      async = BIO.effectAsyncInterrupt[Any, Nothing, Nothing] { _ =>
        latch.success(()); Left(release.succeed(42).unit)
      }
      fiber <- async.fork
      _ <- BIO.effectAsync[Any, Throwable, Unit] { k =>
            latch.future.onComplete {
              case Success(a) => k(BIO.succeed(a))
              case Failure(t) => k(BIO.fail(t))
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
        fiber <- BIO.bracket(BIO.unit)(_ => BIO.unit)(_ => BIO.never).fork
        res   <- fiber.interrupt
      } yield res
    unsafeRun(io) must_=== Exit.interrupt
  }

  def testBracket0UseIsInterruptible = {
    val io =
      for {
        fiber <- BIO.bracketExit(BIO.unit)((_, _: Exit[_, _]) => BIO.unit)(_ => BIO.never).fork
        res   <- fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 0
  }

  def testSupervising = {
    def forkAwaitStart =
      for {
        latch <- Promise.make[Nothing, Unit]
        _     <- (latch.succeed(()) *> UIO.never).fork
        _     <- latch.await
      } yield ()

    unsafeRun(
      (for {
        fibs0 <- ZIO.children
        _     <- forkAwaitStart
        fibs1 <- ZIO.children
        _     <- forkAwaitStart
        fibs2 <- ZIO.children
      } yield (fibs0 must have size (0)) and (fibs1 must have size (1)) and (fibs2 must have size (2))).supervised
    )
  }

  def testSupervisingUnsupervised =
    unsafeRun(
      for {
        latch <- Promise.make[Nothing, Unit]
        _     <- (latch.succeed(()) *> UIO.never).fork
        _     <- latch.await
        fibs  <- ZIO.children
      } yield fibs must have size (0)
    )

  def testSupervise = {
    var counter = 0
    unsafeRun((for {
      _ <- (clock.sleep(200.millis) *> BIO.unit).fork
      _ <- (clock.sleep(400.millis) *> BIO.unit).fork
    } yield ()).superviseWith { fs =>
      fs.foldLeft(BIO.unit)((io, f) => io *> f.join.either *> BIO.effectTotal(counter += 1))
    })
    counter must_=== 2
  }

  def testSuperviseRace =
    unsafeRun(for {
      pa <- Promise.make[Nothing, Int]
      pb <- Promise.make[Nothing, Int]

      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Unit]
      f <- (
            p1.succeed(())
              .bracket_[Any, Nothing]
              .apply[Any](pa.succeed(1).unit)(BIO.never) race //    TODO: Dotty doesn't infer this properly
              p2.succeed(()).bracket_[Any, Nothing].apply[Any](pb.succeed(2).unit)(BIO.never)
          ).supervise.fork
      _ <- p1.await *> p2.await

      _ <- f.interrupt
      r <- pa.await zip pb.await
    } yield r) must_=== (1 -> 2)

  def testSuperviseFork =
    unsafeRun(for {
      pa <- Promise.make[Nothing, Int]
      pb <- Promise.make[Nothing, Int]

      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Unit]
      f <- (
            p1.succeed(())
              .bracket_[Any, Nothing]
              .apply[Any](pa.succeed(1).unit)(BIO.never)
              .fork *> //    TODO: Dotty doesn't infer this properly
              p2.succeed(()).bracket_[Any, Nothing].apply[Any](pb.succeed(2).unit)(BIO.never).fork *>
              BIO.never
          ).supervise.fork
      _ <- p1.await *> p2.await

      _ <- f.interrupt
      r <- pa.await zip pb.await
    } yield r) must_=== (1 -> 2)

  def testSupervised =
    unsafeRun(for {
      pa <- Promise.make[Nothing, Int]
      pb <- Promise.make[Nothing, Int]
      _ <- (for {
            p1 <- Promise.make[Nothing, Unit]
            p2 <- Promise.make[Nothing, Unit]
            _ <- p1
                  .succeed(())
                  .bracket_[Any, Nothing]
                  .apply[Any](pa.succeed(1).unit)(BIO.never)
                  .fork //    TODO: Dotty doesn't infer this properly
            _ <- p2.succeed(()).bracket_[Any, Nothing].apply[Any](pb.succeed(2).unit)(BIO.never).fork
            _ <- p1.await *> p2.await
          } yield ()).supervise
      r <- pa.await zip pb.await
    } yield r) must_=== (1 -> 2)

  def testRaceChoosesWinner =
    unsafeRun(BIO.fail(42).race(BIO.succeed(24)).either) must_=== Right(24)

  def testRaceChoosesWinnerInTerminate = {
    implicit val d
      : Diffable[Right[Nothing, Int]] = Diffable.eitherRightDiffable[Int] //    TODO: Dotty has ambiguous implicits
    unsafeRun(BIO.die(new Throwable {}).race(BIO.succeed(24)).either) must_=== Right(24)
  }

  def testRaceChoosesFailure = {
    implicit val d
      : Diffable[Left[Int, Nothing]] = Diffable.eitherLeftDiffable[Int] //    TODO: Dotty has ambiguous implicits
    unsafeRun(BIO.fail(42).race(BIO.fail(42)).either) must_=== Left(42)
  }

  def testRaceOfValueNever =
    unsafeRun(BIO.succeedLazy(42).race(BIO.never)) must_=== 42

  def testRaceOfFailNever =
    unsafeRun(BIO.fail(24).race(BIO.never).timeout(10.milliseconds)) must beNone

  def testRaceAllOfValues =
    unsafeRun(BIO.raceAll(BIO.fail(42), List(BIO.succeed(24))).either) must_=== Right(24)

  def testRaceAllOfFailures = {
    implicit val d
      : Diffable[Left[Int, Nothing]] = Diffable.eitherLeftDiffable[Int] //    TODO: Dotty has ambiguous implicits
    unsafeRun(ZIO.raceAll(BIO.fail(24).delay(10.millis), List(BIO.fail(24))).either) must_=== Left(24)
  }

  def testRaceAllOfFailuresOneSuccess =
    unsafeRun(ZIO.raceAll(BIO.fail(42), List(BIO.succeed(24).delay(1.millis))).either) must_=== Right(
      24
    )

  def testRaceBothInterruptsLoser =
    unsafeRun(for {
      s      <- Semaphore.make(0L)
      effect <- Promise.make[Nothing, Int]
      winner = s.acquire *> BIO.effectAsync[Any, Throwable, Unit](_(BIO.unit))
      loser  = BIO.bracket(s.release)(_ => effect.succeed(42).unit)(_ => BIO.never)
      race   = winner raceEither loser
      _      <- race.either
      b      <- effect.await
    } yield b) must_=== 42

  def testRepeatedPar = {
    def countdown(n: Int): UIO[Int] =
      if (n == 0) BIO.succeed(0)
      else BIO.succeed[Int](1).zipPar(BIO.succeed[Int](2)).flatMap(t => countdown(n - 1).map(y => t._1 + t._2 + y))

    unsafeRun(countdown(50)) must_=== 150
  }

  def testRaceAttemptInterruptsLoser =
    unsafeRun(for {
      s      <- Promise.make[Nothing, Unit]
      effect <- Promise.make[Nothing, Int]
      winner = s.await *> BIO.fromEither(Left(new Exception))
      loser  = BIO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => BIO.never)
      race   = winner raceAttempt loser
      _      <- race.either
      b      <- effect.await
    } yield b) must_=== 42

  def testPar =
    (0 to 1000).map { _ =>
      unsafeRun(BIO.succeed[Int](1).zipPar(BIO.succeed[Int](2)).flatMap(t => BIO.succeed(t._1 + t._2))) must_=== 3
    }

  def testReduceAll =
    unsafeRun(
      BIO.reduceAll(BIO.succeedLazy(1), List(2, 3, 4).map(BIO.succeedLazy[Int](_)))(_ + _)
    ) must_=== 10

  def testReduceAllEmpty =
    unsafeRun(
      BIO.reduceAll(BIO.succeedLazy(1), Seq.empty)(_ + _)
    ) must_=== 1

  def testTimeoutFailure =
    unsafeRun(
      BIO.fail("Uh oh").timeout(1.hour)
    ) must (throwA[FiberFailure])

  def testTimeoutTerminate =
    unsafeRunSync(
      BIO.die(ExampleError).timeout(1.hour): ZIO[Clock, Nothing, Option[Int]]
    ) must_=== Exit.die(ExampleError)

  def testDeadlockRegression = {

    import java.util.concurrent.Executors

    val rts = new DefaultRuntime {}

    val e = Executors.newSingleThreadExecutor()

    (0 until 10000).foreach { _ =>
      rts.unsafeRun {
        BIO.effectAsync[Any, Nothing, Int] { k =>
          val c: Callable[Unit] = () => k(BIO.succeed(1))
          val _                 = e.submit(c)
        }
      }
    }

    e.shutdown() must_=== (())
  }

  def testInterruptionRegression1 = {

    val c = new AtomicInteger(0)

    def test =
      BIO.effect {
        if (c.incrementAndGet() <= 1) throw new RuntimeException("x")
      }.forever
        .ensuring(BIO.unit)
        .either
        .forever

    unsafeRun(
      for {
        f <- test.fork
        c <- (BIO.effectTotal[Int](c.get) <* clock.sleep(1.millis)).repeat(ZSchedule.doUntil[Int](_ >= 1)) <* f.interrupt
      } yield c must be_>=(1)
    )

  }

  def testManualSyncInterruption = {
    def sync[A](effect: => A): BIO[Throwable, A] =
      BIO.effectTotal(effect)
        .foldCauseM({
          case Cause.Die(t) => BIO.fail(t)
          case cause        => BIO.halt(cause)
        }, BIO.succeed(_))

    def putStr(text: String): BIO[Throwable, Unit] =
      sync(scala.io.StdIn.print(text))

    unsafeRun(
      for {
        fiber <- putStr(".").forever.fork
        _     <- fiber.interrupt
      } yield true
    )
  }

  // FIXME: This is a flaky test!
  def testBlockingThreadCaching = {
    val currentNumLiveWorkers =
      blocking.blockingExecutor.map(_.metrics.get.workersCount)

    unsafeRunSync(for {
      thread1  <- blocking.blocking(BIO.effectTotal(Thread.currentThread()))
      workers1 <- currentNumLiveWorkers
      thread2  <- blocking.blocking(BIO.effectTotal(Thread.currentThread()))
      workers2 <- currentNumLiveWorkers
    } yield workers1 == workers2 && thread1 == thread2) must_=== Exit.Success(true)
  }

  def testBlockingIOIsEffectBlocking = unsafeRun(
    for {
      done  <- Ref.make(false)
      start <- BIO.succeed(internal.OneShot.make[Unit])
      fiber <- blocking.effectBlocking { start.set(()); Thread.sleep(Long.MaxValue) }.ensuring(done.set(true)).fork
      _     <- BIO.succeed(start.get())
      res   <- fiber.interrupt
      value <- done.get
    } yield (res, value) must_=== ((Exit.interrupt, true))
  )

  def testInterruptSyncForever = unsafeRun(
    for {
      f <- BIO.effectTotal[Int](1).forever.fork
      _ <- f.interrupt
    } yield true
  )

  // Utility stuff
  val ExampleError    = new Exception("Oh noes!")
  val InterruptCause1 = new Exception("Oh noes 1!")
  val InterruptCause2 = new Exception("Oh noes 2!")
  val InterruptCause3 = new Exception("Oh noes 3!")

  val TaskExampleError: Task[Int] = BIO.fail[Throwable](ExampleError)

  def asyncExampleError[A]: Task[A] =
    BIO.effectAsync[Any, Throwable, A](_(BIO.fail(ExampleError)))

  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  def deepMapPoint(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, BIO.succeedLazy(0))
  }

  def deepMapNow(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, BIO.succeed(0))
  }

  def deepMapEffect(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, BIO.effectTotal(0))
  }

  def deepErrorEffect(n: Int): Task[Unit] =
    if (n == 0) BIO.effect(throw ExampleError)
    else BIO.unit *> deepErrorEffect(n - 1)

  def deepErrorFail(n: Int): Task[Unit] =
    if (n == 0) BIO.fail(ExampleError)
    else BIO.unit *> deepErrorFail(n - 1)

  def fib(n: Int): BigInt =
    if (n <= 1) n
    else fib(n - 1) + fib(n - 2)

  def concurrentFib(n: Int): Task[BigInt] =
    if (n <= 1) BIO.succeedLazy[BigInt](n)
    else
      for {
        f1 <- concurrentFib(n - 1).fork
        f2 <- concurrentFib(n - 2).fork
        v1 <- f1.join
        v2 <- f2.join
      } yield v1 + v2

  def AsyncUnit[E] = BIO.effectAsync[Any, E, Unit](_(BIO.unit))

  def testMergeAll =
    unsafeRun(
      BIO.mergeAll(List("a", "aa", "aaa", "aaaa").map(BIO.succeedLazy[String](_)))(0) { (b, a) =>
        b + a.length
      }
    ) must_=== 10

  def testMergeAllEmpty =
    unsafeRun(
      BIO.mergeAll(List.empty[UIO[Int]])(0)(_ + _)
    ) must_=== 0
}
