package zio

object RuntimeBootstrapTests {
  implicit class RunSyntax[A](
    task: Task[A]
  ) {
    def run(): A = Runtime.default.unsafeRun(task)
  }

  def test(name: String)(task: Task[Any]): Unit = {
    print(s" - $name...")
    try {
      task.run()

      println("...OK")
    } catch {
      case e: java.lang.AssertionError =>
        println("...FAILED")
        e.printStackTrace()
      case t: Throwable =>
        println("...CATASTROPHIC")
        t.printStackTrace()
    }
  }

  def testN(n: Int)(name: String)(task: Task[Any]): Unit =
    (1 to n).foreach(_ => test(name)(task))

  def helloWorld() =
    test("Hello World") {
      for {
        _ <- Console.print("Hello World!")
      } yield assert(true)
    }

  def fibAcc(n: Int): Task[Int] =
    if (n <= 1)
      ZIO.succeed(n)
    else
      for {
        a <- fibAcc(n - 1)
        b <- fibAcc(n - 2)
      } yield a + b

  def fib() =
    test("fib") {
      for {
        result <- fibAcc(10)
      } yield assert(result == 55)
    }

  def runtimeFlags() =
    test("runtimeFlags") {
      ZIO.succeed {
        import RuntimeFlag._

        val flags =
          RuntimeFlags(Interruption, CurrentFiber)

        assert(flags.enabled(Interruption))
        assert(flags.enabled(CurrentFiber))
        assert(flags.disabled(FiberRoots))
        assert(flags.disabled(OpLog))
        assert(flags.disabled(OpSupervision))
        assert(flags.disabled(RuntimeMetrics))

        assert(RuntimeFlags.enable(Interruption)(RuntimeFlags.none).interruption)
      }
    }

  def race() =
    testN(100)("race") {
      ZIO.unit.race(ZIO.unit)
    }

  def iteration() =
    test("iteration") {
      ZIO.iterate(0)(_ < 100)(index => ZIO.succeed(index + 1)).map(value => assert(value == 100))
    }

  def asyncInterruption() =
    testN(100)("async interruption") {
      for {
        fiber <- ZIO.never.forkDaemon
        _     <- fiber.interrupt
      } yield assert(true)
    }

  def syncInterruption() =
    testN(100)("sync interruption") {
      for {
        fiber <- fibAcc(100).forkDaemon
        _     <- fiber.interrupt
      } yield assert(true)
    }

  def autoInterruption() =
    test("auto interruption with finalization") {
      for {
        ref    <- Ref.make(0)
        latch  <- Promise.make[Nothing, Unit]
        child   = (latch.succeed(()) *> ZIO.infinity).ensuring(ref.update(_ + 1))
        parent <- (child.fork *> latch.await).fork
        _      <- parent.await
        count  <- ref.get
      } yield assert(count == 1)
    }

  def autoInterruption2() =
    test("auto interruption with finalization 2") {
      def plus1(latch: Promise[Nothing, Unit], finalizer: UIO[Any]) =
        (latch.succeed(()) *> ZIO.sleep(1.hour)).onInterrupt(finalizer)

      for {
        interruptionRef <- Ref.make(0)
        latch1Start     <- Promise.make[Nothing, Unit]
        latch2Start     <- Promise.make[Nothing, Unit]
        inc              = interruptionRef.update(_ + 1)
        left             = plus1(latch1Start, inc)
        right            = plus1(latch2Start, inc)
        fiber           <- left.race(right).fork
        _               <- latch1Start.await *> latch2Start.await *> fiber.interrupt
        interrupted     <- interruptionRef.get
      } yield assert(interrupted == 2)
    }

  def asyncInterruptionOfNever() =
    test("async interruption of never") {
      for {
        finalized <- Ref.make(false)
        fork <- (ZIO
                  .asyncMaybe[Any, Nothing, Unit] { _ =>
                    Some(ZIO.unit)
                  }
                  .flatMap { _ =>
                    ZIO.never
                  }
                  .ensuring(finalized.set(true)))
                  .uninterruptible
                  .forkDaemon
        _      <- fork.interrupt.timeout(5.seconds)
        result <- finalized.get
      } yield assert(result == false)
    }

  def raceInterruption() =
    test("race of two forks does not interrupt winner") {
      for {
        interrupted <- Ref.make(0)
        fibers      <- Ref.make(Set.empty[Fiber[Any, Any]])
        latch       <- Promise.make[Nothing, Unit]
        forkWaiter = ZIO.uninterruptibleMask { restore =>
                       (restore(latch.await)
                         .onInterrupt(
                           interrupted.update(_ + 1)
                         ))
                         .fork
                         .tap(f => fibers.update(_ + f))
                     }
        awaitAll = fibers.get.flatMap(Fiber.awaitAll(_))
        _       <- forkWaiter.race(forkWaiter)
        count   <- latch.succeed(()) *> awaitAll *> interrupted.get
      } yield assert(count <= 1)
    }

  def main(args: Array[String]): Unit = {
    runtimeFlags()
    helloWorld()
    fib()
    iteration()
    asyncInterruption()
    syncInterruption()
    race()
    autoInterruption()
    autoInterruption2()
    asyncInterruptionOfNever()
    raceInterruption()
  }
}
