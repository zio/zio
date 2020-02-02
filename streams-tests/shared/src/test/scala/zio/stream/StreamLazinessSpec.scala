package zio.stream

import zio.stream.ZStream.Pull
import zio.test._
import zio.{ UIO, ZIOBaseSpec }

object StreamLazinessSpec extends ZIOBaseSpec {

  def assertLazy(f: (=> Nothing) => Any): UIO[TestResult] =
    UIO.effectTotal {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec = suite("StreamLazinessSpec")(
    suite("Pull")(
      testM("die")(assertLazy(Pull.die)),
      testM("done")(assertLazy(Pull.done)),
      testM("emit")(assertLazy(Pull.emit)),
      testM("fail")(assertLazy(Pull.fail)),
      testM("fromTake")(assertLazy(Pull.fromTake)),
      testM("halt")(assertLazy(Pull.halt))
    ),
    suite("Sink")(
      testM("die")(assertLazy(Sink.die)),
      testM("fail")(assertLazy(Sink.fail)),
      testM("halt")(assertLazy(Sink.halt)),
      testM("succeed")(assertLazy(Sink.succeed))
    ),
    suite("Stream")(
      testM("die")(assertLazy(Stream.die)),
      testM("dieMessage")(assertLazy(Stream.dieMessage)),
      testM("fail")(assertLazy(Stream.fail)),
      testM("fromChunk")(assertLazy(Stream.fromChunk)),
      testM("fromIterable")(assertLazy(Stream.fromIterable)),
      testM("halt")(assertLazy(Stream.halt)),
      testM("succeed")(assertLazy(Stream.succeed))
    ),
    suite("ZSink")(
      testM("die")(assertLazy(ZSink.die)),
      testM("fail")(assertLazy(ZSink.fail)),
      testM("halt")(assertLazy(ZSink.halt)),
      testM("succeed")(assertLazy(ZSink.succeed))
    ),
    suite("ZStream")(
      testM("die")(assertLazy(ZStream.die)),
      testM("dieMessage")(assertLazy(ZStream.dieMessage)),
      testM("fail")(assertLazy(ZStream.fail)),
      testM("fromChunk")(assertLazy(ZStream.fromChunk)),
      testM("fromIterable")(assertLazy(ZStream.fromIterable)),
      testM("halt")(assertLazy(ZStream.halt)),
      testM("succeed")(assertLazy(ZStream.succeed))
    ),
    suite("ZStreamChunk")(
      testM("succeed")(assertLazy(ZStreamChunk.succeed))
    )
  )
}
