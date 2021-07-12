package zio.stream

import zio.test._
import zio.{UIO, ZIOBaseSpec}

object StreamLazinessSpec extends ZIOBaseSpec {

  def assertLazy(f: (=> Nothing) => Any): UIO[TestResult] =
    UIO.succeed {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec: ZSpec[Environment, Failure] = suite("StreamLazinessSpec")(
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
      test("failCause")(assertLazy(ZStream.failCause)),
      test("fromChunk")(assertLazy(ZStream.fromChunk)),
      test("fromIterable")(assertLazy(ZStream.fromIterable)),
      test("succeed")(assertLazy(ZStream.succeed))
    )
  )
}
