package zio.stm

import zio.ZIOBaseSpec
import zio.test._

object STMLazinessSpec extends ZIOBaseSpec {

  def assertLazy[A, B](f: (=> A) => B): TestResult = {
    val _ = f(throw new RuntimeException("not lazy"))
    assertCompletes
  }

  def spec = suite("STMLazinessSpec")(
    suite("STM")(
      test("check")(assertLazy(STM.check)),
      test("die")(assertLazy(STM.die)),
      test("dieMessage")(assertLazy(STM.dieMessage)),
      test("done")(assertLazy(STM.done)),
      test("fail")(assertLazy(STM.fail)),
      test("fromEither")(assertLazy(STM.fromEither)),
      test("fromTry")(assertLazy(STM.fromTry)),
      test("succeed")(assertLazy(STM.succeed))
    ),
    suite("ZSTM")(
      test("check")(assertLazy(ZSTM.check)),
      test("die")(assertLazy(ZSTM.die)),
      test("dieMessage")(assertLazy(ZSTM.dieMessage)),
      test("done")(assertLazy(ZSTM.done)),
      test("fail")(assertLazy(ZSTM.fail)),
      test("fromEither")(assertLazy(ZSTM.fromEither)),
      test("fromTry")(assertLazy(ZSTM.fromTry)),
      test("succeed")(assertLazy(ZSTM.succeed))
    )
  )
}
