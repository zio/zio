package zio

import zio.test._
import zio.test.Assertion._

object CatchAllDefectSpec extends ZIOBaseSpec {

  def spec = suite("CatchAllDefectSpec")(
    suite("catchAll with defects")(
      test("should not catch pure defects") {
        val defectCause = Cause.die(new RuntimeException("boom"))
        val result = ZIO.failCause(defectCause).catchAll(_ => ZIO.succeed("caught"))
        
        // Should fail with the defect, not be caught
        assertZIO(result.exit)(Assertion.dies(Assertion.anything))
      },
      
      test("should not catch combined failure and defect") {
        val dieCause = Cause.die(new RuntimeException("boom"))
        val combinedCause = dieCause && Cause.fail("failure")
        val result = ZIO.failCause(combinedCause).catchAll(_ => ZIO.succeed("caught"))
        
        // Should fail with defect, not catch the failure
        assertZIO(result.exit)(Assertion.dies(Assertion.anything))
      },
      
      test("should still catch pure failures") {
        val failureCause = Cause.fail("error")
        val result = ZIO.failCause(failureCause).catchAll(e => ZIO.succeed(s"caught: $e"))
        
        assertZIO(result)(Assertion.equalTo("caught: error"))
      },
      
      test("should preserve interruptions") {
        for {
          fiber <- ZIO.never.fork
          _     <- fiber.interrupt
          result <- ZIO.failCause(Cause.interrupt(fiber.id) && Cause.fail("error"))
                      .catchAll(_ => ZIO.succeed("caught"))
                      .exit
        } yield assert(result)(Assertion.isInterrupted)
      },
      
      test("foreachPar should work correctly") {
        val items = List(1, 2, 3)
        val effect = ZIO.foreachPar(items) { i =>
          if (i == 2) ZIO.fail("error") else ZIO.succeed(i)
        }.catchAll(_ => ZIO.succeed(List.empty))
        
        // Should catch the failure, not break due to internal interruptions
        assertZIO(effect)(Assertion.equalTo(List.empty))
      },
      
      test("should handle complex cause trees correctly") {
        val defect1 = Cause.die(new RuntimeException("defect1"))
        val defect2 = Cause.die(new IllegalStateException("defect2"))
        val failure1 = Cause.fail("failure1")
        val failure2 = Cause.fail("failure2")
        
        // ((defect1 && failure1) then (defect2 && failure2))
        val complexCause = (defect1 && failure1).then(defect2 && failure2)
        
        val result = ZIO.failCause(complexCause).catchAll(_ => ZIO.succeed("caught"))
        
        // Should not be caught due to defects
        assertZIO(result.exit)(Assertion.dies(Assertion.anything))
      },
      
      test("should handle sequential defects correctly") {
        val defect1 = Cause.die(new RuntimeException("defect1"))
        val defect2 = Cause.die(new IllegalStateException("defect2"))
        val failure = Cause.fail("failure")
        
        // defect1 then (failure then defect2)
        val sequentialCause = defect1.then(failure.then(defect2))
        
        val result = ZIO.failCause(sequentialCause).catchAll(_ => ZIO.succeed("caught"))
        
        // Should not be caught due to defects
        assertZIO(result.exit)(Assertion.dies(Assertion.anything))
      },
      
      test("should handle parallel defects correctly") {
        val defect1 = Cause.die(new RuntimeException("defect1"))
        val defect2 = Cause.die(new IllegalStateException("defect2"))
        val failure = Cause.fail("failure")
        
        // (defect1 && defect2) && failure
        val parallelCause = (defect1 && defect2) && failure
        
        val result = ZIO.failCause(parallelCause).catchAll(_ => ZIO.succeed("caught"))
        
        // Should not be caught due to defects
        assertZIO(result.exit)(Assertion.dies(Assertion.anything))
      }
    )
  )
} 