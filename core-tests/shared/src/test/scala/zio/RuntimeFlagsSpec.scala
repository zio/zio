package zio

import zio.test._

object RuntimeFlagsSpec extends ZIOBaseSpec {
  import RuntimeFlag._

  val genFlags: Seq[Gen[Any, RuntimeFlag]] =
    RuntimeFlag.all.toSeq.map(Gen.const(_))

  val genRuntimeFlag = Gen.oneOf(genFlags: _*)

  val genRuntimeFlags = Gen.setOf(genRuntimeFlag).map(set => RuntimeFlags(set.toSeq: _*))

  def spec =
    suite("RuntimeFlagsSpec") {
      suite("unit") {
        test("enabled & isDisabled") {
          val flags =
            RuntimeFlags(Interruption, CurrentFiber)

          assertTrue(RuntimeFlags.isEnabled(flags)(Interruption)) &&
          assertTrue(RuntimeFlags.isEnabled(flags)(CurrentFiber)) &&
          assertTrue(RuntimeFlags.isDisabled(flags)(FiberRoots)) &&
          assertTrue(RuntimeFlags.isDisabled(flags)(OpLog)) &&
          assertTrue(RuntimeFlags.isDisabled(flags)(OpSupervision)) &&
          assertTrue(RuntimeFlags.isDisabled(flags)(RuntimeMetrics))
        } +
          test("enabled patching") {
            val on = RuntimeFlags.Patch.andThen(RuntimeFlags.enable(CurrentFiber), RuntimeFlags.enable(OpLog))

            assertTrue(
              RuntimeFlags
                .toSet(RuntimeFlags.Patch.patch(on)(RuntimeFlags.none)) == Set[RuntimeFlag](CurrentFiber, OpLog)
            )
          } +
          test("inverse") {
            val bothOn = RuntimeFlags.Patch.andThen(RuntimeFlags.enable(CurrentFiber), RuntimeFlags.enable(OpLog))

            val initial = RuntimeFlags(CurrentFiber, OpLog)

            assertTrue(
              RuntimeFlags.Patch.patch(RuntimeFlags.Patch.inverse(RuntimeFlags.enable(CurrentFiber)))(
                initial
              ) == RuntimeFlags(OpLog)
            ) &&
            assertTrue(RuntimeFlags.Patch.patch(RuntimeFlags.Patch.inverse(bothOn))(initial) == RuntimeFlags.none)
          } +
          test("diff") {
            val oneOn  = RuntimeFlags(CurrentFiber)
            val bothOn = RuntimeFlags(CurrentFiber, OpLog)

            assertTrue(RuntimeFlags.diff(oneOn, bothOn) == RuntimeFlags.enable(OpLog))

          }
      } +
        suite("gen") {
          test("enabled") {
            checkN(100)(genRuntimeFlags) { flags =>
              assertTrue(RuntimeFlags.toSet(flags).forall(flag => RuntimeFlags.isEnabled(flags)(flag)))
            }
          } +
            test("diff") {
              checkN(100)(genRuntimeFlags) { flags =>
                val diff = RuntimeFlags.diff(RuntimeFlags.none, flags)

                assertTrue(RuntimeFlags.Patch.patch(diff)(RuntimeFlags.none) == flags)
              }
            } +
            test("inverse") {
              checkN(100)(genRuntimeFlags) { flags =>
                val d = RuntimeFlags.diff(RuntimeFlags.none, flags)

                assertTrue(
                  RuntimeFlags.Patch.patch(RuntimeFlags.Patch.inverse(d))(flags) == RuntimeFlags.none &&
                    RuntimeFlags.Patch
                      .patch(RuntimeFlags.Patch.inverse(RuntimeFlags.Patch.inverse(d)))(RuntimeFlags.none) == flags
                )
              }
            }
        } +
        suite("setting flags via Runtime layers") {
          import RuntimeFlag._
          test("enabling") {
            val expected =
              RuntimeFlags.enableAll(RuntimeFlags.default)(RuntimeFlags(RuntimeMetrics, OpLog))
            ZIO.runtimeFlags
              .map(f => assertTrue(f == expected))
              .provideLayer(Runtime.enableFlags(OpLog, RuntimeMetrics))
          } +
            test("disabling") {
              val expected =
                RuntimeFlags.disableAll(RuntimeFlags.default)(
                  RuntimeFlags(FiberRoots, CooperativeYielding)
                )
              ZIO.runtimeFlags
                .map(f => assertTrue(f == expected))
                .provideLayer(Runtime.disableFlags(FiberRoots, CooperativeYielding))
            } +
            test("enabling & disabling via macros") {
              val expected = {
                val f1 = RuntimeFlags.enableAll(RuntimeFlags.default)(RuntimeFlags(OpLog, RuntimeMetrics))
                RuntimeFlags.disableAll(f1)(RuntimeFlags(FiberRoots, CooperativeYielding))
              }
              ZIO.runtimeFlags
                .map(f => assertTrue(f == expected))
                .provideLayer(
                  ZLayer.make[Any](
                    Runtime.enableFlags(OpLog, RuntimeMetrics),
                    Runtime.disableFlags(FiberRoots, CooperativeYielding)
                  )
                )
            }
        } +
        suite("EagerShiftBack") {
          test("enabled") {
            for {
              _    <- ZIO.fiberId
              _    <- ZIO.succeedBlocking(())
              name <- ZIO.succeed(Thread.currentThread().getName)
            } yield assertTrue(name.startsWith("zio-default-blocking"))
          }.provide(Runtime.disableFlags(RuntimeFlag.EagerShiftBack)) +
            test("disabled") {
              for {
                _    <- ZIO.fiberId
                _    <- ZIO.succeedBlocking(())
                name <- ZIO.succeed(Thread.currentThread().getName)
              } yield assertTrue(name.startsWith("ZScheduler-Worker"))
            }.provide(Runtime.enableFlags(RuntimeFlag.EagerShiftBack))
        } @@ TestAspect.jvmOnly +
        suite("OpLog") {
          test("rendered") {
            val syncEffect = ZIO.Sync(Trace.empty, () => {})
            val whileLoopEffect = ZIO
              .WhileLoop[Any, Nothing, Unit](Trace.empty, () => true, () => ZIO.succeed(()), (_: Any) => {})
            val statefulEffect =
              ZIO.Stateful[Any, Nothing, Unit](Trace.empty, (_: Any, _) => ZIO.succeed(()))
            val yieldNowEffect = ZIO.YieldNow(Trace.empty, forceAsync = true)
            val flatMapEffect =
              ZIO.FlatMap[Any, Nothing, Unit, Unit](Trace.empty, ZIO.succeed(()), (_: Any) => ZIO.unit)
            val updateRuntimeFlagsEffect =
              ZIO.UpdateRuntimeFlags(Trace.empty, RuntimeFlags.enable(RuntimeFlag.Interruption))
            val foldZioEffect =
              ZIO
                .FoldZIO[Any, Nothing, Unit, Unit, Unit](
                  Trace.empty,
                  ZIO.succeed(()),
                  (_: Any) => ZIO.succeed(()),
                  (_: Any) => ZIO.fail(())
                )

            val asyncEffect = ZIO
              .Async[Any, Nothing, Unit](Trace.empty, (_: Any) => ZIO.succeed(()), () => FiberId.None)

            val dynamicNoBoxEffect =
              ZIO.UpdateRuntimeFlagsWithin
                .DynamicNoBox[Any, Nothing, Unit](
                  Trace.empty,
                  RuntimeFlags.enable(RuntimeFlag.Interruption),
                  (_: Int) => ZIO.succeed(())
                )
            val exitSuccessEffect = Exit.succeed(0)
            val exitFailureEffect = Exit.fail("boom")

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
              ZIO.render(exitFailureEffect) == "Exit.Failure(cause=Fail(boom,Stack trace for thread \"zio-fiber-\":\n))"
            )
          } +
            test("enabled") {
              val effect = for {
                a <- ZIO.succeed(1)
                b <- ZIO.succeed(2)
                c  = a + b
              } yield c

              for {
                _      <- effect
                output <- ZTestLogger.logOutput
              } yield assertTrue(output.exists(_.message() == ZIO.render(effect)))
            }.provide(Runtime.enableFlags(RuntimeFlag.OpLog))
        }
    }
}
