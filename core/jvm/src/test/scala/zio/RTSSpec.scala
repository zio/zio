package zio

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

import com.github.ghik.silencer.silent
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.describe.Diffable
import zio.Cause.{ die, fail, Fail, Then }
import zio.duration._
import zio.clock.Clock

import scala.annotation.tailrec
import scala.util.{ Failure, Success }

class RTSSpec(implicit ee: ExecutionEnv) extends TestRuntime {

  def is = {
    s2"""
  RTS synchronous correctness
    widen Nothing                           $testWidenNothing
    evaluation of point                     $testPoint
    blocking caches threads                 $testBlockingThreadCaching
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
    catchAllCause                           $testCatchAllCause

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
    asyncPure creation is interruptible     $testAsyncPureCreationIsInterruptible
    asyncInterrupt runs cancel token on interrupt   $testAsync0RunsCancelTokenOnInterrupt
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
    raceAttempt interrupts loser on success $testRaceAttemptInterruptsLoserOnSuccess
    raceAttempt interrupts loser on failure $testRaceAttemptInterruptsLoserOnFailure
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
    provideManaged is modular               $testProvideManagedIsModular
    effectAsync can use environment         $testAsyncCanUseEnvironment

  RTS forking inheritability
    interruption status is heritable        $testInterruptStatusIsHeritable
    executor is hereditble                  $testExecutorIsHeritable
    supervision is heritable                $testSupervisionIsHeritable
    supervision inheritance                 $testSupervisingInheritance
  """
  }

  def testPoint =
    unsafeRun(IO.succeedLazy(1)) must_=== 1

  def testWidenNothing = {
    val op1 = IO.effectTotal[String]("1")
    val op2 = IO.effectTotal[String]("2")

    val result: IO[RuntimeException, String] = for {
      r1 <- op1
      r2 <- op2
    } yield r1 + r2

    unsafeRun(result) must_=== "12"
  }

  def testPointIsLazy =
    IO.succeedLazy(throw new Error("Not lazy")) must not(throwA[Throwable])

  @silent
  def testNowIsEager =
    IO.succeed(throw new Error("Eager")) must (throwA[Error])

  def testSuspendIsLazy =
    IO.suspend(throw new Error("Eager")) must not(throwA[Throwable])

  def testSuspendIsEvaluatable =
    unsafeRun(IO.suspend(IO.succeedLazy[Int](42))) must_=== 42

  def testSyncEvalLoop = {
    def fibIo(n: Int): Task[BigInt] =
      if (n <= 1) IO.succeedLazy(n)
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
    implicit val d
      : Diffable[Right[Nothing, Int]] = Diffable.eitherRightDiffable[Int] //    TODO: Dotty has ambiguous implicits
    val io                            = IO.succeed(100).flip
    unsafeRun(io.either) must_=== Left(100)
  }

  def testFlipDouble = {
    val io = IO.succeedLazy(100)
    unsafeRun(io.flip.flip) must_=== unsafeRun(io)
  }

  def testEvalOfSyncEffect = {
    def sumIo(n: Int): Task[Int] =
      if (n <= 0) IO.effectTotal(0)
      else IO.effectTotal(n).flatMap(b => sumIo(n - 1).map(a => a + b))

    unsafeRun(sumIo(1000)) must_=== sum(1000)
  }

  def testManualSyncOnDefer = {
    def sync[A](effect: => A): IO[Throwable, A] =
      IO.effectTotal(effect)
        .foldCauseM({
          case Cause.Die(t) => IO.fail(t)
          case cause        => IO.halt(cause)
        }, IO.succeed(_))

    def putStrLn(text: String): IO[Throwable, Unit] =
      sync(println(text))

    unsafeRun(putStrLn("Hello")) must_=== (())
  }

  @silent
  def testEvalOfRedeemOfSyncEffectError =
    unsafeRun(
      IO.effect[Unit](throw ExampleError).fold[Option[Throwable]](Some(_), _ => None)
    ) must_=== Some(ExampleError)

