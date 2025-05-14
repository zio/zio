package zio

import zio.test._
import zio.test.Assertion._

object OptimizedRaceSpec extends ZIOSpecDefault {
  def spec = suite("OptimizedRaceSpec")(    
    test("race - left side wins") {
      for {
        ref <- Ref.make(0)
        result <- OptimizedRace.race(
          ref.update(_ + 1).as("left"),
          ZIO.sleep(1.second) *> ref.update(_ + 2).as("right")
        )
        value <- ref.get
      } yield {
        assertTrue(result == "left") &&
        assertTrue(value == 1) // Only left side executed
      }
    },
    
    test("race - right side wins") {
      for {
        ref <- Ref.make(0)
        result <- OptimizedRace.race(
          ZIO.sleep(1.second) *> ref.update(_ + 1).as("left"),
          ref.update(_ + 2).as("right")
        )
        value <- ref.get
      } yield {
        assertTrue(result == "right") &&
        assertTrue(value == 2) // Only right side executed
      }
    },
    
    test("race - interrupts loser") {
      for {
        ref <- Ref.make(0)
        promise <- Promise.make[Nothing, Unit]
        result <- OptimizedRace.race(
          ZIO.sleep(1.second) *> ref.update(_ + 1).as("left"),
          ref.update(_ + 2).as("right")
        )
        // Give time for interruption to propagate
        _ <- ZIO.sleep(100.millis)
        value <- ref.get
      } yield {
        assertTrue(result == "right") &&
        assertTrue(value == 2) // Loser should be interrupted
      }
    },
    
    test("raceFibersWithOptimized - left side wins") {
      for {
        ref <- Ref.make(0)
        result <- OptimizedRace.raceFibersWithOptimized(
          ref.update(_ + 1).as("left"),
          ZIO.sleep(1.second) *> ref.update(_ + 2).as("right")
        )(
          (leftExit, rightFiber) => ZIO.succeed("left wins") <* rightFiber.interrupt,
          (rightExit, leftFiber) => ZIO.succeed("right wins")
        )
        value <- ref.get
      } yield {
        assertTrue(result == "left wins") &&
        assertTrue(value == 1) // Only left side executed
      }
    },
    
    test("raceFibersWithOptimized - right side wins") {
      for {
        ref <- Ref.make(0)
        result <- OptimizedRace.raceFibersWithOptimized(
          ZIO.sleep(1.second) *> ref.update(_ + 1).as("left"),
          ref.update(_ + 2).as("right")
        )(
          (leftExit, rightFiber) => ZIO.succeed("left wins") <* rightFiber.interrupt,
          (rightExit, leftFiber) => ZIO.succeed("right wins") <* leftFiber.interrupt
        )
        value <- ref.get
      } yield {
        assertTrue(result == "right wins") &&
        assertTrue(value == 2) // Only right side executed
      }
    },
    
    test("raceFibersWithOptimized - fiber inheritance") {
      for {
        ref <- FiberRef.make(0)
        _ <- ref.set(42)
        result <- OptimizedRace.raceFibersWithOptimized(
          ZIO.succeed("left"),
          ZIO.never
        )(
          (leftExit, rightFiber) => for {
            _ <- rightFiber.interrupt
            value <- ref.get
          } yield (leftExit.getOrElse(""), value),
          (rightExit, leftFiber) => for {
            _ <- leftFiber.interrupt
            value <- ref.get
          } yield (rightExit.getOrElse(""), value)
        )
      } yield {
        assertTrue(result._1 == "left") &&
        assertTrue(result._2 == 42) // FiberRef value is preserved
      }
    },
    
    test("raceFibersWithOptimized - interruption propagation") {
      for {
        interrupted <- Ref.make(false)
        fiber <- OptimizedRace.raceFibersWithOptimized(
          ZIO.never,
          ZIO.never
        )(
          (_, _) => ZIO.succeed(1),
          (_, _) => ZIO.succeed(2)
        ).onInterrupt(interrupted.set(true)).fork
        _ <- ZIO.sleep(100.millis)
        _ <- fiber.interrupt
        result <- interrupted.get
      } yield {
        assertTrue(result) // Interruption was propagated
      }
    },
    
    test("raceFibersWithOptimized - synthetic fiber correctly represents parent") {
      for {
        parentRef <- FiberRef.make("parent")
        childRef <- FiberRef.make("child")
        _ <- parentRef.set("modified parent")
        result <- OptimizedRace.raceFibersWithOptimized(
          ZIO.sleep(1.second) *> ZIO.succeed("left"),
          childRef.set("modified child") *> ZIO.succeed("right")
        )(
          (_, rightFiber) => for {
            _ <- rightFiber.interrupt
            parentValue <- parentRef.get
            childValue <- childRef.get
          } yield (parentValue, childValue),
          (_, leftFiber) => for {
            parentValue <- parentRef.get
            childValue <- childRef.get
          } yield (parentValue, childValue)
        )
      } yield {
        // If right side wins, the synthetic left fiber should correctly represent parent fiber
        assertTrue(result._1 == "modified parent") &&
        assertTrue(result._2 == "modified child")
      }
    },
    
    test("raceFibersWithOptimized - synthetic leftFiber has correct id") {
      for {
        parentFiberId <- ZIO.fiberId
        fiberIdRef <- Ref.make[Option[FiberId]](None)
        _ <- OptimizedRace.raceFibersWithOptimized(
          ZIO.sleep(1.second).as("left"),
          ZIO.succeed("right")
        )(
          (_, _) => ZIO.succeed(()),
          (_, leftFiber) => fiberIdRef.set(Some(leftFiber.id))
        )
        capturedLeftFiberId <- fiberIdRef.get
      } yield {
        assertTrue(capturedLeftFiberId.contains(parentFiberId))
      }
    }
  )
}