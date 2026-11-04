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
          test("logs evaluated ZIO operations at Debug level when RuntimeFlag.OpLog is enabled") {
            val effect = ZIO.succeed(1).map(_ + 1).flatMap(n => ZIO.succeed(n * 2))
            for {
              _       <- effect.provide(Runtime.enableFlags(RuntimeFlag.OpLog))
              output  <- ZTestLogger.logOutput
              messages = output.filter(_.logLevel == LogLevel.Debug).map(_.message())
            } yield assertTrue(
              messages.exists(_.startsWith("FlatMap(")),
              messages.exists(_.startsWith("Mapped(")),
              messages.exists(_.startsWith("Sync("))
            )
          } +
            test("does not log ZIO operations when RuntimeFlag.OpLog is disabled") {
              for {
                _       <- ZIO.succeed(1).map(_ + 1)
                output  <- ZTestLogger.logOutput
                messages = output.filter(_.logLevel == LogLevel.Debug).map(_.message())
              } yield assertTrue(messages.isEmpty)
            }
        }
    }
}
