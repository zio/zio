// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import com.github.ghik.silencer.silent
import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.ExitResult.Cause
import scalaz.zio.ExitResult.Cause.{ Checked, Then, Unchecked }
import scalaz.zio.duration._

import scala.util.{ Failure, Success }

class RTSSpec(implicit ee: ExecutionEnv) extends AbstractRTSSpec {

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
    sync effect                             $testEvalOfSyncEffect
    deep effects                            $testEvalOfDeepSyncEffect
    flip must make error into value         $testFlipError
    flip must make value into error         $testFlipValue
    flipping twice returns identical value  $testFlipDouble

  RTS failure
    error in sync effect                    $testEvalOfRedeemOfSyncEffectError
    attempt . fail                          $testEvalOfAttemptOfFail
    deep attempt sync effect error          $testAttemptOfDeepSyncEffectError
    deep attempt fail error                 $testAttemptOfDeepFailError
    attempt . sandboxed . terminate         $testSandboxedAttemptOfTerminate
    redeem . sandboxed . terminate          $testSandboxedRedeemPureOfTerminate
    catch sandboxed terminate               $testSandboxedTerminate
    uncaught fail                           $testEvalOfUncaughtFail
    uncaught fail supervised                $testEvalOfUncaughtFailSupervised
    uncaught sync effect error              $testEvalOfUncaughtThrownSyncEffect
    uncaught supervised sync effect error   $testEvalOfUncaughtThrownSupervisedSyncEffect
    deep uncaught sync effect error         $testEvalOfDeepUncaughtThrownSyncEffect
    deep uncaught fail                      $testEvalOfDeepUncaughtFail
    catch failing finalizers with fail      $testFailOfMultipleFailingFinalizers
    catch failing finalizers with terminate $testTerminateOfMultipleFailingFinalizers
    run preserves interruption status       $testRunInterruptIsInterrupted

  RTS finalizers
    fail ensuring                           $testEvalOfFailEnsuring
    fail on error                           $testEvalOfFailOnError
    finalizer errors not caught             $testErrorInFinalizerCannotBeCaught
    finalizer errors reported               $testErrorInFinalizerIsReported
    bracket result is usage result          $testExitResultIsUsageResult
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
    async0 runs cancel token on interrupt   $testAsync0RunsCancelTokenOnInterrupt
    redeem + ensuring + interrupt           $testRedeemEnsuringInterrupt
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

