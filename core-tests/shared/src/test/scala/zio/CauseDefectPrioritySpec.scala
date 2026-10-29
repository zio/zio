package zio

import zio.test.Assertion._
import zio.test._

object CauseDefectPrioritySpec extends ZIOBaseSpec {

  def spec = suite("CauseDefectPrioritySpec")(
    test("failureOrCause prioritizes defects over failures in Both and Then") {
      val defect  = Cause.die(new RuntimeException("boom"))
      val failure = Cause.fail("error")

      assert((defect && failure).failureOrCause)(isRight(equalTo(defect))) &&
      assert((failure && defect).failureOrCause)(isRight(equalTo(defect))) &&
      assert((defect ++ failure).failureOrCause)(isRight(equalTo(defect))) &&
      assert((failure ++ defect).failureOrCause)(isRight(equalTo(defect)))
    },
    test("failureTraceOrCause prioritizes defects over failures in Both and Then") {
      val defect  = Cause.die(new RuntimeException("boom"))
      val failure = Cause.fail("error")

      assert((defect && failure).failureTraceOrCause)(isRight(equalTo(defect))) &&
      assert((failure && defect).failureTraceOrCause)(isRight(equalTo(defect))) &&
      assert((defect ++ failure).failureTraceOrCause)(isRight(equalTo(defect))) &&
      assert((failure ++ defect).failureTraceOrCause)(isRight(equalTo(defect)))
    },
    test("failureOrCause preserves Interrupt alongside Die while stripping Fail") {
      val defect    = Cause.die(new RuntimeException("boom"))
      val interrupt = Cause.interrupt(FiberId.None)
      val failure   = Cause.fail("error")
      val combined  = defect && (failure && interrupt)

      assert(combined.failureOrCause)(isRight(equalTo(defect && interrupt))) &&
      assert(combined.failureTraceOrCause)(isRight(equalTo(defect && interrupt)))
    },
    test("failureOrCause returns Left when Fail co-occurs only with Interrupt") {
      val interrupt = Cause.interrupt(FiberId.None)
      val failure   = Cause.fail("error")

      assert((failure && interrupt).failureOrCause)(isLeft(equalTo("error"))) &&
      assert((interrupt && failure).failureOrCause)(isLeft(equalTo("error"))) &&
      assert((failure ++ interrupt).failureOrCause)(isLeft(equalTo("error"))) &&
      assert((interrupt ++ failure).failureOrCause)(isLeft(equalTo("error"))) &&
      assert((failure && interrupt).failureTraceOrCause.left.map(_._1))(isLeft(equalTo("error")))
    },
    test("stripFailures collapses Empty branches and preserves linearize/prettyPrint on Fail ++ Die") {
      val boom     = new RuntimeException("finalizer-boom")
      val defect   = Cause.die(boom)
      val combined = Cause.fail("primary-error") ++ defect
      val stripped = combined.stripFailures

      assertTrue(
        stripped == defect,
        stripped.linearize == Set(defect),
        stripped.prettyPrint.contains("finalizer-boom"),
        (Cause.fail("a") && Cause.fail("b")).stripFailures.isEmpty,
        Cause.stackless(Cause.fail("a")).stripFailures.isEmpty
      )
    },
    test("catchAll, catchSome, foldZIO, and either do not swallow defects in composite causes") {
      val boom       = new RuntimeException("boom")
      val bothCause  = Cause.die(boom) && Cause.fail("error")
      val thenCause  = Cause.fail("error") ++ Cause.die(boom)
      val traceCause = Cause.fail("error") && Cause.die(boom)

      for {
        exit1 <- ZIO.failCause(bothCause).catchAll(_ => ZIO.succeed("recovered")).exit
        exit2 <- ZIO.failCause(thenCause).catchAll(_ => ZIO.succeed("recovered")).exit
        exit3 <- ZIO.failCause(bothCause).catchSome { case "error" => ZIO.succeed("recovered") }.exit
        exit4 <- ZIO.failCause(bothCause).foldZIO(_ => ZIO.succeed("recovered"), ZIO.succeed(_)).exit
        exit5 <- ZIO.failCause(bothCause).either.exit
        exit6 <- ZIO.failCause(traceCause).catchAllTrace(_ => ZIO.succeed("recovered")).exit
        exit7 <- ZIO.fail("error").ensuring(ZIO.die(boom)).catchAll(_ => ZIO.succeed("recovered")).exit
      } yield assert(exit1)(dies(equalTo(boom))) &&
        assert(exit2)(dies(equalTo(boom))) &&
        assert(exit3)(dies(equalTo(boom))) &&
        assert(exit4)(dies(equalTo(boom))) &&
        assert(exit5)(dies(equalTo(boom))) &&
        assert(exit6)(dies(equalTo(boom))) &&
        assert(exit7)(dies(equalTo(boom)))
    },
    test("foreachPar recovers from typed failure when sibling fibers are interrupted but not when a sibling dies") {
      val boom = new RuntimeException("sibling-defect")
      for {
        latch <- Promise.make[Nothing, Unit]
        recovered <- ZIO
                       .foreachPar(List(1, 2))(i => if (i == 1) ZIO.fail("typed-fail") else ZIO.never)
                       .catchAll(e => ZIO.succeed(s"handled:$e"))
        diedExit <- ZIO
                      .foreachPar(List(1, 2)) { i =>
                        if (i == 1) (latch.await *> ZIO.fail("typed-fail")).uninterruptible
                        else (latch.succeed(()) *> ZIO.die(boom)).uninterruptible
                      }
                      .catchAll(e => ZIO.succeed(s"handled:$e"))
                      .exit
      } yield assertTrue(recovered == "handled:typed-fail") &&
        assert(diedExit)(dies(equalTo(boom)))
    }
  )
}
