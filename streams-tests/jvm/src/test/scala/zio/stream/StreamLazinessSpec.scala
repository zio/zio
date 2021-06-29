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
      testM("die")(assertLazy(ZSink.die)),
      testM("fail")(assertLazy(ZSink.fail)),
      testM("failCause")(assertLazy(ZSink.failCause)),
      testM("succeed")(assertLazy(ZSink.succeed))
    ),
    suite("ZStream")(
      testM("die")(assertLazy(ZStream.die)),
      testM("dieMessage")(assertLazy(ZStream.dieMessage)),
      testM("fail")(assertLazy(ZStream.fail)),
      testM("failCause")(assertLazy(ZStream.failCause)),
      testM("fromChunk")(assertLazy(ZStream.fromChunk)),
      testM("fromIterable")(assertLazy(ZStream.fromIterable)),
      testM("succeed")(assertLazy(ZStream.succeed))
    )
  )
}
