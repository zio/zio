package zio.app

import zio._
import zio.test._
import zio.test.TestAspect._

/**
 * Test suite specifically for signal handling behavior in ZIOApp.
 * 
 * These tests verify that:
 * - External signals (SIGINT, SIGTERM) trigger graceful shutdown
 * - Finalizers are executed when signals are received
 * - The shutdown sequence completes in a timely manner
 * 
 * Note: These tests are Unix-specific and will be skipped on Windows.
 */
object ZIOAppSignalHandlingSpec extends ZIOSpecDefault {

  import ProcessTestUtils._

  override def spec: Spec[TestEnvironment with Scope, Any] = 
    suite("ZIOAppSignalHandlingSpec")(
      sigintSuite,
      sigtermSuite,
      multipleSignalSuite
    ) @@ sequential @@ timeout(90.seconds) @@ withLiveClock @@ 
      ifProp("os.name")(n => !n.toLowerCase.contains("win"))

  val sigintSuite: Spec[Any, Throwable] = suite("SIGINT Handling")(
    test("SIGINT causes app to terminate") {
      for {
        result <- ZIO.scoped {
          for {
            process <- startApp("zio.app.SignalFinalizerApp")
            _       <- ZIO.sleep(500.millis)
            _       <- sendSignal(process.pid, "SIGINT")
            result  <- waitForProcess(process, 5.seconds)
          } yield result
        }
      } yield assertTrue(result.exitCode != -1) // -1 means timeout
    },
    
    test("SIGINT triggers finalizer execution") {
      for {
        result <- ZIO.scoped {
          for {
            process <- startApp("zio.app.SignalFinalizerApp")
            _       <- ZIO.sleep(500.millis)
            _       <- sendSignal(process.pid, "SIGINT")
            result  <- waitForProcess(process, 10.seconds)
          } yield result
        }
      } yield assertTrue(
        result.outputContains("ACQUIRED") &&
        result.outputContains("RUNNING") &&
        result.outputContains("FINALIZED")
      )
    },
    
    test("SIGINT allows slow finalizers to complete") {
      for {
        result <- ZIO.scoped {
          for {
            process <- startApp("zio.app.SlowFinalizerApp")
            _       <- ZIO.sleep(500.millis)
            _       <- sendSignal(process.pid, "SIGINT")
            result  <- waitForProcess(process, 15.seconds)
          } yield result
        }
      } yield assertTrue(
        result.outputContains("FINALIZER_START") &&
        result.outputContains("FINALIZER_END")
      )
    }
  )

  val sigtermSuite: Spec[Any, Throwable] = suite("SIGTERM Handling")(
    test("SIGTERM causes app to terminate") {
      for {
        result <- ZIO.scoped {
          for {
            process <- startApp("zio.app.SignalFinalizerApp")
            _       <- ZIO.sleep(500.millis)
            _       <- sendSignal(process.pid, "SIGTERM")
            result  <- waitForProcess(process, 5.seconds)
          } yield result
        }
      } yield assertTrue(result.exitCode != -1)
    },
    
    test("SIGTERM triggers finalizer execution") {
      for {
        result <- ZIO.scoped {
          for {
            process <- startApp("zio.app.SignalFinalizerApp")
            _       <- ZIO.sleep(500.millis)
            _       <- sendSignal(process.pid, "SIGTERM")
            result  <- waitForProcess(process, 10.seconds)
          } yield result
        }
      } yield assertTrue(
        result.outputContains("ACQUIRED") &&
        result.outputContains("FINALIZED")
      )
    }
  )

  val multipleSignalSuite: Spec[Any, Throwable] = suite("Multiple Signals")(
    test("repeated SIGINT doesn't cause issues") {
      for {
        result <- ZIO.scoped {
          for {
            process <- startApp("zio.app.SlowFinalizerApp")
            _       <- ZIO.sleep(500.millis)
            _       <- sendSignal(process.pid, "SIGINT")
            _       <- ZIO.sleep(200.millis)
            _       <- sendSignal(process.pid, "SIGINT") // Second signal
            result  <- waitForProcess(process, 15.seconds)
          } yield result
        }
      } yield assertTrue(
        result.outputContains("FINALIZER_START") &&
        result.exitCode != -1 &&
        !result.stderrContains("Exception")
      )
    },
    
    test("SIGTERM after SIGINT doesn't cause issues") {
      for {
        result <- ZIO.scoped {
          for {
            process <- startApp("zio.app.SlowFinalizerApp")
            _       <- ZIO.sleep(500.millis)
            _       <- sendSignal(process.pid, "SIGINT")
            _       <- ZIO.sleep(200.millis)
            _       <- sendSignal(process.pid, "SIGTERM")
            result  <- waitForProcess(process, 15.seconds)
          } yield result
        }
      } yield assertTrue(result.exitCode != -1)
    }
  )
}
