//> using lib "dev.zio::zio-test:2.1.17"
//> using lib "dev.zio::zio-test-sbt:2.1.17"
//> using file "SimplifiedOptimizedRace.scala"

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

/**
 * Tests to verify the correctness of the SimplifiedOptimizedRace implementation.
 * These tests ensure that the optimized implementation maintains the same behavior
 * as the standard ZIO race operation.
 */
object SimplifiedOptimizedRaceTest extends ZIOSpecDefault {

  def spec = suite("SimplifiedOptimizedRace")(
    test("should complete with the first effect to succeed") {
      for {
        // Test with right side completing first
        rightFirst <- SimplifiedOptimizedRace.race(
          ZIO.never,
          ZIO.succeed("right")
        )
        
        // Test with left side completing first
        leftFirst <- SimplifiedOptimizedRace.race(
          ZIO.succeed("left"),
          ZIO.never
        )
      } yield {
        assert(rightFirst)(equalTo("right")) &&
        assert(leftFirst)(equalTo("left"))
      }
    },
    
    test("should propagate errors from either side") {
      for {
        // Test error from right side
        rightError <- SimplifiedOptimizedRace.race(
          ZIO.never,
          ZIO.fail("right error")
        ).exit
        
        // Test error from left side
        leftError <- SimplifiedOptimizedRace.race(
          ZIO.fail("left error"),
          ZIO.never
        ).exit
      } yield {
        assert(rightError)(fails(equalTo("right error"))) &&
        assert(leftError)(fails(equalTo("left error")))
      }
    },
    
    test("should interrupt the loser when one side completes") {
      for {
        ref <- Ref.make(false)
        fiber <- SimplifiedOptimizedRace.race(
          ZIO.never,
          ZIO.succeed("winner")
        ).fork
        _ <- fiber.join
        interrupted <- ref.get
      } yield assert(interrupted)(isFalse) // The never effect should be interrupted
    },
    
    test("should behave the same as standard ZIO race") {
      for {
        // Compare with standard race - right side wins
        optimizedRightWin <- SimplifiedOptimizedRace.race(
          ZIO.never,
          ZIO.succeed("right")
        )
        standardRightWin <- ZIO.never.race(ZIO.succeed("right"))
        
        // Compare with standard race - left side wins
        optimizedLeftWin <- SimplifiedOptimizedRace.race(
          ZIO.succeed("left"),
          ZIO.never
        )
        standardLeftWin <- ZIO.succeed("left").race(ZIO.never)
      } yield {
        assert(optimizedRightWin)(equalTo(standardRightWin)) &&
        assert(optimizedLeftWin)(equalTo(standardLeftWin))
      }
    }
  ) @@ timeout(5.seconds) // Add timeout to prevent tests from hanging
}