  def testEvalOfAttemptOfFail = Seq(
    unsafeRun(TaskExampleError.either) must_=== Left(ExampleError),
    unsafeRun(IO.suspend(IO.suspend(TaskExampleError).either)) must_=== Left(
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

  def testErrorInFinalizerIsReported = {
    @volatile var reported: Exit[Nothing, Int] = null

    unsafeRun {
      IO.succeedLazy[Int](42)
        .ensuring(IO.die(ExampleError))
        .fork
        .flatMap(_.await.flatMap[Any, Nothing, Any](e => UIO.effectTotal { reported = e }))
    }

    reported must_=== Exit.Failure(die(ExampleError))
  }

  def testExitIsUsageResult =
    unsafeRun(IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.succeedLazy[Int](42))) must_=== 42

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
    val io =
      IO.absolve(
        IO.unit
          .bracket_[Any, Nothing]
          .apply[Any](IO.unit)(TaskExampleError)
          .either //    TODO: Dotty doesn't infer this properly
      )

    unsafeRunSync(io) must_=== Exit.Failure(fail(ExampleError))
  }

  def testEvalOfAsyncAttemptOfFail = {
    val io1 = IO.unit.bracket_[Any, Nothing].apply[Any](AsyncUnit[Nothing])(asyncExampleError[Unit]) //    TODO: Dotty doesn't infer this properly
    val io2 = AsyncUnit[Throwable].bracket_[Any, Throwable].apply[Any](IO.unit)(asyncExampleError[Unit])

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
    unsafeRun((0 until 1000).foldLeft(IO.succeedLazy[Int](42))((acc, _) => IO.absolve(acc.either))) must_=== 42

  def testDeepAsyncAbsolveAttemptIsIdentity =
    unsafeRun(
      (0 until 1000)
        .foldLeft(IO.effectAsync[Any, Int, Int](k => k(IO.succeed(42))))((acc, _) => IO.absolve(acc.either))
    ) must_=== 42

  def testAsyncEffectReturns =
    unsafeRun(IO.effectAsync[Any, Throwable, Int](k => k(IO.succeed(42)))) must_=== 42

  def testAsyncIOEffectReturns =
    unsafeRun(IO.effectAsyncM[Any, Throwable, Int](k => IO.effectTotal(k(IO.succeed(42))))) must_=== 42

  def testDeepAsyncIOThreadStarvation = {
    def stackIOs(clock: Clock.Service[Any], count: Int): UIO[Int] =
      if (count <= 0) IO.succeed(42)
      else asyncIO(clock, stackIOs(clock, count - 1))

    def asyncIO(clock: Clock.Service[Any], cont: UIO[Int]): UIO[Int] =
      IO.effectAsyncM[Any, Nothing, Int] { k =>
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
                .effectAsyncM[Any, Nothing, Unit] { _ =>
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

  def testSleepZeroReturns =
    unsafeRun(clock.sleep(1.nanos)) must_=== ((): Unit)

  def testShallowBindOfAsyncChainIsCorrect = {
    val result = (0 until 10).foldLeft[Task[Int]](IO.succeedLazy[Int](0)) { (acc, _) =>
      acc.flatMap(n => IO.effectAsync[Any, Throwable, Int](_(IO.succeed(n + 1))))
    }

    unsafeRun(result) must_=== 10
  }

  def testForkJoinIsId =
    unsafeRun(IO.succeedLazy[Int](42).fork.flatMap(_.join)) must_=== 42

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
      async = IO.effectAsyncInterrupt[Any, Nothing, Unit] { _ =>
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
        .bracketExit[Any, Error, Int]( // TODO: Dotty doesn't infer curried version
          (r: Int, exit: Exit[Error, Int]) =>
            if (exit.interrupted) exitLatch.succeed(r)
            else IO.die(new Error("Unexpected case")),
          (a: Int) => startLatch.succeed(a) *> IO.never *> IO.succeed(1)
        )
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
                (ZIO.succeedLazy(throw new Error).run *> release *> ZIO.never)
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
                (ZIO.succeedLazy(throw new Error).run *> release *> ZIO.unit.forever)
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
        fiber <- IO.effectAsyncM[Any, Nothing, Nothing](_ => IO.never).fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testAsyncIsInterruptible = {
    val io =
      for {
        fiber <- IO.effectAsync[Any, Nothing, Nothing](_ => ()).fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testAsyncPureCreationIsInterruptible = {
    val io = for {
      release <- Promise.make[Nothing, Int]
      acquire <- Promise.make[Nothing, Unit]
      task = IO.effectAsyncM[Any, Nothing, Unit] { _ =>
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
      async = IO.effectAsyncInterrupt[Any, Nothing, Nothing] { _ =>
        latch.success(()); Left(release.succeed(42).unit)
      }
      fiber <- async.fork
      _ <- IO.effectAsync[Any, Throwable, Unit] { k =>
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

    unsafeRun(
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
    unsafeRun(
      for {
        ref  <- Ref.make(Option.empty[Fiber[_, _]])
        _    <- withLatch(release => (release *> UIO.never).fork.tap(fiber => ref.set(Some(fiber))))
        fibs <- ZIO.children
      } yield fibs must have size (0)
    )

  def testSupervise = {
    var counter = 0
    unsafeRun((for {
      ref <- Ref.make(List.empty[Fiber[_, _]])
      _   <- (clock.sleep(200.millis) *> IO.unit).fork.tap(fiber => ref.update(fiber :: _))
      _   <- (clock.sleep(400.millis) *> IO.unit).fork.tap(fiber => ref.update(fiber :: _))
    } yield ()).handleChildrenWith { fs =>
      fs.foldLeft(IO.unit)((io, f) => io *> f.join.either *> IO.effectTotal(counter += 1))
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
              .apply[Any](pa.succeed(1).unit)(IO.never) race //    TODO: Dotty doesn't infer this properly
              p2.succeed(()).bracket_[Any, Nothing].apply[Any](pb.succeed(2).unit)(IO.never)
          ).interruptChildren.fork
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
              .apply[Any](pa.succeed(1).unit)(IO.never)
              .fork *> //    TODO: Dotty doesn't infer this properly
              p2.succeed(()).bracket_[Any, Nothing].apply[Any](pb.succeed(2).unit)(IO.never).fork *>
              IO.never
          ).interruptChildren.fork
      _ <- p1.await *> p2.await

      _ <- f.interrupt
      r <- pa.await zip pb.await
    } yield r) must_=== (1 -> 2)

  def testSupervised =
    nonFlaky {
      for {
        pa <- Promise.make[Nothing, Int]
        pb <- Promise.make[Nothing, Int]
        _ <- (for {
              p1 <- Promise.make[Nothing, Unit]
              p2 <- Promise.make[Nothing, Unit]
              _ <- p1
                    .succeed(())
                    .bracket_[Any, Nothing]
                    .apply[Any](pa.succeed(1).unit)(IO.never)
                    .fork //    TODO: Dotty doesn't infer this properly
              _ <- p2.succeed(()).bracket_[Any, Nothing].apply[Any](pb.succeed(2).unit)(IO.never).fork
              _ <- p1.await *> p2.await
            } yield ()).interruptChildren
        r <- pa.await zip pb.await
      } yield r must_=== (1 -> 2)
    }

  def testRaceChoosesWinner =
    unsafeRun(IO.fail(42).race(IO.succeed(24)).either) must_=== Right(24)

  def testRaceChoosesWinnerInTerminate = {
    implicit val d
      : Diffable[Right[Nothing, Int]] = Diffable.eitherRightDiffable[Int] //    TODO: Dotty has ambiguous implicits
    unsafeRun(IO.die(new Throwable {}).race(IO.succeed(24)).either) must_=== Right(24)
  }

  def testRaceChoosesFailure = {
    implicit val d
      : Diffable[Left[Int, Nothing]] = Diffable.eitherLeftDiffable[Int] //    TODO: Dotty has ambiguous implicits
    unsafeRun(IO.fail(42).race(IO.fail(42)).either) must_=== Left(42)
  }

  def testRaceOfValueNever =
    unsafeRun(IO.succeedLazy(42).race(IO.never)) must_=== 42

  def testRaceOfFailNever =
    unsafeRun(IO.fail(24).race(IO.never).timeout(10.milliseconds)) must beNone

  def testRaceAllOfValues =
    unsafeRun(IO.raceAll(IO.fail(42), List(IO.succeed(24))).either) must_=== Right(24)

  def testRaceAllOfFailures = {
    implicit val d
      : Diffable[Left[Int, Nothing]] = Diffable.eitherLeftDiffable[Int] //    TODO: Dotty has ambiguous implicits
    unsafeRun(ZIO.raceAll(IO.fail(24).delay(10.millis), List(IO.fail(24))).either) must_=== Left(24)
  }

  def testRaceAllOfFailuresOneSuccess =
    unsafeRun(ZIO.raceAll(IO.fail(42), List(IO.succeed(24).delay(1.millis))).either) must_=== Right(
      24
    )

  def testRaceBothInterruptsLoser =
    unsafeRun(for {
      s      <- Semaphore.make(0L)
      effect <- Promise.make[Nothing, Int]
      winner = s.acquire *> IO.effectAsync[Any, Throwable, Unit](_(IO.unit))
      loser  = IO.bracket(s.release)(_ => effect.succeed(42).unit)(_ => IO.never)
      race   = winner raceEither loser
      _      <- race.either
      b      <- effect.await
    } yield b) must_=== 42

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
      IO.reduceAll(IO.succeedLazy(1), List(2, 3, 4).map(IO.succeedLazy[Int](_)))(_ + _)
    ) must_=== 10

  def testReduceAllEmpty =
    unsafeRun(
      IO.reduceAll(IO.succeedLazy(1), Seq.empty)(_ + _)
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
        IO.effectAsync[Any, Nothing, Int] { k =>
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

  def testManualSyncInterruption = {
    def sync[A](effect: => A): IO[Throwable, A] =
      IO.effectTotal(effect)
        .foldCauseM({
          case Cause.Die(t) => IO.fail(t)
          case cause        => IO.halt(cause)
        }, IO.succeed(_))

    def putStr(text: String): IO[Throwable, Unit] =
      sync(scala.io.StdIn.print(text))

    unsafeRun(
      for {
        fiber <- putStr(".").forever.fork
        _     <- fiber.interrupt
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
      fiber <- blocking.effectBlocking { start.set(()); Thread.sleep(Long.MaxValue) }.ensuring(done.set(true)).fork
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
    IO.effectAsync[Any, Throwable, A](_(IO.fail(ExampleError)))

  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  def deepMapPoint(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.succeedLazy(0))
  }

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
    if (n <= 1) IO.succeedLazy[BigInt](n)
    else
      for {
        f1 <- concurrentFib(n - 1).fork
        f2 <- concurrentFib(n - 2).fork
        v1 <- f1.join
        v2 <- f2.join
      } yield v1 + v2

  def AsyncUnit[E] = IO.effectAsync[Any, E, Unit](_(IO.unit))

  def testMergeAll =
    unsafeRun(
      IO.mergeAll(List("a", "aa", "aaa", "aaaa").map(IO.succeedLazy[String](_)))(0) { (b, a) =>
        b + a.length
      }
    ) must_=== 10

  def testMergeAllEmpty =
    unsafeRun(
      IO.mergeAll(List.empty[UIO[Int]])(0)(_ + _)
    ) must_=== 0

  def nonFlaky(v: => ZIO[Environment, Any, org.specs2.matcher.MatchResult[Any]]): org.specs2.matcher.MatchResult[Any] =
    (1 to 100).foldLeft[org.specs2.matcher.MatchResult[Any]](true must_=== true) {
      case (acc, _) =>
        acc and unsafeRun(v)
    }
}
