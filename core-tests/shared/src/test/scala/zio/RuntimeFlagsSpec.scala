package zio

import zio.test._

object RuntimeFlagsSpec extends ZIOBaseSpec {
  import RuntimeFlag._

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
            checkN(100)(Gen.runtimeFlags) { flags =>
              assertTrue(RuntimeFlags.toSet(flags).forall(flag => RuntimeFlags.isEnabled(flags)(flag)))
            }
          } +
            test("diff") {
              checkN(100)(Gen.runtimeFlags) { flags =>
                val diff = RuntimeFlags.diff(RuntimeFlags.none, flags)

                assertTrue(RuntimeFlags.Patch.patch(diff)(RuntimeFlags.none) == flags)
              }
            } +
            test("inverse") {
              checkN(100)(Gen.runtimeFlags) { flags =>
                val d = RuntimeFlags.diff(RuntimeFlags.none, flags)

                assertTrue(
                  RuntimeFlags.Patch.patch(RuntimeFlags.Patch.inverse(d))(flags) == RuntimeFlags.none &&
                    RuntimeFlags.Patch
                      .patch(RuntimeFlags.Patch.inverse(RuntimeFlags.Patch.inverse(d)))(RuntimeFlags.none) == flags
                )
              }
            }
        }
    }
}
