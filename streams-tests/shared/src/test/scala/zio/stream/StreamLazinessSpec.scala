package zio.stream

import zio._
import zio.test._

object StreamLazinessSpec extends ZIOBaseSpec {

  def assertLazy(f: (=> Nothing) => Any): UIO[TestResult] =
    ZIO.succeed {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec = suite("StreamLazinessSpec")(
    suite("ZSink")(
      test("die")(assertLazy(ZSink.die)),
      test("fail")(assertLazy(ZSink.fail)),
      test("failCause")(assertLazy(ZSink.failCause)),
      test("succeed")(assertLazy(ZSink.succeed))
    ),
    suite("ZStream")(
      test("die")(assertLazy(ZStream.die)),
      test("dieMessage")(assertLazy(ZStream.dieMessage)),
      test("fail")(assertLazy(ZStream.fail)),
      test("fromChunk")(assertLazy(ZStream.fromChunk)),
      test("fromIterable")(assertLazy(ZStream.fromIterable)),
      test("halt")(assertLazy(ZStream.failCause)),
      test("succeed")(assertLazy(ZStream.succeed)),
      test("timeoutError")(
        assertLazy(
          ZStream.succeed(1).timeoutFail(_)(Duration.Infinity)
        )
      )
    )
  )
}
