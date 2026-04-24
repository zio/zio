package zio

import zio.test.Assertion.isNull
import zio.test.TestAspect.{blocking, nonFlaky, sequential, withLiveClock}
import zio.test._

import java.util.concurrent.atomic.AtomicBoolean

object ZIOSpecJVM extends ZIOBaseSpec {

  def spec = suite("ZIOSpecJVM")(
    suite("cooperative yielding") {
      test("cooperative yielding") {
        import java.util.concurrent._

        val executor = zio.Executor.fromJavaExecutor(Executors.newSingleThreadExecutor())

        val checkExecutor =
          ZIO.executor.flatMap(e => if (e != executor) ZIO.dieMessage("Executor is incorrect") else ZIO.unit)

        def infiniteProcess(ref: Ref[Int]): UIO[Nothing] =
          checkExecutor *> ref.update(_ + 1) *> infiniteProcess(ref)

        for {
          ref1   <- Ref.make(0)
          ref2   <- Ref.make(0)
          ref3   <- Ref.make(0)
          fiber1 <- infiniteProcess(ref1).onExecutor(executor).fork
          fiber2 <- infiniteProcess(ref2).onExecutor(executor).fork
          fiber3 <- infiniteProcess(ref3).onExecutor(executor).fork
          _      <- Live.live(ZIO.sleep(Duration.fromSeconds(1)))
          _      <- fiber1.interruptFork *> fiber2.interruptFork *> fiber3.interruptFork
          _      <- fiber1.await *> fiber2.await *> fiber3.await
          v1     <- ref1.get
          v2     <- ref2.get
          v3     <- ref3.get
        } yield assertTrue(v1 > 0 && v2 > 0 && v3 > 0)
      }
    },
    suite("fromAutoCloseable")(
      test("is null-safe") {
        // Will be `null` because the file doesn't exist
        def loadNonExistingFile = ZIO.attempt(this.getClass.getResourceAsStream(s"this_file_doesnt_exist.json"))

        for {
          shouldBeNull <- loadNonExistingFile
          // Should not fail when closing a null resource
          // The test will fail if the resource is not closed properly
          _ <- ZIO.fromAutoCloseable(loadNonExistingFile)
        } yield assert(shouldBeNull)(isNull)
      }
    ),
    suite("race")(
      test("interrupts both fibers running synchronous effects") {
        // NOTE: Using Java's concurrency classes since we want our effects to run fully synchronously
        val latch              = new java.util.concurrent.CountDownLatch(2)
        val p1, p2             = new java.util.concurrent.Semaphore(1)
        val p1Called, p2Called = new AtomicBoolean()

        val f1 = ZIO.succeed {
          latch.countDown()
          p1.acquire()
        } *> ZIO.succeed(p1Called.set(true))

        val f2 = ZIO.succeed {
          latch.countDown()
          p2.acquire()
        } *> ZIO.succeed(p2Called.set(true))

        p1.acquire()
        p2.acquire()
        for {
          f <- f1.race(f2).fork
          _  = latch.await()
          _ <- f.interruptFork
          _ <- ZIO.sleep(2.millis) // No better way to do this, unfortunately
          _  = p1.release()
          _  = p2.release()
          _ <- f.await
        } yield assertTrue(!p1Called.get(), !p2Called.get())
      } @@ nonFlaky(100) @@ blocking @@ withLiveClock
    )
  ) @@ sequential
}
