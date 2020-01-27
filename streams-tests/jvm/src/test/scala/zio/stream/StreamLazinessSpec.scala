package zio.stream

import zio.ZIOBaseSpec
import zio.stream.ZStream.Pull
import zio.test._

object StreamLazinessSpec extends ZIOBaseSpec {

  def assertLazy[A, B](f: (=> A) => B): TestResult = {
    val _ = f(throw new RuntimeException("not lazy"))
    assertCompletes
  }

  def spec = suite("StreamLazinessSpec")(
    suite("Pull")(
      test("die")(assertLazy(Pull.die)),
      test("done")(assertLazy(Pull.done)),
      test("emit")(assertLazy(Pull.emit)),
      test("fail")(assertLazy(Pull.fail)),
      test("fromTake")(assertLazy(Pull.fromTake)),
      test("halt")(assertLazy(Pull.halt))
    ),
    suite("Sink")(
      test("die")(assertLazy(Sink.die)),
      test("fail")(assertLazy(Sink.fail)),
      test("halt")(assertLazy(Sink.halt)),
      test("succeed")(assertLazy(Sink.succeed))
    ),
    suite("Stream")(
      test("die")(assertLazy(Stream.die)),
      test("dieMessage")(assertLazy(Stream.dieMessage)),
      test("fail")(assertLazy(Stream.fail)),
      test("fromChunk")(assertLazy(Stream.fromChunk)),
      test("fromIterable")(assertLazy(Stream.fromIterable)),
      test("halt")(assertLazy(Stream.halt)),
      test("succeed")(assertLazy(Stream.succeed))
    ),
    suite("ZSink")(
      test("die")(assertLazy(ZSink.die)),
      test("fail")(assertLazy(ZSink.fail)),
      test("halt")(assertLazy(ZSink.halt)),
      test("succeed")(assertLazy(ZSink.succeed))
    ),
    suite("ZStream")(
      test("die")(assertLazy(ZStream.die)),
      test("dieMessage")(assertLazy(ZStream.dieMessage)),
      test("fail")(assertLazy(ZStream.fail)),
      test("fromChunk")(assertLazy(ZStream.fromChunk)),
      test("fromIterable")(assertLazy(ZStream.fromIterable)),
      test("halt")(assertLazy(ZStream.halt)),
      test("succeed")(assertLazy(ZStream.succeed))
    ),
    suite("ZStreamChunk")(
      test("succeed")(assertLazy(ZStreamChunk.succeed))
    )
  )
}
