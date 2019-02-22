package scalaz.zio

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

import com.github.ghik.silencer.silent
import org.specs2.concurrent.ExecutionEnv
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
    unyielding reuses cached thread         $testBlockingThreadCaching

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
    blocking IO is interruptible            $testBlockingIOIsInterruptible
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
    unsafeRun(IO.succeedLazy(1)) must_=== 1

  def testWidenNothing = {
    val op1 = IO.defer[String]("1")
    val op2 = IO.defer[String]("2")

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

  def testFlipError = {
    val error = new Error("Left")
    val io    = IO.fail(error).flip
    unsafeRun(io) must_=== error
  }

  def testFlipValue = {
    val io = IO.succeed(100).flip
    unsafeRun(io.attempt) must_=== Left(100)
  }

  def testFlipDouble = {
    val io = IO.succeedLazy(100)
    unsafeRun(io.flip.flip) must_=== unsafeRun(io)
  }

  def testEvalOfSyncEffect = {
    def sumIo(n: Int): Task[Int] =
      if (n <= 0) IO.defer(0)
      else IO.defer(n).flatMap(b => sumIo(n - 1).map(a => a + b))

    unsafeRun(sumIo(1000)) must_=== sum(1000)
  }

  def testManualSyncOnDefer = {
    def sync[A](effect: => A): IO[Throwable, A] =
      IO.defer(effect)
        .redeem0({
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
      IO.sync[Unit](throw ExampleError).fold[Option[Throwable]](Some(_), _ => None)
    ) must_=== Some(ExampleError)

  def testEvalOfAttemptOfFail = Seq(
    unsafeRun(IO.fail[Throwable](ExampleError).attempt) must_=== Left(ExampleError),
    unsafeRun(IO.suspend(IO.suspend(IO.fail[Throwable](ExampleError)).attempt)) must_=== Left(
      ExampleError
    )
  )

  def testSandboxAttemptOfTerminate =
    unsafeRun(IO.defer[Int](throw ExampleError).sandbox.attempt) must_=== Left(Die(ExampleError))

  def testSandboxFoldOfTerminate =
    unsafeRun(
      IO.defer[Int](throw ExampleError).sandbox.fold(Some(_), Function.const(None))
    ) must_=== Some(Die(ExampleError))

  def testSandboxTerminate =
    unsafeRun(
      IO.defer[Cause[Any]](throw ExampleError)
        .sandbox
        .fold(identity, identity)
    ) must_=== Die(ExampleError)

  def testAttemptOfDeepSyncEffectError =
    unsafeRun(deepErrorEffect(100).attempt) must_=== Left(ExampleError)

  def testAttemptOfDeepFailError =
    unsafeRun(deepErrorFail(100).attempt) must_=== Left(ExampleError)

  def testEvalOfUncaughtFail =
    unsafeRun(Task.fail(ExampleError): Task[Any]) must (throwA(FiberFailure(Fail(ExampleError))))

  def testEvalOfUncaughtFailSupervised =
    unsafeRun(Task.fail(ExampleError).supervise: Task[Unit]) must (throwA(
      FiberFailure(Fail(ExampleError))
    ))

  def testEvalOfUncaughtThrownSyncEffect =
    unsafeRun(IO.defer[Int](throw ExampleError)) must (throwA(FiberFailure(Die(ExampleError))))

  def testEvalOfUncaughtThrownSupervisedSyncEffect =
    unsafeRun(IO.defer[Int](throw ExampleError).supervise) must (throwA(FiberFailure(Die(ExampleError))))

  def testEvalOfDeepUncaughtThrownSyncEffect =
    unsafeRun(deepErrorEffect(100)) must (throwA(FiberFailure(Fail(ExampleError))))

  def testEvalOfDeepUncaughtFail =
    unsafeRun(deepErrorEffect(100)) must (throwA(FiberFailure(Fail(ExampleError))))

  def testFailOfMultipleFailingFinalizers =
    unsafeRun(
      IO.fail[Throwable](ExampleError)
        .ensuring(IO.defer(throw InterruptCause1))
        .ensuring(IO.defer(throw InterruptCause2))
        .ensuring(IO.defer(throw InterruptCause3))
        .run
    ) must_=== Exit.halt(
      Cause.fail(ExampleError) ++
        Cause.die(InterruptCause1) ++
        Cause.die(InterruptCause2) ++
        Cause.die(InterruptCause3)
    )

  def testTerminateOfMultipleFailingFinalizers =
    unsafeRun(
      IO.die(ExampleError)
        .ensuring(IO.defer(throw InterruptCause1))
        .ensuring(IO.defer(throw InterruptCause2))
        .ensuring(IO.defer(throw InterruptCause3))
        .run
    ) must_=== Exit.halt(
      Cause.die(ExampleError) ++
        Cause.die(InterruptCause1) ++
        Cause.die(InterruptCause2) ++
        Cause.die(InterruptCause3)
    )

  def testEvalOfFailEnsuring = {
    var finalized = false

    unsafeRun((Task.fail(ExampleError): Task[Unit]).ensuring(IO.defer[Unit] { finalized = true; () })) must (throwA(
      FiberFailure(Fail(ExampleError))
    ))
    finalized must_=== true
  }

  def testEvalOfFailOnError = {
    @volatile var finalized = false
    val cleanup: Cause[Throwable] => UIO[Unit] =
      _ => IO.defer[Unit] { finalized = true; () }

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
      IO.fail[Throwable](ExampleError)
        .ensuring(IO.die(e2))
        .ensuring(IO.die(e3))

    unsafeRun(nested) must (throwA(FiberFailure(Then(Fail(ExampleError), Then(Die(e2), Die(e3))))))
  }

  def testErrorInFinalizerIsReported = {
    @volatile var reported: Exit[Nothing, Int] = null

    unsafeRun {
      IO.succeedLazy[Int](42)
        .ensuring(IO.die(ExampleError))
        .fork
        .flatMap(_.await.flatMap[Any, Nothing, Any](e => UIO.defer { reported = e }))
    }

    reported must_=== Exit.Failure(Die(ExampleError))
  }

  def testExitIsUsageResult =
    unsafeRun(IO.bracket(IO.unit)(_ => IO.unit)(_ => IO.succeedLazy[Int](42))) must_=== 42

  def testBracketErrorInAcquisition =
    unsafeRun(IO.bracket(IO.fail[Throwable](ExampleError))(_ => IO.unit)(_ => IO.unit)) must
      (throwA(FiberFailure(Fail(ExampleError))))

  def testBracketErrorInRelease =
    unsafeRun(IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit)) must
      (throwA(FiberFailure(Die(ExampleError))))

  def testBracketErrorInUsage =
    unsafeRun(Task.bracket(Task.unit)(_ => Task.unit)(_ => Task.fail(ExampleError): Task[Unit])) must
      (throwA(FiberFailure(Fail(ExampleError))))

  def testBracketRethrownCaughtErrorInAcquisition = {
    lazy val actual = unsafeRun(
      IO.absolve(IO.bracket(IO.fail[Throwable](ExampleError))(_ => IO.unit)(_ => IO.unit).attempt)
    )

    actual must (throwA(FiberFailure(Fail(ExampleError))))
  }

  def testBracketRethrownCaughtErrorInRelease = {
    lazy val actual = unsafeRun(
      IO.bracket(IO.unit)(_ => IO.die(ExampleError))(_ => IO.unit)
    )

    actual must (throwA(FiberFailure(Die(ExampleError))))
  }

  def testBracketRethrownCaughtErrorInUsage = {
    lazy val actual = unsafeRun(
      IO.absolve(
        IO.bracket(IO.unit)(_ => IO.unit)(_ => Task.fail(ExampleError): Task[Unit]).attempt
      )
    )

    actual must (throwA(FiberFailure(Fail(ExampleError))))
  }

  def testEvalOfAsyncAttemptOfFail = {
    val io1 = IO.bracket(IO.unit)(_ => AsyncUnit[Nothing])(_ => asyncExampleError[Unit])
    val io2 = IO.bracket(AsyncUnit[Throwable])(_ => IO.unit)(_ => asyncExampleError[Unit])

    unsafeRun(io1) must (throwA(FiberFailure(Fail(ExampleError))))
    unsafeRun(io2) must (throwA(FiberFailure(Fail(ExampleError))))
    unsafeRun(IO.absolve(io1.attempt)) must (throwA(FiberFailure(Fail(ExampleError))))
    unsafeRun(IO.absolve(io2.attempt)) must (throwA(FiberFailure(Fail(ExampleError))))
  }

  def testBracketRegression1 = {
    def makeLogger: Ref[List[String]] => String => UIO[Unit] =
      (ref: Ref[List[String]]) => (line: String) => ref.update(_ ::: List(line)).void

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
      _ <- (ref.get <* clock.sleep(1.millis)).repeat(Schedule.doUntil[List[String]](_.contains("start 1")))
      _ <- f.interrupt
      _ <- (ref.get <* clock.sleep(1.millis)).repeat(Schedule.doUntil[List[String]](_.contains("release 2")))
      l <- ref.get
    } yield l) must_=== ("start 1" :: "release 1" :: "start 2" :: "release 2" :: Nil)
  }

  def testInterruptWaitsForFinalizer =
    unsafeRun(for {
      r  <- Ref.make(false)
      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Int]
      s <- (p1.succeed(()) *> p2.await)
            .ensuringR(r.set(true) *> clock.sleep(10.millis))
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
    unsafeRun((0 until 10000).foldLeft(IO.sync[Unit](())) { (acc, _) =>
      acc.attempt.void
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
    unsafeRun((0 until 1000).foldLeft(IO.succeedLazy[Int](42))((acc, _) => IO.absolve(acc.attempt))) must_=== 42

  def testDeepAsyncAbsolveAttemptIsIdentity =
    unsafeRun(
      (0 until 1000)
        .foldLeft(IO.async[Int, Int](k => k(IO.succeed(42))))((acc, _) => IO.absolve(acc.attempt))
    ) must_=== 42

  def testAsyncEffectReturns =
    unsafeRun(IO.async[Throwable, Int](k => k(IO.succeed(42)))) must_=== 42

  def testAsyncIOEffectReturns =
    unsafeRun(IO.asyncM[Throwable, Int](k => IO.defer(k(IO.succeed(42))))) must_=== 42

  def testDeepAsyncIOThreadStarvation = {
    def stackIOs(clock: Clock.Service[Any], count: Int): UIO[Int] =
      if (count <= 0) IO.succeed(42)
      else asyncIO(clock, stackIOs(clock, count - 1))

    def asyncIO(clock: Clock.Service[Any], cont: UIO[Int]): UIO[Int] =
      IO.asyncM[Nothing, Int] { k =>
        clock.sleep(5.millis) *> cont *> IO.defer(k(IO.succeed(42)))
      }

    val procNum = java.lang.Runtime.getRuntime.availableProcessors()

    unsafeRun(clock.clockService.flatMap(stackIOs(_, procNum + 1))) must_=== 42
  }

  def testAsyncPureInterruptRegister =
    unsafeRun(for {
      release <- Promise.make[Nothing, Unit]
      acquire <- Promise.make[Nothing, Unit]
      fiber <- IO
                .asyncM[Nothing, Unit] { _ =>
                  IO.bracket(acquire.succeed(()))(_ => release.succeed(()))(_ => IO.never)
                }
                .fork
      _ <- acquire.await
      _ <- fiber.interrupt.fork
      a <- release.await
    } yield a) must_=== (())

  def testSleepZeroReturns =
    unsafeRun(clock.sleep(1.nanos)) must_=== ((): Unit)

  def testShallowBindOfAsyncChainIsCorrect = {
    val result = (0 until 10).foldLeft[Task[Int]](IO.succeedLazy[Int](0)) { (acc, _) =>
      acc.flatMap(n => IO.async[Throwable, Int](_(IO.succeed(n + 1))))
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
        fiber   <- IO.bracket(promise.succeed(()) *> IO.never)(_ => IO.unit)(_ => IO.unit).fork
        res     <- promise.await *> fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 42
  }

  def testBracket0AcquireIsUninterruptible = {
    val io =
      for {
        promise <- Promise.make[Nothing, Unit]
        fiber <- IO
                  .bracket0[Any, Nothing, Unit, Unit](promise.succeed(()) *> IO.never)((_, _) => IO.unit)(_ => IO.unit)
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

  def testBracket0ReleaseOnInterrupt = {
    val io =
      for {
        p1 <- Promise.make[Nothing, Unit]
        p2 <- Promise.make[Nothing, Unit]
        fiber <- IO
                  .bracket0[Any, Nothing, Unit, Unit](IO.unit)((_, _) => p2.succeed(()) *> IO.unit)(
                    _ => p1.succeed(()) *> IO.never
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
      release <- scalaz.zio.Promise.make[Nothing, Int]
      latch   = internal.OneShot.make[Unit]
      async = IO.asyncInterrupt[Any, Nothing, Unit] { _ =>
        latch.set(()); Left(release.succeed(42).void)
      }
      fiber  <- async.fork
      _      <- IO.defer(latch.get(1000))
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
      bracketed = IO
        .succeed(21)
        .bracket0[Any, Nothing, Unit] {
          case (r, e) if e.interrupted => exitLatch.succeed(r)
          case (_, _)                  => IO.die(new Error("Unexpected case"))
        }(a => startLatch.succeed(a) *> IO.never)
      fiber      <- bracketed.fork
      startValue <- startLatch.await
      _          <- fiber.interrupt.fork
      exitValue  <- exitLatch.await
    } yield startValue + exitValue

    (0 to 100).map { _ =>
      unsafeRun(io) must_=== 42
    }.reduce(_ and _)
  }

  def testAsyncPureIsInterruptible = {
    val io =
      for {
        fiber <- IO.asyncM[Nothing, Nothing](_ => IO.never).fork
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
      task = IO.asyncM[Nothing, Unit] { _ =>
        IO.bracket(acquire.succeed(()))(_ => release.succeed(42).void)(_ => IO.never)
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
      async = IO.asyncInterrupt[Any, Nothing, Nothing] { _ =>
        latch.success(()); Left(release.succeed(42).void)
      }
      fiber <- async.fork
      _ <- IO.async[Throwable, Unit] { k =>
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
        fiber <- IO.bracket0[Any, Nothing, Unit, Unit](IO.unit)((_, _) => IO.unit)(_ => IO.never).fork
        res   <- fiber.interrupt.timeoutTo(42)(_ => 0)(1.second)
      } yield res
    unsafeRun(io) must_=== 0
  }

  def testSupervise = {
    var counter = 0
    unsafeRun((for {
      _ <- (clock.sleep(200.millis) *> IO.unit).fork
      _ <- (clock.sleep(400.millis) *> IO.unit).fork
    } yield ()).superviseWith { fs =>
      fs.foldLeft(IO.unit)((io, f) => io *> f.join.attempt *> IO.defer(counter += 1))
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
            p1.succeed(()).bracket_(pa.succeed(1).void)(IO.never) race
              p2.succeed(()).bracket_(pb.succeed(2).void)(IO.never)
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
            p1.succeed(()).bracket_(pa.succeed(1).void)(IO.never).fork *>
              p2.succeed(()).bracket_(pb.succeed(2).void)(IO.never).fork *>
              IO.never
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
            _  <- p1.succeed(()).bracket_(pa.succeed(1).void)(IO.never).fork
            _  <- p2.succeed(()).bracket_(pb.succeed(2).void)(IO.never).fork
            _  <- p1.await *> p2.await
          } yield ()).supervise
      r <- pa.await zip pb.await
    } yield r) must_=== (1 -> 2)

  def testRaceChoosesWinner =
    unsafeRun(IO.fail(42).race(IO.succeed(24)).attempt) must_=== Right(24)

  def testRaceChoosesWinnerInTerminate =
    unsafeRun(IO.die(new Throwable {}).race(IO.succeed(24)).attempt) must_=== Right(24)

  def testRaceChoosesFailure =
    unsafeRun(IO.fail(42).race(IO.fail(42)).attempt) must_=== Left(42)

  def testRaceOfValueNever =
    unsafeRun(IO.succeedLazy(42).race(IO.never)) must_=== 42

  def testRaceOfFailNever =
    unsafeRun(IO.fail(24).race(IO.never).timeout(10.milliseconds)) must beNone

  def testRaceAllOfValues =
    unsafeRun(IO.raceAll(IO.fail(42), List(IO.succeed(24))).attempt) must_=== Right(24)

  def testRaceAllOfFailures =
    unsafeRun(ZIO.raceAll(IO.fail(24).delay(10.millis), List(IO.fail(24))).attempt) must_=== Left(
      24
    )

  def testRaceAllOfFailuresOneSuccess =
    unsafeRun(ZIO.raceAll(IO.fail(42), List(IO.succeed(24).delay(1.millis))).attempt) must_=== Right(
      24
    )

  def testRaceBothInterruptsLoser =
    unsafeRun(for {
      s      <- Semaphore.make(0L)
      effect <- Promise.make[Nothing, Int]
      winner = s.acquire *> IO.async[Throwable, Unit](_(IO.unit))
      loser  = IO.bracket(s.release)(_ => effect.succeed(42).void)(_ => IO.never)
      race   = winner raceEither loser
      _      <- race.attempt
      b      <- effect.await
    } yield b) must_=== 42

  def testRepeatedPar = {
    def countdown(n: Int): UIO[Int] =
      if (n == 0) IO.succeed(0)
      else IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => countdown(n - 1).map(y => t._1 + t._2 + y))

    unsafeRun(countdown(50)) must_=== 150
  }

  def testRaceAttemptInterruptsLoser =
    unsafeRun(for {
      s      <- Promise.make[Nothing, Unit]
      effect <- Promise.make[Nothing, Int]
      winner = s.await *> IO.fromEither(Left(new Exception))
      loser  = IO.bracket(s.succeed(()))(_ => effect.succeed(42))(_ => IO.never)
      race   = winner raceAttempt loser
      _      <- race.attempt
      b      <- effect.await
    } yield b) must_=== 42

  def testPar =
    (0 to 1000).map { _ =>
      unsafeRun(IO.succeed[Int](1).zipPar(IO.succeed[Int](2)).flatMap(t => IO.succeed(t._1 + t._2))) must_=== 3
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
    ) must (throwA[FiberFailure])

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
        IO.async[Nothing, Int] { k =>
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
      IO.sync {
        if (c.incrementAndGet() <= 1) throw new RuntimeException("x")
      }.forever
        .ensuring(IO.unit)
        .attempt
        .forever

    unsafeRun(
      for {
        f <- test.fork
        c <- (IO.defer[Int](c.get) <* clock.sleep(1.millis)).repeat(Schedule.doUntil[Int](_ >= 1)) <* f.interrupt
      } yield c must be_>=(1)
    )

  }

  def testManualSyncInterruption = {
    def sync[A](effect: => A): IO[Throwable, A] =
      IO.defer(effect)
        .redeem0({
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
    val currentNumLiveWorkers =
      blocking.blockingExecutor.map(_.metrics.get.workersCount)

    unsafeRunSync(for {
      thread1  <- blocking.blocking(IO.defer(Thread.currentThread()))
      workers1 <- currentNumLiveWorkers
      thread2  <- blocking.blocking(IO.defer(Thread.currentThread()))
      workers2 <- currentNumLiveWorkers
    } yield workers1 == workers2 && thread1 == thread2) must_=== Exit.Success(true)
  }

  def testBlockingIOIsInterruptible = unsafeRun(
    for {
      done  <- Ref.make(false)
      start <- IO.succeed(internal.OneShot.make[Unit])
      fiber <- blocking.interruptible { start.set(()); Thread.sleep(Long.MaxValue) }.ensuring(done.set(true)).fork
      _     <- IO.succeed(start.get())
      _     <- fiber.interrupt
      value <- done.get
    } yield value must_=== true
  )

  def testInterruptSyncForever = unsafeRun(
    for {
      f <- IO.defer[Int](1).forever.fork
      _ <- f.interrupt
    } yield true
  )

  // Utility stuff
  val ExampleError    = new Exception("Oh noes!")
  val InterruptCause1 = new Exception("Oh noes 1!")
  val InterruptCause2 = new Exception("Oh noes 2!")
  val InterruptCause3 = new Exception("Oh noes 3!")

  def asyncExampleError[A]: Task[A] =
    IO.async[Throwable, A](_(IO.fail(ExampleError)))

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

    loop(n, IO.defer(0))
  }

  def deepErrorEffect(n: Int): Task[Unit] =
    if (n == 0) IO.sync(throw ExampleError)
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

  def AsyncUnit[E] = IO.async[E, Unit](_(IO.unit))

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
}
