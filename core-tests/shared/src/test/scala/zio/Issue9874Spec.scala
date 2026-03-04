package zio

import zio.test._

object Issue9874Spec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Issue9874Spec")(
      test("defects should not be silently ignored by catchAll") {
        val dieCause      = Cause.die(new RuntimeException("boom"))
        val failCause     = Cause.fail("error")
        val combinedCause = dieCause && failCause

        val effect = ZIO.failCause(combinedCause).catchAll(e => ZIO.succeed(s"handled: $e"))

        for {
          result <- effect.exit
        } yield assertTrue(result.isFailure) &&
          assertTrue(result.failureOption.exists(_.isDie))
      },
      test("catchAll should handle pure failures normally") {
        val failCause: Cause[String] = Cause.fail("error")

        val effect = ZIO.failCause(failCause).catchAll(e => ZIO.succeed(s"handled: $e"))

        for {
          result <- effect
        } yield assertTrue(result == "handled: error")
      },
      test("failureOrCause should return full cause when defects are present") {
        val dieCause: Cause[String] = Cause.die(new RuntimeException("boom"))
        val failCause: Cause[String] = Cause.fail("error")
        val combined = dieCause && failCause

        val result = combined.failureOrCause
        
        assertTrue(result.isRight) &&
          assertTrue(result.toOption.exists(_.isDie))
      },
      test("failureOrCause should return failure when no defects") {
        val failCause: Cause[String] = Cause.fail("error")

        val result = failCause.failureOrCause
        
        assertTrue(result.isLeft) &&
          assertTrue(result.left.toOption.contains("error"))
      },
      test("defect-only cause should be returned by failureOrCause") {
        val dieCause: Cause[String] = Cause.die(new RuntimeException("boom"))

        val result = dieCause.failureOrCause
        
        assertTrue(result.isRight) &&
          assertTrue(result.toOption.exists(_.isDie))
      },
      test("catchAllDefect should still work for pure defects") {
        val dieCause: Cause[String] = Cause.die(new RuntimeException("boom"))

        val effect = ZIO.failCause(dieCause).catchAllDefect(t => ZIO.succeed(s"defect handled: ${t.getMessage}"))

        for {
          result <- effect
        } yield assertTrue(result == "defect handled: boom")
      }
    )
}
