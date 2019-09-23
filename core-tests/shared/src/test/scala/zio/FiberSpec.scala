package zio

import zio.test._
import zio.test.Assertion._
import zio.LatchOps._
import FiberSpecData._

object FiberSpec
    extends ZIOBaseSpec(
      suite("FiberSpec")(
        suite("Create a new Fiber and")(testM("lift it into Managed") {
          for {
            ref <- Ref.make(false)
            fiber <- withLatch { release =>
                      (release *> IO.unit).bracket_(ref.set(true))(IO.never).fork
                    }
            _     <- fiber.toManaged.use(_ => IO.unit)
            _     <- fiber.await
            value <- ref.get
          } yield assert(value, isTrue)
        }),
        suite("`inheritLocals` works for Fiber created using:")(
          testM("`map`") {
            for {
              fiberRef <- FiberRef.make(initial)
              child <- withLatch { release =>
                        (fiberRef.set(update) *> release).fork
                      }
              _     <- child.map(_ => ()).inheritFiberRefs
              value <- fiberRef.get
            } yield assert(value, equalTo(update))
          },
          testM("`orElse`") {
            for {
              fiberRef <- FiberRef.make(initial)
              latch1   <- Promise.make[Nothing, Unit]
              latch2   <- Promise.make[Nothing, Unit]
              child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
              child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
              _        <- latch1.await *> latch2.await
              _        <- child1.orElse(child2).inheritFiberRefs
              value    <- fiberRef.get
            } yield assert(value, equalTo("child1"))
          },
          testM("`zip`") {
            for {
              fiberRef <- FiberRef.make(initial)
              latch1   <- Promise.make[Nothing, Unit]
              latch2   <- Promise.make[Nothing, Unit]
              child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
              child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
              _        <- latch1.await *> latch2.await
              _        <- child1.zip(child2).inheritFiberRefs
              value    <- fiberRef.get
            } yield assert(value, equalTo("child1"))
          }
        ),
        suite("`Fiber.join` on interrupted Fiber")(
          testM("is inner interruption") {
            for {
              exit <- Fiber.interrupt.join.run
            } yield assert(exit, equalTo(Exit.interrupt))
          }
        )
      )
    )

object FiberSpecData {
  val (initial, update) = ("initial", "update")
}
