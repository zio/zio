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
        test("enabled & disabled") {
          val flags =
            RuntimeFlags(Interruption, CurrentFiber)

          assertTrue(flags.enabled(Interruption)) &&
          assertTrue(flags.enabled(CurrentFiber)) &&
          assertTrue(flags.disabled(FiberRoots)) &&
          assertTrue(flags.disabled(OpLog)) &&
          assertTrue(flags.disabled(OpSupervision)) &&
          assertTrue(flags.disabled(RuntimeMetrics))
        } +
          test("enabled patching") {
            val on = RuntimeFlags.enable(CurrentFiber) <> RuntimeFlags.enable(OpLog)

            assertTrue(on(RuntimeFlags.none).toSet == Set[RuntimeFlag](CurrentFiber, OpLog))
          } +
          test("inverse") {
            val bothOn = RuntimeFlags.enable(CurrentFiber) <> RuntimeFlags.enable(OpLog)

            val initial = RuntimeFlags(CurrentFiber, OpLog)

            assertTrue(RuntimeFlags.enable(CurrentFiber).inverse(initial) == RuntimeFlags(OpLog)) &&
            assertTrue(bothOn.inverse(initial) == RuntimeFlags.none)
          } +
          test("diff") {
            val oneOn  = RuntimeFlags(CurrentFiber)
            val bothOn = RuntimeFlags(CurrentFiber, OpLog)

            assertTrue(oneOn.diff(bothOn) == RuntimeFlags.enable(OpLog))

          }
      } +
        suite("gen") {
          test("enabled") {
            checkN(100)(genRuntimeFlags) { flags =>
              assertTrue(flags.toSet.forall(flag => flags.enabled(flag)))
            }
          } +
            test("diff") {
              checkN(100)(genRuntimeFlags) { flags =>
                val diff = RuntimeFlags.none.diff(flags)

                assertTrue(diff(RuntimeFlags.none) == flags)
              }
            } +
            test("inverse") {
              checkN(100)(genRuntimeFlags) { flags =>
                val d = RuntimeFlags.none.diff(flags)

                assertTrue(d.inverse(flags) == RuntimeFlags.none && d.inverse.inverse(RuntimeFlags.none) == flags)
              }
            }
        }
    }
}
