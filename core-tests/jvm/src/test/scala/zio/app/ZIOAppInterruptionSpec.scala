package zio.app

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

/**
 * A minimal test suite to reproduce the bug where a ZIOApp,
 * when interrupted externally, returns a failure exit code (1)
 * instead of a success exit code (0).
 *
 * This test is expected to FAIL until the underlying bug in ZIOApp is fixed.
 */
object ZIOAppInterruptionSpec extends ZIOSpecDefault {

  def spec = suite("ZIOAppInterruptionSpec")(
    test("interrupted successful app should return exit code 0") {
      for {
        process  <- ProcessTestUtils.runApp("zio.app.InterruptionReproApp")
        // Wait for the app to confirm it has started
        _        <- process.waitForOutput("InterruptionReproApp started successfully.", 10.seconds)
        // Interrupt the process to simulate an external shutdown signal
        _        <- process.sendSignal("INT")
        // Wait for the process to exit
        exitCode <- process.waitForExit()
        _        <- process.destroy
      } yield {
        // This is the core of the bug demonstration.
        // A successful app that is interrupted externally should exit gracefully with code 0.
        // This assertion will fail because the bug causes it to exit with 1.
        assert(exitCode)(equalTo(0))
      }
    }
  ) @@ jvmOnly @@ withLiveClock
} 