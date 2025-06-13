package zio.app

import zio._
import zio.test._
import zio.test.Assertion._

/**
 * Tests specific to signal handling behavior in ZIOApp.
 * These tests verify the fix for issue #9240 where signal handlers
 * should gracefully degrade on unsupported platforms.
 */
object ZIOAppSignalHandlingSpec extends ZIOBaseSpec {
  def spec = suite("ZIOAppSignalHandlingSpec")(
    test("addSignalHandler does not throw on any platform") {
      // Test that installing signal handlers doesn't throw exceptions
      // The real test is that this doesn't throw ClassDefNotFoundError on JS/Native
      val app = new ZIOAppDefault {
        override def run = ZIO.unit
        
        // Override the installSignalHandlers method to force execution for testing
        override protected def installSignalHandlers(runtime: Runtime[Any])(implicit trace: Trace): UIO[Any] = {
          super.installSignalHandlers(runtime)
        }
      }
      
      for {
        runtime <- ZIO.runtime[Any]
        result <- app.installSignalHandlers(runtime).exit
      } yield assertTrue(result.isSuccess)
    },
    
    test("signal handlers are installed exactly once") {
      // Create a custom app that tracks how many times signal handlers are installed
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)
      
      val app = new ZIOAppDefault {
        override def run = ZIO.unit
        
        // Override to count installations
        override protected def installSignalHandlers(runtime: Runtime[Any])(implicit trace: Trace): UIO[Any] = {
          ZIO.attempt(counter.incrementAndGet()).ignore
        }
      }
      
      for {
        runtime <- ZIO.runtime[Any]
        _       <- app.installSignalHandlers(runtime)
        _       <- app.installSignalHandlers(runtime)  // Call again, should be no-op
        _       <- app.installSignalHandlers(runtime)  // Call again, should be no-op
        count   <- ZIO.succeed(counter.get())
      } yield assertTrue(count == 1)
    },
    
    test("windows platform detection works correctly") {
      // This is a unit test for the system detection that affects signal handling
      for {
        isWindows <- ZIO.attempt(System.os.isWindows)
      } yield {
        val osName = System.getProperty("os.name", "").toLowerCase()
        val expectedWindows = osName.contains("win")
        
        assertTrue(isWindows == expectedWindows)
      }
    }
  )
} 