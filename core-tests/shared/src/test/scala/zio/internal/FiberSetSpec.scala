package zio.internal

import zio.test._
import zio.{Fiber, FiberId, FiberRefs, RuntimeFlags, Trace, ZIO, ZIOBaseSpec}

object FiberSetSpec extends ZIOBaseSpec {

  def spec =
    suite("FiberSetSpec")(
      test("add and remove a live fiber") {
        val set   = FiberSet()
        val fiber = runtimeFiber(1)

        assertTrue(
          set.add(fiber),
          set.iterator.contains(fiber),
          set.remove(fiber),
          set.isEmpty
        )
      },
      test("does not add a completed fiber") {
        for {
          fiber <- ZIO.unit.fork
          _     <- fiber.await
          set    = FiberSet()
        } yield assertTrue(!set.add(fiber), set.isEmpty)
      },
      test("supports concurrent add and remove") {
        val set    = FiberSet()
        val fibers = (1 to 1000).map(runtimeFiber)

        for {
          _    <- ZIO.foreachParDiscard(fibers)(fiber => ZIO.succeed(set.add(fiber)))
          size <- ZIO.succeed(set.size)
          _    <- ZIO.foreachParDiscard(fibers)(fiber => ZIO.succeed(set.remove(fiber)))
        } yield assertTrue(size == fibers.size, set.isEmpty)
      },
      test("iterator drops interrupted fibers") {
        val set = FiberSet()

        for {
          fiber <- ZIO.never.fork
          _      = set.add(fiber)
          _     <- fiber.interrupt
        } yield assertTrue(set.isEmpty)
      },
      test("gc keeps live fibers visible") {
        val set   = FiberSet()
        val fiber = runtimeFiber(2)

        set.add(fiber)
        set.gc()

        assertTrue(set.iterator.contains(fiber), set.size == 1)
      },
      test("Fiber.roots excludes completed daemon fibers") {
        def rootContains(fiberId: FiberId): ZIO[Any, Nothing, Boolean] =
          Fiber.roots.map(_.exists(_.id == fiberId))

        for {
          fiber <- ZIO.never.forkDaemon
          _     <- rootContains(fiber.id).repeatUntil(identity)
          _     <- fiber.interrupt
          roots <- Fiber.roots
        } yield assertTrue(!roots.exists(_.id == fiber.id))
      }
    )

  private def runtimeFiber(id: Int): FiberRuntime[Any, Any] =
    FiberRuntime(FiberId.Runtime(id, 0L, Trace.empty), FiberRefs.empty, RuntimeFlags.default)
}
