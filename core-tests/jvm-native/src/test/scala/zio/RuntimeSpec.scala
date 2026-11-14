package zio

import zio.test.{TestAspect, ZTestLogger, assertCompletes, assertTrue}

import java.util.concurrent.atomic.AtomicBoolean

object RuntimeSpec extends ZIOBaseSpec {
  private implicit val unsafe: Unsafe = Unsafe.unsafe

  private val rt = Runtime.default.unsafe

  def spec = suite("RuntimeSpec")(
    test("unsafe.fork on synchronous effects") {
      ZIO.succeed {
        val (f1, f2) = mkEffects()
        rt.fork(f1)
        rt.fork(f2)
        assertCompletes
      }
    },
    test("unsafe.runToFuture on synchronous effects") {
      ZIO.suspendSucceed {
        val (f1, f2) = mkEffects()
        val fut1     = rt.runToFuture(f1)
        val fut2     = rt.runToFuture(f2)
        ZIO.fromFuture(_ => fut1) *> ZIO.fromFuture(_ => fut2) *> assertCompletes
      }
    },
    suite("OpLog")(
      test("rendered") {
        @scala.annotation.nowarn("cat=deprecation")
        val deprecatedAssertions = {
          val generateStackTraceEffect = ZIO.GenerateStackTrace(Trace.empty)
          val interruptibleEffect =
            ZIO.UpdateRuntimeFlagsWithin.Interruptible[Any, Nothing, Unit](Trace.empty, ZIO.succeed(()))
          val uninterruptibleEffect =
            ZIO.UpdateRuntimeFlagsWithin.Uninterruptible[Any, Nothing, Unit](Trace.empty, ZIO.succeed(()))
          val dynamicEffect =
            ZIO.UpdateRuntimeFlagsWithin.Dynamic[Any, Nothing, Unit](
              Trace.empty,
              RuntimeFlags.enable(RuntimeFlag.Interruption),
              (_: RuntimeFlags) => ZIO.unit
            )
          assertTrue(
            ZIO.render(generateStackTraceEffect) == s"GenerateStackTrace(trace=${generateStackTraceEffect.trace})"
          ) &&
          assertTrue(
            ZIO.render(
              interruptibleEffect
            ) == s"UpdateRuntimeFlagsWithin.Interruptible(trace=${interruptibleEffect.trace}, effect=Sync(trace=${interruptibleEffect.effect.trace}))"
          ) &&
          assertTrue(
            ZIO.render(
              uninterruptibleEffect
            ) == s"UpdateRuntimeFlagsWithin.Uninterruptible(trace=${uninterruptibleEffect.trace}, effect=Sync(trace=${uninterruptibleEffect.effect.trace}))"
          ) &&
          assertTrue(
            ZIO.render(dynamicEffect) == s"UpdateRuntimeFlagsWithin.Dynamic(trace=${dynamicEffect.trace})"
          )
        }

        val syncEffect = ZIO.Sync(Trace.empty, () => ())
        val whileLoopEffect =
          ZIO.WhileLoop[Any, Nothing, Unit](Trace.empty, () => true, () => ZIO.unit, (_: Any) => ())
        val statefulEffect =
          ZIO.Stateful[Any, Nothing, Unit](Trace.empty, (_: Any, _) => ZIO.unit)
        val yieldNowEffect = ZIO.YieldNow(Trace.empty, forceAsync = true)
        val flatMapEffect =
          ZIO.FlatMap[Any, Nothing, Unit, Unit](Trace.empty, ZIO.succeed(()), (_: Any) => ZIO.unit)
        val mappedEffect =
          ZIO.Mapped[Any, Nothing, Unit, Unit](Trace.empty, ZIO.succeed(()), (_: Any) => ())
        val updateRuntimeFlagsEffect =
          ZIO.UpdateRuntimeFlags(Trace.empty, RuntimeFlags.enable(RuntimeFlag.Interruption))
        val foldZioEffect =
          ZIO.FoldZIO[Any, Nothing, Unit, Unit, Unit](
            Trace.empty,
            ZIO.succeed(()),
            (_: Any) => ZIO.unit,
            (_: Any) => ZIO.fail(())
          )
        val asyncEffect =
          ZIO.Async[Any, Nothing, Unit](Trace.empty, (_: Any) => Right(ZIO.unit), () => FiberId.None)
        val dynamicNoBoxEffect =
          ZIO.UpdateRuntimeFlagsWithin.DynamicNoBox[Any, Nothing, Unit](
            Trace.empty,
            RuntimeFlags.enable(RuntimeFlag.Interruption),
            (_: Int) => ZIO.unit
          )
        val exitSuccessEffect = Exit.succeed(0)
        val exitFailureEffect = Exit.fail("boom")
        val throwingToStringEffect = Exit.succeed(new AnyRef {
          override def toString: String = throw new RuntimeException("toString boom")
        })
        val deepEffect =
          (1 to 10000).foldLeft[UIO[Unit]](ZIO.succeed(()))((acc, _) =>
            ZIO.FlatMap[Any, Nothing, Unit, Unit](Trace.empty, acc, (_: Unit) => ZIO.unit)
          )

        assertTrue(ZIO.render(null) == "null") &&
        assertTrue(ZIO.render(syncEffect) == s"Sync(trace=${syncEffect.trace})") &&
        assertTrue(ZIO.render(whileLoopEffect) == s"WhileLoop(trace=${whileLoopEffect.trace})") &&
        assertTrue(ZIO.render(statefulEffect) == s"Stateful(trace=${statefulEffect.trace})") &&
        assertTrue(ZIO.render(yieldNowEffect) == s"YieldNow(trace=${yieldNowEffect.trace}, forceAsync=true)") &&
        assertTrue(
          ZIO.render(
            flatMapEffect
          ) == s"FlatMap(trace=${flatMapEffect.trace}, first=Sync(trace=${flatMapEffect.first.trace}))"
        ) &&
        assertTrue(
          ZIO.render(
            mappedEffect
          ) == s"Mapped(trace=${mappedEffect.trace}, first=Sync(trace=${mappedEffect.first.trace}))"
        ) &&
        assertTrue(
          ZIO.render(updateRuntimeFlagsEffect) == s"UpdateRuntimeFlags(trace=${updateRuntimeFlagsEffect.trace})"
        ) &&
        assertTrue(
          ZIO.render(
            foldZioEffect
          ) == s"FoldZIO(trace=${foldZioEffect.trace}, first=Sync(trace=${foldZioEffect.first.trace}))"
        ) &&
        assertTrue(ZIO.render(asyncEffect) == s"Async(trace=${asyncEffect.trace})") &&
        assertTrue(
          ZIO.render(
            dynamicNoBoxEffect
          ) == s"UpdateRuntimeFlagsWithin.DynamicNoBox(trace=${dynamicNoBoxEffect.trace})"
        ) &&
        assertTrue(ZIO.render(exitSuccessEffect) == "Exit.Success(value=0)") &&
        assertTrue(
          ZIO.render(exitFailureEffect) == "Exit.Failure(cause=Fail(boom,StackTrace.none))"
        ) &&
        assertTrue(
          ZIO.render(throwingToStringEffect) == "Exit.Success(value=<java.lang.RuntimeException>)"
        ) &&
        assertTrue(
          ZIO.render(deepEffect) == "FlatMap(trace=, first=FlatMap(trace=, first=FlatMap(trace=, first=...)))"
        ) &&
        deprecatedAssertions
      },
      test("enabled") {
        val effect1 = ZIO.succeed(1)
        val effect = for {
          a <- effect1
          b <- ZIO.succeed(2)
          c  = a + b
        } yield c

        val effectRendered =
          s"FlatMap(trace=${effect.trace}, first=Sync(trace=${effect1.trace}))"

        for {
          _      <- effect
          output <- ZTestLogger.logOutput
        } yield assertTrue(
          output.exists(entry => entry.message() == effectRendered && entry.logLevel == LogLevel.Debug)
        )
      }.provide(Runtime.enableOpLog),
      test("disabled") {
        val effect1 = ZIO.succeed(1)
        val effect = for {
          a <- effect1
          b <- ZIO.succeed(2)
        } yield a + b

        val effectRendered =
          s"FlatMap(trace=${effect.trace}, first=Sync(trace=${effect1.trace}))"

        for {
          _      <- effect
          _      <- ZIO.logInfo("user-marker")
          output <- ZTestLogger.logOutput
        } yield assertTrue(
          output.map(_.message()) == Chunk("user-marker") &&
            !output.exists(_.message() == effectRendered)
        )
      }.provide(Runtime.disableFlags(RuntimeFlag.OpLog))
    )
  ) @@ TestAspect.timeout(5.seconds)

  private def mkEffects() = {
    val loop = new AtomicBoolean(true)

    val f1 = ZIO.succeed {
      while (loop.get()) { Thread.sleep(1) }
    }
    val f2 = ZIO.succeed {
      loop.set(false)
    }

    (f1, f2)
  }

}
