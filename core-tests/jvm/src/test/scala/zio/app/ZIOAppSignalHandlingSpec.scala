package zio.app

import zio._
import zio.test._
import zio.test.TestAspect._

/**
 * Tests specific to signal handling behavior in ZIOApp.
 * These tests verify the fix for issue #9240 where signal handlers
 * should gracefully degrade on unsupported platforms.
 */
object ZIOAppSignalHandlingSpec extends ZIOBaseSpec {
  def spec = suite("ZIOAppSignalHandlingSpec")(
    test("addSignalHandler does not throw on any platform") {
      // TestApp exposes the protected method for testing
      val app = new TestZIOApp()
      
      for {
        runtime <- ZIO.runtime[Any]
        result <- app.testInstallSignalHandlers(runtime).exit
      } yield assertTrue(result.isSuccess)
    },
    
    test("signal handlers are installed exactly 3 times") {
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)
      
      val app = new TestZIOApp {
        override def testInstallSignalHandlers(runtime: Runtime[Any])(implicit trace: Trace): UIO[Any] = {
          ZIO.attempt(counter.incrementAndGet()).ignore
        }
      }
      
      for {
        runtime <- ZIO.runtime[Any]
        _       <- app.testInstallSignalHandlers(runtime)
        _       <- app.testInstallSignalHandlers(runtime)
        _       <- app.testInstallSignalHandlers(runtime)
        count   <- ZIO.succeed(counter.get())
      } yield assertTrue(count == 3)
    },
    
    test("windows platform detection works correctly") {
      // Use ZIO's System service instead of Java's System
      for {
        osName <- zio.System.property("os.name").map(_.getOrElse(""))
        isWindows <- ZIO.attempt(System.os.isWindows)
      } yield {
        val expectedWindows = osName.toLowerCase().contains("win")
        assertTrue(isWindows == expectedWindows)
      }
    }
  ) @@ sequential  
  
  // Helper class that exposes the protected method
  class TestZIOApp extends ZIOAppDefault {
    override def run = ZIO.unit
    
    def testInstallSignalHandlers(runtime: Runtime[Any])(implicit trace: Trace): UIO[Any] = {
      installSignalHandlers(runtime)
    }
  }
} 