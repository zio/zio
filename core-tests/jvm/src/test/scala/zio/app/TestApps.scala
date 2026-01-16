package zio.app

import zio._

// === Completion Apps ===

object SuccessApp extends ZIOAppDefault {
  def run = ZIO.succeed("done")
}

object FailureApp extends ZIOAppDefault {
  def run = ZIO.fail("fail")
}

object DefectApp extends ZIOAppDefault {
  def run = ZIO.die(new RuntimeException("defect"))
}

object ThrowingApp extends ZIOAppDefault {
  def run = ZIO.attempt(throw new RuntimeException("thrown"))
}

object SuccessExitApp extends ZIOAppDefault {
  def run = ZIO.succeed(ExitCode.success)
}

// === Finalizer Apps ===

object FinalizerApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZED").orDie)
    _ <- Console.printLine("RUNNING")
    _ <- ZIO.never
  } yield ()
}

object SlowFinalizerApp extends ZIOAppDefault {
  def run = for {
    _ <-
      ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ =>
        Console.printLine("FINALIZER_START").orDie *> ZIO.sleep(2.seconds) *> Console.printLine("FINALIZER_END").orDie
      )
    _ <- Console.printLine("RUNNING")
    _ <- ZIO.never
  } yield ()
}

object FinalizerOnSuccessApp extends ZIOAppDefault {
  def run = ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZED").orDie) *>
    Console.printLine("COMPLETED")
}

object FinalizerOnFailureApp extends ZIOAppDefault {
  def run = ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZED").orDie) *>
    ZIO.fail("FAILED")
}

object MultipleFinalizersApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED_1"))(_ => Console.printLine("FINALIZED_1").orDie)
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED_2"))(_ => Console.printLine("FINALIZED_2").orDie)
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED_3"))(_ => Console.printLine("FINALIZED_3").orDie)
    _ <- Console.printLine("READY")
    _ <- ZIO.never
  } yield ()
}

object ParallelFinalizersApp extends ZIOAppDefault {
  def run = for {
    fiber1 <- ZIO.acquireRelease(Console.printLine("ACQUIRED_1"))(_ => Console.printLine("FINALIZED_1").orDie).fork
    fiber2 <- ZIO.acquireRelease(Console.printLine("ACQUIRED_2"))(_ => Console.printLine("FINALIZED_2").orDie).fork
    fiber3 <- ZIO.acquireRelease(Console.printLine("ACQUIRED_3"))(_ => Console.printLine("FINALIZED_3").orDie).fork
    _      <- fiber1.join
    _      <- fiber2.join
    _      <- fiber3.join
    _      <- Console.printLine("COMPLETED")
  } yield ()
}

// === Signal Handling Apps ===

object SignalFinalizerApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZED").orDie)
    _ <- Console.printLine("READY")
    _ <- ZIO.never
  } yield ()
}

object HangingFinalizerApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZER_START").orDie *> ZIO.never)
    _ <- Console.printLine("READY")
    _ <- ZIO.never
  } yield ()
}

// === Custom Timeout Apps ===

object CustomTimeoutApp extends ZIOAppDefault {
  override def gracefulShutdownTimeout: Duration = 5.seconds

  def run = for {
    _ <-
      ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ =>
        Console.printLine("FINALIZER_START").orDie *> ZIO.sleep(3.seconds) *> Console.printLine("FINALIZER_END").orDie
      )
    _ <- Console.printLine("READY")
    _ <- ZIO.never
  } yield ()
}

// === Regression Test Apps ===

object Issue9901App extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZED").orDie)
    _ <- Console.printLine("READY")
    _ <- ZIO.never
  } yield ()
}

object Issue9807App extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED_FAST"))(_ =>
           Console.printLine("FINALIZER_FAST_START").orDie *> ZIO
             .sleep(1.second) *> Console.printLine("FINALIZED_FAST").orDie
         )
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED_SLOW"))(_ =>
           Console.printLine("FINALIZER_SLOW_START").orDie *> ZIO
             .sleep(3.seconds) *> Console.printLine("FINALIZED_SLOW").orDie
         )
    _ <- Console.printLine("READY")
    _ <- ZIO.never
  } yield ()
}

object Issue9240App extends ZIOAppDefault {
  def run = ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZED").orDie) *>
    ZIO.succeed(ExitCode(42))
}

object Issue10122App extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZED").orDie)
    _ <- Console.printLine("READY")
    _ <- ZIO.never
  } yield ()
}

// === Catastrophic Failure Apps ===

object StackOverflowApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED"))(_ => Console.printLine("FINALIZED_SHOULD_NOT_RUN").orDie)
    _ <- Console.printLine("READY")
    _ <- ZIO.attemptBlocking(boom(0))
  } yield ()

  private def boom(depth: Int): Unit = {
    // This will cause a stack overflow
    boom(depth + 1)
  }
}