  RTS interruption
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
  """
  }

  def testPoint =
    unsafeRun(IO.point(1)) must_=== 1

  def testWidenNothing = {
    val op1 = IO.sync[String]("1")
    val op2 = IO.sync[String]("2")

    val result: IO[RuntimeException, String] = for {
      r1 <- op1
      r2 <- op2
    } yield r1 + r2

    unsafeRun(result) must_=== "12"
  }

  def testPointIsLazy =
    IO.point(throw new Error("Not lazy")) must not(throwA[Throwable])

  @silent
  def testNowIsEager =
    IO.now(throw new Error("Eager")) must (throwA[Error])

  def testSuspendIsLazy =
    IO.suspend(throw new Error("Eager")) must not(throwA[Throwable])

  def testSuspendIsEvaluatable =
    unsafeRun(IO.suspend(IO.point[Int](42))) must_=== 42

  def testSyncEvalLoop = {
    def fibIo(n: Int): IO[Throwable, BigInt] =
      if (n <= 1) IO.point(n)
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
    val io = IO.now(100).flip
    unsafeRun(io.attempt) must_=== Left(100)
  }

  def testFlipDouble = {
    val io = IO.point(100)
    unsafeRun(io.flip.flip) must_=== unsafeRun(io)
  }

  def testEvalOfSyncEffect = {
    def sumIo(n: Int): IO[Throwable, Int] =
      if (n <= 0) IO.sync(0)
      else IO.sync(n).flatMap(b => sumIo(n - 1).map(a => a + b))

    unsafeRun(sumIo(1000)) must_=== sum(1000)
  }

  @silent
  def testEvalOfRedeemOfSyncEffectError =
    unsafeRun(
      IO.syncThrowable[Unit](throw ExampleError).redeemPure[Option[Throwable]](Some(_), _ => None)
    ) must_=== Some(ExampleError)

  def testEvalOfAttemptOfFail = Seq(
    unsafeRun(IO.fail[Throwable](ExampleError).attempt) must_=== Left(ExampleError),
    unsafeRun(IO.suspend(IO.suspend(IO.fail[Throwable](ExampleError)).attempt)) must_=== Left(
      ExampleError
    )
  )

  def testSandboxedAttemptOfTerminate =
    unsafeRun(IO.sync[Int](throw ExampleError).sandboxed.attempt) must_=== Left(Unchecked(ExampleError))

  def testSandboxedRedeemPureOfTerminate =
    unsafeRun(
      IO.sync[Int](throw ExampleError).sandboxed.redeemPure(Some(_), Function.const(None))
    ) must_=== Some(Unchecked(ExampleError))

  def testSandboxedTerminate =
    unsafeRun(
      IO.sync[Cause[Any]](throw ExampleError)
        .sandboxed
        .redeemPure(identity, identity)
    ) must_=== Unchecked(ExampleError)

  def testAttemptOfDeepSyncEffectError =
    unsafeRun(deepErrorEffect(100).attempt) must_=== Left(ExampleError)

  def testAttemptOfDeepFailError =
    unsafeRun(deepErrorFail(100).attempt) must_=== Left(ExampleError)

  def testEvalOfUncaughtFail =
    unsafeRun(IO.fail[Throwable](ExampleError).as[Any]) must (throwA(FiberFailure(Checked(ExampleError))))

  def testEvalOfUncaughtFailSupervised =
    unsafeRun(IO.fail[Throwable](ExampleError).supervised.as[Any]) must (throwA(FiberFailure(Checked(ExampleError))))

  def testEvalOfUncaughtThrownSyncEffect =
    unsafeRun(IO.sync[Int](throw ExampleError)) must (throwA(FiberFailure(Unchecked(ExampleError))))

  def testEvalOfUncaughtThrownSupervisedSyncEffect =
    unsafeRun(IO.sync[Int](throw ExampleError).supervised) must (throwA(FiberFailure(Unchecked(ExampleError))))

  def testEvalOfDeepUncaughtThrownSyncEffect =
    unsafeRun(deepErrorEffect(100)) must (throwA(FiberFailure(Checked(ExampleError))))

  def testEvalOfDeepUncaughtFail =
    unsafeRun(deepErrorEffect(100)) must (throwA(FiberFailure(Checked(ExampleError))))

  def testFailOfMultipleFailingFinalizers =
    unsafeRun(
      IO.fail[Throwable](ExampleError)
        .ensuring(IO.sync(throw InterruptCause1))
        .ensuring(IO.sync(throw InterruptCause2))
        .ensuring(IO.sync(throw InterruptCause3))
        .run
    ) must_=== ExitResult.failed(
      Cause.checked(ExampleError) ++
        Cause.unchecked(InterruptCause1) ++
        Cause.unchecked(InterruptCause2) ++
        Cause.unchecked(InterruptCause3)
    )

  def testTerminateOfMultipleFailingFinalizers =
    unsafeRun(
      IO.terminate(ExampleError)
        .ensuring(IO.sync(throw InterruptCause1))
        .ensuring(IO.sync(throw InterruptCause2))
        .ensuring(IO.sync(throw InterruptCause3))
        .run
    ) must_=== ExitResult.failed(
      Cause.unchecked(ExampleError) ++
        Cause.unchecked(InterruptCause1) ++
        Cause.unchecked(InterruptCause2) ++
        Cause.unchecked(InterruptCause3)
    )

  def testEvalOfFailEnsuring = {
    var finalized = false

    unsafeRun(IO.fail[Throwable](ExampleError).as[Any].ensuring(IO.sync[Unit] { finalized = true; () })) must (throwA(
      FiberFailure(Checked(ExampleError))
    ))
    finalized must_=== true
  }

  def testEvalOfFailOnError = {
    var finalized = false
    val cleanup: ExitResult[Throwable, Nothing] => IO[Nothing, Unit] =
      _ => IO.sync[Unit] { finalized = true; () }

    unsafeRun(
      IO.fail[Throwable](ExampleError).onError(cleanup).as[Any]
    ) must (throwA(FiberFailure(Checked(ExampleError))))

    // FIXME: Is this an issue with thread synchronization?
    while (!finalized) Thread.`yield`()

    finalized must_=== true
  }

  def testErrorInFinalizerCannotBeCaught = {

    val e2 = new Error("e2")
    val e3 = new Error("e3")

    val nested: IO[Throwable, Int] =
      IO.fail[Throwable](ExampleError)
        .ensuring(IO.terminate(e2))
        .ensuring(IO.terminate(e3))

    unsafeRun(nested) must (throwA(FiberFailure(Then(Checked(ExampleError), Then(Unchecked(e2), Unchecked(e3))))))
  }

  def testErrorInFinalizerIsReported = {
    var reported: Cause[Any] = null

    unsafeRun {
      IO.point[Int](42)
        .ensuring(IO.terminate(ExampleError))
        .fork0(es => IO.sync[Unit] { reported = es; () })
    }

    // FIXME: Is this an issue with thread synchronization?
    while (reported eq null) Thread.`yield`()

    reported must_=== Unchecked(ExampleError)
  }

  def testExitResultIsUsageResult =
    unsafeRun(IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.point[Int](42))) must_=== 42

  def testBracketErrorInAcquisition =
    unsafeRun(IO.bracket(IO.fail[Throwable](ExampleError))(_ => IO.unit)(_ => IO.unit)) must
      (throwA(FiberFailure(Checked(ExampleError))))

  def testBracketErrorInRelease =
    unsafeRun(IO.bracket(IO.unit)(_ => IO.terminate(ExampleError))(_ => IO.unit)) must
      (throwA(FiberFailure(Unchecked(ExampleError))))

  def testBracketErrorInUsage =
    unsafeRun(IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.fail[Throwable](ExampleError).as[Any])) must
      (throwA(FiberFailure(Checked(ExampleError))))

  def testBracketRethrownCaughtErrorInAcquisition = {
    lazy val actual = unsafeRun(
      IO.absolve(IO.bracket(IO.fail[Throwable](ExampleError))(_ => IO.unit)(_ => IO.unit).attempt)
    )

    actual must (throwA(FiberFailure(Checked(ExampleError))))
  }

  def testBracketRethrownCaughtErrorInRelease = {
    lazy val actual = unsafeRun(
      IO.bracket(IO.unit)(_ => IO.terminate(ExampleError))(_ => IO.unit)
    )

    actual must (throwA(FiberFailure(Unchecked(ExampleError))))
  }

  def testBracketRethrownCaughtErrorInUsage = {
    lazy val actual = unsafeRun(
      IO.absolve(
        IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.fail[Throwable](ExampleError).as[Any]).attempt
      )
    )

    actual must (throwA(FiberFailure(Checked(ExampleError))))
  }

  def testEvalOfAsyncAttemptOfFail = {
    val io1 = IO.bracket(IO.unit)(_ => AsyncUnit[Nothing])(_ => asyncExampleError[Unit])
    val io2 = IO.bracket(AsyncUnit[Throwable])(_ => IO.unit)(_ => asyncExampleError[Unit])

    unsafeRun(io1) must (throwA(FiberFailure(Checked(ExampleError))))
    unsafeRun(io2) must (throwA(FiberFailure(Checked(ExampleError))))
    unsafeRun(IO.absolve(io1.attempt)) must (throwA(FiberFailure(Checked(ExampleError))))
    unsafeRun(IO.absolve(io2.attempt)) must (throwA(FiberFailure(Checked(ExampleError))))
  }

  def testBracketRegression1 = {
    def makeLogger: Ref[List[String]] => String => IO[Nothing, Unit] =
      (ref: Ref[List[String]]) => (line: String) => ref.update(_ ::: List(line)).void

    unsafeRun(for {
      ref <- Ref[List[String]](Nil)
      log = makeLogger(ref)
      f <- IO
            .bracket(
              IO.bracket(IO.unit)(_ => log("start 1") *> IO.sleep(10.millis) *> log("release 1"))(
                _ => IO.unit
              )
            )(_ => log("start 2") *> IO.sleep(10.millis) *> log("release 2"))(_ => IO.unit)
            .fork
      _ <- (ref.get <* IO.sleep(1.millis)).repeat(Schedule.doUntil[List[String]](_.contains("start 1")))
      _ <- f.interrupt
      _ <- (ref.get <* IO.sleep(1.millis)).repeat(Schedule.doUntil[List[String]](_.contains("release 2")))
      l <- ref.get
    } yield l) must_=== ("start 1" :: "release 1" :: "start 2" :: "release 2" :: Nil)
  }

  def testInterruptWaitsForFinalizer =
    unsafeRun(for {
      r  <- Ref(false)
      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Int]
      s <- (p1.complete(()) *> p2.get)
            .ensuring(r.set(true).void.delay(10.millis))
            .fork
      _    <- p1.get
      _    <- s.interrupt
      test <- r.get
    } yield test must_=== true)

  def testRunInterruptIsInterrupted =
    unsafeRun(for {
      p    <- Promise.make[Nothing, Unit]
      f    <- (p.complete(()) *> IO.never).run.fork
      _    <- p.get
      _    <- f.interrupt
      test <- f.observe.map(_.interrupted)
    } yield test) must_=== true

  def testEvalOfDeepSyncEffect = {
    def incLeft(n: Int, ref: Ref[Int]): IO[Throwable, Int] =
      if (n <= 0) ref.get
      else incLeft(n - 1, ref) <* ref.update(_ + 1)

    def incRight(n: Int, ref: Ref[Int]): IO[Throwable, Int] =
      if (n <= 0) ref.get
      else ref.update(_ + 1) *> incRight(n - 1, ref)

    unsafeRun(for {
      ref <- Ref(0)
      v   <- incLeft(100, ref)
    } yield v) must_=== 100

    unsafeRun(for {
      ref <- Ref(0)
      v   <- incRight(1000, ref)
    } yield v) must_=== 1000
  }

  def testDeepMapOfPoint =
    unsafeRun(deepMapPoint(10000)) must_=== 10000

  def testDeepMapOfNow =
    unsafeRun(deepMapNow(10000)) must_=== 10000

  def testDeepMapOfSyncEffectIsStackSafe =
    unsafeRun(deepMapEffect(10000)) must_=== 10000

  def testDeepAttemptIsStackSafe =
    unsafeRun((0 until 10000).foldLeft(IO.syncThrowable[Unit](())) { (acc, _) =>
      acc.attempt.void
    }) must_=== (())

  def testDeepFlatMapIsStackSafe = {
    def fib(n: Int, a: BigInt = 0, b: BigInt = 1): IO[Error, BigInt] =
      IO.now(a + b).flatMap { b2 =>
        if (n > 0)
          fib(n - 1, b, b2)
        else
          IO.now(b2)
      }

    val future = fib(1000)
    unsafeRun(future) must_=== BigInt(
      "113796925398360272257523782552224175572745930353730513145086634176691092536145985470146129334641866902783673042322088625863396052888690096969577173696370562180400527049497109023054114771394568040040412172632376"
    )
  }

  def testDeepAbsolveAttemptIsIdentity =
    unsafeRun((0 until 1000).foldLeft(IO.point[Int](42))((acc, _) => IO.absolve(acc.attempt))) must_=== 42

  def testDeepAsyncAbsolveAttemptIsIdentity =
    unsafeRun(
      (0 until 1000)
        .foldLeft(IO.async[Int, Int](k => k(ExitResult.succeeded(42))))((acc, _) => IO.absolve(acc.attempt))
    ) must_=== 42

  def testAsyncEffectReturns =
    unsafeRun(IO.async[Throwable, Int](cb => cb(ExitResult.succeeded(42)))) must_=== 42

  def testAsyncIOEffectReturns =
    unsafeRun(IO.asyncPure[Throwable, Int](cb => IO.sync(cb(ExitResult.succeeded(42))))) must_=== 42

  def testDeepAsyncIOThreadStarvation = {
    def stackIOs(count: Int): IO[Nothing, Int] =
      if (count <= 0) IO.done(ExitResult.succeeded(42))
      else asyncIO(stackIOs(count - 1))

    def asyncIO(cont: IO[Nothing, Int]): IO[Nothing, Int] =
      IO.asyncPure[Nothing, Int] { cb =>
        IO.sleep(5.millis) *> cont *> IO.sync(cb(ExitResult.succeeded(42)))
      }

    val procNum = Runtime.getRuntime.availableProcessors()

    unsafeRun(stackIOs(procNum + 1)) must_=== 42
  }

  def testAsyncPureInterruptRegister =
    unsafeRun(for {
      release <- Promise.make[Nothing, Unit]
      acquire <- Promise.make[Nothing, Unit]
      fiber <- IO
                .asyncPure[Nothing, Unit] { _ =>
                  IO.bracket(acquire.complete(()))(_ => release.complete(()))(_ => IO.never)
                }
                .fork
      _ <- acquire.get
      _ <- fiber.interrupt.fork
      a <- release.get
    } yield a) must_=== (())

  def testSleepZeroReturns =
    unsafeRun(IO.sleep(1.nanos)) must_=== ((): Unit)

  def testShallowBindOfAsyncChainIsCorrect = {
    val result = (0 until 10).foldLeft[IO[Throwable, Int]](IO.point[Int](0)) { (acc, _) =>
      acc.flatMap(n => IO.async[Throwable, Int](_(ExitResult.succeeded[Int](n + 1))))
    }

    unsafeRun(result) must_=== 10
  }

  def testForkJoinIsId =
    unsafeRun(IO.point[Int](42).fork.flatMap(_.join)) must_=== 42

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
        fiber   <- IO.bracket[Nothing, Unit, Unit](promise.complete(()) *> IO.never)(_ => IO.unit)(_ => IO.unit).fork
        res     <- promise.get *> fiber.interrupt.timeout0(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 42
  }

  def testBracket0AcquireIsUninterruptible = {
    val io =
      for {
        promise <- Promise.make[Nothing, Unit]
        fiber <- IO
                  .bracket0[Nothing, Unit, Unit](promise.complete(()) *> IO.never)((_, _) => IO.unit)(_ => IO.unit)
                  .fork
        res <- promise.get *> fiber.interrupt.timeout0(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 42
  }

  def testBracketReleaseOnInterrupt = {
    val io =
      for {
        p1    <- Promise.make[Nothing, Unit]
        p2    <- Promise.make[Nothing, Unit]
        fiber <- IO.bracket(IO.unit)(_ => p2.complete(()) *> IO.unit)(_ => p1.complete(()) *> IO.never).fork
        _     <- p1.get
        _     <- fiber.interrupt
        _     <- p2.get
      } yield ()

    unsafeRun(io.timeout0(42)(_ => 0)(1.second)) must_=== 0
  }

  def testBracket0ReleaseOnInterrupt = {
    val io =
      for {
        p1 <- Promise.make[Nothing, Unit]
        p2 <- Promise.make[Nothing, Unit]
        fiber <- IO
                  .bracket0[Nothing, Unit, Unit](IO.unit)((_, _) => p2.complete(()) *> IO.unit)(
                    _ => p1.complete(()) *> IO.never
                  )
                  .fork
        _ <- p1.get
        _ <- fiber.interrupt
        _ <- p2.get
      } yield ()

    unsafeRun(io.timeout0(42)(_ => 0)(1.second)) must_=== 0
  }

  def testRedeemEnsuringInterrupt = {
    val io = for {
      cont <- Promise.make[Nothing, Unit]
      p1   <- Promise.make[Nothing, Boolean]
      f1   <- (cont.complete(()) *> IO.never).catchAll(IO.fail).ensuring(p1.complete(true)).fork
      _    <- cont.get
      _    <- f1.interrupt
      res  <- p1.get
    } yield res

    unsafeRun(io) must_=== true
  }

  def testFinalizerCanDetectInterruption = {
    val io = for {
      p1  <- Promise.make[Nothing, Boolean]
      c   <- Promise.make[Nothing, Unit]
      f1  <- (c.complete(()) *> IO.never).ensuring(IO.descriptor.flatMap(d => p1.complete(d.interrupted))).fork
      _   <- c.get
      _   <- f1.interrupt
      res <- p1.get
    } yield res

    unsafeRun(io) must_=== true
  }

  def testInterruptedOfRaceInterruptsContestents = {
    val io = for {
      ref   <- Ref(0)
      cont1 <- Promise.make[Nothing, Unit]
      cont2 <- Promise.make[Nothing, Unit]
      make  = (p: Promise[Nothing, Unit]) => (p.complete(()) *> IO.never).onInterrupt(ref.update(_ + 1))
      raced <- (make(cont1) race (make(cont2))).fork
      _     <- cont1.get *> cont2.get
      _     <- raced.interrupt
      count <- ref.get
    } yield count

    unsafeRun(io) must_=== 2
  }

  def testCancelationIsGuaranteed = {
    val io = for {
      release <- scalaz.zio.Promise.make[Nothing, Int]
      latch   = internal.OneShot.make[Unit]
      async = IO.async0[Nothing, Unit] { _ =>
        latch.set(()); Async.maybeLater(release.complete(42).void)
      }
      fiber  <- async.fork
      _      <- IO.sync(latch.get)
      _      <- fiber.interrupt.fork
      result <- release.get
    } yield result

    (0 to 100).map { _ =>
      unsafeRun(io) must_=== 42
    }.reduce(_ and _)
  }

  def testInterruptionOfUnendingBracket = {
    val io = for {
      startLatch <- Promise.make[Nothing, Int]
      exitLatch  <- Promise.make[Nothing, Int]
      bracketed = IO
        .now(21)
        .bracket0[Nothing, Unit] {
          case (r, e) if e.interrupted => exitLatch.complete(r)
          case (_, _)                  => IO.terminate(new Error("Unexpected case"))
        }(a => startLatch.complete(a) *> IO.never)
      fiber      <- bracketed.fork
      startValue <- startLatch.get
      _          <- fiber.interrupt.fork
      exitValue  <- exitLatch.get
    } yield startValue + exitValue

    (0 to 100).map { _ =>
      unsafeRun(io) must_=== 42
    }.reduce(_ and _)
  }

  def testAsyncPureIsInterruptible = {
    val io =
      for {
        fiber <- IO.asyncPure[Nothing, Nothing](_ => IO.never).fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testAsyncIsInterruptible = {
    val io =
      for {
        fiber <- IO.async[Nothing, Nothing](_ => ()).fork
        _     <- fiber.interrupt
      } yield 42

    unsafeRun(io) must_=== 42
  }

  def testAsyncPureCreationIsInterruptible = {
    val io = for {
      release <- Promise.make[Nothing, Int]
      acquire <- Promise.make[Nothing, Unit]
      task = IO.asyncPure[Nothing, Unit] { _ =>
        IO.bracket(acquire.complete(()))(_ => release.complete(42).void)(_ => IO.never)
      }
      fiber <- task.fork
      _     <- acquire.get
      _     <- fiber.interrupt
      a     <- release.get
    } yield a

    unsafeRun(io) must_=== 42
  }

  def testAsync0RunsCancelTokenOnInterrupt = {
    val io = for {
      release <- Promise.make[Nothing, Int]
      latch   = scala.concurrent.Promise[Unit]()
      async = IO.async0[Nothing, Nothing] { _ =>
        latch.success(()); Async.maybeLater(release.complete(42).void)
      }
      fiber <- async.fork
      _ <- IO.async[Throwable, Unit] { cb =>
            latch.future.onComplete {
              case Success(a) => cb(ExitResult.succeeded(a))
              case Failure(t) => cb(ExitResult.checked(t))
            }(scala.concurrent.ExecutionContext.global)
          }
      _      <- fiber.interrupt
      result <- release.get
    } yield result

    unsafeRun(io) must_=== 42
  }

  def testBracketUseIsInterruptible = {
    val io =
      for {
        fiber <- IO.bracket[Nothing, Unit, Unit](IO.unit)(_ => IO.unit)(_ => IO.never).fork
        res   <- fiber.interrupt.timeout0(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 0
  }

  def testBracket0UseIsInterruptible = {
    val io =
      for {
        fiber <- IO.bracket0[Nothing, Unit, Unit](IO.unit)((_, _) => IO.unit)(_ => IO.never).fork
        res   <- fiber.interrupt.timeout0(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 0
  }

  def testSupervise = {
    var counter = 0
    unsafeRun((for {
      _ <- (IO.sleep(200.millis) *> IO.unit).fork
      _ <- (IO.sleep(400.millis) *> IO.unit).fork
    } yield ()).supervised { fs =>
      fs.foldLeft(IO.unit)((io, f) => io *> f.join.attempt *> IO.sync(counter += 1))
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
            p1.complete(()).bracket_(pa.complete(1).void)(IO.never) race
              p2.complete(()).bracket_(pb.complete(2).void)(IO.never)
          ).supervised.fork
      _ <- p1.get *> p2.get

      _ <- f.interrupt
      r <- pa.get seq pb.get
    } yield r) must_=== (1 -> 2)

  def testSuperviseFork =
    unsafeRun(for {
      pa <- Promise.make[Nothing, Int]
      pb <- Promise.make[Nothing, Int]

      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Unit]
      f <- (
            p1.complete(()).bracket_(pa.complete(1).void)(IO.never).fork *>
              p2.complete(()).bracket_(pb.complete(2).void)(IO.never).fork *>
              IO.never
          ).supervised.fork
      _ <- p1.get *> p2.get

      _ <- f.interrupt
      r <- pa.get seq pb.get
    } yield r) must_=== (1 -> 2)

  def testSupervised =
    unsafeRun(for {
      pa <- Promise.make[Nothing, Int]
      pb <- Promise.make[Nothing, Int]
      _ <- (for {
            p1 <- Promise.make[Nothing, Unit]
            p2 <- Promise.make[Nothing, Unit]
            _  <- p1.complete(()).bracket_(pa.complete(1).void)(IO.never).fork
            _  <- p2.complete(()).bracket_(pb.complete(2).void)(IO.never).fork
            _  <- p1.get *> p2.get
          } yield ()).supervised
      r <- pa.get seq pb.get
    } yield r) must_=== (1 -> 2)

  def testRaceChoosesWinner =
    unsafeRun(IO.fail(42).race(IO.now(24)).attempt) must_=== Right(24)

  def testRaceChoosesWinnerInTerminate =
    unsafeRun(IO.terminate(new Throwable {}).race(IO.now(24)).attempt) must_=== Right(24)

  def testRaceChoosesFailure =
    unsafeRun(IO.fail(42).race(IO.fail(42)).attempt) must_=== Left(42)

  def testRaceOfValueNever =
    unsafeRun(IO.point(42).race(IO.never)) must_=== 42

  def testRaceOfFailNever =
    unsafeRun(IO.fail(24).race(IO.never).timeout(10.milliseconds)) must beNone

  def testRaceAllOfValues =
    unsafeRun(IO.raceAll[Int, Int](IO.fail(42), List(IO.now(24))).attempt) must_=== Right(24)

  def testRaceAllOfFailures =
    unsafeRun(IO.raceAll[Int, Nothing](IO.fail(24).delay(10.millis), List(IO.fail(24))).attempt) must_=== Left(
      24
    )

  def testRaceAllOfFailuresOneSuccess =
    unsafeRun(IO.raceAll[Int, Int](IO.fail(42), List(IO.now(24).delay(1.millis))).attempt) must_=== Right(
      24
    )

  def testRaceBothInterruptsLoser =
    unsafeRun(for {
      s      <- Semaphore(0L)
      effect <- Promise.make[Nothing, Int]
      winner = s.acquire *> IO.async[Throwable, Unit](_(ExitResult.succeeded(())))
      loser  = IO.bracket(s.release)(_ => effect.complete(42).void)(_ => IO.never)
      race   = winner raceBoth loser
      _      <- race.attempt
      b      <- effect.get
    } yield b) must_=== 42

  def testRepeatedPar = {
    def countdown(n: Int): IO[Nothing, Int] =
      if (n == 0) IO.now(0)
      else IO.now[Int](1).par(IO.now[Int](2)).flatMap(t => countdown(n - 1).map(y => t._1 + t._2 + y))

    unsafeRun(countdown(50)) must_=== 150
  }

  def testRaceAttemptInterruptsLoser =
    unsafeRun(for {
      s      <- Promise.make[Nothing, Unit]
      effect <- Promise.make[Nothing, Int]
      winner = s.get *> IO.fromEither(Left(new Exception))
      loser  = IO.bracket(s.complete(()))(_ => effect.complete(42))(_ => IO.never)
      race   = winner raceAttempt loser
      _      <- race.attempt
      b      <- effect.get
    } yield b) must_=== 42

  def testPar =
    (0 to 1000).map { _ =>
      unsafeRun(IO.now[Int](1).par(IO.now[Int](2)).flatMap(t => IO.now(t._1 + t._2))) must_=== 3
    }

  def testReduceAll =
    unsafeRun(
      IO.reduceAll[Nothing, Int](IO.point(1), List(2, 3, 4).map(IO.point[Int](_)))(_ + _)
    ) must_=== 10

  def testReduceAllEmpty =
    unsafeRun(
      IO.reduceAll[Nothing, Int](IO.point(1), Seq.empty)(_ + _)
    ) must_=== 1

  def testTimeoutFailure =
    unsafeRun(
      IO.fail("Uh oh").timeout(1.hour)
    ) must (throwA[FiberFailure])

  def testTimeoutTerminate =
    unsafeRunSync(
      IO.terminate(ExampleError).timeout(1.hour): IO[Nothing, Option[Int]]
    ) must_=== ExitResult.unchecked(ExampleError)

  def testDeadlockRegression = {

    import java.util.concurrent.Executors

    val rts = new RTS {}

    val e = Executors.newSingleThreadExecutor()

    (0 until 10000).foreach { _ =>
      rts.unsafeRun {
        IO.async[Nothing, Int] { cb =>
          val c: Callable[Unit] = () => cb(ExitResult.succeeded(1))
          val _                 = e.submit(c)
        }
      }
    }

    e.shutdown() must_=== (())
  }

  def testInterruptionRegression1 = {

    val c = new AtomicInteger(0)

    def test =
      IO.syncThrowable {
        if (c.incrementAndGet() <= 1) throw new RuntimeException("x")
      }.forever
        .ensuring(IO.unit)
        .attempt
        .forever

    unsafeRun(
      for {
        f <- test.fork
        c <- (IO.sync[Int](c.get) <* IO.sleep(1.millis)).repeat(Schedule.doUntil[Int](_ >= 1)) <* f.interrupt
      } yield c must be_>=(1)
    )

  }

  def testInterruptSyncForever = unsafeRun(
    for {
      f <- IO.sync[Int](1).forever.fork
      _ <- f.interrupt
    } yield true
  )

  // Utility stuff
  val ExampleError    = new Exception("Oh noes!")
  val InterruptCause1 = new Exception("Oh noes 1!")
  val InterruptCause2 = new Exception("Oh noes 2!")
  val InterruptCause3 = new Exception("Oh noes 3!")

  def asyncExampleError[A]: IO[Throwable, A] =
    IO.async[Throwable, A](_(ExitResult.checked(ExampleError)))

  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  def deepMapPoint(n: Int): IO[Nothing, Int] = {
    @tailrec
    def loop(n: Int, acc: IO[Nothing, Int]): IO[Nothing, Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.point(0))
  }

  def deepMapNow(n: Int): IO[Nothing, Int] = {
    @tailrec
    def loop(n: Int, acc: IO[Nothing, Int]): IO[Nothing, Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.now(0))
  }

  def deepMapEffect(n: Int): IO[Nothing, Int] = {
    @tailrec
    def loop(n: Int, acc: IO[Nothing, Int]): IO[Nothing, Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.sync(0))
  }

  def deepErrorEffect(n: Int): IO[Throwable, Unit] =
    if (n == 0) IO.syncThrowable(throw ExampleError)
    else IO.unit *> deepErrorEffect(n - 1)

  def deepErrorFail(n: Int): IO[Throwable, Unit] =
    if (n == 0) IO.fail(ExampleError)
    else IO.unit *> deepErrorFail(n - 1)

  def fib(n: Int): BigInt =
    if (n <= 1) n
    else fib(n - 1) + fib(n - 2)

  def concurrentFib(n: Int): IO[Throwable, BigInt] =
    if (n <= 1) IO.point[BigInt](n)
    else
      for {
        f1 <- concurrentFib(n - 1).fork
        f2 <- concurrentFib(n - 2).fork
        v1 <- f1.join
        v2 <- f2.join
      } yield v1 + v2

  def AsyncUnit[E] = IO.async[E, Unit](_(ExitResult.succeeded(())))

  def testMergeAll =
    unsafeRun(
      IO.mergeAll[Nothing, String, Int](List("a", "aa", "aaa", "aaaa").map(IO.point[String](_)))(
        0,
        f = (b, a) => b + a.length
      )
    ) must_=== 10

  def testMergeAllEmpty =
    unsafeRun(
      IO.mergeAll[Nothing, Int, Int](List.empty)(0, _ + _)
    ) must_=== 0
}
