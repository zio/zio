package zio.stm

import zio.test._
import zio.{UIO, ZIO, ZIOBaseSpec}

object STMLazinessSpec extends ZIOBaseSpec {

  def assertLazy(f: (=> Nothing) => Any): UIO[TestResult] =
    ZIO.succeed {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec = suite("STMLazinessSpec")(
    test("check")(assertLazy(ZSTM.check)),
    test("die")(assertLazy(ZSTM.die)),
    test("dieMessage")(assertLazy(ZSTM.dieMessage)),
    test("done")(assertLazy(ZSTM.done)),
    test("fail")(assertLazy(ZSTM.fail)),
    test("fromEither")(assertLazy(ZSTM.fromEither)),
    test("fromTry")(assertLazy(ZSTM.fromTry)),
    test("succeed")(assertLazy(ZSTM.succeed))
  )
}
