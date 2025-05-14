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
    
    test("raceFirst - interrupts loser") {
      for {
        ref <- Ref.make(0)
        result <- OptimizedRace.raceFirst(
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
    
    test("raceEither - returns correct winner") {
      for {
        ref <- Ref.make(0)
        result <- OptimizedRace.raceEither(
          ref.update(_ + 1).as("left"),
          ZIO.sleep(1.second) *> ref.update(_ + 2).as("right")
        )
        value <- ref.get
      } yield {
        assertTrue(result == Left("left")) &&
        assertTrue(value == 1) // Only left side executed
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
          (leftExit, rightFiber) => ZIO.succeed("left wins"),
          (rightExit, leftFiber) => ZIO.succeed("right wins")
        )
        value <- ref.get
      } yield {
        assertTrue(result == "right wins") &&
        assertTrue(value == 2) // Only right side executed
      }
    },
    
    test("raceFibersWithOptimized - synthetic leftFiber has correct id") {
      for {
        parentFiberId <- ZIO.fiberId
        var capturedLeftFiberId: FiberId = null
        _ <- OptimizedRace.raceFibersWithOptimized(
          ZIO.sleep(1.second).as("left"),
          ZIO.succeed("right")
        )(
          (_, _) => ZIO.succeed(()),
          (_, leftFiber) => ZIO.succeed(capturedLeftFiberId = leftFiber.id)
        )
      } yield {
        assertTrue(capturedLeftFiberId == parentFiberId)
      }
    },
    
    test("raceFibersWithOptimized - interrupting synthetic leftFiber interrupts parent") {
      for {
        promise <- Promise.make[Nothing, Unit]
        interruptPromise <- Promise.make[Nothing, Boolean]
        fiber <- OptimizedRace.raceFibersWithOptimized(
          ZIO.sleep(1.second).as("left"),
          ZIO.succeed("right")
        )(
          (_, _) => ZIO.never,
          (_, leftFiber) => 
            for {
              _ <- promise.succeed(())
              _ <- ZIO.sleep(100.millis)
              _ <- leftFiber.interrupt
              _ <- ZIO.sleep(100.millis)
              _ <- interruptPromise.succeed(true)
            } yield "interrupted"
        ).fork
        _ <- promise.await
        interrupted <- interruptPromise.await.timeoutTo(false)(true)(5.seconds)
      } yield {
        assertTrue(interrupted)
      }
    },
    
    test("raceFibersWithOptimized - synthetic leftFiber correctly propagates fiber refs") {
      for {
        fiberRef <- FiberRef.make("initial")
        result <- OptimizedRace.raceFibersWithOptimized(
          ZIO.sleep(1.second).as("left"),
          ZIO.succeed("right")
        )(
          (_, _) => ZIO.succeed("left wins"),
          (_, leftFiber) => 
            for {
              _ <- leftFiber.inheritAll // This should inherit from parent fiber
              value <- fiberRef.get
            } yield value
        )
      } yield {
        assertTrue(result == "initial") // Should inherit the initial value from parent
      }
    },
    
    test("raceFibersWithOptimized - synthetic leftFiber map works correctly") {
      for {
        var mapCalled = false
        mappedValue <- OptimizedRace.raceFibersWithOptimized(
          ZIO.sleep(1.second).as("left"),
          ZIO.succeed("right")
        )(
          (_, _) => ZIO.succeed(()),
          (_, leftFiber) => 
            for {
              mappedFiber <- ZIO.succeed(leftFiber.map(_ => {
                mapCalled = true
                "mapped"
              }))
              exit <- mappedFiber.poll.map(_.getOrElse(Exit.fail(Cause.empty)))
            } yield exit.isFailure // Should be a failure since the left fiber is interrupted
        )
      } yield {
        assertTrue(mappedValue) // Exit should be a failure
      }
    },
    
    test("raceFibersWithOptimized - synthetic leftFiber orElse works correctly") {
      for {
        fiberRef <- FiberRef.make("initial")
        _ <- fiberRef.set("parent")
        rightFiberRef <- FiberRef.make("initial")
        rightFiber <- rightFiberRef.set("right").fork
        result <- OptimizedRace.raceFibersWithOptimized(
          ZIO.sleep(1.second).as("left"),
          ZIO.succeed("right")
        )(
          (_, _) => ZIO.succeed("left wins"),
          (_, leftFiber) => 
            for {
              combinedFiber = leftFiber.orElse(rightFiber)
              _ <- combinedFiber.inheritAll
              value <- fiberRef.get
              rightValue <- rightFiberRef.get
            } yield (value, rightValue)
        )
      } yield {
        assertTrue(result._1 == "parent" && result._2 == "right") // Should inherit from both fibers
      }
    }
  )
}